param(
    [int] $Port = 5007,
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $ServerGameDirectory = '',
    [string] $EvidenceDirectory = 'docs/qualification/ui-api/mui2-integrated',
    [ValidateSet('isolated','rc2-integration')] [string] $Profile = 'rc2-integration',
    [string] $PeerMetadataPath = '',
    [string] $ServerLaunchManifest = ''
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
    if (!$file) { throw "No fresh MUI2 API snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
if ($ServerGameDirectory) { Harness require-dedicated | Out-Null }
else { Harness require-qualification DI-Baseline-Containers-RC2-Qualification | Out-Null }
Harness background | Out-Null
Harness resume | Out-Null
Harness window 1920 1080 | Out-Null
foreach ($mode in @('selftest','wide','narrow')) {
    Harness resume | Out-Null
    $since = [datetime]::UtcNow
    Harness command "/dinvmuiapi $mode" | Out-Null
    $path = Join-Path $serverDiagnostics 'mui2-api-selftest.json'
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -ge $since) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    if (!(Test-Path $path) -or (Get-Item $path).LastWriteTimeUtc -lt $since) { throw "No fresh MUI2 factory report: $mode" }
    $server = Get-Content $path -Raw | ConvertFrom-Json
    Check $server.success "$mode server widget and canonical slot lookup assertions pass"
    Copy-Item $path (Join-Path $EvidenceDirectory "$mode-server.json") -Force
    Check ((Harness foreign-check) -match 'count=81 ordinaryIds=') "$mode uses all 81 canonical live player slots"
    $before = Snapshot "$mode-before"
    Check ($before.screen -eq 'com.cleanroommc.modularui.screen.GuiContainerWrapper') "$mode uses a real MUI2 container wrapper"
    foreach ($scale in @(1,2,3,0)) {
        Check ((Harness scale $scale) -match 'PASS geometry') "$mode GUI fits scale $scale"
        Harness foreign-check | Out-Null
    }
    Harness scale 2 | Out-Null
    $scroll = Harness scroll-info
    if ($mode -eq 'wide') { Check ($scroll -match 'actual=72 visible=72') '18-column by 4-row factory displays every storage slot without scrolling' }
    if ($mode -eq 'narrow') { Check ($scroll -match 'actual=216 visible=36') '6-column by 2-row factory retains a 36-pixel viewport over all 72 storage slots' }
    Check ([bool]$before.mainInventory[0].item) "$mode mouse probe starts with an occupied first hotbar slot"
    Harness input-click 0 | Out-Null
    Check (Snapshot "$mode-held").cursorOccupied "$mode real mouse click picks up its live hotbar stack"
    Harness input-click 0 | Out-Null
    $after = Snapshot "$mode-after-input"
    Check ((MainSignature $before) -eq (MainSignature $after) -and !$after.cursorOccupied) "$mode real mouse pickup/return preserves inventory and cursor"
    if ($mode -eq 'narrow') {
        Harness input-wheel 9 -1 | Out-Null
        $afterScroll = Harness scroll-info
        $match = [regex]::Match($afterScroll, '(?:^|\s)scroll=(\d+)')
        Check ($match.Success -and [int]$match.Groups[1].Value -gt 0) 'Fixed narrow storage responds to real mouse wheel input'
        Check ((MainSignature (Snapshot 'narrow-after-scroll')) -eq (MainSignature $before)) 'Scrolling changes presentation while preserving every canonical stack'
    }
    $auditOutput = Harness ui-audit
    $auditPath = [regex]::Match($auditOutput, '(?m)^uiAudit=(.+)$').Groups[1].Value.Trim()
    $audit = Get-Content -LiteralPath $auditPath -Raw | ConvertFrom-Json
    & ./scripts/tools/New-InventoryUiAdapter.ps1 -AuditPath $auditPath -OutputDirectory ".setup/ui-api-scaffolds/mui2-$mode" -Force | Out-Null
    Check $true "$mode live framework audit is accepted by the scaffold generator"
    Check ($audit.framework -eq 'MUI2' -and $audit.candidate -and $audit.playerSlots.Count -eq 81) "$mode audit recognizes framework-backed canonical MUI2 slots"
    Copy-Item $auditPath (Join-Path $EvidenceDirectory "$mode-audit.json") -Force
    Harness clear-chat | Out-Null
    Harness screenshot "step3-ui-api-mui2-$mode.png" | Out-Null
    Copy-Item (Join-Path $GameDirectory "screenshots/step3-ui-api-mui2-$mode.png") (Join-Path $EvidenceDirectory "$mode.png") -Force
}
Harness resume | Out-Null
$report = @{success=$true; scope=$(if ($ServerGameDirectory) {'source-dedicated'} else {'source-integrated'});
    capturedAtUtc=[datetime]::UtcNow.ToString('o');
    checks=@($checks); checkCount=$checks.Count; fullPackQualified=$false}
foreach ($key in $launchEvidence.Keys) { $report[$key] = $launchEvidence[$key] }
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "MUI2 public API qualification PASS: $($checks.Count) checks"
