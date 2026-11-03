param(
    [int] $Port = 5007,
    [string] $WorldName = 'DI-Baseline-Storage-Panel-Lab-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$directory = Join-Path $projectRoot '.setup/storage-panel-lab'
$fixture = Join-Path $directory 'panel-client-fixture.json'
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' "-Ddinv.panelFixtureFile=$fixture" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Storage panel check failed: $args" }
    return ($output -join "`n")
}
Harness require-qualification $WorldName | Out-Null
Harness window 1920 1080 | Out-Null
$scaled = $false
$stations = Get-Content -LiteralPath (Join-Path $directory 'stations.json') -Raw | ConvertFrom-Json
$results = [Collections.Generic.List[object]]::new()
try {
    foreach ($station in $stations) {
        Harness resume | Out-Null
        Harness command ('/tp @p {0} 4 {1}' -f ($station.x + 0.5),($station.z - 2)) | Out-Null
        Harness block-open $station.x $station.y $station.z | Out-Null
        if (!$scaled) { Harness scale 2 | Out-Null; $scaled = $true }
        $mapping = Harness foreign-check
        if ($mapping -notmatch 'ordinaryIds=\[([^\]]+)\]') { throw "Missing player mapping: $($station.name)" }
        $ids = @($Matches[1].Split(',') | ForEach-Object { [int]$_.Trim() })
        if ($ids.Count -ne 81 -or @($ids | Sort-Object -Unique).Count -ne 81) { throw "Invalid canonical mapping: $($station.name)" }
        $frame = Harness storage-frame-check
        Harness gate-state ready | Out-Null
        $check = Harness panel-multi $WorldName
        $check | Set-Content -LiteralPath (Join-Path $directory ($station.name.Replace(' ','-').ToLowerInvariant() + '.log')) -Encoding UTF8
        $results.Add([pscustomobject]@{station=$station.name;passed=$true;mapping=$mapping;frame=$frame;checks=$check})
        Write-Output "PASS $($station.name): opening, canonical mapping, split menu, allocation, dragging, reload and slot identities"
    }
} finally {
    @{success=($results.Count -eq $stations.Count);world=$WorldName;completed=$results.Count;total=$stations.Count;
        capturedAtUtc=[datetime]::UtcNow.ToString('o');results=@($results)} | ConvertTo-Json -Depth 6 |
        Set-Content -LiteralPath (Join-Path $directory 'verification.json') -Encoding UTF8
}
Harness resume | Out-Null
Harness command '/tp @p 0 5 -6' | Out-Null
Harness look 0 15 | Out-Null
Write-Output 'Storage panel batch ready for manual testing.'
