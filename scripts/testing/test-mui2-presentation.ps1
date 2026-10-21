param(
    [int] $Port = 0,
    [string] $GameDirectory = '',
    [string] $EvidenceDirectory = '',
    [string] $PlayerName = '',
    [switch] $SourceFixture,
    [int] $ExpectedPlayerSlots = 0,
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
if ($Port -le 0) { $Port = if ($SourceFixture) { 5007 } else { 5005 } }
if (!$GameDirectory) {
    $GameDirectory = if ($SourceFixture) { 'run/rc2-integration/client-developer' }
        else { "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft" }
}
if (!$EvidenceDirectory) {
    $EvidenceDirectory = if ($SourceFixture) { 'docs/qualification/mui2-presentation/source-fixture' }
        else { 'docs/qualification/mui2-presentation/player-and-chest' }
}
if ($ExpectedPlayerSlots -le 0) { $ExpectedPlayerSlots = if ($SourceFixture) { 112 } else { 109 } }
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$screenshots = Join-Path $GameDirectory 'screenshots'
$clientConfig = Join-Path $GameDirectory 'config/dynamicinventory-client.cfg'
$evidenceFullPath = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) { [IO.Path]::GetFullPath($EvidenceDirectory) }
    else { [IO.Path]::GetFullPath((Join-Path $projectRoot $EvidenceDirectory)) }
