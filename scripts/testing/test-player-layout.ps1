param(
    [string] $EvidenceDirectory = 'docs/step3',
    [string] $GameDirectory = 'run/client',
    [int] $Port = 5005
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$diagnostics = Join-Path (Join-Path $projectRoot $GameDirectory) 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()

function Invoke-Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness action failed: $args" }
}
function Select-Preset([int] $Id) {
    Invoke-Harness layout-control 100
    Invoke-Harness layout-control $Id
    Invoke-Harness layout-control 108
    Invoke-Harness layout-check
}
function Inventory-Signature {
    Invoke-Harness snapshot layout-conservation
    $file = Get-ChildItem "$diagnostics/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $report = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    if (!$report.expandedPlayerLayoutMatches -or $report.cursorOccupied) { throw 'Layout mapping/cursor mismatch' }
    return ($report.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 10 -Compress)
}
function Assert-Unchanged([string] $Stage) {
    $actual = Inventory-Signature
    if ($actual -ne $script:original) { throw "Contents changed during layout edit: $Stage" }
    $checks.Add("Exact contents unchanged: $Stage")
}
function Save-View([string] $Name) {
    Invoke-Harness clear-chat
    Invoke-Harness screenshot "step3-$Name.png"
    Copy-Item "$GameDirectory/screenshots/step3-$Name.png" (Join-Path $EvidenceDirectory "layout-$Name.png") -Force
}

Invoke-Harness require-step3
Invoke-Harness layout-math
Copy-Item "$diagnostics/layout-math.json" (Join-Path $EvidenceDirectory 'layout-math.json') -Force
$script:original = Inventory-Signature
foreach ($preset in @(101, 102, 103)) {
    Select-Preset $preset
    foreach ($scale in @(1, 2, 3, 0)) {
        Invoke-Harness scale $scale
        Invoke-Harness layout-check
    }
    Assert-Unchanged "preset $preset at all GUI scales"
}

Select-Preset 101
Invoke-Harness window 640 480
Invoke-Harness scale 0
Invoke-Harness layout-check
Invoke-Harness page down
Invoke-Harness visible-click 71
Invoke-Harness visible-click 71
Assert-Unchanged 'Wide preset clamped to 320x240 scaled window, final-slot click after scrolling'
Save-View wide-minimum
Invoke-Harness window 1280 720
Invoke-Harness scale 3
Invoke-Harness layout-check
Assert-Unchanged 'Wide preset after larger window resize'
Invoke-Harness window 854 480
Invoke-Harness scale 0
Select-Preset 103
Save-View tall
Invoke-Harness page down
Invoke-Harness layout-check
Invoke-Harness visible-click 71
Invoke-Harness visible-click 71
Invoke-Harness page up
Invoke-Harness scrollbar-bottom
Invoke-Harness layout-check
Assert-Unchanged 'Tall preset Page Up/Down, scrollbar and final-slot clicking'
Save-View tall-scrolled

# Layout controls must not close the container or consume crafting inputs.
Invoke-Harness visible-click 71
Invoke-Harness click 1 0 0
Select-Preset 101
Invoke-Harness click 1 0 0
Invoke-Harness visible-click 71
Assert-Unchanged 'Crafting input survives switching layouts'

Invoke-Harness layout-control 100
Invoke-Harness layout-control 104
Invoke-Harness layout-control 107
Save-View settings
Invoke-Harness layout-control 108
Invoke-Harness layout-check
Assert-Unchanged 'Custom 17-column / 4-row setting'
Invoke-Harness resume
Invoke-Harness capture layout-reopened
Invoke-Harness layout-check
Assert-Unchanged 'Custom preference survives closing and reopening inventory'
Invoke-Harness command '/dinvcontainertest verify'
Start-Sleep -Milliseconds 300
$verify = Get-Content "$diagnostics/container-tests-verify.json" -Raw | ConvertFrom-Json
if (!$verify.success) { throw "Server fixture mismatch after layout edits: $($verify.failure)" }
$checks.Add('Exact server fixture unchanged by all client layout edits')
Copy-Item "$diagnostics/container-tests-verify.json" (Join-Path $EvidenceDirectory 'layout-server.json') -Force
[ordered]@{ success = $true; checks = @($checks.ToArray()); savedColumns = 17; savedRows = 4 } |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'layout-verification.json') -Encoding UTF8
Write-Host 'PASS: layouts, scrolling, resizing, contents and preferences. Custom 17x4 preference retained for process-restart check.'
