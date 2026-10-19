param(
    [string] $EvidenceDirectory = 'docs/step3',
    [string] $GameDirectory = 'run/client',
    [int] $Port = 5005,
    [string] $WorldName = 'DI-Baseline-20261017-Step3'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
& 'C:\Program Files\Java\jdk-25\bin\javac.exe' --add-modules jdk.jdi -d .setup scripts/testing/StorageHarness.java
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed' }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$diagnostics = Join-Path (Join-Path $projectRoot $GameDirectory) 'diagnostics/dynamicinventory'
$checks = [Collections.Generic.List[string]]::new()

function Invoke-Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness action failed: $args" }
}

function Wait-Report([string] $Pattern, [datetime] $Since) {
    $deadline = [datetime]::UtcNow.AddSeconds(8)
    do {
        $file = Get-ChildItem -Path $diagnostics -Filter $Pattern | Where-Object { $_.LastWriteTimeUtc -gt $Since } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($file) { return $file }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Pattern"
}

function Invoke-ServerTest([string] $Action) {
    $since = [datetime]::UtcNow
    Invoke-Harness command "/dinvcontainertest $Action"
    $file = Wait-Report "container-tests-$Action.json" $since
    $report = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    if (!$report.success) { throw "Server fixture failed: $($report.failure)" }
    $checks.Add("Server fixture: $Action")
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "server-$Action.json") -Force
}

function Assert-Sides([string] $Stage) {
    $since = [datetime]::UtcNow
    Invoke-Harness command '/dinvbaseline'
    $serverFile = Wait-Report 'server-*.json' $since
    Start-Sleep -Milliseconds 200
    $since = [datetime]::UtcNow
    Invoke-Harness snapshot "step3-$Stage"
    $clientFile = Wait-Report 'client-*.json' $since
    $server = Get-Content -LiteralPath $serverFile.FullName -Raw | ConvertFrom-Json
    $client = Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
    if (!$server.expandedPlayerLayoutMatches -or !$client.expandedPlayerLayoutMatches) { throw "Layout mismatch: $Stage" }
    if ($server.cursorOccupied -or $client.cursorOccupied) { throw "Unexpected cursor contents: $Stage" }
    for ($main = 0; $main -lt 63; $main++) {
        foreach ($field in @('item', 'count', 'damage', 'nbtCanonical')) {
            if ($server.mainInventory[$main].$field -ne $client.mainInventory[$main].$field) {
                throw "Client/server mismatch at $Stage main $main field $field"
            }
        }
    }
    for ($id = 0; $id -lt 72; $id++) {
        foreach ($field in @('item', 'count', 'damage', 'nbtCanonical', 'inventoryIndex')) {
            if ($server.playerContainer.slots[$id].$field -ne $client.playerContainer.slots[$id].$field) {
                throw "Client/server mismatch at $Stage container $id field $field"
            }
        }
    }
    Copy-Item -LiteralPath $serverFile.FullName -Destination (Join-Path $EvidenceDirectory "$Stage-server.json") -Force
    Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$Stage-client.json") -Force
    $checks.Add("Exact client/server agreement across 63 main and 72 container slots: $Stage")
    Write-Host "PASS sides: $Stage"
}

function Click([int] $Id, [int] $Button = 0, [int] $Mode = 0) {
    Invoke-Harness click $Id $Button $Mode
}

Invoke-Harness require-step3 $WorldName
Invoke-Harness background
Invoke-Harness capture step3-testing
Invoke-Harness register-container-tests
Invoke-Harness command '/difficulty peaceful'
Invoke-Harness command '/gamerule doMobSpawning false'
Invoke-ServerTest selftest
Invoke-ServerTest fill
Invoke-Harness command '/give Developer minecraft:dirt 3'
Invoke-ServerTest verify-fill
Assert-Sides pickup
Invoke-ServerTest seed
Assert-Sides seeded

foreach ($scale in @(1, 2, 3, 0)) { Invoke-Harness scale $scale }
$checks.Add('Menu fits at GUI scales 1, 2, 3 and Auto')
Invoke-Harness clear-chat
Invoke-Harness screenshot step3-menu.png
Copy-Item "$GameDirectory/screenshots/step3-menu.png" (Join-Path $EvidenceDirectory 'menu.png') -Force

# Move NBT stacks out of the way, then exercise first/final extra slots through real C0E packets.
Click 45; Click 9
Click 71; Click 10
Click 36; Click 45
Click 45 1; Click 71
Click 45 1 2
Click 71 0 1
Click 9 0 1; Click 36 0 1
Click 37
Click -999 0 5; Click 46 1 5; Click 71 1 5; Click -999 2 5
Click 38; Click 5
Click 71; Click 6; Click 71
Click 39
foreach ($craftingSlot in 1..4) { Click $craftingSlot 1 }
Click 0 0 1
Click 9; Click 48
Click 10; Click 49
Invoke-ServerTest verify
Assert-Sides interactions
$checks.Add('Real packets: pickup/place, right split, number swap, shift transfers, drag, armor validation, shift crafting and NBT transfers')

Click 71 0 4
Invoke-ServerTest verify-drop
Assert-Sides dropping

Click 71; Click 1
Click 48
Invoke-Harness resume
Invoke-ServerTest verify-close
Assert-Sides closed-cleanup

Invoke-ServerTest sync
Assert-Sides closed-update
$client = Get-Content (Join-Path $EvidenceDirectory 'closed-update-client.json') -Raw | ConvertFrom-Json
if ($client.screen -ne 'none (in-game)' -or $client.mainInventory[61].item -ne 'minecraft:redstone' -or $client.mainInventory[61].count -ne 11) {
    throw 'Closed-menu server mutation did not synchronize'
}
$checks.Add('Server-only extra-slot mutation synchronizes while the menu is closed through normal tick detection')
Invoke-ServerTest clear-sync
Invoke-Harness capture step3-reopened
Invoke-ServerTest verify
Assert-Sides reopened

Invoke-Harness disconnect
Invoke-Harness world $WorldName
Invoke-Harness capture step3-reloaded
Invoke-Harness register-container-tests
Invoke-ServerTest verify
Assert-Sides reloaded
Copy-Item (Join-Path $diagnostics 'runtime.json') (Join-Path $EvidenceDirectory 'runtime.json') -Force
[ordered]@{
    success = $true
    modVersion = (Get-Content (Join-Path $EvidenceDirectory 'reloaded-client.json') -Raw | ConvertFrom-Json).modVersion
    checks = @($checks.ToArray())
    capturedAtUtc = [datetime]::UtcNow.ToString('o')
} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json') -Encoding UTF8
Write-Host 'PASS: all player-menu integration checks; final fixture remains open and saved.'
