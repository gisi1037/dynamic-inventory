param(
    [string] $GameDirectory = 'run/client',
    [string] $PlayerName = 'Developer',
    [int] $Port = 5005,
    [string] $EvidenceDirectory = 'docs/qualification/isolated',
    [string] $ServerGameDirectory = '',
    [switch] $VerifyBaubles
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
if (!$ServerGameDirectory) { $ServerGameDirectory = $GameDirectory }
$serverDiagnostics = Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

function Invoke-Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
function Server-Test([string] $Action, [string] $Extra = '') {
    $since = [datetime]::UtcNow
    Invoke-Harness command "/dinvqualify $Action $Extra"
    $path = Join-Path $serverDiagnostics "qualification/$PlayerName-server-$Action.json"
    $deadline = [datetime]::UtcNow.AddSeconds(40)
    do {
        if ((Test-Path -LiteralPath $path) -and (Get-Item -LiteralPath $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            if (!$report.success) { throw "Server test $Action failed: $($report.failure)" }
            return $report
        }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    throw "Timed out waiting for qualification $Action"
}
function Tick-Phase([string] $Label) {
    Server-Test begin $Label | Out-Null
    Invoke-Harness tick-begin $Label
    Start-Sleep -Milliseconds 3500
    Invoke-Harness tick-end $Label
    $server = Server-Test end
    $clientPath = Join-Path $diagnostics "qualification/$PlayerName-client-ticks-$Label.json"
    $client = Get-Content -LiteralPath $clientPath -Raw | ConvertFrom-Json
    if (!$client.success) { throw "Client tick test failed: $Label" }
    Copy-Item -LiteralPath $clientPath -Destination (Join-Path $EvidenceDirectory "ticks-$Label-client.json") -Force
    $server | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory "ticks-$Label-server.json")
    Write-Host "PASS exactly-once ticking: $Label on client and server"
}
function Verify-Both([string] $Label) {
    $server = Server-Test verify
    Start-Sleep -Milliseconds 250
    Invoke-Harness snapshot "qualification-$Label"
    $clientFile = Get-ChildItem "$diagnostics/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $client = Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
    for ($main = 0; $main -lt $server.inventory.mainSlots; $main++) {
        foreach ($field in @('item', 'count', 'damage', 'nbtCanonical')) {
            if ($client.mainInventory[$main].$field -ne $server.inventory.mainInventory[$main].$field) { throw "Client/server mismatch $Label main $main $field" }
        }
    }
    for ($armor = 5; $armor -lt 9; $armor++) {
        foreach ($field in @('item', 'count', 'damage', 'nbtCanonical')) {
            if ($client.playerContainer.slots[$armor].$field -ne $server.inventory.playerContainer.slots[$armor].$field) {
                throw "Client/server armor mismatch $Label container $armor $field"
            }
        }
    }
    $server | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory "$Label-server.json")
    Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    Write-Host "PASS exact client/server contents: $Label"
    if ($VerifyBaubles) { Verify-Equipment $Label }
}

function Verify-Equipment([string] $Label, [switch] $Empty) {
    $since = [datetime]::UtcNow
    Invoke-Harness command '/dinvpack state'
    $path = Join-Path $serverDiagnostics "qualification/$PlayerName-server-pack-state.json"
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    if ((Get-Item $path).LastWriteTimeUtc -le $since) { throw 'No fresh equipment lifecycle snapshot' }
    $equipment = Get-Content $path -Raw | ConvertFrom-Json
    if (!$equipment.success) { throw 'Equipment lifecycle snapshot failed' }
    Copy-Item $path (Join-Path $EvidenceDirectory "baubles-$Label-server.json") -Force
    $slots = @($equipment.inventory.playerContainer.slots | Where-Object containerSlot -ge ($equipment.inventory.mainSlots + 9))
    $signature = ($slots | Select-Object inventoryIndex,item,count,damage,@{Name='nbt';Expression={([string]$_.nbtCanonical) -replace '"wornTicks":\{"type":3,"value":"[0-9]+"\}', '"wornTicks":TICKING'}} | ConvertTo-Json -Depth 8 -Compress)
    if ($Empty) {
        if (@($slots | Where-Object item).Count -ne 0) { throw 'Baubles equipment remained after normal death' }
    } elseif ($Label -eq 'seeded') {
        if (@($slots | Where-Object item -eq 'dinvpackfixtures:test_ring').Count -ne 1) { throw 'Expected one equipped ring before lifecycle checks' }
        $script:equipmentSignature = $signature
    } elseif ($signature -ne $script:equipmentSignature) { throw "Equipment changed during $Label" }
    Write-Host "PASS native equipment lifecycle: $Label"
}

Invoke-Harness background
Invoke-Harness capture qualification-start
Invoke-Harness command '/difficulty peaceful'
Invoke-Harness command '/gamerule doMobSpawning false'
Server-Test seed | Out-Null
Verify-Both seeded
Invoke-Harness resume
Tick-Phase closed
Invoke-Harness capture qualification-menu
Tick-Phase open
Invoke-Harness layout-set 3 1
Invoke-Harness layout-check
Tick-Phase offscreen
Invoke-Harness layout-set 9 6
Invoke-Harness resume
Server-Test chest | Out-Null
Tick-Phase chest
Invoke-Harness resume
Verify-Both after-ticking
Server-Test nether | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'nether-transition.json')
Verify-Both nether
Server-Test overworld | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'overworld-transition.json')
Verify-Both overworld
Server-Test kill-keep | Out-Null
Invoke-Harness respawn
Invoke-Harness resume
Verify-Both keep-inventory-respawn
Server-Test kill-drop | Out-Null
Server-Test verify-drops | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'death-drops.json')
Invoke-Harness respawn
Invoke-Harness resume
Server-Test verify-empty | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $EvidenceDirectory 'ordinary-respawn.json')
if ($VerifyBaubles) { Verify-Equipment ordinary-respawn -Empty }
@{success=$true; player=$PlayerName; baublesLifecycleVerified=[bool]$VerifyBaubles; ticks=@('closed','open','offscreen','chest'); lifecycle=@('nether','overworld','keepInventory-respawn','death-drops','ordinary-respawn')} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host 'PASS core ticking and lifecycle qualification.'
