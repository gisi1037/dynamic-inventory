param([int] $Port = 5007, [switch] $PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$world = 'DI-Baseline-Storage-Panel-Lab-Qualification'
$directory = Join-Path $projectRoot '.setup/ae2-panel-lab'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
if (Test-Path -LiteralPath (Join-Path $directory 'installed.json')) { throw 'AE2 stations already installed; existing test contents are preserved.' }
function Harness {
    & 'C:/Program Files/Java/jdk-25/bin/java.exe' --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.waitMs=30000' '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "AE2 station setup failed: $args" }
}
$registered = @(Get-Content -LiteralPath (Join-Path $directory 'blocks.txt') | Where-Object { $_ -match '^appliedenergistics2:' })
$items = @{}; $parts = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $directory 'definitions.txt')) {
    if ($line -match '^item=(.+):(\d+)$') { $items[$Matches[1]] = [int]$Matches[2] }
    if ($line -match '^part=(.+):(\d+)$') { $parts[$Matches[1]] = [int]$Matches[2] }
}
if (!$items.ContainsKey('item.ItemMultiPart') -or $parts.Count -ne 7) { throw 'Read the installed AE2 part definitions first.' }
$power = 'appliedenergistics2:tile.BlockCreativeEnergyCell'
if ($registered -notcontains $power) { throw 'The installed pack has no Creative Energy Cell for terminal test stations.' }
$types = @(
    @('Grindstone','BlockGrinder'), @('Condenser','BlockCondenser'), @('ME Chest','BlockChest'),
    @('Sky Stone Chest','BlockSkyChest'), @('ME Drive','BlockDrive'), @('Vibration Chamber','BlockVibrationChamber'),
    @('Inscriber','BlockInscriber'), @('Adv. Inscriber','BlockAdvancedInscriber'), @('ME IO Port','BlockIOPort'),
    @('Crystal Growth','BlockCrystalGrowthChamber'), @('ME Assembler','BlockMolecularAssembler'),
    @('Wireless AP','BlockWireless'), @('Security','BlockSecurity'), @('Quantum Chamber','BlockQuantumLinkChamber'),
    @('Spatial IO Port','BlockSpatialIOPort'), @('Spatial Link','BlockSpatialLinkChamber'),
    @('ME Interface','BlockInterface'), @('Cell Workbench','BlockCellWorkbench'),
    @('ME Terminal','part:Terminal'), @('Crafting Term','part:CraftingTerminal'),
    @('Pattern Term','part:PatternTerminal'), @('Extended Pattern','part:PatternTerminalEx'),
    @('Interface Term','part:InterfaceTerminal'), @('Diagnostic Term','part:CraftingDiagnosticTerminal'),
    @('Portable Cell','supply'))
$commands = [Collections.Generic.List[string]]::new()
$stations = [Collections.Generic.List[object]]::new()
for ($i=0; $i -lt $types.Count; $i++) {
    $name, $type = $types[$i]
    $x = -16 + ($i % 5) * 8; $z = 48 + [int][Math]::Floor($i/5) * 8
    $block = if ($type.StartsWith('part:')) { 'appliedenergistics2:tile.BlockCableBus' }
        elseif ($type -eq 'supply') { 'minecraft:chest' } else { 'appliedenergistics2:tile.' + $type }
    $enabled = $type -eq 'supply' -or $registered -contains $block
    $nbt = ''
    if ($enabled -and $type.StartsWith('part:')) {
        # Native part placement runs on the server thread after block commands, because 1.7.10's
        # command parser cannot represent AE2's colon-containing NBT keys faithfully.
        $commands.Add("/setblock $($x+1) 4 $($z+1) appliedenergistics2:tile.BlockDrive 0 keep")
        $commands.Add("/setblock $($x+2) 4 $($z+1) appliedenergistics2:tile.BlockInterface 0 keep")
    }
    if ($enabled) {
        $commands.Add("/setblock $x 4 $z $block 0 keep$nbt")
        if ($type -ne 'BlockSkyChest' -and $type -ne 'supply') { $commands.Add("/setblock $x 4 $($z+1) $power 0 keep") }
    }
    $hint = if (!$enabled) { 'Disabled by pack' } elseif ($type -eq 'supply') { 'Tools and cells' }
        elseif ($type -eq 'BlockChest') { 'Sneak: cell slot' } else { 'Right-click UI' }
    $commands.Add(('/setblock {0} 4 {1} minecraft:standing_sign 8 keep {{Text1:"{2}",Text2:"{3}",Text3:"Panel batch 3",Text4:"AE2"}}' -f $x,($z-2),$name,$hint))
    $stations.Add([pscustomobject]@{name=$name;type=$type;block=$block;enabled=$enabled;x=$x;y=4;z=$z})
}
$commands.Add('/tp @p 0 5 42')
$commandPath = Join-Path $directory 'commands.txt'
[IO.File]::WriteAllLines($commandPath,$commands,[Text.UTF8Encoding]::new($false))
$stations | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'stations.json') -Encoding UTF8
if ($PrepareOnly) { Write-Output 'Prepared AE2 station commands without modifying the world.'; exit 0 }
Harness require-qualification $world
Harness resume
Harness command '/tp @p 0 5 64'
Harness ae2-lab-empty
Harness commands-file $world $commandPath
Harness ae2-lab-install-parts
Harness ae2-lab-install-supplies
Harness ae2-lab-verify $commandPath
Harness look 0 15
$enabled = @($stations | Where-Object enabled).Count
@{world=$world;stations=$enabled;capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $directory 'installed.json') -Encoding UTF8
Write-Output "Placed $enabled enabled AE2 stations behind the HarvestCraft rows; UI review is left to the player."
