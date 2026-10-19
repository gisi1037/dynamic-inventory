param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [ValidateSet('preflight', 'full-pack')]
    [string] $Stage = 'full-pack'
)

$ErrorActionPreference = 'Stop'

$preflightReports = @(
    'artifact-verification.json',
    'examples-verification.json',
    'integrated/verification.json',
    'mui2-integrated/verification.json',
    'dedicated/verification.json',
    'mui2-dedicated/verification.json',
    'isolated/verification.json',
    'capacity-matrix/verification.json',
    'container-regression/verification.json',
    'machine-modes/verification.json',
    'native-presentation/verification.json'
)
$fullPackReports = @(
    $preflightReports + @(
    'full-pack-containers/verification.json',
    'full-pack-frames/verification.json',
    'gt-mui2/verification.json',
    'full-pack-modes/verification.json',
    'full-pack-presentation/verification.json',
    'full-pack-preservation.json'
    )
)
$artifactHashReports = @(
    'artifact-verification.json',
    'examples-verification.json',
    'integrated/verification.json',
    'mui2-integrated/verification.json',
    'dedicated/verification.json',
    'mui2-dedicated/verification.json',
    'isolated/verification.json'
)

function Read-JsonObject([string] $path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Qualification file is missing: $path"
    }
    $value = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($null -eq $value -or $value -isnot [pscustomobject]) {
        throw "Qualification file must contain a JSON object: $path"
    }
    return $value
}

function Assert-MatchesWhenPresent($report, [string[]] $names, [string] $expected, [string] $path) {
    foreach ($name in $names) {
        $property = $report.PSObject.Properties[$name]
        if ($null -ne $property -and [string]$property.Value -ne $expected) {
            throw "Qualification identity mismatch in ${path}: $name must match '$expected'."
        }
    }
}

$manifestFullPath = [IO.Path]::GetFullPath($ManifestPath)
$manifest = Read-JsonObject $manifestFullPath
if ($manifest.success -ne $true -or $manifest.fullPackSmokeTestAllowed -ne $true) {
    throw 'Qualification manifest does not allow the local full-pack test launch.'
}
if ($manifest.prototype -ne '0.8.0-dev' -or $manifest.packTarget -ne '2.9.0-RC-2') {
    throw 'Qualification manifest prototype or pack target is not the pinned RC-2 build.'
}
if ([string]$manifest.jarSha256 -notmatch '^[A-Fa-f0-9]{64}$') {
    throw 'Qualification manifest must contain a valid production jarSha256.'
}

$manifestStageProperty = $manifest.PSObject.Properties['qualificationStage']
$manifestStage = if ($null -eq $manifestStageProperty) { 'full-pack' } else { [string]$manifestStageProperty.Value }
if ($manifestStage -notin @('preflight', 'full-pack')) {
    throw "Unsupported qualification stage in manifest: $manifestStage"
}
if ($Stage -eq 'preflight' -and ($null -eq $manifestStageProperty -or $manifestStage -ne 'preflight')) {
    throw 'Preflight validation requires an explicit qualificationStage of preflight.'
}
if ($Stage -eq 'full-pack' -and $manifestStage -ne 'full-pack') {
    throw 'Full-pack validation requires full-pack evidence; preflight evidence only permits the local test launch.'
}
$expectedReports = if ($Stage -eq 'preflight') { $preflightReports } else { $fullPackReports }

$reports = @($manifest.requiredReports)
if ($reports.Count -eq 0) { throw 'Qualification manifest requiredReports cannot be empty.' }
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($relative in $reports) {
    if ($relative -isnot [string] -or $relative -notmatch '^[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*$') {
        throw "Invalid qualification report path: $relative"
    }
    if (-not $seen.Add($relative)) { throw "Duplicate qualification report: $relative" }
}
if ($reports.Count -ne $expectedReports.Count) {
    throw "Qualification manifest must name the complete $Stage report set."
}
foreach ($expected in $expectedReports) {
    if (-not $seen.Contains($expected)) { throw "Qualification manifest is missing required $Stage report: $expected" }
}

$reportRoot = Split-Path -Parent $manifestFullPath
$rootFullPath = [IO.Path]::GetFullPath($reportRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$rootPrefix = $rootFullPath + [IO.Path]::DirectorySeparatorChar
foreach ($relative in $expectedReports) {
    $reportPath = [IO.Path]::GetFullPath((Join-Path $rootFullPath ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))))
    if (-not $reportPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Qualification report path escapes its manifest directory: $relative"
    }
    $report = Read-JsonObject $reportPath
    if ($report.success -ne $true) { throw "Required qualification did not pass: $relative" }

    # Source/build fixtures use artifactSha256; runtime fixtures use jarSha256.
    # devArtifactSha256 identifies a separate development artifact and is intentionally excluded.
    $primaryHashName = if ($artifactHashReports -contains $relative) { 'artifactSha256' } else { 'jarSha256' }
    $primaryHash = $report.PSObject.Properties[$primaryHashName]
    if ($null -eq $primaryHash) {
        throw "Required qualification is missing its $primaryHashName identity: $relative"
    }
    if ([string]$primaryHash.Value -notmatch '^[A-Fa-f0-9]{64}$' -or
        [string]$primaryHash.Value -ne [string]$manifest.jarSha256) {
        throw "Required qualification uses a different production artifact: $relative ($primaryHashName)."
    }
    $hashProperties = @('jarSha256', 'artifactSha256', 'productionJarSha256') |
        ForEach-Object { $report.PSObject.Properties[$_] } | Where-Object { $null -ne $_ }
    foreach ($property in $hashProperties) {
        if ([string]$property.Value -notmatch '^[A-Fa-f0-9]{64}$' -or
            [string]$property.Value -ne [string]$manifest.jarSha256) {
            throw "Required qualification uses a different production artifact: $relative ($($property.Name))."
        }
    }

    Assert-MatchesWhenPresent $report @('prototype') ([string]$manifest.prototype) $relative
    Assert-MatchesWhenPresent $report @('packTarget') ([string]$manifest.packTarget) $relative
    foreach ($identity in @(@('profile', 'profile'), @('inventoryProfile', 'inventoryProfile'), @('suite', 'suite'))) {
        $manifestProperty = $manifest.PSObject.Properties[[string]$identity[1]]
        $reportProperty = $report.PSObject.Properties[[string]$identity[0]]
        if ($null -ne $reportProperty) {
            if ([string]::IsNullOrWhiteSpace([string]$reportProperty.Value)) {
                throw "Qualification identity is empty in ${relative}: $($identity[0])."
            }
            if ($null -ne $manifestProperty -and [string]$reportProperty.Value -ne [string]$manifestProperty.Value) {
                throw "Qualification identity mismatch in ${relative}: $($identity[0])."
            }
        }
    }
    if ($null -ne $report.PSObject.Properties['scope'] -and
        [string]::IsNullOrWhiteSpace([string]$report.scope)) {
        throw "Qualification scope is empty: $relative"
    }
}

return $manifest
