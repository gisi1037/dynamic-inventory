param([string] $EvidenceDirectory = 'docs/qualification/backhand/layout')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$diagnostics = 'run/rc2-backhand/client/diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
function Harness {
    & $java --add-modules jdk.jdi -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
Harness capture backhand-layout-matrix
$cases = 0
foreach ($preset in @(@{columns=9;rows=6},@{columns=18;rows=3},@{columns=6;rows=9})) {
    Harness layout-set $preset.columns $preset.rows
    foreach ($scale in @(1,2,3,0)) {
        Harness scale $scale
        Harness layout-check
        $cases++
    }
}
Harness layout-set 18 3
Harness window 640 480
Harness scale 0
Harness layout-check
Harness page down
Harness visible-click 72
Harness visible-click 72
Harness layout-check
$since = [datetime]::UtcNow
Harness command '/dinvqualify verify'
$path = "$diagnostics/qualification/Developer-server-verify.json"
$deadline = [datetime]::UtcNow.AddSeconds(30)
while ((Get-Item $path).LastWriteTimeUtc -le $since -and [datetime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 150 }
if ((Get-Item $path).LastWriteTimeUtc -le $since) { throw 'No new server verification' }
$server = Get-Content $path -Raw | ConvertFrom-Json
if (!$server.success) { throw "Contents changed: $($server.failure)" }
Harness snapshot backhand-layout-minimum
$file = Get-ChildItem "$diagnostics/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
$client = Get-Content $file.FullName -Raw | ConvertFrom-Json
for ($raw = 0; $raw -lt 64; $raw++) {
    foreach ($field in @('item','count','damage','nbtCanonical')) {
        if ($client.mainInventory[$raw].$field -ne $server.inventory.mainInventory[$raw].$field) { throw "Layout content mismatch $raw/$field" }
    }
}
Copy-Item $file.FullName (Join-Path $EvidenceDirectory 'minimum-client.json') -Force
Copy-Item $path (Join-Path $EvidenceDirectory 'minimum-server.json') -Force
Harness clear-chat
Harness screenshot step3-backhand-minimum.png
Copy-Item run/rc2-backhand/client/screenshots/step3-backhand-minimum.png (Join-Path $EvidenceDirectory 'minimum.png') -Force
Harness window 854 480
Harness scale 0
Harness layout-set 9 6
@{success=$true;presets=3;scales=@(1,2,3,0);geometryCases=$cases;offhandVisible=$true;minimumScaledWindow='320x240';finalOrdinarySlotClickable=$true;contentsPreserved=$true} |
    ConvertTo-Json | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host 'PASS Backhand slot visibility across presets, GUI scales and minimum-window scrolling.'