New-Item -ItemType Directory -Force -Path $evidenceFullPath | Out-Null
$verificationPath = Join-Path $evidenceFullPath 'verification.json'
@{success=$false; status='running'; provenanceScope=if ($SourceFixture) {'immutable-source-launch-record'} else {'installed-file-only'};
    capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath $verificationPath -Encoding UTF8
if ($SourceFixture) {
    $sourceLaunch = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port `
        -GameDirectory $GameDirectory -Profile 'rc2-integration' -EvidenceDirectory $evidenceFullPath
    $artifactSha256 = [string]$sourceLaunch.artifactSha256
    $developmentArtifactSha256 = [string]$sourceLaunch.developmentArtifactSha256
} else {
    $installedJar = Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar'
    if (-not [IO.Path]::IsPathRooted($installedJar)) { $installedJar = Join-Path $projectRoot $installedJar }
    $installedJar = [IO.Path]::GetFullPath($installedJar)
    if (-not (Test-Path -LiteralPath $installedJar -PathType Leaf)) { throw "Missing installed production JAR: $installedJar" }
    $artifactSha256 = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
    $developmentArtifactSha256 = $null
    $sourceLaunch = $null
}
$packTarget = if ($SourceFixture) { '2.9.0-RC-2' } else {
    $marker = Join-Path (Split-Path $GameDirectory -Parent) '.dynamicinventory-dev-instance'
    (Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).version
}
$checks = [Collections.Generic.List[string]]::new()

function Harness {
    $arguments = @('--add-modules', 'jdk.jdi', "-Ddinv.port=$Port")
    if (!$SourceFixture) { $arguments += @('-Ddinv.srgMapping=.setup/mcp-srg.srg', '-Ddinv.packInput=true') }
    $arguments += @('-cp', '.setup', 'StorageHarness')
    $output = & $java @arguments @args
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
    Harness snapshot $Label | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw "No fresh MUI2 presentation snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function ContainerSignature($State) {
    return ($State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,inventoryClass,slotClass,role,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Ensure-PresentationEnabled {
    Harness resume | Out-Null
    Harness capture mui2-presentation-settings | Out-Null
    Harness layout-control 100 | Out-Null
    $mode = Harness machine-mode
    if ($mode -notmatch 'mui2Presentation=true') {
        Harness layout-control 112 | Out-Null
        $mode = Harness machine-mode
    }
    Check ($mode -match 'mui2Presentation=true') 'Layout dialog enables the opt-in MUI2 presentation'
    if (!(Test-Path -LiteralPath $clientConfig)) { throw "Missing client config: $clientConfig" }
    $config = Get-Content -LiteralPath $clientConfig -Raw
    Check ($config -match '(?i)B:mui2Presentation=true\b') 'Client config persists mui2Presentation=true'
    Harness layout-control 108 | Out-Null
    Harness resume | Out-Null
}
function Assert-Presentation([string] $Screen, [int] $ExpectedSlots) {
    $report = Harness presentation-check
    Check ($report -match '(?i)(?:^|\s)enabled=true(?:\s|$)') "$Screen has MUI2 presentation enabled"
    Check ($report -match '(?i)(?:^|\s)frameWidgets=1(?:\s|$)') "$Screen renders one shared MUI2 frame widget"
    $slotMatch = [regex]::Match($report, '(?:^|\s)slotWidgets=(\d+)')
    if (!$slotMatch.Success) { throw "$Screen presentation-check omitted slotWidgets: $report" }
    Check ([int] $slotMatch.Groups[1].Value -eq $ExpectedSlots) "$Screen MUI2 slot backgrounds represent $ExpectedSlots canonical slots"
    $visibleMatch = [regex]::Match($report, '(?:^|\s)visibleSlotWidgets=(\d+)')
    if (!$visibleMatch.Success) { throw "$Screen presentation-check omitted visibleSlotWidgets: $report" }
    $visible = [int] $visibleMatch.Groups[1].Value
    Check ($visible -gt 0 -and $visible -le $ExpectedSlots) "$Screen has a bounded positive number of visible MUI2 slot widgets"
    Check ($report -match '(?i)(?:^|\s)canonicalContainer=true(?:\s|$)') "$Screen retains its canonical native container"
    $drawMatch = [regex]::Match($report, '(?:^|\s)drawCalls=(\d+)')
    if (!$drawMatch.Success) { throw "$Screen presentation-check omitted drawCalls: $report" }
    Check ([int] $drawMatch.Groups[1].Value -gt 0) "$Screen has rendered MUI2 presentation draw calls"
}
function Save-Hover([string] $Name, [int] $SlotId) {
    $filename = "step3-mui2-presentation-$Name.png"
    Harness clear-chat | Out-Null
    Harness screenshot-hover $SlotId $filename | Out-Null
    $source = Join-Path $screenshots $filename
    if (!(Test-Path -LiteralPath $source)) { throw "Missing MUI2 presentation screenshot: $source" }
    Copy-Item $source (Join-Path $EvidenceDirectory $filename) -Force
    $checks.Add("$Name has a saved actual-screen slot-hover screenshot")
}
function Set-GuiScale([string] $Name, [int] $Scale, $Baseline, [int] $SlotCount, [int] $WindowId, [int] $HoverSlot) {
    $geometry = Harness scale $Scale
    Check ($geometry -match 'PASS geometry scale=') "$Name fits at GUI scale $Scale"
    $state = Snapshot "$Name-scale-$Scale"
    Check ($state.activeContainer.slotCount -eq $SlotCount -and $state.activeContainer.windowId -eq $WindowId) `
        "$Name keeps its $SlotCount-slot window $WindowId at GUI scale $Scale"
    Check ($state.mainInventory.Count -eq 82 -and !$state.cursorOccupied -and (MainSignature $state) -eq (MainSignature $Baseline)) `
        "$Name preserves all 82 main stacks and an empty cursor at GUI scale $Scale"
    Save-Hover "$Name-scale-$Scale" $HoverSlot
    Assert-Presentation $Name $script:expectedPresentationSlots
}

Harness require-qualification $WorldName | Out-Null
Harness background | Out-Null
Harness resume | Out-Null
$testPlayer = if ($PlayerName) { $PlayerName } else { '@p' }
Harness command "/tp $testPlayer 0 4 0" | Out-Null
Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
Harness command '/setblock 2 4 0 minecraft:air' | Out-Null
Ensure-PresentationEnabled

# The player's real persistent container stays open. The presentation adds frame
# and slot-background widgets while vanilla continues to render items and handle input.
Harness capture mui2-presentation-player | Out-Null
$playerBefore = Snapshot 'player-before'
Check ($playerBefore.screen -eq 'dev.gilbe.dynamicinventory.client.PlayerInventoryScreen') 'Player screen remains the existing PlayerInventoryScreen'
Check ($playerBefore.activeContainer.windowId -eq 0 -and $playerBefore.activeContainer.slotCount -eq $ExpectedPlayerSlots) "Player presentation retains canonical ContainerPlayer window zero with $ExpectedPlayerSlots slots"
Check ($playerBefore.mainInventory.Count -eq 82 -and $playerBefore.ordinarySlots -eq 81 -and $playerBefore.armorSlots -eq 4) 'Player presentation retains 82 raw main slots, 81 ordinary slots, and four armor slots'
Check (!$playerBefore.cursorOccupied) 'Player presentation starts with an empty cursor'
$playerMapping = Harness foreign-check
if ($playerMapping -notmatch 'ordinaryIds=\[([^\]]+)\]') { throw 'Player screen did not expose canonical ordinary slot IDs' }
$playerIds = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
Check ($playerIds.Count -eq 81) 'Player presentation exposes all 81 canonical ordinary slots'
$script:expectedPresentationSlots = $ExpectedPlayerSlots
Assert-Presentation 'Player screen' $ExpectedPlayerSlots

# Use actual client mouse input to move the first hotbar stack out and back. The
# guard avoids operating on an unexpected qualification-world inventory state.
if (!$playerBefore.mainInventory[0].item) { throw 'Player hotbar round trip requires a populated qualification item in ordinary slot 0' }
$playerContainerBefore = ContainerSignature $playerBefore
Harness input-click $playerIds[0] | Out-Null
$playerPicked = Snapshot 'player-picked-up'
Check ($playerPicked.cursorOccupied) 'Actual player-screen mouse input picks up the first hotbar stack'
Harness input-click $playerIds[0] | Out-Null
$playerReturned = Snapshot 'player-round-trip'
Check (!$playerReturned.cursorOccupied -and (MainSignature $playerReturned) -eq (MainSignature $playerBefore) `
    -and (ContainerSignature $playerReturned) -eq $playerContainerBefore) 'Player hotbar mouse round trip restores exact contents in the same window'

# Re-open settings through the real control and prove relayout does not mutate
# the selected player layout or any canonical stacks.
Harness layout-control 100 | Out-Null
$layoutBefore = Snapshot 'player-layout-before-settings'
Harness layout-control 112 | Out-Null
$disabledMode = Harness machine-mode
Check ($disabledMode -match 'mui2Presentation=false') 'Layout dialog can turn the opt-in MUI2 presentation off'
Harness layout-control 112 | Out-Null
$enabledMode = Harness machine-mode
Check ($enabledMode -match 'mui2Presentation=true') 'Layout dialog can restore the opt-in MUI2 presentation'
Harness layout-control 108 | Out-Null
$layoutAfter = Snapshot 'player-layout-after-settings'
Check ((MainSignature $layoutAfter) -eq (MainSignature $layoutBefore) -and !$layoutAfter.cursorOccupied `
    -and (ContainerSignature $layoutAfter) -eq (ContainerSignature $layoutBefore)) 'Changing the presentation preference preserves player layout, items, and cursor'
Check ($layoutAfter.activeContainer.windowId -eq 0 -and $layoutAfter.activeContainer.slotCount -eq $ExpectedPlayerSlots) "Presentation setting relayout keeps the same $ExpectedPlayerSlots-slot window zero"
Check ((Get-Content -LiteralPath $clientConfig -Raw) -match '(?i)B:mui2Presentation=true\b') 'Client config remains persisted with mui2Presentation=true after dialog round trip'
$playerBefore = $layoutAfter
foreach ($scale in @(1, 2, 3, 0)) {
    Set-GuiScale 'player' $scale $playerBefore $ExpectedPlayerSlots 0 $playerIds[0]
}

# Exercise the native chest screen separately. It keeps the chest's normal
# container protocol and all 81 appended player slots while sharing the MUI2 frame.
Harness resume | Out-Null
Harness command '/setblock 2 4 0 minecraft:chest' | Out-Null
Harness block-open 2 4 0 | Out-Null
$chestMapping = Harness foreign-check
if ($chestMapping -notmatch 'foreign=([^ ]+) count=(\d+)') { throw 'Chest did not report its native container and slot count' }
Check ($Matches[1] -eq 'net.minecraft.inventory.ContainerChest' -and [int] $Matches[2] -eq 108) 'Chest keeps native ContainerChest with 27 chest and 81 canonical player slots'
if ($chestMapping -notmatch '(?m)^GUI=([^\r\n]+)') { throw 'Chest did not report its current GUI type' }
Check ($Matches[1].Trim() -eq 'net.minecraft.client.gui.inventory.GuiChest') 'Chest keeps its native GuiChest renderer and input path'
$chestIds = @([regex]::Match($chestMapping, 'ordinaryIds=\[([^\]]+)\]').Groups[1].Value.Split(',') | ForEach-Object { [int] $_.Trim() })
Check ($chestIds.Count -eq 81) 'Chest presentation retains all 81 canonical player slots'
$chestBefore = Snapshot 'chest-before'
Check ($chestBefore.activeContainer.windowId -ne 0 -and $chestBefore.activeContainer.slotCount -eq 108) 'Opened chest has its own native window and 108 real slots'
Check ($chestBefore.mainInventory.Count -eq 82 -and !$chestBefore.cursorOccupied) 'Chest presentation starts with all player slots and an empty cursor'
if ($chestBefore.activeContainer.slots[0].item) { throw 'Chest-slot round trip requires first native chest slot to be empty' }
$script:expectedPresentationSlots = 81
Assert-Presentation 'Chest screen' 81

$chestPlayerBaseline = MainSignature $chestBefore
$chestContainerBaseline = ContainerSignature $chestBefore
if (!$chestBefore.mainInventory[0].item) { throw 'Chest round trip requires a populated player hotbar slot 0' }
Harness input-click $chestIds[0] | Out-Null
$chestPicked = Snapshot 'chest-picked-up'
Check ($chestPicked.cursorOccupied) 'Actual chest-screen mouse input picks up the first player hotbar stack'
Harness input-click 0 | Out-Null
$chestPlaced = Snapshot 'chest-stack-placed'
Check (!$chestPlaced.cursorOccupied -and $chestPlaced.activeContainer.slots[0].item -eq $chestBefore.mainInventory[0].item `
    -and $chestPlaced.activeContainer.slots[0].count -eq $chestBefore.mainInventory[0].count) 'Actual mouse input places the expected stack into the native chest slot'
Harness input-click 0 | Out-Null
Harness input-click $chestIds[0] | Out-Null
$chestReturned = Snapshot 'chest-round-trip'
Check (!$chestReturned.cursorOccupied -and (MainSignature $chestReturned) -eq $chestPlayerBaseline `
    -and (ContainerSignature $chestReturned) -eq $chestContainerBaseline) 'Chest/player mouse round trip restores exact contents and cursor state'

foreach ($scale in @(1, 2, 3, 0)) {
    Set-GuiScale 'chest' $scale $chestBefore 108 $chestBefore.activeContainer.windowId 0
}

# Leave the opt-in enabled for the coordinated root regression. Restore the
# persistent player screen so the next suite starts from the expected GUI.
Harness resume | Out-Null
Harness capture mui2-presentation-final-player | Out-Null
$finalPlayer = Snapshot 'final-player'
Check ($finalPlayer.activeContainer.windowId -eq 0 -and $finalPlayer.activeContainer.slotCount -eq $ExpectedPlayerSlots `
    -and (MainSignature $finalPlayer) -eq (MainSignature $playerBefore)) 'Final player screen returns to the unchanged persistent inventory with presentation still enabled'

$installedJar = if ($SourceFixture) { Join-Path $projectRoot 'build/libs/dynamicinventory-0.8.0-dev.jar' }
    else { Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar' }
if (!(Test-Path -LiteralPath $installedJar)) { throw "Missing installed production JAR: $installedJar" }
@{
    success = $true
    prototype = '0.8.0-dev'
    packTarget = $packTarget
    sourceFixture = [bool] $SourceFixture
    expectedPlayerSlots = $ExpectedPlayerSlots
    presentationEnabled = $true
    screens = @('player-window-0', 'native-chest')
    scales = @(1, 2, 3, 0)
    checks = $checks
    jarSha256 = $artifactSha256
    developmentArtifactSha256 = $developmentArtifactSha256
    provenanceScope = if ($SourceFixture) { 'immutable-source-launch-record' } else { 'installed-file-only' }
    sourceLaunchId = if ($sourceLaunch) { $sourceLaunch.launchId } else { $null }
    sourceLaunchProfile = if ($sourceLaunch) { $sourceLaunch.launchProfile } else { $null }
    sourceLaunchManifest = if ($sourceLaunch) { $sourceLaunch.launchManifest } else { $null }
    sourceLaunchManifestSha256 = if ($sourceLaunch) { $sourceLaunch.launchManifestSha256 } else { $null }
    capturedAtUtc = [datetime]::UtcNow.ToString('o')
    scope = 'Opt-in MUI2 shared frame and slot-background presentation on the existing ContainerPlayer/window 0 and native GuiChest; actual mouse round trips, preference persistence and item/layout preservation, scale bounds, and hovered-slot screenshots. MUI2 ItemSlot migration is not part of this qualification.'
} | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $verificationPath -Encoding UTF8
Write-Host "PASS MUI2 presentation: $($checks.Count) checks."
