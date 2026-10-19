param(
    [string] $GameDirectory = 'run/rc2-backhand/client',
    [string] $ServerGameDirectory = '',
    [string] $PlayerName = 'Developer',
    [int] $Port = 5005,
    [string] $EvidenceDirectory = 'docs/qualification/backhand/interactions'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
if (!$ServerGameDirectory) { $ServerGameDirectory = $GameDirectory }
$clientReports = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$serverReports = Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory'
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
function Report([string] $Command, [string] $Filename) {
    $since = [datetime]::UtcNow
    Harness command $Command
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    $path = Join-Path $serverReports "qualification/$PlayerName-server-$Filename.json"
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content $path -Raw | ConvertFrom-Json
            if (!$report.success) { throw "Server test failed: $($report.failure)" }
            return $report
        }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No new report: $Filename"
}
function State([string] $Label, [bool] $Original = $false) {
    if ($Original) { $server = (Report '/dinvqualify verify' 'verify').inventory }
    else {
        $since = [datetime]::UtcNow
        Harness command '/dinvbaseline'
        $deadline = [datetime]::UtcNow.AddSeconds(30)
        do {
            $file = Get-ChildItem "$serverReports/server-*.json" | Where-Object LastWriteTimeUtc -GT $since |
                Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if ($file) { break }
            Start-Sleep -Milliseconds 150
        } while ([datetime]::UtcNow -lt $deadline)
        if (!$file) { throw 'No new server baseline' }
        $server = Get-Content $file.FullName -Raw | ConvertFrom-Json
    }
    Harness snapshot "backhand-$Label"
    $file = Get-ChildItem "$clientReports/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $client = Get-Content $file.FullName -Raw | ConvertFrom-Json
    if (!$client.expandedPlayerLayoutMatches -or !$server.expandedPlayerLayoutMatches -or $server.offhandIndex -ne 36) {
        throw 'Offhand/container layout mismatch'
    }
    for ($raw = 0; $raw -lt 64; $raw++) {
        foreach ($field in @('item','count','damage','nbtCanonical')) {
            if ($server.mainInventory[$raw].$field -ne $client.mainInventory[$raw].$field) { throw "Mismatch at raw $raw / $field" }
        }
    }
    $server | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory "$Label-server.json")
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    return $server
}
Harness background
Harness resume
Harness capture backhand-start
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
$selftest = Report '/dinvbackhand selftest' 'backhand-selftest'
$selftest | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'migration-pickup-selftest.json')
Report '/dinvqualify seed' 'seed' | Out-Null
State seeded $true | Out-Null
# Equipment and the first ordinary extra are different real slots, with stable packet IDs.
Harness click 45 0 2
$swapped = State number-swap
if ($swapped.mainInventory[0].nbt -notmatch 'probe-offhand' -or $swapped.mainInventory[36].item) { throw 'Offhand number-key swap did not move equipment' }
Harness click 45 0 2
State number-restored $true | Out-Null
Harness click 45 0 1
$shifted = State shift-offhand
if ($shifted.mainInventory[10].nbt -notmatch 'probe-offhand' -or $shifted.mainInventory[36].item) { throw 'Offhand shift-transfer did not reach ordinary storage' }
Harness click 10 0 0
Harness click 45 0 0
State shift-restored $true | Out-Null
Harness click 46 0 0
Harness click 72 0 0
Harness click 46 0 0
Harness click 72 0 0
Harness click 46 0 0
Harness click 72 0 0
State extra-slots-restored $true | Out-Null
# Use the actual Backhand/GTNHLib synchronized key binding path, including network packets.
Harness resume
Harness backhand-key down
Start-Sleep -Milliseconds 200
Harness backhand-key up
$swapped = State swap-key
if ($swapped.mainInventory[0].nbt -notmatch 'probe-offhand' -or $swapped.mainInventory[36].item) { throw 'Backhand synchronized swap key did not move equipment' }
Harness backhand-key down
Start-Sleep -Milliseconds 200
Harness backhand-key up
State swap-key-restored $true | Out-Null
Harness capture backhand-layout
Harness layout-set 3 1
Harness layout-check
State scrolled-layout $true | Out-Null
Harness layout-set 9 6
Harness clear-chat
Harness screenshot step3-backhand-fixture.png
Copy-Item "$GameDirectory/screenshots/step3-backhand-fixture.png" (Join-Path $EvidenceDirectory 'backhand-fixture.png') -Force
Copy-Item "$clientReports/runtime.json" (Join-Path $EvidenceDirectory 'runtime.json') -Force
@{success=$true;offhandIndex=36;ordinarySlots=63;rawMainSlots=64;containerSlots=73;assertions=$selftest.passed.Count;
    tests=@('legacy-migration','prototype-migration','pickup-policy','deep-copy','equipment-slot','number-key','shift-transfer','first-last-extras','Backhand-synchronized-swap-key','scrolled-layout','exact-client-server-NBT')} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host 'PASS fixed offhand migration, pickup policy and actual equipment interactions.'
