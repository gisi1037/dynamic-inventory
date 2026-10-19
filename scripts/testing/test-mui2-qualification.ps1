param(
    [string] $GameDirectory = 'run/rc2-mui2/client',
    [string] $PlayerName = 'Developer',
    [int] $Port = 5005,
    [string] $EvidenceDirectory = 'docs/qualification/rc2-mui2',
    [string] $ServerGameDirectory = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
if (!$ServerGameDirectory) { $ServerGameDirectory = $GameDirectory }
$serverDiagnostics = Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
function Server([string] $Action, [string] $Extra = '') {
    $since = [datetime]::UtcNow
    Harness command "/dinvqualify $Action $Extra"
    $path = Join-Path $serverDiagnostics "qualification/$PlayerName-server-$Action.json"
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content $path -Raw | ConvertFrom-Json
            if (!$report.success) { throw "Server action $Action failed: $($report.failure)" }
            return $report
        }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No new server report for $Action"
}
function Contents([string] $Label) {
    $server = Server verify
    Harness snapshot "mui2-$Label"
    $file = Get-ChildItem "$diagnostics/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $client = Get-Content $file.FullName -Raw | ConvertFrom-Json
    if ($server.inventory.activeContainer.slotCount -ne $ordinarySlots -or $client.activeContainer.slotCount -ne $ordinarySlots) {
        throw 'MUI2 container not active on both sides'
    }
    for ($main = 0; $main -lt $server.inventory.mainSlots; $main++) {
        foreach ($field in @('item','count','damage','nbtCanonical')) {
            if ($server.inventory.mainInventory[$main].$field -ne $client.mainInventory[$main].$field) {
                throw "Client/server mismatch main $main field $field"
            }
        }
    }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "mui2-$Label-client.json") -Force
    $server | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory "mui2-$Label-server.json")
}
function Intermediate([string] $Label, [hashtable] $Expected) {
    $since = [datetime]::UtcNow
    Harness command '/dinvbaseline'
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        $serverFile = Get-ChildItem "$serverDiagnostics/server-*.json" | Where-Object LastWriteTimeUtc -GT $since |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($serverFile) { break }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    if (!$serverFile) { throw 'No new server snapshot' }
    Harness snapshot "mui2-$Label"
    $clientFile = Get-ChildItem "$diagnostics/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $server = Get-Content $serverFile.FullName -Raw | ConvertFrom-Json
    $client = Get-Content $clientFile.FullName -Raw | ConvertFrom-Json
    foreach ($main in $Expected.Keys) {
        $count = $Expected[$main]
        $raw = if ($server.offhandIndex -eq 36 -and $main -ge 36) { $main + 1 } else { $main }
        foreach ($side in @($client, $server)) {
            if ($count -eq 0) {
                if ($side.mainInventory[$raw].item) { throw "Expected empty main $main at $Label" }
            } elseif ($side.mainInventory[$raw].count -ne $count -or $side.mainInventory[$raw].item -ne 'minecraft:diamond') {
                throw "Expected $count diamonds in main $main at $Label"
            }
        }
    }
    Copy-Item $clientFile.FullName (Join-Path $EvidenceDirectory "mui2-$Label-client.json") -Force
    Copy-Item $serverFile.FullName (Join-Path $EvidenceDirectory "mui2-$Label-server.json") -Force
}
Harness background
Harness resume
Harness capture mui2-seed
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
$seed = Server seed
$ordinarySlots = [int] $seed.inventory.ordinarySlots
if ($ordinarySlots -lt 40) { throw 'MUI2 transfer qualification requires at least 40 ordinary slots.' }
$payloadSlot = $ordinarySlots - 2
$splitSlot = $ordinarySlots - 3
Harness resume
Harness command '/dinvmui'
Start-Sleep -Milliseconds 600
Harness mui-check
Contents opened
Server begin mui2 | Out-Null
Harness tick-begin mui2
Start-Sleep -Milliseconds 3500
Harness tick-end mui2
$serverTicks = Server end
$clientTicks = Get-Content "$diagnostics/qualification/$PlayerName-client-ticks-mui2.json" -Raw | ConvertFrom-Json
if (!$clientTicks.success) { throw 'Client MUI2 ticking failed' }
$serverTicks | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'ticks-mui2-server.json')
$clientTicks | ConvertTo-Json -Depth 15 | Set-Content (Join-Path $EvidenceDirectory 'ticks-mui2-client.json')
# Right-click split between added slots, then merge back.
Harness click $payloadSlot 1 0
Harness click $splitSlot 0 0
Intermediate split @{$splitSlot=4;$payloadSlot=3}
Harness click $splitSlot 0 0
Harness click $payloadSlot 0 0
Contents split-merge
# Shift between expanded storage and hotbar, then return the payload by ordinary clicks.
Harness click $payloadSlot 0 1
Intermediate shift-to-hotbar @{0=7;$payloadSlot=0}
Harness click 0 0 1
Intermediate shift-to-storage @{0=0;10=7;$payloadSlot=0}
Harness click 10 0 0
Harness click $payloadSlot 0 0
Contents shift-transfer
# Number-key swap to hotbar index 2, then back.
Harness click $payloadSlot 2 2
Intermediate hotbar-swap @{2=7;$payloadSlot=0}
Harness click $payloadSlot 2 2
Contents number-key
Harness screenshot step3-mui2-fixture.png
Copy-Item "$GameDirectory/screenshots/step3-mui2-fixture.png" (Join-Path $EvidenceDirectory 'mui2-fixture.png') -Force
Harness resume
Server verify | Out-Null
Copy-Item "$diagnostics/runtime.json" (Join-Path $EvidenceDirectory 'runtime.json') -Force
@{success=$true; bindings=$ordinarySlots; tests=@('canonical-storage','client-server-NBT','exactly-once-ticks','split-merge','shift-transfer','number-key-swap','close-preserves-items')} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'mui2-verification.json')
Write-Host 'PASS MUI2 canonical storage, synchronization, ticking and transfers.'
