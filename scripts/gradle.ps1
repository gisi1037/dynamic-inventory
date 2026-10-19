param(
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
# A stable local version until the project has its first Git release tag.
if (-not $env:VERSION) { $env:VERSION = '0.8.0-dev' }
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\gradlew.bat @GradleArguments
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
