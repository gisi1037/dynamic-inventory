param([int] $Port = 5007)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$world = 'DI-Baseline-Panel-Routing-Qualification'
$directory = Join-Path $projectRoot '.setup/harvestcraft-panel-lab'
$fixture = Join-Path $directory 'routing-fixture.json'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$mainProfile = @{detached=$true;visible=$true;width=178;height=190;anchorX=5000;anchorY=5000;
    frameStyle=-1;borderWidth=2;frameCrop=0;cornerCrop=2;minimumMargin=8}
$sideProfile = $mainProfile.Clone(); $sideProfile.width=70; $sideProfile.height=116; $sideProfile.anchorX=9000
@{schemaVersion=7;globalReference=$null;interfaceReferences=@{};global=$mainProfile;interfaces=@{};panelLayouts=@{};
    panels=@(@{id='main';name='Main';slots=@();global=$mainProfile;interfaces=@{}},
        @{id='89103354-e68f-49a5-b69c-02afab3c49b7';name='Tools';slots=@(0..8);global=$sideProfile;interfaces=@{}})} |
    ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $fixture -Encoding UTF8
$original = Join-Path $env:APPDATA 'PrismLauncher/instances/GTNH-SFM-Daily-2026-10-27-803/.minecraft/config/dynamicinventory/player-panels.json'
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.waitMs=30000' '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" "-Ddinv.panelOriginalFile=$original" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Panel routing check failed: $args" }
}
Harness world $world
Harness require-qualification $world
Harness window 1920 1080
Harness command '/gamemode 0'
Harness command '/tp @p 0 5 0'
Harness command '/setblock 0 4 2 minecraft:chest 0 replace'
$cases = @('hotbar-to-main','main-to-hotbar','blocked-main','satellite','spawn','spawn-merge','spawn-full','spawn-infinite')
$results = [Collections.Generic.List[object]]::new()
$scaleChecks = [Collections.Generic.List[object]]::new()
try {
    foreach ($context in @('player','chest')) {
        if ($context -eq 'player') { Harness capture routing-player } else { Harness resume; Harness block-open 0 4 2 }
        Harness scale 2
        if ($context -eq 'chest') { Harness gate-state ready }
        Harness panel-preview $world fixture
        foreach ($case in $cases) {
            if ($context -eq 'chest' -and $case -notlike 'spawn*') { continue }
            Harness routing-seed $case
            if ($case -like 'spawn*') {
                if ($case -eq 'spawn-infinite') { Harness routing-spawn infinite } else { Harness routing-spawn }
            } else {
                $logical = if ($case -eq 'hotbar-to-main') {0} elseif ($case -eq 'satellite') {9} else {18}
                if ($case -eq 'main-to-hotbar') { Harness routing-shift $logical gui } else { Harness routing-shift $logical }
            }
            Harness routing-assert $case
            $results.Add([pscustomobject]@{context=$context;case=$case;passed=$true})
        }
        if ($context -eq 'chest') {
            foreach ($case in @('container-main','container-satellite','container-hotbar')) {
                Harness routing-seed $case
                $logical = if ($case -eq 'container-main') {18} elseif ($case -eq 'container-satellite') {9} else {0}
                Harness routing-shift $logical gui
                Harness routing-assert $case
                $results.Add([pscustomobject]@{context=$context;case=$case;passed=$true})
            }
        }
        if ($context -eq 'player') {
            foreach ($scope in @('global','custom')) {
                Harness scale 2
                Harness panel-scale-save $scope
                foreach ($scale in @(2,1,3,2)) {
                    Harness scale $scale
                    Harness panel-scale-check $scope
                    $scaleChecks.Add([pscustomobject]@{scope=$scope;scale=$scale;passed=$true})
                }
                Harness screenshot ("step3-panel-scale-$scope.png")
            }
        }
    }
} finally {
    Harness panel-preview $world restore
    @{success=($results.Count -eq 15 -and $scaleChecks.Count -eq 8);checks=@($results);scaleChecks=@($scaleChecks);capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $directory 'routing-verification.json') -Encoding UTF8
}
Harness resume
Harness disconnect
Write-Output 'PASS player panel routing, native chest Shift-click and NEI Main spawning on both peers.'
