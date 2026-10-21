param(
    [int] $Port = 5005,
    [ValidateRange(27,216)][int] $ExpectedStorageSlots = 72,
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $EvidenceDirectory = 'docs/qualification/ui-api/gt-mui2',
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$marker = Join-Path (Split-Path $GameDirectory -Parent) '.dynamicinventory-dev-instance'
$packTarget = (Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).version
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
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
    if (!$file) { throw "No fresh GT API snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Set-MachineMode([bool] $Expanded) {
    Harness resume | Out-Null
    Harness capture gt-api-settings | Out-Null
    Harness layout-control 100 | Out-Null
    $mode = Harness machine-mode
    if (($mode -match 'expandMachineInventory=true') -ne $Expanded) { Harness layout-control 110 | Out-Null }
    Check (((Harness machine-mode) -match 'expandMachineInventory=true') -eq $Expanded) "Machine layout preference set to expanded=$Expanded"
    Harness layout-control 108 | Out-Null
    Harness resume | Out-Null
}
Harness require-qualification $WorldName | Out-Null
Harness background | Out-Null
$originalExpanded = (Harness machine-mode) -match 'expandMachineInventory=true'
Set-MachineMode $false
Harness window 1920 1080 | Out-Null
Harness command '/tp @p 0 4 0' | Out-Null
Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
Harness command '/setblock 2 4 0 gregtech:gt.blockmachines 0 replace {mID:9231}' | Out-Null
Harness block-open 2 4 0 | Out-Null
Harness scale 0 | Out-Null
$mapping = Harness foreign-check
$match = [regex]::Match($mapping, 'ordinaryIds=\[([^\]]+)\]')
Check $match.Success 'Actual GT buffer exposes its canonical player slot map'
$ids = @($match.Groups[1].Value.Split(',') | ForEach-Object { [int]$_.Trim() })
$before = Snapshot 'gt-before'
Check ($before.screen -eq 'com.cleanroommc.modularui.screen.GuiContainerWrapper' -and $ids.Count -eq ($ExpectedStorageSlots + 9)) "Actual GT buffer uses the shared stock MUI2 binding for all $($ExpectedStorageSlots + 9) ordinary slots"
Check ($before.mainInventory[0].item -and !$before.cursorOccupied) 'GT input probe starts with an occupied hotbar and an empty cursor'
$occupiedStorage = [bool]$before.mainInventory[9].item
$widgetGeometry = Harness mui-widget-info
# The screen's actual width can move this viewport. Preserve the regression condition,
# rather than requiring one historical absolute coordinate: a first-column click's
# screen X exceeds the LOCAL scrollbar edge, but its local X is still inside the grid.
$viewportMatch = [regex]::Match($widgetGeometry, '(?m)^.*InventoryScrollWidget\s+.*?\bx=(-?\d+)\s+.*?\bwidth=(\d+)')
Check ($viewportMatch.Success -and
    ([int]$viewportMatch.Groups[1].Value + 9) -ge ([int]$viewportMatch.Groups[2].Value - 6) -and
    9 -lt ([int]$viewportMatch.Groups[2].Value - 6)) 'Viewport reproduces the absolute/local scrollbar hit-test collision'
$widgetGeometry | Set-Content (Join-Path $EvidenceDirectory 'widget-geometry.log')
Harness input-click $ids[0] | Out-Null
Check (Snapshot 'gt-held').cursorOccupied 'Real GT hotbar click picks up the probe stack'
Harness input-click $ids[9] | Out-Null
$placed = Snapshot 'gt-first-storage-placed'
Check ($placed.cursorOccupied -eq $occupiedStorage -and $placed.mainInventory[9].item -eq $before.mainInventory[0].item -and $placed.mainInventory[9].count -eq $before.mainInventory[0].count) 'First storage column accepts a real click at the former absolute-coordinate scrollbar collision'
# Preserve an existing first-column stack by swapping it into the vacated hotbar.
# This lets the test use the exact regression coordinate without clearing user items.
if ($occupiedStorage) { Harness input-click $ids[0] | Out-Null }
Harness input-click $ids[9] | Out-Null
Harness input-click $ids[0] | Out-Null
if ($occupiedStorage) { Harness input-click $ids[9] | Out-Null }
$restored = Snapshot 'gt-restored'
Check (!$restored.cursorOccupied -and (MainSignature $restored) -eq (MainSignature $before)) 'Real GT pickup/place roundtrip restores every player stack and empty cursor'
Harness input-wheel $ids[9] -1 | Out-Null
$scroll = Harness scroll-info
$scrollMatch = [regex]::Match($scroll, '(?:^|\s)scroll=(\d+)')
Check ($scrollMatch.Success -and [int]$scrollMatch.Groups[1].Value -gt 0) 'Actual GT storage scrolls through the corrected first-column viewport'
$auditOutput = Harness ui-audit
$auditPath = [regex]::Match($auditOutput, '(?m)^uiAudit=(.+)$').Groups[1].Value.Trim()
$audit = Get-Content -LiteralPath $auditPath -Raw | ConvertFrom-Json
Check ($audit.framework -eq 'MUI2' -and $audit.candidate -and @($audit.playerSlots | Where-Object ordinaryIndex -ge 0).Count -eq ($ExpectedStorageSlots + 9)) 'Production audit recognizes all canonical framework-backed GT player slots'
Copy-Item $auditPath (Join-Path $EvidenceDirectory 'gt-audit.json') -Force
& ./scripts/tools/New-InventoryUiAdapter.ps1 -AuditPath $auditPath -OutputDirectory .setup/ui-api-scaffolds/gt-production -Force | Out-Null
$checks.Add('Live actual GT audit passes scaffold-generator validation')
Check ((MainSignature (Snapshot 'gt-after-audit')) -eq (MainSignature $before)) 'Scrolling and read-only audit conserve exact player inventory contents'
Harness clear-chat | Out-Null
Harness screenshot 'step3-ui-api-gt-mui2.png' | Out-Null
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-ui-api-gt-mui2.png') (Join-Path $EvidenceDirectory 'gt-mui2.png') -Force
Harness resume | Out-Null
Set-MachineMode $originalExpanded
Harness capture gt-api-final-player | Out-Null
Harness scale 2 | Out-Null
@{success=$true; prototype='0.8.0-dev'; packTarget=$packTarget; fullPack=$true;
    jarSha256=(Get-FileHash (Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar') -Algorithm SHA256).Hash;
    checks=@($checks); checkCount=$checks.Count; capturedAtUtc=[datetime]::UtcNow.ToString('o');
    scope='One actual GT MUI2 chest buffer: standard player binding, former absolute/local scrollbar collision, real input, scrolling, live audit/scaffold, exact inventory conservation; no universal MUI2 acceptance'} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "PASS actual GT MUI2 API regression: $($checks.Count) checks"
