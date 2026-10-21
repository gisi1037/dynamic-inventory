$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$validator = Join-Path $projectRoot 'scripts/validate-qualification.ps1'
# Validator tests must work in a clean public checkout without private runtime captures.
# Keep this expected schema independent of the validator so an accidentally omitted
# required report still fails the regression tests. These are not qualification evidence.
$manifestSeed = @{ requiredReports = @(
    'artifact-verification.json', 'examples-verification.json',
    'integrated/verification.json', 'mui2-integrated/verification.json',
    'dedicated/verification.json', 'mui2-dedicated/verification.json',
    'isolated/verification.json', 'capacity-matrix/verification.json',
    'container-regression/verification.json', 'machine-modes/verification.json',
    'native-presentation/verification.json', 'full-pack-containers/verification.json',
    'full-pack-frames/verification.json', 'gt-mui2/verification.json',
    'full-pack-modes/verification.json', 'full-pack-presentation/verification.json',
    'full-pack-preservation.json'
) }
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('dynamicinventory-qualification-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null

function Assert-ValidationFails([string] $manifestPath, [string] $case, [string] $stage = 'full-pack',
    [string] $profile = 'rc2-integration') {
    try {
        & $validator -ManifestPath $manifestPath -Stage $stage -Profile $profile | Out-Null
    } catch {
        return
    }
    throw "Expected qualification validation to fail for $case."
}

function New-DailyFixture([string] $name, [string] $stage = 'full-pack') {
    # Synthetic schema fixtures only. These are never real pack qualification evidence.
    $reportSet = @($manifestSeed.requiredReports)
    if ($stage -eq 'preflight') { $reportSet = @($reportSet | Select-Object -First 11) }
    return New-Fixture $name $reportSet $stage 'daily-2026-10-19'
}

function New-Fixture([string] $name, [string[]] $reportSet, [string] $stage,
    [string] $profile = 'rc2-integration') {
    if ($null -eq $reportSet) { $reportSet = @($manifestSeed.requiredReports) }
    $target = if ($profile -eq 'rc2-integration') { '2.9.0-RC-2' } else { '2.9.0-nightly-2026-10-19' }
    $root = Join-Path $testRoot $name
    New-Item -ItemType Directory -Path $root | Out-Null
    foreach ($relative in $reportSet) {
        $destination = Join-Path $root ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
        $parent = Split-Path -Parent $destination
        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        @{success=$true; artifactSha256=('a' * 64); jarSha256=('a' * 64);
            profile=$profile; packTarget=$target; scope='Synthetic validator regression fixture only'} |
            ConvertTo-Json | Set-Content -LiteralPath $destination -Encoding UTF8
    }
    $manifestPath = Join-Path $root 'build-verification.json'
    $manifest = [pscustomobject]@{success=$true; fullPackSmokeTestAllowed=$true; prototype='0.8.0-dev';
        profile=$profile; packTarget=$target; jarSha256=('a' * 64); requiredReports=@($reportSet);
        scope='Synthetic validator regression fixture only'}
    if ($stage) {
        $manifest | Add-Member -NotePropertyName qualificationStage -NotePropertyValue $stage -Force
    } elseif ($manifest.PSObject.Properties['qualificationStage']) {
        $manifest.PSObject.Properties.Remove('qualificationStage')
    }
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    return $manifestPath
}

try {
    $currentManifest = New-Fixture 'valid-current' $null $null
    & $validator -ManifestPath $currentManifest | Out-Null
    Write-Host 'PASS valid synthetic RC-2 qualification manifest (no local captures required)'

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
    $manifest | Add-Member -NotePropertyName profile -NotePropertyValue 'rc2-integration' -Force
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $reportPath = Join-Path (Split-Path -Parent $manifestPath) 'artifact-verification.json'
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    $report | Add-Member -NotePropertyName profile -NotePropertyValue 'wrong-profile' -Force
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'profile mismatch'
    Write-Host 'PASS profile mismatch rejected'

    foreach ($stage in @('preflight', 'full-pack')) {
        $manifestPath = New-DailyFixture "daily-valid-$stage" $stage
        $daily = & $validator -ManifestPath $manifestPath -Stage $stage -Profile daily-2026-10-19
        $expectedCount = if ($stage -eq 'preflight') { 11 } else { 17 }
        if ($daily.profile -ne 'daily-2026-10-19' -or $daily.requiredReports.Count -ne $expectedCount) {
            throw "Daily $stage did not return its complete synthetic report set."
        }
        Write-Host "PASS explicit daily $stage schema accepts its complete $expectedCount-report set"
    }

    $manifestPath = New-DailyFixture 'daily-with-default-profile'
    Assert-ValidationFails $manifestPath 'daily target under default RC-2 profile'
    Assert-ValidationFails $currentManifest 'RC-2 target under daily profile' 'full-pack' 'daily-2026-10-19'
    Write-Host 'PASS profile selection rejects the other pinned target in both directions'

    $manifestPath = New-DailyFixture 'daily-wrong-profile'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.profile = 'rc2-integration'
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'daily manifest with RC-2 profile' 'full-pack' 'daily-2026-10-19'
    Write-Host 'PASS mismatched daily manifest profile rejected'

    $manifestPath = New-DailyFixture 'daily-missing-profile'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.PSObject.Properties.Remove('profile')
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'daily manifest without explicit profile' 'full-pack' 'daily-2026-10-19'
    Write-Host 'PASS daily manifest requires explicit profile'

    foreach ($identity in @('profile', 'packTarget')) {
        foreach ($missing in @($false, $true)) {
            $manifestPath = New-DailyFixture "daily-report-$identity-missing-$missing"
            $reportPath = Join-Path (Split-Path -Parent $manifestPath) 'artifact-verification.json'
            $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
            if ($missing) { $report.PSObject.Properties.Remove($identity) }
            else { $report.$identity = 'wrong-identity' }
            $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
            Assert-ValidationFails $manifestPath "daily report $identity missing=$missing" 'full-pack' 'daily-2026-10-19'
        }
    }
    Write-Host 'PASS daily reports reject missing or mismatched profile and target'

    $manifestPath = New-DailyFixture 'daily-incomplete' 'preflight'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.qualificationStage = 'full-pack'
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'daily full-pack with only preflight reports' 'full-pack' 'daily-2026-10-19'
    Write-Host 'PASS daily full-pack still requires the full canonical report set'

    $manifestPath = New-DailyFixture 'daily-stale-hash'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $manifest.jarSha256 = 'b' * 64
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-ValidationFails $manifestPath 'daily mismatched production hash' 'full-pack' 'daily-2026-10-19'
    Write-Host 'PASS daily production hash binding remains enforced'
} finally {
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd(
        [IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (!$resolvedTestRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $resolvedTestRoot) -notmatch '^dynamicinventory-qualification-[a-f0-9]{32}$') {
        throw "Refusing to remove an unexpected test directory: $resolvedTestRoot"
    }
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
