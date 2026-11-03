param(
    [int] $Port = 5007,
    [string] $WorldName = 'DI-Baseline-Storage-Panel-Lab-Qualification',
    [string] $GameDirectory = (Join-Path $env:APPDATA 'PrismLauncher/instances/GTNH-SFM-Daily-2026-10-27-803/.minecraft'),
    [switch] $PrepareOnly
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
if ($WorldName -notmatch '^DI-Baseline-Storage-.+-Qualification$') { throw 'A separate storage lab world is required.' }
$directory = Join-Path $projectRoot '.setup/storage-panel-lab'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$commands = [Collections.Generic.List[string]]::new()
$stations = [Collections.Generic.List[object]]::new()
foreach ($command in @('/gamerule doMobSpawning false','/gamerule doDaylightCycle false',
    '/gamerule keepInventory true','/difficulty peaceful','/time set 6000','/weather clear 1000000','/gamemode 0')) {
    $commands.Add($command)
}
$tiers = @('Iron','Gold','Diamond','Copper','Steel','Crystal','Obsidian','Dirt','Netherite','Dark Steel','Silver')
for ($index = 0; $index -lt 13; $index++) {
    $x = -12 + ($index % 5) * 6
    $z = [int][Math]::Floor($index / 5) * 8
    $name = if ($index -lt 11) { $tiers[$index] + ' Chest' } elseif ($index -eq 11) { 'Baby Chest' } else { 'Compressed Chest' }
    $block = if ($index -lt 11) { 'IronChest:BlockIronChest' } elseif ($index -eq 11) { 'dreamcraft:BabyChest' } else { 'avaritiaddons:CompressedChest' }
    $metadata = if ($index -lt 11) { $index } else { 0 }
    $commands.Add("/setblock $x 4 $z $block $metadata replace")
    $detail = if ($index -eq 7) { 'Dirt only' } elseif ($index -in @(8,10)) { 'Recipe pack-disabled' } else { 'Right-click to test' }
    $commands.Add(('/setblock {0} 4 {1} minecraft:standing_sign 8 replace {{Text1:"{2}",Text2:"{3}",Text3:"Panel batch 1",Text4:""}}' -f $x,($z-1),$name,$detail))
    $stations.Add([pscustomobject]@{name=$name;block=$block;metadata=$metadata;x=$x;y=4;z=$z})
}
foreach ($item in @('dirt','cobblestone','iron_ingot','redstone','diamond')) { $commands.Add("/give @p minecraft:$item 64") }
$commands.Add('/setworldspawn 0 4 -6')
$commands.Add('/tp @p 0 5 -6')
$commands.Add('/say Storage panel batch 1: 11 Iron Chests tiers, Baby Chest and Compressed Chest. Split icon: left detach, right settings.')
$commandPath = Join-Path $directory 'commands.txt'
[IO.File]::WriteAllLines($commandPath, $commands, [Text.UTF8Encoding]::new($false))
$stations | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'stations.json') -Encoding UTF8
if ($PrepareOnly) { Write-Output "Prepared 13 labeled chest variants: $commandPath"; exit 0 }
if (Test-Path -LiteralPath (Join-Path $GameDirectory "saves/$WorldName")) { throw 'The lab world already exists. Reopen it for testing, or choose a new lab name; existing test items are preserved.' }
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Storage lab setup failed: $args" }
}
Harness world $WorldName
Harness require-qualification $WorldName
Harness background
Harness command '/tp @p 0 5 8'
$loaded = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' -cp .setup StorageHarness lab-chunks-ready $WorldName 2>&1
    if ($LASTEXITCODE -eq 0) { $loaded = $true; break }
    Start-Sleep -Milliseconds 500
}
if (!$loaded) { throw 'Storage lab chunks did not finish loading; no blocks have been placed.' }
Harness commands-file $WorldName $commandPath
Harness look 0 15
Harness world-status
Harness resume
Write-Output "Ready: $WorldName, 13 labeled chest variants and starter transfer stacks."
