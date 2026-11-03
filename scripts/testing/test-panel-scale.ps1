param([int] $Port = 5007)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$world = 'DI-Baseline-Panel-Routing-Qualification'
$directory = Join-Path $projectRoot '.setup/harvestcraft-panel-lab'
$fixture = Join-Path $directory 'routing-fixture.json'
$original = Join-Path $env:APPDATA 'PrismLauncher/instances/GTNH-SFM-Daily-2026-10-27-803/.minecraft/config/dynamicinventory/player-panels.json'
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.waitMs=30000' '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" "-Ddinv.panelOriginalFile=$original" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Panel scale check failed: $args" }
}
Harness world $world
Harness require-qualification $world
Harness window 1920 1080
Harness capture scale-player
Harness scale 2
Harness panel-preview $world fixture
$checks = [Collections.Generic.List[object]]::new()
try {
    foreach ($scope in @('global','custom')) {
        Harness scale 2
        Harness panel-scale-save $scope
        foreach ($scale in @(2,1,3,2)) {
            Harness scale $scale
            Harness panel-scale-check $scope
            $checks.Add([pscustomobject]@{scope=$scope;scale=$scale;passed=$true})
        }
        Harness screenshot ("step3-panel-scale-verified-$scope.png")
    }
    foreach ($context in @('player','chest')) {
        if ($context -eq 'chest') { Harness resume; Harness block-open 0 4 2; Harness gate-state ready }
        Harness panel-preview $world fixture
        Harness routing-seed spawn-extra
        Harness routing-spawn
        Harness routing-assert spawn-extra
        $checks.Add([pscustomobject]@{context=$context;case='spawn-extra';passed=$true})
    }
} finally {
    Harness panel-preview $world restore
    @{success=($checks.Count -eq 10);checks=@($checks);capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $directory 'scale-verification.json') -Encoding UTF8
}
Harness resume
Harness disconnect
Write-Output 'PASS global/custom layout scope and scale persistence, plus NEI spawns into appended Main slots.'
