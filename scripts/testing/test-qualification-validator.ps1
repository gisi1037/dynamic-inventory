$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$validator = Join-Path $projectRoot 'scripts/validate-qualification.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('dynamicinventory-qualification-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null

# Synthetic data exercises validator rules without shipping anyone's runtime captures.
$currentRoot = Join-Path $testRoot 'seed'
New-Item -ItemType Directory -Path $currentRoot | Out-Null
$currentManifest = Join-Path $currentRoot 'build-verification.json'
$requiredReports = @(
    'artifact-verification.json', 'examples-verification.json',
    'integrated/verification.json', 'mui2-integrated/verification.json',
    'dedicated/verification.json', 'mui2-dedicated/verification.json',
    'isolated/verification.json', 'capacity-matrix/verification.json',
    'container-regression/verification.json', 'machine-modes/verification.json',
    'native-presentation/verification.json', 'full-pack-containers/verification.json',
    'full-pack-frames/verification.json', 'gt-mui2/verification.json',
    'full-pack-modes/verification.json', 'full-pack-presentation/verification.json',
    'full-pack-preservation.json'
)
$fixtureHash = 'A' * 64
$manifestSeed = [pscustomobject]@{
    success=$true; fullPackSmokeTestAllowed=$true; prototype='0.8.0-dev';
    packTarget='2.9.0-RC-2'; jarSha256=$fixtureHash; requiredReports=$requiredReports
}
$manifestSeed | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $currentManifest -Encoding UTF8
foreach ($relative in $requiredReports) {
    $reportPath = Join-Path $currentRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath) | Out-Null
    @{success=$true; artifactSha256=$fixtureHash; jarSha256=$fixtureHash} |
        ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding UTF8
}

function Assert-ValidationFails([string] $manifestPath, [string] $case, [string] $stage = 'full-pack') {
    try {
        & $validator -ManifestPath $manifestPath -Stage $stage | Out-Null
    } catch {
        return
    }
    throw "Expected qualification validation to fail for $case."
}

function New-Fixture([string] $name, [string[]] $reportSet, [string] $stage) {
    if ($null -eq $reportSet) { $reportSet = @($manifestSeed.requiredReports) }
    $root = Join-Path $testRoot $name
    New-Item -ItemType Directory -Path $root | Out-Null
    foreach ($relative in $reportSet) {
        $destination = Join-Path $root ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
        $parent = Split-Path -Parent $destination
        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        Copy-Item -LiteralPath (Join-Path $currentRoot ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))) -Destination $destination
    }
    $manifestPath = Join-Path $root 'build-verification.json'
    $manifest = Get-Content -LiteralPath $currentManifest -Raw | ConvertFrom-Json
    $manifest.requiredReports = @($reportSet)
    if ($stage) {
        $manifest | Add-Member -NotePropertyName qualificationStage -NotePropertyValue $stage -Force
    } elseif ($manifest.PSObject.Properties['qualificationStage']) {
        $manifest.PSObject.Properties.Remove('qualificationStage')
    }
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    return $manifestPath
}

try {
    & $validator -ManifestPath $currentManifest | Out-Null
    Write-Host 'PASS valid synthetic qualification manifest'

    $preflightReports = @($manifestSeed.requiredReports | Select-Object -First 11)
    $manifestPath = New-Fixture 'valid-preflight' $preflightReports 'preflight'
    $preflightManifest = & $validator -ManifestPath $manifestPath -Stage preflight
    if ($preflightManifest.qualificationStage -ne 'preflight' -or $preflightManifest.requiredReports.Count -ne 11) {
        throw 'Preflight stage did not return its exact 11-report manifest.'
    }
    Write-Host 'PASS explicit 11-report preflight accepted without full-pack reports'

    $manifestPath = New-Fixture 'preflight-missing-stage' $preflightReports $null
    Assert-ValidationFails $manifestPath 'preflight without explicit stage' 'preflight'
    Write-Host 'PASS preflight requires an explicit manifest stage'

    $manifestPath = New-Fixture 'full-pack-incomplete' $preflightReports 'full-pack'
    Assert-ValidationFails $manifestPath 'full-pack stage with only preflight reports' 'full-pack'
    Write-Host 'PASS full-pack stage rejects the 11-report preflight set'

    $manifestPath = New-Fixture 'full-pack-requested-for-preflight' $preflightReports 'preflight'
    Assert-ValidationFails $manifestPath 'full-pack validation of preflight manifest' 'full-pack'
    Write-Host 'PASS full-pack validator does not accept preflight evidence'

    $manifestPath = New-Fixture 'stale-hash' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.jarSha256 = '0' * 64
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'stale hash'
    Write-Host 'PASS stale production hash rejected'

    $manifestPath = New-Fixture 'missing-report' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.requiredReports = @($manifest.requiredReports | Select-Object -Skip 1)
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'missing report'
    Write-Host 'PASS missing report rejected'

    $manifestPath = New-Fixture 'empty-list' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.requiredReports = @()
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'empty required report list'
    Write-Host 'PASS empty report list rejected'

    $manifestPath = New-Fixture 'failed-report' $null $null
    $reportPath = Join-Path (Split-Path -Parent $manifestPath) 'artifact-verification.json'
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    $report.success = $false
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'failed report'
    Write-Host 'PASS failed report rejected'

    $manifestPath = New-Fixture 'duplicate-report' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.requiredReports = @($manifest.requiredReports) + @($manifest.requiredReports[0])
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'duplicate report'
    Write-Host 'PASS duplicate report rejected'

    $manifestPath = New-Fixture 'path-escape' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.requiredReports[0] = '../outside.json'
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'path escape'
    Write-Host 'PASS report path escape rejected'

    $manifestPath = New-Fixture 'identity-mismatch' $null $null
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest | Add-Member -NotePropertyName profile -NotePropertyValue 'rc2-integration'
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $reportPath = Join-Path (Split-Path -Parent $manifestPath) 'artifact-verification.json'
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    $report | Add-Member -NotePropertyName profile -NotePropertyValue 'wrong-profile'
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'profile mismatch'
    Write-Host 'PASS profile mismatch rejected'
} finally {
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
