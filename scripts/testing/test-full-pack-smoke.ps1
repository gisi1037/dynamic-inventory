param(
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $World = 'DI-Baseline-UIFixes-RC2-Qualification',
    [string] $EvidenceDirectory = 'docs/qualification/inventory-ui-fixes/full-pack',
    [int] $Port = 5005,
    [switch] $ResumeAtInline
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
    if ($args[0] -eq 'runtime-info') { $output | Set-Content (Join-Path $EvidenceDirectory 'runtime-info.txt') }
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $passed.Add($Message)
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
        Harness snapshot "full-pack-$Label"
        $clientFile = Get-ChildItem "$reports/client-*.json" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $client = Get-Content $clientFile.FullName -Raw | ConvertFrom-Json
        $matches = $true
        foreach ($collection in @('mainInventory','playerContainer')) {
            $left = if ($collection -eq 'mainInventory') { @($server.mainInventory) } else { @($server.playerContainer.slots) }
            $right = if ($collection -eq 'mainInventory') { @($client.mainInventory) } else { @($client.playerContainer.slots) }
            if ($left.Count -ne $right.Count) { $matches = $false }
            for ($i = 0; $i -lt $left.Count; $i++) {
                foreach ($field in @('item','count','damage','nbtCanonical')) {
                    if ($left[$i].$field -ne $right[$i].$field) { $matches = $false }
                }
            }
        }
        if ($matches) { break }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    Check $matches "Client/server contents match after $Label (64 main entries and $($server.playerContainer.slotCount) container slots, including armor and Baubles)"
    Check (!$server.cursorOccupied -and !$client.cursorOccupied) "Cursor empty after $Label"
    Copy-Item $serverFile.FullName (Join-Path $EvidenceDirectory "$Label-server.json") -Force
    Copy-Item $clientFile.FullName (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    return $server
}
function Totals($State) {
    return @($State.mainInventory | Where-Object item | ForEach-Object {
        "$($_.item)|$($_.damage)|$($_.nbtCanonical)|$($_.count)"
    } | Sort-Object) -join "`n"
}
Harness require-qualification $World
Harness runtime-info
Harness background
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
Harness command '/gamerule keepInventory true'
Harness command '/effect @p 11 1000000 4 true'
if (!$ResumeAtInline) {
Harness capture full-pack-smoke-start
$original = State original
Check ($original.mainSlots -eq 64 -and $original.ordinarySlots -eq 63 -and $original.offhandIndex -eq 36 -and $original.playerContainer.slotCount -gt 73) 'Real full-pack preserves the Backhand map and appends native Baubles slots'
Check (!$original.mainInventory[36].item) 'Offhand is empty before adding the tagged test sentinel'
Check (@($original.mainInventory | Where-Object nbt -Match 'full-pack-smoke').Count -eq 0) 'No prior smoke-test probes are present'
Harness command "/give $($original.player) minecraft:diamond 17 0 {role:full-pack-smoke,display:{Name:`"Expanded slot probe`"}}"
Harness command "/give $($original.player) minecraft:cobblestone 13 0 {role:full-pack-smoke-offhand}"
$seeded = State seeded
$probe = @($seeded.playerContainer.slots | Where-Object nbt -Match 'Expanded slot probe')[0]
$offhand = @($seeded.playerContainer.slots | Where-Object nbt -Match 'full-pack-smoke-offhand')[0]
Check ($null -ne $probe -and $probe.count -eq 17 -and $null -ne $offhand -and $offhand.count -eq 13) 'Both tagged vanilla probes arrived through the real server command path'
Harness click $offhand.containerSlot 0 0
Harness click 45 0 0
Harness click $probe.containerSlot 0 0
Harness click 46 0 0
$first = State first-extra
Check ($first.mainInventory[37].count -eq 17 -and $first.mainInventory[37].nbt -match 'Expanded slot probe') 'Real clicks move the complete tagged stack into the first extra slot'
Harness click 46 0 0
Harness click 72 0 0
$last = State final-extra
Check ($last.mainInventory[63].count -eq 17 -and $last.mainInventory[63].nbt -match 'Expanded slot probe') 'Real clicks move the complete tagged stack into the final extra slot'
foreach ($scale in @(1,2,3,0)) {
    Harness scale $scale
    foreach ($preset in @(@(9,6),@(18,3),@(6,9))) {
        Harness layout-set $preset[0] $preset[1]
        Harness layout-check
        Harness pack-overlay-check
    }
}
$passed.Add('All three layout presets fit at GUI scales 1, 2, 3 and Auto with the real pack and NEI overlay registration')
Harness scale 2
Harness layout-set 18 3
Harness layout-control 394658246
$sorted = State sorted
Check ((Totals $seeded) -eq (Totals $sorted)) 'Actual BogoSorter GUI sort conserves all stacks, counts, damage and NBT'
Check (!$sorted.mainInventory[63].item) 'Actual BogoSorter sorts the final extra slot'
Check ($sorted.mainInventory[36].count -eq 13 -and $sorted.mainInventory[36].nbt -match 'full-pack-smoke-offhand') 'BogoSorter preserves the fixed Backhand offhand'
Harness layout-control 109
} else {
    $original = Get-Content (Join-Path $EvidenceDirectory 'original-server.json') -Raw | ConvertFrom-Json
    $sorted = Get-Content (Join-Path $EvidenceDirectory 'sorted-server.json') -Raw | ConvertFrom-Json
    Check ($original.modVersion -eq '0.6.1-dev' -and $sorted.modVersion -eq '0.6.1-dev') "Resume uses this build's previously captured movement/layout/sorting snapshots"
}
Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
$inline = State inline
$inlineClient = Get-Content (Join-Path $EvidenceDirectory 'inline-client.json') -Raw | ConvertFrom-Json
$equipmentSlots = @($inlineClient.playerContainer.slots | Where-Object containerSlot -ge 73)
Check (@($equipmentSlots | Where-Object x -ge 0).Count -eq $equipmentSlots.Count) 'Wide layout displays every registered native Baubles slot inline'
$heldSource = @($inline.playerContainer.slots | Where-Object { $_.containerSlot -ge 9 -and $_.containerSlot -lt 73 -and $_.containerSlot -ne 45 -and $_.item -eq 'minecraft:diamond' })[0]
Harness visible-click $heldSource.containerSlot
foreach ($button in @(100,109,394658246,394658247)) {
    Harness layout-control $button
    Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
}
Harness held-ui-check
Harness clear-chat
foreach ($frame in @(1,2,3)) {
    Harness screenshot-hover 73 "step3-held-invalid-$frame.png"
    Copy-Item (Join-Path $GameDirectory "screenshots/step3-held-invalid-$frame.png") (Join-Path $EvidenceDirectory "held-invalid-$frame.png") -Force
}
Harness visible-click $heldSource.containerSlot
$heldReturned = State held-controls
Check ((Totals $sorted) -eq (Totals $heldReturned)) 'Held-item clicks on sort, config, layout and Baubles controls preserve the stack and expanded screen'
Harness command "/give $($original.player) Botania:manaRing 1 0 {role:header-real-bauble}"
$gifted = State bauble-gifted
$ringSource = @($gifted.playerContainer.slots | Where-Object nbt -Match 'header-real-bauble')[0]
Check ($null -ne $ringSource -and $ringSource.item -eq 'Botania:manaRing') 'A real pack Mana Ring is available for the native equipment test'
Harness click $ringSource.containerSlot 0 0
Harness click 74 0 0
$ringEquipped = State real-bauble-equipped
Check ($ringEquipped.playerContainer.slots[74].nbt -match 'header-real-bauble') 'Real pack Mana Ring equips through the inline native ring slot'
Harness click 74 0 0
Harness click $ringSource.containerSlot 0 0
$ringRemoved = State real-bauble-removed
Check (!$ringRemoved.playerContainer.slots[74].item -and $ringRemoved.playerContainer.slots[$ringSource.containerSlot].nbt -match 'header-real-bauble') 'Real pack Mana Ring unequips into ordinary inventory'
Harness click $ringSource.containerSlot 0 1
$ringShifted = State real-bauble-shifted
Check ($ringShifted.playerContainer.slots[74].nbt -match 'header-real-bauble') 'Real pack Mana Ring shift-equips through native validation'
Harness clear-chat
Harness screenshot step3-inline-full-pack-rc2.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-inline-full-pack-rc2.png') (Join-Path $EvidenceDirectory 'inline-baubles-wide.png') -Force
Harness layout-set 9 6
Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
Harness screen-status
$baubles = State baubles
$baublesClient = Get-Content (Join-Path $EvidenceDirectory 'baubles-client.json') -Raw | ConvertFrom-Json
Check ($baubles.activeContainer.slotCount -eq $original.playerContainer.slotCount -and $baubles.activeContainer.windowId -eq 0) 'Narrow Baubles panel preserves all expanded inventory slots in persistent window zero'
Check (@($baublesClient.playerContainer.slots | Where-Object { $_.containerSlot -ge 73 -and $_.x -ge 0 }).Count -eq $equipmentSlots.Count) 'Narrow side panel displays every native equipment slot'
Harness layout-check
Harness visible-click 74
Harness visible-click $ringSource.containerSlot
Harness visible-click $ringSource.containerSlot
Harness visible-click 74
Harness clear-chat
Harness screenshot step3-baubles-side-full-pack.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-baubles-side-full-pack.png') (Join-Path $EvidenceDirectory 'baubles-side-panel.png') -Force
Harness layout-control 109
Harness wait-screen dev.gilbe.dynamicinventory.client.PlayerInventoryScreen
$returned = State returned
Check ($returned.activeContainer.slotCount -eq $original.playerContainer.slotCount -and (Totals $sorted) -eq (Totals $returned)) 'Closing the Baubles side panel preserves expanded inventory contents'
Harness command '/save-all'
$beforeReload = State before-reload
Harness disconnect
Harness world $World
Harness capture full-pack-reloaded
$reloaded = State reloaded
Check ((Totals $beforeReload) -eq (Totals $reloaded)) 'Full-pack world save and reload preserves every stack and its complete NBT'
for ($i = 0; $i -lt $beforeReload.mainInventory.Count; $i++) {
    foreach ($field in @('item','count','damage','nbtCanonical')) {
        Check ($beforeReload.mainInventory[$i].$field -eq $reloaded.mainInventory[$i].$field) "Saved raw slot $i retains $field"
    }
}
for ($id = 73; $id -lt $beforeReload.playerContainer.slotCount; $id++) {
    foreach ($field in @('item','count','damage','nbtCanonical')) {
        Check ($beforeReload.playerContainer.slots[$id].$field -eq $reloaded.playerContainer.slots[$id].$field) "Saved native equipment slot $id retains $field"
    }
}
foreach ($item in @($original.mainInventory | Where-Object item)) {
    Check (@($reloaded.mainInventory | Where-Object { $_.item -eq $item.item -and $_.count -eq $item.count -and $_.nbtCanonical -eq $item.nbtCanonical }).Count -gt 0) "Original user stack preserved: $($item.item)"
}
Harness layout-set 18 3
Harness command '/effect @p 11 0'
Harness layout-control 109
Harness clear-chat
Harness screenshot step3-full-pack-rc2.png
Copy-Item (Join-Path $GameDirectory 'screenshots/step3-full-pack-rc2.png') (Join-Path $EvidenceDirectory 'player-menu.png') -Force
Copy-Item (Join-Path $reports 'runtime.json') (Join-Path $EvidenceDirectory 'runtime.json') -Force
@{success=$true; prototype='0.6.1-dev'; packTarget='2.9.0-RC-2'; profile='rc2-integration';
    scope='Full-pack integrated-world smoke test; broader compatibility acceptance remains incomplete';
    world=$World; memoryMiB=4096; gameRuntime='Oracle Java 25'; passed=$passed.ToArray();
    resumedAtInline=[bool]$ResumeAtInline;
    testContext=@('survival','peaceful','mob-spawning-disabled','keepInventory=true','resistance-V-for-GUI-testing');
    mainEntriesCompared=64; playerContainerSlotsCompared=$reloaded.playerContainer.slotCount; nativeBaublesSlots=$equipmentSlots.Count; fixturesInProductionJar=$false;
    jarSha256=(Get-FileHash (Join-Path $GameDirectory 'mods/dynamicinventory-0.6.1-dev.jar') -Algorithm SHA256).Hash} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host 'PASS real GTNH RC-2 full-pack smoke test; test world left open for user testing.'
