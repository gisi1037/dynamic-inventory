param(
    [Parameter(Mandatory)][string] $GameDirectory,
    [int] $Port = 5006,
    [string] $EvidenceDirectory = '.setup/iron-grave-smoke/runtime',
    [string] $WorldName = 'DI-Baseline-Large-Grave-Smoke-2-Qualification',
    [ValidateRange(27,216)][int] $StorageSlots = 216,
    [ValidateRange(1,225)][int] $ProbeCount = 130,
    [switch] $PermanentModule,
    [switch] $WithBelt
)
$ErrorActionPreference = 'Stop'
if ($WithBelt -and $StorageSlots -gt 207) { throw 'Belt case requires room for its complete nine-slot bonus' }
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$world = $WorldName
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()
$report = [ordered]@{ success=$false; status='running'; world=$world; fullPackQualified=$false }
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath
function Harness {
    $lines = @(& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' `
        '-Ddinv.packInput=true' -cp .setup StorageHarness @args)
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $lines | ForEach-Object { Write-Host $_ }
    return $lines
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Capacity([int] $Storage) {
    # Equipment reconciliation and the peer agreement finish asynchronously.
    # Wait for the real ready state rather than treating one pending frame as failure.
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    do {
        $savedPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $lines = @(& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' '-Ddinv.waitMs=15000' -cp .setup StorageHarness resize-state $Storage 2>&1)
            $ready = $LASTEXITCODE -eq 0
        } finally { $ErrorActionPreference = $savedPreference }
        if ($ready) { $lines | ForEach-Object { Write-Host $_ }; return }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $deadline)
    throw "Capacity $Storage did not reach peer agreement: $($lines -join ' ')"
}
function Signature($State) {
    return (@($State.playerContainer.slots | Where-Object { $_.item } |
        Select-Object containerSlot,inventoryIndex,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 12 -Compress)
}
function Probes($State) {
    return (@($State.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' } |
        Sort-Object inventoryIndex | Select-Object inventoryIndex,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 12 -Compress)
}
# Index order may legitimately change when a removed belt leaves less capacity.
# Compare the complete typed stack payload independently as well as original indices.
function Payloads($State) {
    return (@($State.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' } |
        Sort-Object nbtCanonical | Select-Object item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 12 -Compress)
}
function Snapshot([string] $Label) {
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    do {
        $since = [datetime]::UtcNow
        $null = Harness command '/dinvbaseline'
        $null = Harness snapshot "large-grave-$Label"
        $states = @{}
        foreach ($side in @('server','client')) {
            $file = Get-ChildItem -LiteralPath $diagnostics -Filter "$side-*.json" |
                Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if (!$file) { throw "Missing fresh $side snapshot" }
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Label-$side.json") -Force
            $states[$side] = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        }
        $same = (Signature $states.server) -eq (Signature $states.client)
    } while (!$same -and [datetime]::UtcNow -lt $deadline)
    Check $same "Exact client/server items agree at $Label"
    Check (!$states.client.cursorOccupied -and !$states.server.cursorOccupied) "Cursor empty at $Label"
    Check (!$states.client.hasRecoveryData -and !$states.server.hasRecoveryData) "No recovery at $Label"
    return $states.client
}
function Grave([string] $Label) {
    $lines = Harness grave-info 100 6 0
    $lines | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$Label-grave.txt")
    $raw = @($lines | Where-Object { $_ -like 'graveNbt=*' })[0].Substring(9)
    return ($raw | ConvertFrom-Json)
}
try {
    $null = Harness require-qualification $world
    $null = Harness background
    $null = Harness resume
    foreach ($command in @('/difficulty peaceful','/gamerule doMobSpawning false',
        '/gamerule keepInventory false','/gamerule openblocks:spawn_graves true','/clear @p',
        '/tp @p 100.5 4 0.5')) {
        $null = Harness command $command
    }
    if ($PermanentModule) {
        if ($StorageSlots -ne 81) { throw 'Permanent-module case expects the default 72 + 9 storage slots' }
        $null = Harness command '/dinvsize @p 72'
        Capacity 72
        $null = Harness hotbar-select 0
        $null = Harness command '/give @p dynamicinventory:capacity_module 1'
        Start-Sleep -Milliseconds 500
        $null = Harness use-held
    } else {
        $null = Harness command "/dinvsize @p $StorageSlots"
    }
    Capacity $StorageSlots
    if ($WithBelt) {
        $null = Harness command '/give @p minecraft:iron_helmet 1'
        $helmetReady = Snapshot armor-ready
        $helmetSource = ($helmetReady.playerContainer.slots | Where-Object { $_.item -eq 'minecraft:iron_helmet' }).containerSlot
        $null = Harness capture grave-armor-shift
        $null = Harness click $helmetSource 0 1
        $helmetWorn = Snapshot armor-equipped
        Check (@($helmetWorn.playerContainer.slots | Where-Object { $_.containerSlot -eq 5 -and $_.item -eq 'minecraft:iron_helmet' }).Count -eq 1) 'Ordinary armor still shift-equips'
        $null = Harness click 5 0 1
        $helmetRemoved = Snapshot armor-removed
        Check (@($helmetRemoved.playerContainer.slots | Where-Object { $_.containerSlot -eq 5 -and $_.item }).Count -eq 0) 'Ordinary armor still shift-unequips'
        $null = Harness command '/clear @p'
        # Native clicks also verify the manual-only equipment policy before death.
        $null = Harness command '/give @p dynamicinventory:inventory_belt 1'
        Start-Sleep -Milliseconds 600
        $beforeBelt = Snapshot belt-ready
        $source = ($beforeBelt.playerContainer.slots | Where-Object { $_.item -eq 'dynamicinventory:inventory_belt' }).containerSlot
        $beltSlot = ($beforeBelt.playerContainer.slots | Where-Object { $_.inventoryClass -eq 'baubles.common.container.InventoryBaubles' -and $_.inventoryIndex -eq 3 }).containerSlot
        Check ($null -ne $source -and $null -ne $beltSlot) 'Actual inventory belt and native belt slot identified'
        $null = Harness capture grave-belt-shift
        $null = Harness click $source 0 1
        $shifted = Snapshot belt-shift-refused
        Check (@($shifted.playerContainer.slots | Where-Object { $_.containerSlot -eq $beltSlot -and $_.item }).Count -eq 0) 'Shift-click never auto-equips a Bauble'
        $source = ($shifted.playerContainer.slots | Where-Object { $_.item -eq 'dynamicinventory:inventory_belt' }).containerSlot
        $null = Harness click $source 0 0
        $null = Harness click $beltSlot 0 0
        Capacity ($StorageSlots+9)
        $equipped = Snapshot belt-equipped
        $beltSlot = ($equipped.playerContainer.slots | Where-Object { $_.inventoryClass -eq 'baubles.common.container.InventoryBaubles' -and $_.inventoryIndex -eq 3 }).containerSlot
        $null = Harness capture grave-belt-shift-unequip
        $null = Harness click $beltSlot 0 1
        $stillEquipped = Snapshot belt-shift-unequip-refused
        Check ((Signature $stillEquipped) -eq (Signature $equipped)) 'Shift-click never unequips a Bauble'
    }
    $effective = $StorageSlots + $(if ($WithBelt) { 9 } else { 0 })
    $null = Harness seed-large-grave $ProbeCount
    $null = Harness capture large-grave-seeded
    $seeded = Snapshot seeded
    Check ($seeded.ordinarySlots -eq $effective+9) "Live capacity is $effective storage plus hotbar"
    $probes = @($seeded.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' })
    Check ($probes.Count -eq $ProbeCount) "Seeded $ProbeCount distinct typed-NBT stacks before real death"
    Check ($null -eq (Grave before-death)) 'Fresh world has no existing grave at test site'
    $null = Harness resume
    foreach ($command in @('/setblock 100 5 0 minecraft:stone 0 keep','/tp @p 100.5 6 0.5','/gamemode 0 @p','/kill')) {
        $null = Harness command $command
    }
    Start-Sleep -Seconds 2
    $dead = Grave dead
    Check ($null -ne $dead) 'Actual player death created OpenBlocks grave'
    $items = @($dead.entries.Items.values)
    Check (@($items | Where-Object { $_.entries.tag.entries.dinv_grave_smoke.value -eq '"large-grave-130"' }).Count -eq $ProbeCount) 'Grave retains every seeded stack'
    Check (@($items | Where-Object { $_.entries.Slot.type -ne 3 }).Count -eq 0) 'Native grave writes integer slot tags'
    foreach ($slot in @(@(127,128,129) | Where-Object { $_ -lt $ProbeCount })) {
        Check (@($items | Where-Object { $_.entries.Slot.value -eq "$slot" }).Count -eq 1) "Grave has unique slot$slot"
    }
    $null = Harness respawn
    Start-Sleep -Seconds 2
    Capacity $StorageSlots
    $null = Harness command '/tp @p 100.5 4 2.5'
    $null = Harness capture large-grave-after-death
    $empty = Snapshot after-death
    Check (@($empty.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' }).Count -eq 0) 'Death removed every player probe'
    $null = Harness disconnect-safe $world
    $null = Harness world $world
    $null = Harness require-qualification $world
    $loaded = Grave reloaded
    Check (($loaded | ConvertTo-Json -Depth 70 -Compress) -eq ($dead | ConvertTo-Json -Depth 70 -Compress)) 'Full canonical grave NBT survives saved-world reload'
    $null = Harness resume
    # GT++ grants food on respawn. Remove those unrelated fixture gifts before
    # checking exact origin restoration into an otherwise empty player inventory.
    $null = Harness command '/clear @p'
    $null = Harness hotbar-select 8
    $null = Harness sneak true
    $null = Harness block-open 100 6 0
    $null = Harness sneak false
    $null = Harness capture large-grave-reclaimed
    $reclaimed = Snapshot reclaimed-first
    if ($WithBelt) {
        # The native grave may restore the belt after trying ordinary items. Its
        # bonus is applied at END tick, so a second native reclaim collects leftovers.
        $remaining = Grave partial
        $remainingProbes = @($remaining.entries.Items.values | Where-Object { $_.entries.tag.entries.dinv_grave_smoke.value -eq '"large-grave-130"' })
        $received = @($reclaimed.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' })
        Check ($received.Count + $remainingProbes.Count -eq $ProbeCount) 'Shrunken capacity preserves every probe in player or grave'
        Capacity $effective
        # Reclaim filled every hotbar slot. Make the selected hand empty again:
        # native graves require an empty hand, even when more storage just appeared.
        $null = Harness capture grave-belt-free-hand
        $expanded = Snapshot before-second-reclaim
        $free = @($expanded.mainInventory | Where-Object { !$_.item -and $_.ordinaryIndex -ge 9 })[0]
        Check ($null -ne $free) 'Restored belt creates ordinary space for the remaining grave contents'
        $destination = ($expanded.playerContainer.slots | Where-Object { $_.inventoryClass -eq 'net.minecraft.entity.player.InventoryPlayer' -and $_.inventoryIndex -eq $free.inventoryIndex }).containerSlot
        $null = Harness click 44 0 0
        $null = Harness click $destination 0 0
        $null = Harness resume
        $null = Harness sneak true
        $null = Harness block-open 100 6 0
        $null = Harness sneak false
        $null = Harness capture large-grave-reclaimed-again
        $reclaimed = Snapshot reclaimed-final
        Check (@($reclaimed.playerContainer.slots | Where-Object { $_.item -eq 'dynamicinventory:inventory_belt' -and $_.inventoryClass -eq 'baubles.common.container.InventoryBaubles' }).Count -eq 1) 'Native grave restores exactly one equipped belt'
    } else {
        Check ((Probes $reclaimed) -eq (Probes $seeded)) 'All exact typed-NBT stacks return to original ordinary indices'
    }
    Check ((Payloads $reclaimed) -eq (Payloads $seeded)) 'All original typed stack payloads recovered exactly once'
    $remainder = Grave reclaimed
    if ($null -ne $remainder) {
        # A completely full inventory cannot also hold the native player-head drop.
        # Confirm it stays in the grave, then make room with a real offhand move.
        Check (@($remainder.entries.Items.values | Where-Object { $_.entries.tag.entries.dinv_grave_smoke }).Count -eq 0) 'Full inventory leaves only additional native drops in the grave'
        $offhand = ($reclaimed.playerContainer.slots | Where-Object { $_.inventoryIndex -eq $reclaimed.offhandIndex -and $_.inventoryClass -eq 'net.minecraft.entity.player.InventoryPlayer' }).containerSlot
        Check ($null -ne $offhand) 'Native offhand available for an explicit recovery-space move'
        $null = Harness click 44 0 0
        $null = Harness click $offhand 0 0
        $null = Harness resume
        $null = Harness sneak true
        $null = Harness block-open 100 6 0
        $null = Harness sneak false
        $null = Harness capture grave-native-extra-recovered
        $reclaimed = Snapshot reclaimed-extra
        Check ((Payloads $reclaimed) -eq (Payloads $seeded)) 'Making room for native drops preserves every recovered probe'
    }
    Check ($null -eq (Grave reclaimed)) 'Native reclaim removed completed grave'
    $report.success = $true
    $report.status = 'passed'
} catch {
    $report.status = 'failed'
    $report.failure = $_.Exception.Message
    throw
} finally {
    $report.checks = $checks
    $report.jarSha256 = (Get-FileHash -LiteralPath (Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar')).Hash
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath
}
Write-Host "PASS grave lifecycle: $($checks.Count) checks at $StorageSlots base storage; belt=$WithBelt."
