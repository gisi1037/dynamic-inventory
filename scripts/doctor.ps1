$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$runtime = Resolve-JavaRuntime -MinimumMajor 25

Write-Host "JDK $($runtime.Major) found via $($runtime.Source)."
Write-Host "JAVA_HOME: $($runtime.Home)"
Write-Host "java:      $($runtime.Java)"
Write-Host "javac:     $($runtime.Javac)"
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat') -PathType Leaf)) {
    throw 'Gradle wrapper is missing from the project checkout.'
}
Write-Host 'Gradle wrapper is present.'
