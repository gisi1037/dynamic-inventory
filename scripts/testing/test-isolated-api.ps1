param(
    [ValidateSet('Developer','Observer')] [string] $PlayerName = 'Observer',
    [int] $Port = 5008,
    [string] $GameDirectory = 'run/isolated/client-observer',
    [string] $EvidenceDirectory = 'docs/qualification/stabilization/isolated'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
$launch = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port -GameDirectory $GameDirectory `
    -Profile isolated -EvidenceDirectory $EvidenceDirectory
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$checks = [Collections.Generic.List[string]]::new()
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
    $checks.Add($Message)
}
function Snapshot([string] $Name) {
    $since = [datetime]::UtcNow
    Harness snapshot "isolated-$Name" | Out-Null
    $file = Get-ChildItem -LiteralPath $diagnostics -Filter 'client-*.json' |
        Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $file) { throw "No fresh isolated snapshot: $Name" }
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Name.json") -Force
    return Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
}
function Signature($State) {
    return $State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,item,count,damage,nbtCanonical |
        ConvertTo-Json -Depth 12 -Compress
}

Harness require-qualification DI-Baseline-Isolated-Stabilization-Step2-Qualification | Out-Null
Harness background | Out-Null
Harness resume | Out-Null
Harness window 1920 1080 | Out-Null
$runtimePath = Join-Path $diagnostics 'runtime.json'
$runtime = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json
$unexpected = @($runtime.mods | Where-Object { $_.id -in @('modularui','modularui2','backhand','Baubles','bogosorter') })
Check ($unexpected.Count -eq 0) 'Isolated runtime has no MUI1, MUI2, Backhand, Baubles or BogoSorter'
Copy-Item -LiteralPath $runtimePath -Destination (Join-Path $EvidenceDirectory 'runtime.json') -Force
Harness command "/clear $PlayerName" | Out-Null
Harness command "/give $PlayerName minecraft:diamond 7 0 {display:{Name:`"Isolated API payload`"},fixture:{nested:4242}}" | Out-Null
Harness command '/dinvadapters chest' | Out-Null
$binding = Harness ui-binding
Check ($binding -match 'adapter=dynamicinventory:chest native=63 ordinary=81 count=108 canonical=true layout=true') `
    'Built-in native chest binds all 81 canonical ordinary slots with 108 total container slots'
foreach ($scale in @(1,2,3,0)) {
    Check ((Harness scale $scale) -match 'PASS geometry') "Isolated native chest fits GUI scale $scale"
    Harness foreign-check | Out-Null
}
Harness scale 2 | Out-Null
$auditOutput = Harness ui-audit
$auditMatch = [regex]::Match($auditOutput, '(?m)^uiAudit=(.+)$')
if (-not $auditMatch.Success) { throw 'Isolated live audit returned no path' }
$auditPath = $auditMatch.Groups[1].Value.Trim()
$audit = Get-Content -LiteralPath $auditPath -Raw | ConvertFrom-Json
Copy-Item -LiteralPath $auditPath -Destination (Join-Path $EvidenceDirectory 'chest-audit.json') -Force
$hotbar = ($audit.playerSlots | Where-Object ordinaryIndex -eq 0).containerSlot
$before = Snapshot before
if (-not $before.mainInventory[0].item -or $before.mainInventory[0].nbtCanonical -notmatch '4242') {
    throw 'Isolated click probe has no tagged payload in its first hotbar slot'
}
Harness input-click $hotbar | Out-Null
$held = Snapshot held
Harness input-click $hotbar | Out-Null
$after = Snapshot after
Check ($held.cursorOccupied -and -not $after.cursorOccupied -and (Signature $before) -eq (Signature $after)) `
    'Real mouse pickup and return preserves every native/container item count, damage and nested NBT'
& ./scripts/tools/New-InventoryUiAdapter.ps1 -AuditPath $auditPath -OutputDirectory '.setup/ui-api-scaffolds/isolated' -Force | Out-Null
Check ($audit.candidate -and $audit.adapterId -eq 'dynamicinventory:chest' -and $audit.playerSlots.Count -eq 81 `
    -and $audit.missingLegacyIndices.Count -eq 0 -and $audit.duplicateOrdinaryIndices.Count -eq 0) `
    'Live isolated native audit exposes every ordinary slot and is accepted by the adapter generator'
Harness clear-chat | Out-Null
Harness screenshot step3-stabilization-isolated-native.png | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDirectory 'screenshots/step3-stabilization-isolated-native.png') `
    -Destination (Join-Path $EvidenceDirectory 'native.png') -Force
Harness resume | Out-Null
$report = @{success=$true; scope='source-isolated-without-mui'; storageSlots=72; checks=@($checks); checkCount=$checks.Count;
    fullPackQualified=$false; capturedAtUtc=[datetime]::UtcNow.ToString('o')}
foreach ($key in $launch.Keys) { $report[$key] = $launch[$key] }
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "Isolated built-in API PASS: $($checks.Count) checks"
