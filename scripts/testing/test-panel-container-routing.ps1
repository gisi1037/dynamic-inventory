param([int] $Port = 5007)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$world = 'DI-Baseline-Panel-Routing-Qualification'
$directory = Join-Path $projectRoot '.setup/container-shift-routing'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$fixture = Join-Path $directory 'panel-client-fixture.json'
$main = @{detached=$true;visible=$true;width=178;height=190;anchorX=5000;anchorY=5000;
    frameStyle=-1;borderWidth=2;frameCrop=0;cornerCrop=2;minimumMargin=8}
$side = $main.Clone(); $side.width=70; $side.height=116; $side.anchorX=9000
@{schemaVersion=7;globalReference=$null;interfaceReferences=@{};global=$main;interfaces=@{};panelLayouts=@{};
    panels=@(@{id='main';name='Main';slots=@();global=$main;interfaces=@{}},
        @{id='89103354-e68f-49a5-b69c-02afab3c49b7';name='Tools';slots=@(0..8);global=$side;interfaces=@{}})} |
    ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $fixture -Encoding UTF8
$original = Join-Path $env:APPDATA 'PrismLauncher/instances/GTNH-SFM-Daily-2026-10-27-803/.minecraft/config/dynamicinventory/player-panels.json'
function Harness {
    & 'C:/Program Files/Java/jdk-25/bin/java.exe' --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.waitMs=30000' '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" "-Ddinv.panelOriginalFile=$original" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Container routing check failed: $args" }
}
Harness world $world
Harness command '/gamemode 0'
Harness command '/tp @p 0 5 0'
Harness command '/setblock 0 4 2 minecraft:chest 0 keep'
Harness capture routing-player
Harness scale 2
Harness panel-preview $world fixture
$results = [Collections.Generic.List[object]]::new()
try {
    foreach ($case in @('hotbar-to-main','main-to-hotbar')) {
        Harness routing-seed $case
        $logical = if ($case -eq 'hotbar-to-main') {0} else {18}
        Harness routing-shift $logical gui
        Harness routing-assert $case
        $results.Add([pscustomobject]@{case=$case;passed=$true})
    }
    Harness resume
    Harness block-open 0 4 2
    Harness scale 2
    Harness panel-preview $world fixture
    Harness gate-state ready
    foreach ($case in @('container-main','container-satellite','container-hotbar')) {
        Harness routing-seed $case
        $logical = if ($case -eq 'container-main') {18} elseif ($case -eq 'container-satellite') {9} else {0}
        if ($case -eq 'container-satellite') { Harness routing-shift $logical } else { Harness routing-shift $logical gui }
        Harness routing-assert $case
        $results.Add([pscustomobject]@{case=$case;passed=$true})
    }
} finally {
    Harness panel-preview $world restore
    @{success=($results.Count -eq 5);checks=@($results);capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $directory 'verification.json') -Encoding UTF8
}
Harness resume
Harness disconnect
Harness world DI-Baseline-Storage-Panel-Lab-Qualification
Harness command '/tp @p 0 5 42'
Harness look 0 15
Harness clear-chat
Write-Output 'PASS player-only routing and native chest transfers from Main, satellite and hotbar on both peers.'
