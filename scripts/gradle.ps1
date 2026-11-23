param(
    [switch] $UseDevelopmentVersion,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# Fixture clients load the development JAR as well as class directories. Rewriting an open
# JAR invalidates LaunchWrapper's cached ZIP offsets even when compilation succeeds.
$changesBuildInputs = @($GradleArguments | Where-Object {
    $_ -match '(^|:)(build|assemble|jar|reobfJar|clean|writeFixtureClientLaunch)$' -or
    $_ -match '^run(Client|Server)\d*$'
}).Count -gt 0
if ($changesBuildInputs) {
    # This guards only JVMs explicitly registered in .setup/peer-*.json. It is not a
    # system-wide process scan; unregistered or foreign launchers need a separate check.
    foreach ($metadata in Get-ChildItem -LiteralPath (Join-Path $projectRoot '.setup') -Filter 'peer-*.json' -ErrorAction SilentlyContinue) {
        $fixture = Get-Content -LiteralPath $metadata.FullName -Raw | ConvertFrom-Json
        $running = Get-Process -Id $fixture.pid -ErrorAction SilentlyContinue
        if ($running -and $running.ProcessName -match '^javaw?$') {
            throw "Close the $($fixture.player) fixture JVM (PID $($fixture.pid)) and wait for it to exit before rebuilding its development JAR."
        }
    }
}
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$javaRuntime = Resolve-JavaRuntime -MinimumMajor 25
$env:JAVA_HOME = $javaRuntime.Home
$env:Path = "$(Split-Path -Parent $javaRuntime.Java);$env:Path"
# Local and hosted installs must agree even when a calling shell supplies an older version.
$deploymentBuild = $GradleArguments -contains 'writeHostedInventoryArtifact'
if ($deploymentBuild -or $UseDevelopmentVersion) {
    $versionPath = Join-Path $projectRoot 'gradle/development-version.txt'
    $env:VERSION = (Get-Content -LiteralPath $versionPath -Raw).Trim()
    if ($env:VERSION -cnotmatch '^\d+\.\d+\.\d+-dev\.[1-9]\d*$') {
        throw "Invalid development build version in $versionPath."
    }
    if ($deploymentBuild) { Write-Host "Dynamic Inventory development version: $env:VERSION" }
} elseif (-not $env:VERSION) {
    $env:VERSION = '0.8.0-dev'
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\gradlew.bat @GradleArguments
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($GradleArguments -contains 'build' -and $deploymentBuild) {
        $artifactManifest = if ($GradleArguments -contains '-PinventoryProfile=gtnh-2.9.1' -or
            $GradleArguments -contains 'inventoryProfile=gtnh-2.9.1') {
            'build/hosted-inventory-artifact-2.9.1.txt'
        } else {
            'build/hosted-inventory-artifact.txt'
        }
        $productionJar = (Get-Content -LiteralPath $artifactManifest -Raw).Trim()
        $hash = (Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash
        Write-Host "Production JAR: $productionJar"
        Write-Host "SHA-256: $hash"
    }
} finally {
    Pop-Location
}
