param(
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $World = 'DI-Baseline-Tabs-RC2-Qualification',
    [string] $EvidenceDirectory = 'docs/qualification/inventory-tabs/full-pack',
    [int] $Port = 5005,
    [switch] $ReuseSeed
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$reports = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$passed = [Collections.Generic.List[string]]::new()
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    if ($args[0] -eq 'tabs-check') { $output | Set-Content (Join-Path $EvidenceDirectory 'native-tabs.txt') }
}
function State([string] $Label) {
    $since = [datetime]::UtcNow
    Harness command '/dinvbaseline'
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        $serverFile = Get-ChildItem "$reports/server-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($serverFile.LastWriteTimeUtc -gt $since) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    if ($serverFile.LastWriteTimeUtc -le $since) { throw 'No fresh server snapshot' }
    $server = Get-Content $serverFile.FullName -Raw | ConvertFrom-Json
    do {
        Harness snapshot "tabs-$Label"
        $file = Get-ChildItem "$reports/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $client = Get-Content $file.FullName -Raw | ConvertFrom-Json
        if ((Signature $server) -eq (Signature $client)) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    if ((Signature $server) -ne (Signature $client)) { throw "Client/server mismatch: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    Copy-Item $serverFile.FullName (Join-Path $EvidenceDirectory "$Label-server.json") -Force
    return $client
}
function Signature($State) {
    return @($State.playerContainer.slots | ForEach-Object {
        "$($_.containerSlot)|$($_.item)|$($_.count)|$($_.damage)|$($_.nbtCanonical)"
    }) -join "`n"
}
function Unchanged([string] $Label) {
    $state = State $Label
    if ($state.cursorOccupied -or $state.playerContainer.slotCount -ne 91 -or (Signature $state) -ne $script:original) {
        throw "Player contents or cursor changed after $Label"
    }
    $passed.Add("Exact item/count/damage/NBT of all 91 player slots preserved after $Label")
}
Harness require-qualification $World
Harness background
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
Harness command '/gamerule keepInventory true'
Harness capture tabs-start
Harness window 854 480
Harness scale 2
Harness layout-set 18 3
Harness tabs-check
$empty = State empty
if (!$ReuseSeed) {
if ($empty.mainInventory[36].item) { throw 'Requires a fresh empty-offhand test world' }
Harness command "/give $($empty.player) minecraft:diamond 17 0 {role:tab-extra-probe}"
Harness command "/give $($empty.player) minecraft:cobblestone 13 0 {role:tab-offhand-probe}"
Harness command "/give $($empty.player) Botania:manaRing 1 0 {role:tab-bauble-probe}"
$seed = State seed
foreach ($probe in @(@('tab-extra-probe',72),@('tab-offhand-probe',45),@('tab-bauble-probe',74))) {
    $slot = @($seed.playerContainer.slots | Where-Object nbt -Match $probe[0])[0]
    if (!$slot.item) { throw "Missing probe $($probe[0])" }
    Harness click $slot.containerSlot 0 0
    Harness click $probe[1] 0 0
}
} else {
    if ($empty.playerContainer.slots[72].nbt -notmatch 'tab-extra-probe' -or $empty.playerContainer.slots[72].count -ne 17 -or
        $empty.playerContainer.slots[45].nbt -notmatch 'tab-offhand-probe' -or $empty.playerContainer.slots[45].count -ne 13 -or
        $empty.playerContainer.slots[74].nbt -notmatch 'tab-bauble-probe') {
        throw 'Reusable disposable probes differ from the expected seed'
    }
}
$baseline = State baseline
$script:original = Signature $baseline
$tabs = Get-Content (Join-Path $EvidenceDirectory 'native-tabs.txt') | Where-Object { $_ -like 'TAB *' } | ForEach-Object { ($_ -split ' ')[1] }
foreach ($tab in $tabs | Where-Object { $_ -ne 'tconstruct.client.tabs.InventoryTabVanilla' }) {
    Harness tab-click $tab
    Harness screen-status
    Harness tab-click tconstruct.client.tabs.InventoryTabVanilla
    Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
    Harness tabs-check
    Unchanged "return-from-$($tab -replace '.*\.','')"
}
foreach ($scale in @(1,2,3,0)) {
    Harness scale $scale
    foreach ($preset in @(@(18,3),@(9,6),@(6,9))) {
        Harness layout-set $preset[0] $preset[1]
        Harness layout-check
        Harness tabs-check
        Harness layout-control 109
        Harness layout-check
        Harness tabs-check
        Harness layout-control 109
        Unchanged "scale-$scale-width-$($preset[0])"
    }
}
Harness window 640 480
Harness scale 0
Harness layout-set 9 6
Harness layout-control 109
Harness layout-check
Harness tabs-check
Harness clear-chat
Harness screenshot step3-tabs-minimum.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-tabs-minimum.png') (Join-Path $EvidenceDirectory 'minimum-window.png') -Force
Unchanged minimum-window
Harness window 854 480
Harness scale 2
Harness layout-set 18 3
Harness tabs-check
Harness layout-control 100
Harness layout-control 108
Harness tabs-check
Unchanged settings-roundtrip
# A real cursor stack blocks native tab mousePressed side effects as well as sorting.
Harness visible-click 36
foreach ($tab in $tabs | Where-Object { $_ -ne 'tconstruct.client.tabs.InventoryTabVanilla' }) {
    Harness tab-click $tab
    Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
}
Harness layout-control 394658246
Harness held-ui-check
Harness screenshot-hover 73 step3-tabs-held-invalid.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-tabs-held-invalid.png') (Join-Path $EvidenceDirectory 'held-invalid.png') -Force
Harness visible-click 36
Unchanged held-item-guards
Harness layout-set 9 6
Harness layout-control 394658246
Harness layout-check
Harness tabs-check
Harness clear-chat
Harness screenshot step3-tabs-side-panel.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-tabs-side-panel.png') (Join-Path $EvidenceDirectory 'side-panel.png') -Force
Harness layout-set 18 3
Harness clear-chat
Harness screenshot step3-inventory-tabs.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-inventory-tabs.png') (Join-Path $EvidenceDirectory 'player-menu.png') -Force
@{success=$true; prototype=$baseline.modVersion; packTarget='2.9.0-RC-2'; nativeTabs=$tabs;
    passed=$passed.ToArray(); scope='Native tab rendering and real tab clicks/return, all presets/scales, minimum viewport, held-item guards, stable sort-button anchoring';
    playerContainerSlots=91; jarSha256=(Get-FileHash (Join-Path $GameDirectory 'mods/dynamicinventory-0.6.2-dev.jar') -Algorithm SHA256).Hash} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "PASS full-pack inventory tabs: $($tabs.Count) tabs, $($passed.Count) conservation checks."
