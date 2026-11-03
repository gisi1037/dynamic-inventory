param(
    [int] $Port = 5007,
    [string] $WorldName = 'DI-Baseline-Storage-Panel-Lab-Qualification',
    [switch] $PrepareOnly
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$directory = Join-Path $projectRoot '.setup/harvestcraft-panel-lab'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$commands = [Collections.Generic.List[string]]::new()
$stations = [Collections.Generic.List[object]]::new()
$types = @('Churn','Oven','Quern','Animal trap','Fish trap','Apiary','Grinder','Presser')
$blocks = @('churn','oven','quern','animaltrap','fishtrap','apiary',$null,'presser')
for ($i=0; $i -lt $types.Count; $i++) {
    $x = -12 + ($i % 4) * 8
    $z = 28 + [int][Math]::Floor($i/4) * 8
    $enabled = $null -ne $blocks[$i]
    if ($blocks[$i] -eq 'fishtrap') {
        for ($dx=-2; $dx -le 2; $dx++) { for ($dz=-2; $dz -le 2; $dz++) {
            if ([Math]::Abs($dx) -eq 2 -or [Math]::Abs($dz) -eq 2) {
                $commands.Add(('/setblock {0} 4 {1} minecraft:stone 0 replace' -f ($x+$dx),($z+$dz)))
            }
        } }
        for ($dx=-1; $dx -le 1; $dx++) { for ($dz=-1; $dz -le 1; $dz++) {
            $commands.Add(('/setblock {0} 4 {1} minecraft:water 0 replace' -f ($x+$dx),($z+$dz)))
        } }
    }
    if ($enabled) { $commands.Add("/setblock $x 4 $z harvestcraft:$($blocks[$i]) 0 replace") }
    $detail = if ($enabled) { 'Right-click to test' } else { 'Disabled by pack' }
    $signZ = if ($blocks[$i] -eq 'fishtrap') { $z-3 } else { $z-2 }
    $commands.Add(('/setblock {0} 4 {1} minecraft:standing_sign 8 replace {{Text1:"{2}",Text2:"{3}",Text3:"Panel batch 2",Text4:"HarvestCraft"}}' -f $x,$signZ,$types[$i],$detail))
    $stations.Add([pscustomobject]@{name=$types[$i];block=$blocks[$i];enabled=$enabled;x=$x;y=4;z=$z})
}
$commands.Add('/tp @p 0 5 22')
$commandPath = Join-Path $directory 'commands.txt'
[IO.File]::WriteAllLines($commandPath,$commands,[Text.UTF8Encoding]::new($false))
$stations | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'stations.json') -Encoding UTF8
if ($PrepareOnly) { Write-Output 'Prepared seven enabled HarvestCraft stations and a disabled Grinder marker.'; exit 0 }
if (Test-Path -LiteralPath (Join-Path $directory 'installed.json')) { throw 'This lab addition already ran; existing stations and test items are preserved.' }
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "HarvestCraft lab setup failed: $args" }
}
Harness require-qualification $WorldName
Harness resume
Harness command '/tp @p 0 5 24'
Harness harvest-lab-empty $WorldName
Harness commands-file $WorldName $commandPath
Harness look 0 15
@{world=$WorldName;stations=7;capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $directory 'installed.json') -Encoding UTF8
Write-Output 'HarvestCraft stations ready behind chest batch 1.'
