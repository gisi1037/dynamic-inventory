param(
    [Parameter(Mandatory=$true)] [ValidateSet('Before','After')] [string] $Phase,
    [int] $Port = 5007,
    [string] $EvidenceDirectory = 'docs/qualification/header-baubles/restart',
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $ServerGameDirectory = 'run/rc2-integration/server'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
New-Item $EvidenceDirectory -ItemType Directory -Force | Out-Null
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
Harness capture equipment-restart
$since = [datetime]::UtcNow
Harness command '/dinvpack state'
$path = Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory/qualification/Developer-server-pack-state.json'
$deadline = [datetime]::UtcNow.AddSeconds(30)
do {
    if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) { break }
    Start-Sleep -Milliseconds 100
} while ([datetime]::UtcNow -lt $deadline)
if ((Get-Item $path).LastWriteTimeUtc -le $since) { throw 'No fresh server equipment snapshot' }
$report = Get-Content $path -Raw | ConvertFrom-Json
if (!$report.success -or !$report.dedicatedServer -or @($report.baubles | Where-Object nbt -Match 'test-ring').Count -ne 1) {
    throw 'Requires a dedicated fixture with exactly one equipped test ring'
}
Copy-Item $path (Join-Path $EvidenceDirectory "$Phase-server.json") -Force
if ($Phase -eq 'Before') {
    Harness command '/save-all'
    Harness command '/stop'
    Write-Host 'PASS captured storage and equipped Bauble; graceful server shutdown requested.'
} else {
    $before = Get-Content (Join-Path $EvidenceDirectory 'Before-server.json') -Raw | ConvertFrom-Json
    if ($before.inventory.playerContainer.slotCount -ne $report.inventory.playerContainer.slotCount) { throw 'Equipment count changed across restart' }
    for ($id=0; $id -lt $report.inventory.playerContainer.slotCount; $id++) {
        foreach ($field in @('item','count','damage','nbtCanonical','inventoryIndex')) {
            $old = [string]$before.inventory.playerContainer.slots[$id].$field
            $current = [string]$report.inventory.playerContainer.slots[$id].$field
            if ($id -ge 73 -and $field -eq 'nbtCanonical') {
                $old = $old -replace '"wornTicks":\{"type":3,"value":"[0-9]+"\}', '"wornTicks":TICKING'
                $current = $current -replace '"wornTicks":\{"type":3,"value":"[0-9]+"\}', '"wornTicks":TICKING'
            }
            if ($old -ne $current) { throw "Restart changed slot $id field $field" }
        }
    }
    Harness layout-set 18 3
    Harness layout-control 109
    Harness pack-action inline-check
    $clientPath=Join-Path $GameDirectory 'diagnostics/dynamicinventory/qualification/Developer-client-pack-inline-check.json'
    Copy-Item $clientPath (Join-Path $EvidenceDirectory 'After-client-check.json') -Force
    Harness pack-action client-sync-guard-set
    Harness pack-action client-sync-guard-check
    $guardPath=Join-Path $GameDirectory 'diagnostics/dynamicinventory/qualification/Developer-client-pack-client-sync-guard-check.json'
    Copy-Item $guardPath (Join-Path $EvidenceDirectory 'client-sync-guard-check.json') -Force
    @{success=$true; prototype='0.6.0-dev'; dedicatedServer=$true; players=1;
        containerSlots=$report.inventory.playerContainer.slotCount; nativeBaubles=$report.baubles.Count;
        checks=@('actual-server-restart','ordinary-offhand-armor-NBT','equipped-native-Bauble-NBT','client-canonical-inventory-binding','native-client-event-guard-reset')} |
        ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
    Write-Host 'PASS actual server restart with ordinary storage, equipment, and canonical client Baubles binding.'
}
