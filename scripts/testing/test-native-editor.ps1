param(
    [ValidateSet('rc2-integration', 'daily-2026-10-19')]
    [string] $Profile = 'rc2-integration',
    [string] $LaunchManifest = '',
    [string] $EvidenceDirectory = '',
    [switch] $Build
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')

function Resolve-ProjectPath([string] $Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return [IO.Path]::GetFullPath($Path) }
    return [IO.Path]::GetFullPath((Join-Path $projectRoot $Path))
}

if (!$LaunchManifest) { $LaunchManifest = ".setup/$Profile-offline-client-launch.json" }
if (!$EvidenceDirectory) { $EvidenceDirectory = ".setup/native-editor-checks/$Profile" }
$manifestPath = Resolve-ProjectPath $LaunchManifest
$evidencePath = Resolve-ProjectPath $EvidenceDirectory
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null
$reportPath = Join-Path $evidencePath 'verification.json'
$logPath = Join-Path $evidencePath 'fixture-output.txt'
$report = [ordered]@{
    success = $false
    status = 'running'
    scope = 'headless-source-fixture'
    fullPackQualified = $false
    profile = $Profile
    startedAtUtc = [datetime]::UtcNow.ToString('o')
    launchManifest = $manifestPath
    fixture = 'dev.gilbe.dynamicinventory.qualification.NativeEditorFixtures'
    buildRequested = [bool]$Build
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Set-Content -LiteralPath $logPath -Value '' -Encoding UTF8

Push-Location -LiteralPath $projectRoot
try {
    $runtime = Resolve-JavaRuntime -MinimumMajor 25
    $report.java = $runtime.Java
    $report.javaMajor = $runtime.Major
    if ($Build) {
        # Exporting resolves and compiles the fixtures; it does not run Minecraft.
        # The shared helper also prevents rewriting a registered fixture JVM's JAR.
        & (Join-Path (Split-Path $PSScriptRoot -Parent) 'gradle.ps1') writeFixtureClientLaunch `
            "-PinventoryProfile=$Profile" -PinventoryStorageTests --no-daemon --no-configuration-cache
        if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the native editor fixture classpath.' }
    }
    if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Missing fixture launch manifest: $manifestPath. Run with -Build to prepare it."
    }
    $launch = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($launch.offlineOnly -ne $true -or [string]$launch.profile -ne $Profile -or
        [string]::IsNullOrWhiteSpace([string]$launch.classpath)) {
        throw 'Expected an offline fixture manifest with a classpath and the selected profile.'
    }
    $fixtureClasses = Join-Path $projectRoot 'build/classes/java/packTest'
    $classpathEntries = @(([string]$launch.classpath).Split([IO.Path]::PathSeparator) |
        Where-Object { $_ } | ForEach-Object { Resolve-ProjectPath $_ })
    if ($fixtureClasses -notin $classpathEntries -or
        !(Test-Path -LiteralPath (Join-Path $fixtureClasses 'dev/gilbe/dynamicinventory/qualification/NativeEditorFixtures.class') -PathType Leaf) -or
        !(Test-Path -LiteralPath (Join-Path $fixtureClasses 'dev/gilbe/dynamicinventory/qualification/AttachedChestLayoutFixtures.class') -PathType Leaf)) {
        throw 'The manifest must include this workspace packTest classes. Re-run with -Build.'
    }
    $report.launchManifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
    # This is a SOURCE fixture. Exported game manifests contain the packaged dev
    # JAR, which may still be in use by Minecraft. Prefer freshly compiled classes
    # and resources without rebuilding or replacing that running game's JAR.
    $sourceClasses = Join-Path $projectRoot 'build/classes/java/main'
    $sourceResources = Join-Path $projectRoot 'build/resources/main'
    $layoutClass = Join-Path $sourceClasses 'dev/gilbe/dynamicinventory/client/editor/NativeLayoutPatches.class'
    if (!(Test-Path -LiteralPath $layoutClass -PathType Leaf)) {
        throw 'Missing compiled production classes. Compile the source fixtures first.'
    }
    $sourceClasspath = @($sourceClasses, $sourceResources, $launch.classpath) -join [IO.Path]::PathSeparator
    $report.sourceClassDirectory = $sourceClasses
    $report.layoutClassSha256 = (Get-FileHash -LiteralPath $layoutClass -Algorithm SHA256).Hash
    # LaunchWrapper, game arguments and native/UI startup flags are unnecessary.
    & $runtime.Java '-Djava.awt.headless=true' "-Ddynamicinventory.profile=$Profile" `
        -cp $sourceClasspath $report.fixture 2>&1 | Tee-Object -FilePath $logPath
    $fixtureExitCode = $LASTEXITCODE
    $report.exitCode = $fixtureExitCode
    if ($fixtureExitCode -ne 0) { throw "Native editor fixture failed with exit code $fixtureExitCode. See $logPath" }
    if ((Get-Content -LiteralPath $logPath -Raw) -notmatch '(?m)^PASS native editor codec/target guards,') {
        throw "Native editor fixture did not print its completion marker. See $logPath"
    }
    $report.success = $true
    $report.status = 'passed'
} catch {
    $report.status = 'failed'
    $report.error = $_.Exception.Message
    throw
} finally {
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Pop-Location
}
Write-Host "PASS headless native editor source checks ($Profile). Report: $reportPath"
