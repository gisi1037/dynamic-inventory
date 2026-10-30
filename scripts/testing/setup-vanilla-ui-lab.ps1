param(
    [int] $Port = 5006,
    [string] $WorldName = 'DI-Baseline-Vanilla-UI-Lab-Qualification',
    [switch] $PrepareOnly,
    [switch] $PopulateExisting
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
if ($WorldName -notmatch '^DI-Baseline-Vanilla-.+-Qualification$') { throw 'A separate vanilla lab world is required.' }
$directory = Join-Path $projectRoot '.setup/vanilla-ui-lab'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$commands = [Collections.Generic.List[string]]::new()
$stations = [Collections.Generic.List[object]]::new()
function Command([string] $Text) { $commands.Add($Text) }
function Sign([int] $X, [int] $Z, [string] $Title, [string] $Detail = 'Right-click to open') {
    Command ('/setblock {0} 4 {1} minecraft:standing_sign 8 replace {{Text1:"{2}",Text2:"{3}",Text3:"Vanilla UI lab",Text4:""}}' -f $X,$Z,$Title,$Detail)
}
function Station([string] $Name, [string] $Block, [int] $X, [int] $Z, [int] $Metadata = 0) {
    Command "/setblock $X 4 $Z minecraft:$Block $Metadata"
    Sign $X ($Z - 1) $Name
    $stations.Add([pscustomobject]@{name=$Name;block="minecraft:$Block";x=$X;y=4;z=$Z})
}
Command '/gamerule doMobSpawning false'
Command '/gamerule doDaylightCycle false'
Command '/gamerule mobGriefing false'
Command '/gamerule keepInventory true'
Command '/difficulty peaceful'
Command '/time set 6000'
Command '/weather clear 1000000'
Command '/gamemode 0'
Station 'Single chest' 'chest' -12 0 2
Station 'Double chest' 'chest' -4 0 2
Command '/setblock -3 4 0 minecraft:chest 2'
Station 'Trapped chest' 'trapped_chest' 4 0 2
Station 'Ender chest' 'ender_chest' 12 0 2
Station 'Crafting table' 'crafting_table' -12 8
Station 'Furnace' 'furnace' -4 8 2
Station 'Dispenser' 'dispenser' 4 8 2
Station 'Dropper' 'dropper' 12 8 2
Station 'Hopper' 'hopper' -12 16
Station 'Brewing stand' 'brewing_stand' -4 16
Station 'Enchanting' 'enchanting_table' 4 16
foreach ($offset in @(@(-2,-2),@(-1,-2),@(0,-2),@(1,-2),@(2,-2),@(-2,0),@(2,0),@(-2,2),@(-1,2),@(0,2),@(1,2),@(2,2),@(-2,1),@(2,1),@(-2,-1))) {
    Command ('/setblock {0} 4 {1} minecraft:bookshelf' -f (4+$offset[0]),(16+$offset[1]))
}
Station 'Anvil' 'anvil' 12 16
for ($x=-13; $x -le -11; $x++) { for ($z=23; $z -le 25; $z++) { Command "/setblock $x 3 $z minecraft:iron_block" } }
Station 'Beacon' 'beacon' -12 24
Station 'Command block' 'command_block' -4 24
Sign -4 23 'Command block' 'Creative mode only'
Station 'Sign editor' 'standing_sign' 4 24 8
Sign 4 23 'Sign editor' 'Replace sign to edit'
Command '/give @p minecraft:sign 8'
Station 'Jukebox' 'jukebox' 12 24
Sign 12 23 'Jukebox' 'No inventory screen'
# Entity inventory variants have individual pens and stable names for repeatable opening checks.
foreach ($pen in @(@(-12,'Villager'),@(-4,'Horse'),@(4,'Donkey'),@(12,'Mule'))) {
    $cx=[int]$pen[0]
    for ($dx=-2; $dx -le 2; $dx++) { for ($dz=-2; $dz -le 2; $dz++) {
        if ([Math]::Abs($dx) -eq 2 -or [Math]::Abs($dz) -eq 2) {
            Command ('/setblock {0} 4 {1} minecraft:fence' -f ($cx+$dx),(34+$dz))
        }
    } }
    Sign $cx 31 ([string]$pen[1]) $(if ($pen[1] -eq 'Villager') {'Right-click to trade'} else {'Mount then press E'})
}
Command '/summon Villager -12 4 34 {CustomName:"Lab Trader",PersistenceRequired:1b,Profession:0,Offers:{Recipes:[{buy:{id:388,Count:1b},sell:{id:297,Count:3b},maxUses:9999,uses:0}]}}'
Command '/summon EntityHorse -4 4 34 {CustomName:"Lab Horse",PersistenceRequired:1b,Tame:1b,Type:0,SaddleItem:{id:329,Count:1b}}'
Command '/summon EntityHorse 4 4 34 {CustomName:"Lab Donkey",PersistenceRequired:1b,Tame:1b,Type:1,ChestedHorse:1b,SaddleItem:{id:329,Count:1b}}'
Command '/summon EntityHorse 12 4 34 {CustomName:"Lab Mule",PersistenceRequired:1b,Tame:1b,Type:2,ChestedHorse:1b,SaddleItem:{id:329,Count:1b}}'
Station 'Chest minecart' 'rail' -4 44 1
Station 'Hopper minecart' 'rail' 4 44 1
Command '/summon MinecartChest -3.5 4.1 44.5 {CustomName:"Lab Chest Cart"}'
Command '/summon MinecartHopper 4.5 4.1 44.5 {CustomName:"Lab Hopper Cart"}'
foreach ($item in @('emerald','diamond','iron_ingot','coal','iron_ore','wheat','potion','blaze_powder','nether_wart','book','iron_sword')) {
    Command "/give @p minecraft:$item 8"
}
Command '/xp 100L @p'
Command '/setworldspawn 0 4 -8'
Command '/tp @p 0 5 -8 0 15'
Sign 0 -10 'Vanilla UI lab' 'Stations to the south'
$commandPath = Join-Path $directory 'commands.txt'
[IO.File]::WriteAllLines($commandPath,$commands,[Text.UTF8Encoding]::new($false))
$stations | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'stations.json') -Encoding UTF8
if ($PrepareOnly) { Write-Output "Prepared $($stations.Count) block stations and entity variants: $commandPath"; exit 0 }
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Lab setup action failed: $args" }
}
# StorageHarness refuses to replace an open world. Creation/population never targets an existing player save.
if (!$PopulateExisting) { Harness world $WorldName }
Harness require-qualification $WorldName
Harness command '/tp @p 0 5 16'
$loaded=$false
for ($attempt=0; $attempt -lt 20; $attempt++) {
    $chunkOutput=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' -cp .setup StorageHarness lab-chunks-ready $WorldName 2>&1
    if ($LASTEXITCODE -eq 0) { $loaded=$true; break }
    Start-Sleep -Milliseconds 500
}
if (!$loaded) { throw 'Vanilla lab chunks did not finish loading; no blocks have been placed.' }
Harness commands-file $WorldName $commandPath
Harness world-status
Harness resume
Harness disconnect-safe $WorldName
Harness world $WorldName
Write-Output "Created $WorldName with $($stations.Count) labeled block stations plus villager, horse, donkey, mule and minecart inventories."
