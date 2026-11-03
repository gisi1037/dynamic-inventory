param([int] $Port = 5007, [string] $WorldName = 'DI-Baseline-Storage-Panel-Lab-Qualification')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$directory = Join-Path $projectRoot '.setup/harvestcraft-panel-lab'
$fixture = Join-Path $directory 'panel-client-fixture.json'
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "HarvestCraft check failed: $args" }
}
Harness require-qualification $WorldName
$stations = Get-Content -LiteralPath (Join-Path $directory 'stations.json') -Raw | ConvertFrom-Json
$results = [Collections.Generic.List[object]]::new()
try {
    foreach ($station in @($stations | Where-Object enabled)) {
        Harness resume
        Harness command ('/tp @p {0} 5 {1}' -f ($station.x+0.5),($station.z-3))
        Harness block-open $station.x $station.y $station.z
        Harness gate-state ready
        $mapping = Harness foreign-check
        if (($mapping -join '') -notmatch 'ordinaryIds=\[([^\]]+)\]') { throw 'Missing canonical mapping' }
        $ids = @($Matches[1].Split(',') | ForEach-Object {[int]$_.Trim()})
        if ($ids.Count -ne 81 -or @($ids | Sort-Object -Unique).Count -ne 81) { throw 'Invalid canonical player mapping' }
        Harness storage-frame-check
        Harness panel-multi $WorldName
        Harness screenshot ('step3-harvestcraft-'+$station.block+'.png')
        $results.Add([pscustomobject]@{station=$station.name;passed=$true})
        Write-Output "PASS HarvestCraft $($station.name): real opening, slots, split menu, allocation, dragging, reload and identities"
    }
} finally {
    @{success=($results.Count -eq 7);results=@($results);grinder='disabled by pack';capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $directory 'verification.json') -Encoding UTF8
}
Harness resume
Harness command '/tp @p 0 5 22'
Harness look 0 15
Harness clear-chat
Write-Output 'HarvestCraft batch ready for manual testing in the existing lab.'
