param(
    [ValidateSet('BeforeRestart','AfterRestart')] [string] $Phase,
    [ValidateSet('rc2-mui2','rc2-backhand','rc2-integration')] [string] $Profile = 'rc2-mui2',
    [string] $EvidenceDirectory = 'docs/qualification/dedicated/restart'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$serverDirectory = "run/$Profile/server/diagnostics/dynamicinventory"
$evidence = $EvidenceDirectory
$payloadSlot = if ($Profile -in @('rc2-backhand','rc2-integration')) { '71' } else { '70' }
$payloadRaw = if ($Profile -in @('rc2-backhand','rc2-integration')) { 62 } else { 61 }
New-Item -ItemType Directory -Force $evidence | Out-Null
function Harness([int] $Port, [string[]] $Arguments) {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @Arguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $Port $Arguments" }
}
function Server([string] $Player, [int] $Port, [string] $Action) {
    $since = [datetime]::UtcNow
    Harness $Port @('command', "/dinvqualify $Action")
    $path = "$serverDirectory/qualification/$Player-server-$Action.json"
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content $path -Raw | ConvertFrom-Json
            if (!$report.success -or !$report.dedicatedServer -or $report.onlinePlayers -ne 2) {
                throw "Requires two connected clients and a successful dedicated-server $Action"
            }
            return $report
        }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No new $Player/$Action report"
}
if ($Phase -eq 'BeforeRestart') {
    Harness 5005 @('capture','dedicated-restart')
    Server Developer 5005 seed | Out-Null
    Harness 5006 @('capture','dedicated-restart')
    Server Observer 5006 seed | Out-Null
    # Move Developer's payload out of an extra slot while Observer keeps its own payload.
    Harness 5005 @('click',$payloadSlot,'0','2')
    $observer = Server Observer 5006 verify
    $observer | ConvertTo-Json -Depth 30 | Set-Content "$evidence/observer-during-developer-mutation.json"
    Harness 5005 @('snapshot','dedicated-mutated')
    $clientFile = Get-ChildItem "run/$Profile/client-developer/diagnostics/dynamicinventory/client-*.json" |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $mutated = Get-Content $clientFile.FullName -Raw | ConvertFrom-Json
    if ($mutated.mainInventory[0].count -ne 7 -or $mutated.mainInventory[$payloadRaw].item) {
        throw 'Developer payload did not move to hotbar'
    }
    Copy-Item $clientFile.FullName "$evidence/developer-mutated-client.json" -Force
    Harness 5005 @('click',$payloadSlot,'0','2')
}
foreach ($peer in @(@{name='Developer';port=5005},@{name='Observer';port=5006})) {
    $report = Server $peer.name $peer.port verify
    $file = "$evidence/$($peer.name)-$Phase.json"
    $report | ConvertTo-Json -Depth 30 | Set-Content $file
    if ($Profile -eq 'rc2-integration') {
        $since = [datetime]::UtcNow
        Harness $peer.port @('command','/dinvpack state')
        $packPath = "$serverDirectory/qualification/$($peer.name)-server-pack-state.json"
        $deadline = [datetime]::UtcNow.AddSeconds(30)
        do {
            if ((Test-Path $packPath) -and (Get-Item $packPath).LastWriteTimeUtc -gt $since) { break }
            Start-Sleep -Milliseconds 100
        } while ([datetime]::UtcNow -lt $deadline)
        if ((Get-Item $packPath).LastWriteTimeUtc -le $since) { throw 'No fresh Baubles restart report' }
        $equipment = Get-Content $packPath -Raw | ConvertFrom-Json
        Copy-Item $packPath "$evidence/$($peer.name)-Baubles-$Phase.json" -Force
        if (@($equipment.baubles | Where-Object nbt -Match 'test-ring').Count -ne 1) { throw 'Expected exactly one equipped test ring' }
        if ($Phase -eq 'AfterRestart') {
            $beforeEquipment = Get-Content "$evidence/$($peer.name)-Baubles-BeforeRestart.json" -Raw | ConvertFrom-Json
            for ($slot = 0; $slot -lt $equipment.baubles.Count; $slot++) {
                # Native Baubles ticks keep running; compare all stored item NBT except the test counter.
                $old = ([string]$beforeEquipment.baubles[$slot].nbt) -replace 'wornTicks:[0-9]+','wornTicks:TICKING'
                $current = ([string]$equipment.baubles[$slot].nbt) -replace 'wornTicks:[0-9]+','wornTicks:TICKING'
                if ($old -ne $current) { throw "Baubles persistence mismatch: $($peer.name) slot $slot" }
            }
        }
    }
    if ($Phase -eq 'AfterRestart') {
        $before = Get-Content "$evidence/$($peer.name)-BeforeRestart.json" -Raw | ConvertFrom-Json
        for ($main = 0; $main -lt $report.inventory.mainSlots; $main++) {
            foreach ($field in @('item','count','damage','nbtCanonical')) {
                if ($before.inventory.mainInventory[$main].$field -ne $report.inventory.mainInventory[$main].$field) {
                    throw "Restart mismatch: $($peer.name) main $main field $field"
                }
            }
        }
    }
}
if ($Phase -eq 'BeforeRestart') {
    Harness 5006 @('disconnect')
    Harness 5005 @('command','/save-all')
    Harness 5005 @('command','/stop')
    Write-Host 'PASS player isolation; requested a graceful test-server save and shutdown.'
} else {
    @{success=$true; dedicatedServer=$true; onlinePlayers=2; baublesEquipmentVerified=($Profile -eq 'rc2-integration'); checks=@('independent-player-storage','actual-server-restart','exact-NBT-both-players','armor-retained')} |
        ConvertTo-Json -Depth 8 | Set-Content "$evidence/verification.json"
    Write-Host 'PASS two-player inventory persistence after an actual dedicated-server restart.'
}
