param(
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $ServerGameDirectory = '',
    [string] $PlayerName = 'Developer',
    [int] $Port = 5005,
    [string] $EvidenceDirectory = 'docs/qualification/inventory-ui-fixes/integrated'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
if (!$ServerGameDirectory) { $ServerGameDirectory = $GameDirectory }
$clientReports = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$serverReports = Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory'
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$passed = [Collections.Generic.List[string]]::new()
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $passed.Add($Message)
}
function Report([string] $Action) {
    $since = [datetime]::UtcNow
    Harness command "/dinvpack $Action"
    $path = Join-Path $serverReports "qualification/$PlayerName-server-pack-$Action.json"
    $deadline = [datetime]::UtcNow.AddSeconds(30)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content $path -Raw | ConvertFrom-Json
            if (!$report.success) { throw "Pack $Action failed: $($report.failure)" }
            return $report
        }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "Timed out waiting for server pack $Action"
}
function State([string] $Label) {
    $server = Report state
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        Harness snapshot "pack-$Label"
        $file = Get-ChildItem "$clientReports/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $client = Get-Content $file.FullName -Raw | ConvertFrom-Json
        $matches = $true
        for ($raw = 0; $raw -lt $server.inventory.mainInventory.Count; $raw++) {
            foreach ($field in @('item','count','damage','nbtCanonical')) {
                if ($server.inventory.mainInventory[$raw].$field -ne $client.mainInventory[$raw].$field) { $matches = $false }
            }
        }
        for ($slot = 0; $slot -lt $server.inventory.playerContainer.slotCount; $slot++) {
            foreach ($field in @('item','count','damage','nbtCanonical')) {
                $left = [string] $server.inventory.playerContainer.slots[$slot].$field
                $right = [string] $client.playerContainer.slots[$slot].$field
                if ($slot -ge 73 -and $field -eq 'nbtCanonical') {
                    # The fixture ring advances this server-only counter continuously.
                    $left = $left -replace '"wornTicks":\{"type":3,"value":"[0-9]+"\}', '"wornTicks":TICKING'
                    $right = $right -replace '"wornTicks":\{"type":3,"value":"[0-9]+"\}', '"wornTicks":TICKING'
                }
                if ($left -ne $right) { $matches = $false }
            }
        }
        if ($matches) { break }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    Check $matches "Client/server canonical storage matches after $Label"
    $server | ConvertTo-Json -Depth 40 | Set-Content (Join-Path $EvidenceDirectory "$Label-server.json")
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    return $server
}
function ClientCheck([string] $Action, [string] $Label = '') {
    if (!$Label) { $Label = $Action }
    Harness pack-action $Action
    $path = Join-Path $clientReports "qualification/$PlayerName-client-pack-$Action.json"
    $report = Get-Content $path -Raw | ConvertFrom-Json
    Check $report.success "Client integration checks: $Label"
    Copy-Item $path (Join-Path $EvidenceDirectory "$Label-client-check.json") -Force
    return $report
}
function Totals($Inventory) {
    return @($Inventory.mainInventory | Where-Object item | ForEach-Object {
        "$($_.item)|$($_.damage)|$($_.nbtCanonical)|$($_.count)"
    } | Sort-Object) -join "`n"
}
Harness background
Harness resume
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
$selftest = Report selftest
$selftest | ConvertTo-Json -Depth 40 | Set-Content (Join-Path $EvidenceDirectory 'server-selftest.json')
Report seed | Out-Null
State seeded | Out-Null
ClientCheck refill | Out-Null
$refilled = State refilled
Check (!$refilled.inventory.mainInventory[63].item -and $refilled.inventory.mainInventory[0].count -eq 4) 'Actual refill packet moves final extra stack into hotbar and synchronizes source removal'
Check ($refilled.inventory.mainInventory[36].count -eq 13) 'Actual refill packet preserves the fixed offhand'
Report seed | Out-Null
State reseeded | Out-Null
Harness capture pack-menu
foreach ($scale in @(1,2,3,0)) {
    Harness scale $scale
    foreach ($preset in @(@(9,6),@(18,3),@(6,9))) {
        Harness layout-set $preset[0] $preset[1]
        Harness layout-check
        ClientCheck check "layout-$scale-$($preset[0])" | Out-Null
    }
}
Harness scale 2
Harness layout-set 9 6
Harness layout-control 100
Harness layout-control 108
ClientCheck check after-settings | Out-Null
Harness layout-set 18 3
ClientCheck nei-transfer | Out-Null
$transfer = State nei-transfer
Check (!$transfer.inventory.mainInventory[63].item) 'NEI removes crafting materials from final extra slot'
Check ($transfer.inventory.mainInventory[36].count -eq 13) 'NEI preserves equipped offhand planks'
$inputs = @($transfer.inventory.playerContainer.slots | Where-Object role -eq 'crafting-input')
Check (@($inputs | Where-Object { $_.item -eq 'minecraft:planks' -and $_.count -eq 1 }).Count -eq 4) 'All four real server crafting inputs contain one plank'
Harness click 0 0 1
$crafted = State crafted
Check (@($crafted.inventory.mainInventory | Where-Object item -eq 'minecraft:crafting_table').Count -eq 1) 'Shift-crafting produces one real crafting table'
ClientCheck offhand-only-check | Out-Null
Harness resume
Report seed | Out-Null
$before = State before-sort
Harness capture pack-sort
# Use the real GUI button and Forge event path, including BogoSorter's client/server packet.
Harness layout-control 394658246
$sorted = State sorted
Check ((Totals $before.inventory) -eq (Totals $sorted.inventory)) 'BogoSorter GUI sort preserves all ordinary/equipment counts and NBT'
Check (!$sorted.inventory.mainInventory[63].item) 'BogoSorter includes final extra slot in the storage sort'
Harness resume
Report seed | Out-Null
State before-baubles | Out-Null
Harness capture pack-baubles-access
Harness layout-set 18 3
Harness layout-control 109
$inline = ClientCheck inline-check
Harness visible-click 46
foreach ($id in @(100,109,394658246,394658247)) {
    Harness layout-control $id
    Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
}
ClientCheck held-ui-check | Out-Null
Harness clear-chat
Harness screenshot-hover 73 step3-held-invalid-slot.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-held-invalid-slot.png') (Join-Path $EvidenceDirectory 'held-invalid-slot.png') -Force
Harness visible-click 46
Check $true 'Holding an item keeps both Bogo icons visible and blocks sort, settings, layout and Baubles clicks without moving the cursor stack'
$inlineBefore = State inline-before-equip
Check ($inlineBefore.baubles.Count -ge 20 -and $inlineBefore.inventory.playerContainer.slotCount -eq (73 + $inlineBefore.baubles.Count)) 'Persistent window appends every registered native equipment slot after the established 73 IDs'
Harness click $inline.sourceSlot 0 0
Harness click $inline.ringSlot 0 0
$inlineEquipped = State inline-equipped
$ring = @($inlineEquipped.baubles | Where-Object nbt -Match 'test-ring')[0]
Check ($ring.nbt -match 'equips:1' -and $ring.nbt -match 'wornTicks:[1-9]') 'Inline equip invokes one native server equip callback and worn ticking'
Report lock | Out-Null
$lockedInline = ClientCheck inline-check inline-locked
Check (!$lockedInline.canTakeRing) 'Inline native slot enforces canUnequip=false on the client'
Harness click $inline.ringSlot 0 1
$inlineLocked = State inline-locked
Check (!$inlineLocked.inventory.cursorOccupied -and @($inlineLocked.baubles | Where-Object nbt -Match 'test-ring').Count -eq 1) 'Server rejects shift-removal of locked inline equipment'
Report unlock | Out-Null
Report rebind | Out-Null
ClientCheck inline-check after-native-inventory-replacement | Out-Null
State inline-rebound | Out-Null
Harness click $inline.ringSlot 0 1
$removed = State inline-removed
$removedRing = @($removed.inventory.playerContainer.slots | Where-Object { $_.inventoryClass -eq 'net.minecraft.entity.player.InventoryPlayer' -and $_.item -eq 'dinvpackfixtures:test_ring' })[0]
Check (@($removed.baubles | Where-Object nbt -Match 'test-ring').Count -eq 0 -and $removed.callbacks.unequips -eq 1) 'Inline shift-removal returns the ring to ordinary storage and invokes one native unequip callback'
Harness click $removedRing.containerSlot 0 1
$reequipped = State inline-reequipped
Check (@($reequipped.baubles | Where-Object nbt -Match 'equips:2').Count -eq 1) 'Shift-clicking an ordinary ring equips it through native slot validation'
Harness click 46 0 0
Harness click 1 0 0
$beforeToggle = State crafting-before-toggle
Harness layout-control 109
Harness layout-control 109
$afterToggle = State crafting-after-toggle
Check ($afterToggle.inventory.playerContainer.slots[1].count -eq 7 -and $afterToggle.inventory.playerContainer.slots[1].nbtCanonical -eq $beforeToggle.inventory.playerContainer.slots[1].nbtCanonical) 'Inline panel toggles preserve crafting inputs in the same server window'
Harness click 1 0 0
Harness click 46 0 0
Harness clear-chat
Harness screenshot step3-inline-baubles-wide.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-inline-baubles-wide.png') (Join-Path $EvidenceDirectory 'inline-baubles-wide.png') -Force
Harness layout-set 9 6
# Narrow widths retain the expanded window and expose the same native slots beside it.
ClientCheck side-panel-check | Out-Null
foreach ($scale in @(1,2,3,0)) {
    Harness scale $scale
    foreach ($columns in @(9,6,12,13)) {
        Harness layout-set $columns 6
        Harness layout-check
        ClientCheck side-panel-check "side-panel-$scale-$columns" | Out-Null
    }
}
Harness scale 2
Harness layout-set 9 6
Report seed | Out-Null
$baubles = ClientCheck side-panel-check
Harness visible-click $baubles.sourceSlot
Harness visible-click $baubles.ringSlot
$equipped = State equipped
Check (@($equipped.baubles | Where-Object nbt -Match 'test-ring').Count -eq 1) 'Native Baubles container equips exactly one test ring'
Start-Sleep -Milliseconds 600
$ticked = State worn-tick
$ringBefore = @($equipped.baubles | Where-Object nbt -Match 'test-ring')[0].nbt
$ringAfter = @($ticked.baubles | Where-Object nbt -Match 'test-ring')[0].nbt
Check ($ringBefore -ne $ringAfter -and $ringAfter -match 'wornTicks:[1-9]') 'Equipped Bauble receives native worn-item ticks'
Report lock | Out-Null
Start-Sleep -Milliseconds 250
$lockedClient = ClientCheck baubles-check locked-ring
Check (!$lockedClient.canTakeRing) 'Native Baubles client respects canUnequip=false'
Harness click $baubles.ringSlot 0 0
$locked = State locked
Check (!$locked.inventory.cursorOccupied -and @($locked.baubles | Where-Object nbt -Match 'test-ring').Count -eq 1) 'Server rejects removing a locked Bauble'
Report unlock | Out-Null
Start-Sleep -Milliseconds 250
$unlockedClient = ClientCheck baubles-check unlocked-ring
Check $unlockedClient.canTakeRing 'Native Baubles client restores canUnequip=true'
Harness clear-chat
Harness screenshot step3-baubles-side-panel.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-baubles-side-panel.png') (Join-Path $EvidenceDirectory 'baubles-side-panel.png') -Force
Harness layout-control 109
Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
$returned = State returned
Check ($returned.inventory.activeContainer.slotCount -eq (73 + $returned.baubles.Count)) 'Closing the side panel preserves all expanded ordinary and native equipment slots'
ClientCheck check after-baubles-return | Out-Null
Harness layout-set 18 3
Harness clear-chat
Harness screenshot step3-pack-integration.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-pack-integration.png') (Join-Path $EvidenceDirectory 'player-menu.png') -Force
@{success=$true; prototype=$returned.inventory.modVersion; packTarget='2.9.0-RC-2'; profile='rc2-integration';
    serverAssertions=$selftest.passed.Count; passed=$passed.ToArray(); dedicatedServer=$returned.dedicatedServer} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "PASS RC-2 menu integration: $($selftest.passed.Count) server assertions and $($passed.Count) integration checks."
