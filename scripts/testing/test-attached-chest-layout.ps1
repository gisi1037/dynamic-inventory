param(
    [int] $Port = 5005,
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $EvidenceDirectory = 'docs/qualification/aligned-chest-default/attached-chest-layout',
    [switch] $IncludeAlternateLayouts,
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification',
    [int] $StationX = 9,
    [int] $StationZ = 0
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
$report = @{success=$false; status='running'; provenanceScope='installed-file-only';
    capturedAtUtc=[datetime]::UtcNow.ToString('o'); port=$Port}
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding UTF8
$installedJar = Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar'
if (!(Test-Path -LiteralPath $installedJar)) { throw "Missing installed production JAR: $installedJar" }
$report.jarSha256 = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding UTF8
$checks = [Collections.Generic.List[string]]::new()
$views = [Collections.Generic.List[object]]::new()
$fixtureOpened = $false

function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot "attached-$Label" | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw "No fresh snapshot: $Label" }
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function ContainerSignature($State) {
    return ($State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,inventoryClass,slotClass,role,item,count,damage,nbtCanonical |
        ConvertTo-Json -Depth 12 -Compress)
}
function GeometrySignature($State) {
    return ($State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,inventoryClass,slotClass,role,x,y |
        ConvertTo-Json -Depth 5 -Compress)
}
function PlayerSlots($State) {
    return @($State.activeContainer.slots | Where-Object role -in @('hotbar','storage','additional-storage'))
}
function VisibleChest($State) {
    # Native disabled ChestSlots share one collapsed coordinate; snapshots omit their enabled flag.
    return @($State.activeContainer.slots | Where-Object slotClass -eq 'tconstruct.tools.gui.ChestSlot' |
        Group-Object x,y | Where-Object Count -eq 1 | ForEach-Object Group |
        Where-Object { $_.x -ge 0 -and $_.y -ge 0 })
}
function Check-ChestGrid($State, [string] $Label) {
    $visible = @(VisibleChest $State)
    if ($visible.Count -eq 0) { throw "$Label has no visible chest cells." }
    $rows = @($visible | Group-Object y | Sort-Object { [int]$_.Name })
    $columns = @($visible | Group-Object x | Sort-Object { [int]$_.Name })
    Check ($rows.Count -gt 0 -and $visible.Count -le $rows.Count * $columns.Count) "$Label chest cells stay within the rectangular viewport"
    $expectedX = @($columns | ForEach-Object { [int]$_.Name })
    for ($i = 1; $i -lt $expectedX.Count; $i++) {
        if ($expectedX[$i] - $expectedX[$i - 1] -ne 18) { throw "$Label chest columns are not evenly spaced by 18 pixels." }
    }
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $rowSlots = @($rows[$i].Group | Sort-Object x)
        # Native Tinkers pads unused cells in the last row. They are artwork, never extra slots.
        if ($rowSlots.Count -ne $columns.Count -and $i -ne $rows.Count - 1) { throw "$Label chest has a partial interior row." }
        for ($column = 0; $column -lt $rowSlots.Count; $column++) {
            if ([int]$rowSlots[$column].x -ne $expectedX[$column]) { throw "$Label chest row $i does not share the same rectangular columns." }
        }
        if ($i -gt 0 -and [int]$rows[$i].Name - [int]$rows[$i - 1].Name -ne 18) {
            throw "$Label chest rows are not evenly spaced by 18 pixels."
        }
    }
    Check $true "$Label native chest rows stay aligned with padding confined to the final row"
}
function Overlap($First, $Second) {
    return $First.x -lt $Second.x + 16 -and $Second.x -lt $First.x + 16 -and
        $First.y -lt $Second.y + 16 -and $Second.y -lt $First.y + 16
}
function ButtonOverlap($Button, $Cell) {
    return $Button.x -lt $Cell.x + 16 -and $Cell.x -lt $Button.x + $Button.width -and
        $Button.y -lt $Cell.y + 16 -and $Cell.y -lt $Button.y + $Button.height
}
function Audit([string] $Label) {
    $result = Harness ui-audit
    $match = [regex]::Match($result, '(?m)^uiAudit=(.+)$')
    if (!$match.Success) { throw 'No saved live audit path' }
    $path = $match.Groups[1].Value.Trim()
    Copy-Item -LiteralPath $path -Destination (Join-Path $EvidenceDirectory "$Label-audit.json") -Force
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
function Check-Mapping($Audit, [string] $Label) {
    Check ($Audit.candidate -and [bool]$Audit.adapterId -and $Audit.playerSlots.Count -eq 81 -and
        $Audit.missingLegacyIndices.Count -eq 0 -and $Audit.duplicateOrdinaryIndices.Count -eq 0) "$Label audit finds all 81 canonical ordinary slots"
    for ($logical = 0; $logical -lt 81; $logical++) {
        $entry = @($Audit.playerSlots | Where-Object ordinaryIndex -eq $logical)
        $raw = $logical + [int]($logical -ge 36)
        if ($entry.Count -ne 1 -or $entry[0].rawIndex -ne $raw) { throw "$Label canonical raw mapping failed at $logical" }
    }
    Check ((Harness foreign-check) -match 'ordinaryIds=\[') "$Label live container uses canonical inventory"
}

try {
    Harness require-qualification $WorldName | Out-Null
    Harness background | Out-Null
    Harness resume | Out-Null
    $initial = Snapshot 'initial'
    Check (!$initial.cursorOccupied) 'Fixture starts with an empty cursor'
    $probe = @($initial.mainInventory | Where-Object { $_.inventoryIndex -lt 9 -and $_.item } | Select-Object -First 1)
    Check ($probe.Count -eq 1) 'Existing hotbar supplies a populated probe slot without giving or clearing items'
    $probeLogical = [int]$probe[0].inventoryIndex
    Harness command "/tp @p $($StationX + 0.5) 4 $($StationZ + 2.5)" | Out-Null
    # Reserved coordinates differ from the older x1/x2 fixture. Never overwrite existing blocks/data.
    # An isolated location avoids a previously placed neighbouring inventory
    # taking precedence in Tinkers' native attached-inventory discovery.
    Harness command "/setblock $($StationX - 1) 4 $StationZ avaritiaddons:CompressedChest 0 keep" | Out-Null
    Harness command "/setblock $StationX 4 $StationZ TConstruct:CraftingStation 0 keep" | Out-Null
    Harness block-open $StationX 4 $StationZ | Out-Null
    $fixtureOpened = $true
    $before = Snapshot 'opened'
    Check ($before.screen -eq 'tconstruct.tools.gui.CraftingStationGui' -and
        $before.activeContainer.class -eq 'tconstruct.tools.inventory.CraftingStationContainer') 'Fixture retains the native crafting station GUI and container'
    Check (@($before.activeContainer.slots | Where-Object slotClass -eq 'tconstruct.tools.gui.ChestSlot').Count -eq 243) 'Native compressed chest exposes all 243 attached slots'
    Check ((MainSignature $before) -eq (MainSignature $initial)) 'Opening the fresh fixture preserves player inventory'
    $contents = ContainerSignature $before

    # A real chest-sort packet must act only on the chest group. Use one existing
    # hotbar stack, then put it back exactly; no fixture items are created or cleared.
    Harness window 1920 1080 | Out-Null
    Harness native-layout-set 9 8 | Out-Null
    Harness scale 1 | Out-Null
    $sortBefore = Snapshot 'sort-before'
    $allChest = @($sortBefore.activeContainer.slots | Where-Object slotClass -eq 'tconstruct.tools.gui.ChestSlot' | Sort-Object containerSlot)
    Check (@($allChest | Where-Object item).Count -eq 0) 'Reserved chest is empty before the reversible sort probe'
    $firstChest = $allChest[0].containerSlot
    $lastChest = $allChest[-1].containerSlot
    Harness chest-scroll bottom | Out-Null
    $sortBottom = Snapshot 'sort-bottom'
    Check (@(VisibleChest $sortBottom | Where-Object containerSlot -eq $allChest[-1].containerSlot).Count -eq 1) 'Native chest scrollbar exposes its final cell without widening the frame'
    $sortAudit = Audit 'sort-before'
    $sortButtonOutput = Harness button-geometry
    $sortButtonMatch = [regex]::Match($sortButtonOutput, '(?m)^buttonGeometry=(\{.+\})$')
    if (!$sortButtonMatch.Success) { throw 'Missing sort-group metadata before the packet probe' }
    $sortButtonsBefore = ($sortButtonMatch.Groups[1].Value | ConvertFrom-Json).buttons
    $nativeChestIds = @($allChest | Select-Object -ExpandProperty containerSlot | Sort-Object) -join ','
    $chestSortBefore = @($sortButtonsBefore | Where-Object {
        $_.class -eq 'com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton' -and !$_.playerGroup
    })
    Check ($chestSortBefore.Count -eq 2 -and @($chestSortBefore | Where-Object {
        (@($_.groupSlotIds | Sort-Object) -join ',') -ne $nativeChestIds
    }).Count -eq 0) 'Client chest-sort buttons target only the 243 native chest slots before any sorting packet'
    $hotbarId = ($sortAudit.playerSlots | Where-Object ordinaryIndex -eq $probeLogical).containerSlot
    # Populate an appended player slot as a guard. An old server-side chest range
    # would pull this stack into the chest even if the client's buttons looked correct.
    $guard = @($initial.mainInventory | Where-Object { $_.inventoryIndex -ne 36 -and $_.inventoryIndex -ne 81 -and $_.inventoryIndex -ne $probeLogical -and $_.item } | Select-Object -First 1)
    Check ($guard.Count -eq 1 -and !$initial.mainInventory[81].item) 'Sort probe has a second existing ordinary stack and a vacant appended player slot'
    $guardLogical = [int]$guard[0].inventoryIndex
    if ($guardLogical -gt 36) { $guardLogical-- } # Backhand keeps raw index 36 for its offhand.
    $guardSourceId = ($sortAudit.playerSlots | Where-Object ordinaryIndex -eq $guardLogical).containerSlot
    $guardStorageId = ($sortAudit.playerSlots | Where-Object ordinaryIndex -eq 80).containerSlot
    Harness input-click $guardSourceId | Out-Null
    Harness input-click $guardStorageId | Out-Null
    $guardPlaced = Snapshot 'sort-guard-placed'
    Check (!$guardPlaced.cursorOccupied -and $guardPlaced.mainInventory[81].item -eq $guard[0].item -and
        $guardPlaced.mainInventory[81].count -eq $guard[0].count -and !$guardPlaced.mainInventory[$guard[0].inventoryIndex].item) 'Server sort guard occupies appended player storage before the chest probe'
    Harness input-click $hotbarId | Out-Null
    Harness input-click $lastChest | Out-Null
    $sortPlaced = Snapshot 'sort-placed'
    Check (!$sortPlaced.cursorOccupied -and $sortPlaced.activeContainer.slots[$lastChest].item -eq $probe[0].item) 'Sort probe places the original stack in the final chest cell'
    Harness input-sort chest | Out-Null
    $sortDeadline = [datetime]::UtcNow.AddSeconds(4)
    $sortFrame = 0
    do {
        $sorted = Snapshot "sort-result-$sortFrame"
        $sortFrame++
        if ($sorted.activeContainer.slots[$firstChest].item -eq $probe[0].item -and !$sorted.activeContainer.slots[$lastChest].item) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $sortDeadline)
    Check (!$sorted.cursorOccupied -and $sorted.activeContainer.slots[$firstChest].item -eq $probe[0].item -and
        !$sorted.activeContainer.slots[$lastChest].item -and (MainSignature $sorted) -eq (MainSignature $sortPlaced)) 'Actual chest sort moves the probe inside the chest and preserves every player slot'
    Harness chest-scroll top | Out-Null
    Harness input-click $firstChest | Out-Null
    Harness input-click $hotbarId | Out-Null
    Harness input-click $guardStorageId | Out-Null
    Harness input-click $guardSourceId | Out-Null
    $sortReturned = Snapshot 'sort-returned'
    Check (!$sortReturned.cursorOccupied -and (ContainerSignature $sortReturned) -eq $contents -and
        (MainSignature $sortReturned) -eq (MainSignature $initial)) 'Chest sort round trip restores exact player, crafting and chest contents'
    # Hit the relocated crafting input through its live GUI coordinate as well.
    # Do not take the result: the existing stack is returned without crafting or consuming it.
    Harness input-click $hotbarId | Out-Null
    Harness input-click 1 | Out-Null
    $craftPlaced = Snapshot 'centered-crafting-placed'
    Check (!$craftPlaced.cursorOccupied -and $craftPlaced.activeContainer.slots[1].item -eq $probe[0].item -and
        $craftPlaced.activeContainer.slots[1].count -eq $probe[0].count) 'Vertically centered crafting input accepts the real mouse placement'
    Harness input-click 1 | Out-Null
    Harness input-click $hotbarId | Out-Null
    $craftReturned = Snapshot 'centered-crafting-returned'
    Check (!$craftReturned.cursorOccupied -and (ContainerSignature $craftReturned) -eq $contents -and
        (MainSignature $craftReturned) -eq (MainSignature $initial)) 'Centered crafting round trip preserves every original stack without taking the result'

    $presets = @(@{name='compact'; columns=9; rows=8})
    if ($IncludeAlternateLayouts) { $presets += @{name='wide'; columns=18; rows=4} }
    foreach ($window in @(@(1920,1080), @(854,480))) {
        Harness window $window[0] $window[1] | Out-Null
        foreach ($preset in $presets) {
            Harness native-layout-set $preset.columns $preset.rows | Out-Null
            foreach ($scale in @(1,2,3,0)) {
                $label = "$($window[0])x$($window[1])-$($preset.name)-scale-$scale"
                # A maximized window or a manual resize can override DisplayMode.
                # Fail before moving items if the real framebuffer differs from
                # the dimensions used to calculate this test's mouse positions.
                $geometry = Harness scale $scale
                Check ($geometry -match "PASS geometry.*framebuffer=$($window[0])x$($window[1])(?: |$)") "$label complete menu fits the requested framebuffer"
                $audit = Audit $label
                Check-Mapping $audit $label
                $state = Snapshot $label
                Check (!$state.cursorOccupied -and (ContainerSignature $state) -eq $contents) "$label resizing preserves all player, crafting and chest contents"
                if ($window[0] -eq 1920 -and $window[1] -eq 1080 -and $scale -eq 1) {
                    Check-ChestGrid $state $label
                }
                $players = @(PlayerSlots $state | Where-Object { $_.x -ge 0 -and $_.y -ge 0 })
                $chest = @(VisibleChest $state)
                $craft = @($state.activeContainer.slots | Where-Object role -in @('crafting-input','crafting-result'))
                Check ($craft.Count -eq 10 -and $chest.Count -gt 0) "$label retains ten crafting slots and a visible chest viewport"
                $chestBottom = ($chest | Measure-Object y -Maximum).Maximum + 16
                Check (@($players + $craft | Where-Object y -lt $chestBottom).Count -eq 0) "$label chest pane is above the player and crafting panes"
                foreach ($player in $players) {
                    foreach ($cell in $craft) { if (Overlap $player $cell) { throw "$label player/crafting overlap" } }
                }
                Check $true "$label player and crafting slots remain separate"
                # Crafting Tweaks overwrites absolute positions during its draw.
                # Inspect the live fields after frames have drawn, not only our layout calculation.
                $buttonOutput = Harness button-geometry
                $buttonMatch = [regex]::Match($buttonOutput, '(?m)^buttonGeometry=(\{.+\})$')
                if (!$buttonMatch.Success) { throw 'No live button geometry returned' }
                $buttonGeometry = $buttonMatch.Groups[1].Value | ConvertFrom-Json
                $layout = $buttonGeometry.attachedLayout
                Check ($layout.width -le 372) "$label chest capacity does not widen the standard external layout"
                Check ($layout.chestColumns -eq [math]::Max(5, [math]::Floor(($layout.width - 26) / 18))) "$label chest grid uses the available panel width instead of a capacity divisor"
                Check ($null -ne $layout -and $layout.playerY -eq $layout.lowerY -and $layout.sideBySide) "$label default player inventory starts at the separate lower panel"
                Check ($layout.craftingY -eq $layout.lowerY + [math]::Floor(($layout.lowerHeight-80)/2)) "$label crafting section is vertically centered in the lower panel"
                $tweaks = @($buttonGeometry.buttons | Where-Object {
                    $_.class -eq 'net.blay09.mods.craftingtweaks.client.GuiTweakButton' -and $_.visible
                })
                Check ($tweaks.Count -eq 3) "$label retains all three Crafting Tweaks buttons"
                foreach ($button in $tweaks) {
                    $relative = [pscustomobject]@{x=$button.xPosition-$buttonGeometry.guiLeft; y=$button.yPosition-$buttonGeometry.guiTop}
                    foreach ($cell in @($players + $craft + $chest)) {
                        if (Overlap $relative $cell) { throw "$label Crafting Tweaks button overlaps a live slot" }
                    }
                }
                Check $true "$label drawn Crafting Tweaks buttons do not cover chest, player or crafting slots"
                $sortButtons = @($buttonGeometry.buttons | Where-Object {
                    $_.class -eq 'com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton' -and $_.visible
                })
                $playerButtons = @($sortButtons | Where-Object playerGroup)
                $chestButtons = @($sortButtons | Where-Object { !$_.playerGroup })
                Check ($playerButtons.Count -eq 2 -and $chestButtons.Count -eq 2) "$label keeps separate BogoSorter player and chest button pairs"
                $storageIds = @($state.activeContainer.slots | Where-Object role -in @('storage','additional-storage') |
                    Select-Object -ExpandProperty containerSlot | Sort-Object) -join ','
                $chestIds = @($state.activeContainer.slots | Where-Object slotClass -eq 'tconstruct.tools.gui.ChestSlot' |
                    Select-Object -ExpandProperty containerSlot | Sort-Object) -join ','
                foreach ($button in $sortButtons) {
                    $actualIds = @($button.groupSlotIds | Sort-Object) -join ','
                    $expectedIds = if ($button.playerGroup) { $storageIds } else { $chestIds }
                    if ($button.hotbarGroup -or $actualIds -ne $expectedIds) { throw "$label BogoSorter group mixes storage, hotbar, equipment or chest slots" }
                    $relative = [pscustomobject]@{x=$button.xPosition-$buttonGeometry.guiLeft; y=$button.yPosition-$buttonGeometry.guiTop;
                        width=$button.width; height=$button.height}
                    foreach ($cell in @($players + $craft + $chest)) {
                        if (ButtonOverlap $relative $cell) { throw "$label BogoSorter button covers a live slot" }
                    }
                    $firstRow = if ($button.playerGroup) { ($players | Measure-Object y -Minimum).Minimum }
                        else { ($chest | Measure-Object y -Minimum).Minimum }
                    if ($relative.y -lt 0 -or $relative.y + $relative.height -gt $firstRow) {
                        throw "$label BogoSorter button is outside its own pane header"
                    }
                }
                Check $true "$label BogoSorter groups contain exactly 72 player storage or 243 chest slots, with no shared members"
                for ($i=0; $i -lt $sortButtons.Count; $i++) {
                    for ($j=$i+1; $j -lt $sortButtons.Count; $j++) {
                        $a=$sortButtons[$i]; $b=$sortButtons[$j]
                        if ($a.xPosition -lt $b.xPosition+$b.width -and $b.xPosition -lt $a.xPosition+$a.width -and
                            $a.yPosition -lt $b.yPosition+$b.height -and $b.yPosition -lt $a.yPosition+$a.height) {
                            throw "$label BogoSorter buttons overlap"
                        }
                    }
                }
                Check $true "$label BogoSorter buttons stay separate in their own headers and clear of live slots"
                $buttonGeometry | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory "$label-buttons.json")
                $sideBySide = ($craft | Measure-Object x -Minimum).Minimum -ge ($players | Measure-Object x -Maximum).Maximum + 16
                # Auto can select a 270-pixel logical height. With NEI margins reserved, even
                # Compact correctly scrolls there. The reachability checks below cover that fallback.
                if ($window[0] -eq 1920 -and $scale -ne 0) {
                    Check ($players.Count -eq 81 -and $sideBySide) "$label full expanded inventory fits left of crafting"
                }
                $view = @{label=$label; requestedWindowWidth=$window[0]; requestedWindowHeight=$window[1];
                    visiblePlayerSlots=$players.Count; totalPlayerSlots=81;
                    scrollFallback=($players.Count -lt 81); sideBySide=$sideBySide; visibleChestSlots=$chest.Count}
                $filename = "step3-attached-$label.png"
                Harness clear-chat | Out-Null
                Harness screenshot-hover ($audit.playerSlots | Where-Object ordinaryIndex -eq $probeLogical).containerSlot $filename | Out-Null
                Copy-Item -LiteralPath (Join-Path $GameDirectory "screenshots/$filename") -Destination (Join-Path $EvidenceDirectory $filename) -Force
                # The window manager may adjust the requested size. Record the actual PNG framebuffer,
                # rather than treating the human-readable view label as measured dimensions.
                $png = [IO.File]::ReadAllBytes((Join-Path $EvidenceDirectory $filename))
                $view.capturedFramebufferWidth = [Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($png,16))
                $view.capturedFramebufferHeight = [Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($png,20))
                $views.Add($view)
                for ($frame = 0; $frame -lt 3; $frame++) {
                    $fresh = Snapshot "$label-frame-$frame"
                    Check ((GeometrySignature $fresh) -eq (GeometrySignature $state) -and
                        (ContainerSignature $fresh) -eq $contents) "$label fresh frame $frame has no coordinate drift or content changes"
                }
                $lastId = ($audit.playerSlots | Where-Object ordinaryIndex -eq 80).containerSlot
                $bottom = $state
                for ($wheel = 0; $wheel -lt 72 -and $bottom.activeContainer.slots[$lastId].x -lt 0; $wheel++) {
                    # Occupied slots intentionally let NEI move items. Probe an empty cell when testing viewport scroll.
                    $target = PlayerSlots $bottom | Where-Object { $_.role -ne 'hotbar' -and $_.x -ge 0 -and !$_.item } | Select-Object -First 1
                    if ($null -eq $target) { throw "$label has no empty visible cell for the viewport-wheel probe" }
                    Harness input-wheel $target.containerSlot -1 | Out-Null
                    $bottom = Snapshot "$label-wheel-$wheel"
                }
                Check ($bottom.activeContainer.slots[$lastId].x -ge 0) "$label wheel reaches the final expanded player slot"
                Check (!$bottom.cursorOccupied -and (ContainerSignature $bottom) -eq $contents) "$label scrolling preserves every stack and empty cursor"
                # Reinitialization resets only the viewport before an exact packet-backed item round trip.
                Harness native-layout-set $preset.columns $preset.rows | Out-Null
                # Tinkers preserves its independent chest slider across reinitialization.
                # Reset it too before comparing the complete container with the top-row reference.
                Harness chest-scroll top | Out-Null
                $reset = Snapshot "$label-reset"
                Check ((GeometrySignature $reset) -eq (GeometrySignature $state)) "$label reset retains the adapted geometry through Forge initialization"
                $empty = VisibleChest $reset | Where-Object { !$_.item } | Sort-Object containerSlot | Select-Object -First 1
                Check ($null -ne $empty) "$label has an empty visible native chest cell for the probe"
                $hotbarId = ($audit.playerSlots | Where-Object ordinaryIndex -eq $probeLogical).containerSlot
                Harness input-click $hotbarId | Out-Null
                Check (Snapshot "$label-held").cursorOccupied "$label real mouse pickup holds the hotbar stack"
                Harness input-click $empty.containerSlot | Out-Null
                $placed = Snapshot "$label-placed"
                Check (!$placed.cursorOccupied -and $placed.activeContainer.slots[$empty.containerSlot].item -eq $probe[0].item -and
                    $placed.activeContainer.slots[$empty.containerSlot].count -eq $probe[0].count) "$label real mouse places the exact probe stack in the chest"
                Harness input-click $empty.containerSlot | Out-Null
                Harness input-click $hotbarId | Out-Null
                $returned = Snapshot "$label-returned"
                Check (!$returned.cursorOccupied -and (ContainerSignature $returned) -eq $contents -and
                    (MainSignature $returned) -eq (MainSignature $initial)) "$label round trip restores all exact contents and cursor"
            }
        }
    }
    # Exercise the production editor too: this control used to be disabled for split stations.
    Harness editor-open | Out-Null
    Harness editor-control 113 | Out-Null # Advanced page.
    Harness editor-control 13 | Out-Null # Texture crop + 1.
    $crop = Harness editor-crop-check
    Check ($crop -match 'PASS live native crop inset=[1-8]') 'Split-station Texture crop is enabled and changes the actual crop rectangle'
    Harness editor-control 101 | Out-Null # Cancel: no user layout file is saved.
    $afterEditor = Snapshot 'crop-editor-cancel'
    Check (!$afterEditor.cursorOccupied -and (ContainerSignature $afterEditor) -eq $contents -and
        (MainSignature $afterEditor) -eq (MainSignature $initial)) 'Crop preview/cancel preserves every stack and the empty cursor'
    $report.success = $true
    $report.status = 'passed'
} catch {
    $report.status = 'failed'
    $report.error = $_.Exception.Message
    throw
} finally {
    if ($fixtureOpened) {
        try { Harness native-layout-clear | Out-Null; $report.restoredLayout = 'Saved family layout restored; no layout file changed' }
        catch { $report.success = $false; $report.status = 'failed'; $report.restoreError = $_.Exception.Message }
    }
    $report.checks = $checks.ToArray()
    $report.checkCount = $checks.Count
    $report.views = $views.ToArray()
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report.scope = 'Selected actual full-pack native TConstruct attached-chest layout checks: installed file hash only; default 72 storage with Compact preference and responsive rectangular chest grid, optional Wide inventory preset, 2 requested window sizes (actual framebuffer dimensions recorded per capture), 4 GUI scales, separate equal-width panels with centered crafting, isolated chest-sort packet with occupied added player storage, canonical slots, geometry, wheel reachability, frame stability and exact item round trips. Border style is visually reviewed in the screenshots.'
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding UTF8
}
if (!$report.success) { throw 'Compact layout restoration failed; inspect verification.json' }
Write-Host "PASS attached chest layout: $($checks.Count) checks."
