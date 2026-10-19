$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$scanner = Join-Path $projectRoot 'scripts/tools/Test-InventoryArtifact.ps1'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('inventory-artifact-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$checks = 0
$resources = @(
    'mcmod.info', 'mixins.dynamicinventory.early.json', 'mixins.dynamicinventory.json',
    'mixins.dynamicinventory.late.json', 'mixins.dynamicinventory.refmap.json'
)

function New-TestJar([string] $Path, [hashtable] $Classes, [string[]] $Resources = $script:resources,
    [switch] $MissingTinkersForegroundMapping) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($resource in $Resources) {
            $entry = $archive.CreateEntry($resource)
            $stream = $entry.Open()
            try {
                $body = '{}'
                if ($resource -eq 'mixins.dynamicinventory.refmap.json') {
                    $mappings = [ordered]@{}
                    $selectorMap = [ordered]@{}
                    if (-not $MissingTinkersForegroundMapping) {
                        $selectorMap['drawGuiContainerForegroundLayer'] = 'Ltconstruct/tools/gui/CraftingStationGui;func_146979_b(II)V'
                    }
                    $mappings['dev/gilbe/dynamicinventory/mixins/CraftingStationGuiMixin'] = $selectorMap
                    $body = @{mappings=$mappings} | ConvertTo-Json -Depth 6 -Compress
                }
                $data = [Text.Encoding]::UTF8.GetBytes($body)
                $stream.Write($data, 0, $data.Length)
            }
            finally { $stream.Dispose() }
        }
        foreach ($name in $Classes.Keys) {
            $entry = $archive.CreateEntry($name)
            $stream = $entry.Open()
            try {
                $major = [int]$Classes[$name]
                $data = [byte[]]@(0xCA, 0xFE, 0xBA, 0xBE, 0, 0, [byte](($major -shr 8) -band 255), [byte]($major -band 255))
                $stream.Write($data, 0, $data.Length)
            } finally { $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
}

function Invoke-Scanner([string] $Name, [hashtable] $Classes, [switch] $MissingTinkersForegroundMapping) {
    $prod = Join-Path $temp "$Name-prod.jar"
    $dev = Join-Path $temp "$Name-dev.jar"
    $evidence = Join-Path $temp "$Name-evidence.json"
    New-TestJar $prod $Classes -MissingTinkersForegroundMapping:$MissingTinkersForegroundMapping
    New-TestJar $dev @{ 'dev/gilbe/dynamicinventory/Development.class' = 52 }
    $failed = $false
    try { & $scanner -ProductionJar $prod -DevelopmentJar $dev -EvidencePath $evidence | Out-Null }
    catch { $failed = $true }
    return [pscustomobject]@{ failed = $failed; report = Get-Content -LiteralPath $evidence -Raw | ConvertFrom-Json; evidence = $evidence }
}

function Assert-Check([bool] $Condition, [string] $Name) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:checks++
    Write-Host "PASS: $Name"
}

try {
    $valid = Invoke-Scanner 'valid' @{
        'dev/gilbe/dynamicinventory/api/InventoryUi.class' = 52
        'dev/gilbe/dynamicinventory/mixins/PickBlockSlotMixin.class' = 52
    }
    Assert-Check (-not $valid.failed -and $valid.report.success -and -not $valid.report.fullPackQualified -and
        $valid.report.classCount -eq 2 -and $valid.report.publicApiClassCount -eq 1 -and $valid.report.pickBlockSlotMixinIncluded) 'valid Java 8 production artifact passes with counts and explicit non-qualification'

    $badMajor = Invoke-Scanner 'bad-major' @{
        'dev/gilbe/dynamicinventory/api/InventoryUi.class' = 61
        'dev/gilbe/dynamicinventory/mixins/PickBlockSlotMixin.class' = 52
    }
    Assert-Check ($badMajor.failed -and -not $badMajor.report.success -and $badMajor.report.status -eq 'failed' -and
        $badMajor.report.nonJava8Classes.Count -eq 1 -and $badMajor.report.nonJava8Classes[0].majorVersion -eq 61) 'non-Java-8 class is rejected and reported'

    $fixture = Invoke-Scanner 'fixture' @{
        'dev/gilbe/dynamicinventory/api/InventoryUi.class' = 52
        'dev/gilbe/dynamicinventory/mixins/PickBlockSlotMixin.class' = 52
        'dev/gilbe/dynamicinventory/qualification/Fixture.class' = 52
    }
    Assert-Check ($fixture.failed -and -not $fixture.report.success -and $fixture.report.fixtureClasses -contains 'dev/gilbe/dynamicinventory/qualification/Fixture.class') 'fixture class is rejected and reported'

    $missingPick = Invoke-Scanner 'missing-pick' @{
        'dev/gilbe/dynamicinventory/api/InventoryUi.class' = 52
    }
    Assert-Check ($missingPick.failed -and -not $missingPick.report.success -and $missingPick.report.failure -match 'PickBlockSlotMixin') 'missing required pick-block mixin is rejected'

    $missingTinkersMap = Invoke-Scanner 'missing-tinkers-map' @{
        'dev/gilbe/dynamicinventory/api/InventoryUi.class' = 52
        'dev/gilbe/dynamicinventory/mixins/PickBlockSlotMixin.class' = 52
    } -MissingTinkersForegroundMapping
    Assert-Check ($missingTinkersMap.failed -and -not $missingTinkersMap.report.success -and
        $missingTinkersMap.report.failure -match 'CraftingStationGui foreground selector') 'unmapped Tinkers foreground selector is rejected'

    Write-Host "PASS: $checks inventory artifact scanner regression checks"
} finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
