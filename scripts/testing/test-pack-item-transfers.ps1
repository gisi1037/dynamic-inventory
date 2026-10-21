param(
    [Parameter(Mandatory)][string] $GameDirectory,
    [Parameter(Mandatory)][string] $WorldName,
    [int] $Port = 5006,
    [ValidateRange(27,216)][int] $ExpectedStorageSlots = 72,
    [ValidateRange(0,8)][int] $ProbeRawIndex = 1,
    [string] $EvidenceDirectory = 'docs/qualification/pack-item-transfers'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$checks = [Collections.Generic.List[string]]::new()
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
$report = @{success=$false; status='running'; world=$WorldName; capturedAtUtc=[datetime]::UtcNow.ToString('o')}
$report | ConvertTo-Json | Set-Content $reportPath
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Snapshot([string] $Label) {
    # A predicted client move is insufficient evidence. Capture both logical
    # sides after the real packet and require every inventory's contents to agree.
    $deadline = [datetime]::UtcNow.AddSeconds(10)
    do {
        $since = [datetime]::UtcNow
        Harness command '/dinvbaseline'
        Harness snapshot "item-transfer-$Label"
        $states = @{}
        foreach ($side in @('server','client')) {
            $file = Get-ChildItem -LiteralPath $diagnostics -Filter "$side-*.json" |
                Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if (!$file) { throw "Missing fresh $side snapshot: $Label" }
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Label-$side.json") -Force
            $states[$side] = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        }
        $matched = (Contents $states.client) -eq (Contents $states.server)
    } while (!$matched -and [datetime]::UtcNow -lt $deadline)
    if (!$matched) { throw "Client/server item or cursor disagreement: $Label" }
    return $states.client
}
function Signature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Contents($State) {
    return ([ordered]@{main=(Signature $State); cursor=$State.cursorOccupied;
        player=@($State.playerContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical);
        active=@($State.activeContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical)} | ConvertTo-Json -Depth 12 -Compress)
}
function SameItem($Stack) {
    return $Stack.item -eq $probe.item -and $Stack.damage -eq $probe.damage -and $Stack.nbtCanonical -eq $probe.nbtCanonical
}
function PlayerSlot($State, [int] $RawIndex) {
    $matches = @($State.activeContainer.slots | Where-Object {
        $_.role -in @('hotbar','storage','additional-storage') -and $_.inventoryIndex -eq $RawIndex
    })
    if ($matches.Count -ne 1) { throw "Ambiguous canonical raw index: $RawIndex" }
    return $matches[0].containerSlot
}
try {
    Harness require-qualification $WorldName
    Harness background
    Harness resume
    Harness window 1920 1080
    Harness command '/tp @p 20 4 2'
    # Reserved disposable-world coordinates; never replace an existing block's data.
    Harness command '/setblock 20 4 0 minecraft:chest 0 keep'
    Harness block-open 20 4 0
    Harness scale 1
    $before = Snapshot 'before'
    Check ($before.activeContainer.class -eq 'net.minecraft.inventory.ContainerChest' -and $before.activeContainer.slotCount -eq (27 + $ExpectedStorageSlots + 9)) "Single native chest exposes its 27 slots plus $($ExpectedStorageSlots + 9) ordinary player slots"
    Check (!$before.cursorOccupied -and @($before.activeContainer.slots[0..26] | Where-Object item).Count -eq 0) 'Reserved chest and cursor start empty'
    $probe = $before.mainInventory[$ProbeRawIndex]
    Check ($probe.item -and $probe.count -ge 2 -and @($before.mainInventory | Where-Object { SameItem $_ }).Count -eq 1) 'Existing player stack supplies a unique multi-item probe without giving or clearing items'
    $lastRaw = $before.mainInventory.Count - 1
    Check ($ProbeRawIndex -ge 0 -and $ProbeRawIndex -lt 9 -and !$before.mainInventory[$lastRaw].item) 'Probe starts in the hotbar and final expanded storage is empty'
    $originalId = PlayerSlot $before $ProbeRawIndex
    $lastId = PlayerSlot $before $lastRaw
    $neiConfig = Get-Content -LiteralPath (Join-Path $GameDirectory 'config/NEI/client.cfg') -Raw
    Check ($neiConfig -match '(?m)^\s*disableMouseScrollTransfer=false\s*$') 'NEI item-wheel transfer is enabled'
    # NEI's shouldInvertMouseScrollTransfer() negates this stored setting.
    # Match that API so the event sends from the hovered cell rather than retrieves.
    $wheel = if ($neiConfig -match '(?m)^\s*invertMouseScrollTransfer=true\s*$') { 1 } else { -1 }
    foreach ($source in @($originalId, $lastId)) {
        if ($source -ne $originalId) { Harness input-click $originalId; Harness input-click $source }
        $positioned = Snapshot "source-$source"
        # Mode 1 uses the real shift-click packet path, including prediction and
        # server processing. Do not invoke transferStackInSlot on a fake container.
        Harness click $source 0 1
        $sent = Snapshot "shift-$source-sent"
        Check (!$sent.cursorOccupied -and (SameItem $sent.activeContainer.slots[0]) -and $sent.activeContainer.slots[0].count -eq $probe.count -and @($sent.mainInventory | Where-Object { SameItem $_ }).Count -eq 0) "Source $source shift-click transfers the exact stack into the chest"
        Harness click 0 0 1
        $returned = Snapshot "shift-$source-returned"
        $relocated = @($returned.activeContainer.slots | Where-Object { $_.containerSlot -ge 27 -and (SameItem $_) })
        Check (!$returned.cursorOccupied -and !$returned.activeContainer.slots[0].item -and $relocated.Count -eq 1 -and $relocated[0].count -eq $probe.count) "Source $source reverse shift-click returns the exact stack to player storage"
        # Vanilla may choose another free cell on return. Move it back with real
        # clicks before comparing exact positions or probing the wheel direction.
        if ($relocated[0].containerSlot -ne $source) {
            Check (!$returned.activeContainer.slots[$source].item) 'Original probe cell remains vacant before restoration'
            Harness input-click $relocated[0].containerSlot; Harness input-click $source
        }
        Harness input-wheel $source $wheel
        $wheeled = Snapshot "wheel-$source-sent"
        Check (!$wheeled.cursorOccupied -and (SameItem $wheeled.activeContainer.slots[0]) -and $wheeled.activeContainer.slots[0].count -eq 1 -and $wheeled.activeContainer.slots[$source].count -eq ($probe.count - 1)) "Source $source real NEI wheel sends one item into the chest"
        Harness input-wheel 0 $wheel
        $wheelReturned = Snapshot "wheel-$source-returned"
        Check (!$wheelReturned.cursorOccupied -and !$wheelReturned.activeContainer.slots[0].item -and (Signature $wheelReturned) -eq (Signature $positioned)) "Source $source reverse NEI wheel restores all exact item/count/damage/NBT values"
        if ($source -ne $originalId) { Harness input-click $source; Harness input-click $originalId }
        Check ((Signature (Snapshot "source-$source-restored")) -eq (Signature $before)) "Source $source round trips preserve the complete original player inventory"
    }
    $report.jarSha256 = (Get-FileHash -LiteralPath (Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar')).Hash
    $marker = Get-Content -LiteralPath (Join-Path (Split-Path $GameDirectory -Parent) '.dynamicinventory-dev-instance') -Raw | ConvertFrom-Json
    $report.packTarget = $marker.version
    $report.success = $true
    $report.status = 'passed'
    Harness resume
} catch {
    # Leave an interrupted probe visible for diagnosis rather than guessing at
    # rollback operations that could hide the failing transfer or overwrite items.
    $report.status = 'failed'
    $report.error = $_.Exception.Message
    throw
} finally {
    $report.checks = $checks.ToArray()
    $report.checkCount = $checks.Count
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report.scope = 'Selected real-pack single-chest shift-click packets and NEI wheel events in both directions, from hotbar and final expanded storage, preserving exact player contents; not universal container acceptance'
    $report | ConvertTo-Json -Depth 10 | Set-Content $reportPath
}
Write-Host "PASS actual pack item transfers: $($checks.Count) checks"
