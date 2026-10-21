param(
    [int] $Port = 5007,
    [ValidateSet('Developer','Observer')] [string] $PlayerName = 'Developer',
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $ServerGameDirectory = '',
    [string] $EvidenceDirectory = 'docs/qualification/ui-api/integrated',
    [ValidateSet('isolated','rc2-integration','daily-2026-10-19')] [string] $Profile = 'rc2-integration',
    [string] $PeerMetadataPath = '',
    [string] $ServerLaunchManifest = '',
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$serverDiagnostics = if ($ServerGameDirectory) { Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory' } else { $diagnostics }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
$launchEvidence = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port -GameDirectory $GameDirectory `
    -Profile $Profile -EvidenceDirectory $EvidenceDirectory -PeerMetadataPath $PeerMetadataPath `
    -ServerGameDirectory $ServerGameDirectory -ServerLaunchManifest $ServerLaunchManifest
$checks = [Collections.Generic.List[string]]::new()
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot $Label | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw "No fresh snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Audit([string] $Label) {
    $result = Harness ui-audit
    $match = [regex]::Match($result, '(?m)^uiAudit=(.+)$')
    if (!$match.Success) { throw 'Audit did not return its saved path' }
    $path = $match.Groups[1].Value.Trim()
    Copy-Item -LiteralPath $path -Destination (Join-Path $EvidenceDirectory "$Label-audit.json") -Force
    & ./scripts/tools/New-InventoryUiAdapter.ps1 -AuditPath $path -OutputDirectory ".setup/ui-api-scaffolds/$Label" -Force | Out-Null
    Check $true "$Label live audit is accepted by the scaffold generator"
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
function SaveView([string] $Label) {
    Harness clear-chat | Out-Null
    Harness screenshot "step3-ui-api-$Label.png" | Out-Null
    Copy-Item (Join-Path $GameDirectory "screenshots/step3-ui-api-$Label.png") (Join-Path $EvidenceDirectory "$Label.png") -Force
}
if ($ServerGameDirectory) { Harness require-dedicated | Out-Null }
else { Harness require-qualification $WorldName | Out-Null }
# The real-input checks below click the final storage row; give the test a known viewport.
Harness window 1280 960 | Out-Null
Harness background | Out-Null
Harness capture ui-api-test-layout | Out-Null
Harness layout-set 18 4 | Out-Null
Harness resume | Out-Null
$since = [datetime]::UtcNow
Harness command '/dinvuiapi selftest' | Out-Null
$selftestPath = Join-Path $serverDiagnostics 'ui-api-selftest.json'
$deadline = [datetime]::UtcNow.AddSeconds(20)
do {
    if ((Test-Path $selftestPath) -and (Get-Item $selftestPath).LastWriteTimeUtc -ge $since) { break }
    Start-Sleep -Milliseconds 100
} while ([datetime]::UtcNow -lt $deadline)
if (!(Test-Path $selftestPath) -or (Get-Item $selftestPath).LastWriteTimeUtc -lt $since) { throw 'No fresh UI API selftest result' }
$selftest = Get-Content $selftestPath -Raw | ConvertFrom-Json
Check $selftest.success 'Server public API selftests passed'
Copy-Item $selftestPath (Join-Path $EvidenceDirectory 'server-selftest.json') -Force
# The guarded fixture world is disposable; use one deterministic payload for real click packets.
Harness command "/clear $PlayerName" | Out-Null
Harness command "/give $PlayerName minecraft:diamond 7" | Out-Null
foreach ($family in @('native','filtered')) {
    Harness resume | Out-Null
    Harness command "/dinvuiapi $family" | Out-Null
    $binding = Harness ui-binding
    Check ($binding -match 'canonical=true layout=true') "$family lifecycle attaches canonical inventory and presentation without a targeted mixin"
    Check ((Harness foreign-check) -match 'ordinaryIds=') "$family exposes every ordinary slot exactly once"
    $before = Snapshot "$family-before"
    $audit = Audit $family
    Check ($audit.candidate -and $audit.adapterId -eq "dinvuiapifixtures:$(@{native='native-chest';filtered='filtered-machine'}[$family])") "$family audit identifies the registered adapter"
    Check ($audit.playerSlots.Count -eq 81 -and $audit.missingLegacyIndices.Count -eq 0 -and $audit.duplicateOrdinaryIndices.Count -eq 0) "$family audit finds all 81 canonical ordinary slots"
    $afterAudit = Snapshot "$family-after-audit"
    Check ((MainSignature $before) -eq (MainSignature $afterAudit) -and !$afterAudit.cursorOccupied) "$family audit leaves inventory and cursor unchanged"
    foreach ($scale in @(1,2,3,0)) {
        Check ((Harness scale $scale) -match 'PASS geometry') "$family fits GUI scale $scale"
        Harness foreign-check | Out-Null
    }
    Harness scale 2 | Out-Null
    $hotbar = ($audit.playerSlots | Where-Object ordinaryIndex -eq 0).containerSlot
    Check ([bool]$before.mainInventory[0].item) "$family mouse probe starts with an occupied first hotbar slot"
    Harness input-click $hotbar | Out-Null
    Check (Snapshot "$family-held").cursorOccupied "$family first real mouse click picks up a stack"
    Harness input-click $hotbar | Out-Null
    $after = Snapshot "$family-after-input"
    Check ((MainSignature $before) -eq (MainSignature $after) -and !$after.cursorOccupied) "$family real mouse pickup and return preserves exact contents"
    if ($family -eq 'native') {
        $lastSlot = ($audit.playerSlots | Where-Object ordinaryIndex -eq 80).containerSlot
        $firstStorage = ($audit.playerSlots | Where-Object ordinaryIndex -eq 9).containerSlot
        Harness input-click $hotbar | Out-Null
        Harness input-click $lastSlot | Out-Null
        $placed = Snapshot 'native-final-slot-placed'
        Check (!$placed.cursorOccupied -and $placed.activeContainer.slots[$lastSlot].count -eq 7) 'Real mouse places all seven probe diamonds into the final expanded slot'
        Harness click $lastSlot 0 1 | Out-Null
        $shifted = Snapshot 'native-final-slot-shifted'
        Check (!$shifted.cursorOccupied -and $shifted.activeContainer.slots[0].count -eq 7 -and !$shifted.activeContainer.slots[$lastSlot].item) 'Client and server quick-move the added source slot into the machine through the public policy'
        Harness click 0 0 1 | Out-Null
        $returned = Snapshot 'native-machine-shifted'
        Check ($returned.activeContainer.slots[$firstStorage].count -eq 7 -and !$returned.activeContainer.slots[0].item) 'Native machine quick-move returns all diamonds to canonical expanded storage'
        Harness input-click $firstStorage | Out-Null
        Harness input-click $hotbar | Out-Null
        $restored = Snapshot 'native-transfer-restored'
        Check ((MainSignature $restored) -eq (MainSignature $before) -and !$restored.cursorOccupied) 'Networked public-adapter transfer roundtrip conserves exact inventory contents'
    }
    SaveView $family
}
Harness resume | Out-Null
Harness command '/dinvuiapi unknown' | Out-Null
Check ((Harness ui-binding none) -match 'PASS unregistered count=63') 'Unregistered subclass keeps its original 63 native slots'
$unknown = Audit 'unknown'
Check (!$unknown.adapterId -and $unknown.slotCount -eq 63) 'Audit does not register or expand an unknown GUI'
SaveView 'unknown'
Harness resume | Out-Null
Harness capture ui-api-restore-layout | Out-Null
Harness layout-set 18 4 | Out-Null
Harness resume | Out-Null
$report = @{success=$true; scope= $(if ($ServerGameDirectory) {'source-dedicated'} else {'source-integrated'});
    capturedAtUtc=[datetime]::UtcNow.ToString('o');
    checks=@($checks); checkCount=$checks.Count; serverCheckCount=$selftest.checkCount; fullPackQualified=$false}
foreach ($key in $launchEvidence.Keys) { $report[$key] = $launchEvidence[$key] }
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "UI API source qualification PASS: $($checks.Count) client checks"
