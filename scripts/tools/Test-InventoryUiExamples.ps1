param(
    [string] $InventoryProfile = 'rc2-integration',
    [string] $ClasspathMetadata = '.setup/inventory-example-classpath.json',
    [string] $EvidencePath = 'docs/qualification/ui-api/examples-verification.json'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$javaRuntimePath = Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1'
. $javaRuntimePath
$javaRuntime = Resolve-JavaRuntime -MinimumMajor 25
$classpathPath = if ([IO.Path]::IsPathRooted($ClasspathMetadata)) { $ClasspathMetadata }
    else { Join-Path $projectRoot $ClasspathMetadata }

# This task resolves the public API compile classpath and compiles classes only. It
# does not require a developer launch manifest or start Minecraft.
$gradle = Join-Path (Split-Path $PSScriptRoot -Parent) 'gradle.ps1'
& $gradle writeInventoryExampleClasspath "-PinventoryProfile=$InventoryProfile" --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Could not export the public example compile classpath.' }

$classpath = Get-Content -LiteralPath $classpathPath -Raw | ConvertFrom-Json
if ($classpath.profile -ne $InventoryProfile -or
    [string]::IsNullOrWhiteSpace([string]$classpath.classpath) -or
    $classpath.classpath -notlike "*$projectRoot*") {
    throw "The exported compile classpath is not for profile '$InventoryProfile' or does not include this checkout."
}
$examples = @(Get-ChildItem examples/java/example -Filter '*.java' -File)
foreach ($example in $examples) {
    if ((Get-Content $example.FullName -Raw) -match 'import\s+dev\.gilbe\.dynamicinventory\.(?!api\.)') {
        throw "Developer example uses an internal implementation import: $($example.Name)"
    }
}
$output = Join-Path $projectRoot '.setup/developer-example-classes'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& $javaRuntime.Javac --release 8 -Xlint:-options -proc:none -cp $classpath.classpath -d $output @($examples.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Public developer examples did not compile' }
$jarPath = Join-Path $projectRoot 'build/libs/dynamicinventory-0.8.0-dev.jar'
if (Test-Path -LiteralPath $jarPath -PathType Leaf) {
    $report = @{success=$true; apiVersion=1; javaRelease=8; annotationProcessors=$false;
        examples=@($examples.Name); internalImports=$false;
        artifactSha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash;
        capturedAtUtc=[datetime]::UtcNow.ToString('o')}
    $evidenceFullPath = if ([IO.Path]::IsPathRooted($EvidencePath)) { $EvidencePath }
        else { Join-Path $projectRoot $EvidencePath }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $evidenceFullPath) | Out-Null
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $evidenceFullPath
} else {
    Write-Host 'Examples compiled; no artifact report written because the production jar is not present yet.'
}
Write-Host "PASS: $($examples.Count) public-only developer examples compile for Java 8"
