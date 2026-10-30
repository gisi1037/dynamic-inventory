param(
    [int] $Port = 5006,
    [string] $WorldName = 'DI-Baseline-Vanilla-UI-Lab-Qualification',
    [switch] $EntitiesOnly
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$directory=Join-Path $projectRoot '.setup/vanilla-ui-lab'
$fixture=Join-Path $directory 'panel-client-fixture.json'
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    $output=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Vanilla panel check failed: $args" }
    return ($output -join "`n")
}
Harness require-qualification $WorldName | Out-Null
$stations=Get-Content (Join-Path $directory 'stations.json') -Raw | ConvertFrom-Json
$results=[Collections.Generic.List[object]]::new()
foreach ($station in $stations | Where-Object { !$EntitiesOnly -and $_.block -notmatch 'command_block|standing_sign|jukebox|rail' }) {
    Harness resume | Out-Null
    Harness command ('/tp @p {0} 4 {1}' -f $station.x,($station.z-2)) | Out-Null
    Harness block-open $station.x $station.y $station.z | Out-Null
    $mapping=Harness foreign-check
    if ($mapping -notmatch '(?m)^GUI=(.+)$') { throw "Missing GUI: $($station.name)" }
    $gui=$Matches[1].Trim()
    Harness gate-state ready | Out-Null
    $check=Harness panel-multi $WorldName
    $check | Set-Content -LiteralPath (Join-Path $directory ($station.name.Replace(' ','-').ToLowerInvariant()+'.log'))
    $results.Add([pscustomobject]@{station=$station.name;gui=$gui;passed=$true})
    Write-Output "PASS $($station.name): $gui"
}
foreach ($entity in @(
    @{name='Lab Trader';x=-12;z=34;sneak=$false},
    @{name='Lab Horse';x=-4;z=34;sneak=$true},
    @{name='Lab Donkey';x=4;z=34;sneak=$true},
    @{name='Lab Mule';x=12;z=34;sneak=$true},
    @{name='Lab Chest Cart';selector='net.minecraft.entity.item.EntityMinecartChest|mods.railcraft.common.carts.EntityCartChest';x=-4;z=44;sneak=$false},
    @{name='Lab Hopper Cart';selector='net.minecraft.entity.item.EntityMinecartHopper';x=4;z=44;sneak=$false}
)) {
    Harness resume | Out-Null
    Harness command ('/tp @p {0} 4 {1}' -f ($entity.x+0.5),($entity.z-0.5)) | Out-Null
    Harness sneak ([string][bool]$entity.sneak).ToLowerInvariant() | Out-Null
    $selector=if ($entity.selector) { $entity.selector } else { $entity.name }
    try { Harness entity-open $selector | Out-Null }
    finally { Harness sneak false | Out-Null }
    $mapping=Harness foreign-check
    if ($mapping -notmatch '(?m)^GUI=(.+)$') { throw "Missing GUI: $($entity.name)" }
    $gui=$Matches[1].Trim()
    Harness gate-state ready | Out-Null
    $check=Harness panel-multi $WorldName
    $check | Set-Content -LiteralPath (Join-Path $directory ($entity.name.Replace(' ','-').ToLowerInvariant()+'.log'))
    $results.Add([pscustomobject]@{station=$entity.name;gui=$gui;passed=$true})
    Write-Output "PASS $($entity.name): $gui"
}
Harness resume | Out-Null
Harness command '/tp @p 0 5 -8 0 15' | Out-Null
$resultFile=if ($EntitiesOnly) {'nightly-entity-checks.json'} else {'nightly-panel-checks.json'}
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory $resultFile) -Encoding UTF8
Write-Output "PASS $($results.Count) nightly inventory variants; original panel preferences restored after every check."
