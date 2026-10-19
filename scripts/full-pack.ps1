param(
    [switch] $PrepareOnly,
    [switch] $ExperimentalInventoryIntegration,
    [switch] $VisualPreview,
    [string] $QualificationManifest = 'docs/qualification/rc2-opening-gate-qualified/build-verification.json',
    [ValidateRange(0,65535)] [int] $DebugPort = 5005,
    [string] $PackConfig = 'pack-baseline.json'
)
$ErrorActionPreference = 'Stop'
if (-not $PrepareOnly -and -not $ExperimentalInventoryIntegration) {
    throw 'Full-pack inventory testing requires -ExperimentalInventoryIntegration and a matching successful local qualification report. Use a new disposable test world.'
}
if ($VisualPreview -and $PSBoundParameters.ContainsKey('QualificationManifest')) {
    throw 'Choose an unqualified visual preview or a qualified test manifest, not both.'
}
$projectRoot = Split-Path $PSScriptRoot -Parent
$configPath = if ([IO.Path]::IsPathRooted($PackConfig)) { $PackConfig } else { Join-Path $projectRoot $PackConfig }
$pack = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
if ($pack.instanceId -notmatch '^[A-Za-z0-9_-]+$') { throw 'Pack instanceId must contain only letters, numbers, underscores and hyphens.' }
if ($pack.version -notmatch '^[A-Za-z0-9_+.-]+$') { throw 'Pack version contains unsupported filename characters.' }
$instancesRoot = Join-Path $env:APPDATA 'PrismLauncher\instances'
$target = Join-Path $instancesRoot $pack.instanceId
$marker = Join-Path $target '.dynamicinventory-dev-instance'
$launcher = Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\prismlauncher.exe'

if (-not (Test-Path -LiteralPath $launcher)) { throw "Prism Launcher was not found: $launcher" }
if (-not (Test-Path -LiteralPath $marker)) {
    if (Test-Path -LiteralPath $target) { throw "Existing instance has no project marker: $target" }
    $cache = Join-Path $projectRoot '.setup'
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $archive = Join-Path $cache "GTNH-$($pack.version).zip"
    if (-not (Test-Path -LiteralPath $archive)) {
        & curl.exe --fail --location --retry 2 --output $archive $pack.archiveUrl
        if ($LASTEXITCODE -ne 0) { throw 'Pack download failed.' }
    }
    $actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    if ($actualHash -ne $pack.archiveSha256) { throw "Pack archive hash mismatch: $archive" }
    New-Item -ItemType Directory -Path $target | Out-Null
    # Windows tar handles the pack's long config paths, unlike Windows PowerShell Expand-Archive.
    & tar.exe -xf $archive --strip-components 1 -C $target
    if ($LASTEXITCODE -ne 0) { throw "Pack extraction failed: $archive" }
    if (-not (Test-Path -LiteralPath (Join-Path $target 'mmc-pack.json'))) { throw 'Archive does not contain a Prism instance.' }
    $cfg = Join-Path $target 'instance.cfg'
    $text = Get-Content -LiteralPath $cfg -Raw
    $text = [regex]::Replace($text, '(?m)^name=.*', "name=$($pack.instanceName)")
    $text = [regex]::Replace($text, '(?m)^OverrideJavaLocation=.*', 'OverrideJavaLocation=true')
    $text = [regex]::Replace($text, '(?m)^OverrideMemory=.*', 'OverrideMemory=true')
    $text += "`nJavaPath=C:/Program Files/Java/jdk-25/bin/javaw.exe`nMinMemAlloc=1024`nMaxMemAlloc=8192`n"
    Set-Content -LiteralPath $cfg -Value $text -Encoding UTF8
    $pack | ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding UTF8
}

$installed = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
if ($installed.archiveSha256 -ne $pack.archiveSha256) { throw 'Instance baseline differs from config. Use a new instanceId for a different pack build.' }
$memoryMiB = if ($pack.memoryMiB) { [int] $pack.memoryMiB } else { 8192 }
if ($memoryMiB -lt 4096 -or $memoryMiB -gt 32768) { throw 'Pack memoryMiB is outside the supported range.' }
$cfg = Join-Path $target 'instance.cfg'
$text = Get-Content -LiteralPath $cfg -Raw
$text = [regex]::Replace($text, '(?m)^MaxMemAlloc=.*', "MaxMemAlloc=$memoryMiB")
if ($PrepareOnly) {
    Set-Content -LiteralPath $cfg -Value $text -Encoding UTF8
    Write-Host "Prepared isolated GTNH $($pack.version) test instance: $target"
    exit 0
}

# Fail before building or changing the deployed JAR/config if the qualified
# game runtime is unavailable. Source fixtures use GTNHGradle's own toolchain.
$runtimeJava = 'C:\Program Files\Java\jdk-25\bin\javaw.exe'
if (!(Test-Path -LiteralPath $runtimeJava)) { throw 'The qualified Oracle Java 25 game runtime was not found.' }

if ($pack.version -ne '2.9.0-RC-2') { throw 'The experimental adapter is pinned to GTNH 2.9.0-RC-2.' }
if (!$VisualPreview) {
    $qualificationPath = if ([IO.Path]::IsPathRooted($QualificationManifest)) { $QualificationManifest }
        else { Join-Path $projectRoot $QualificationManifest }
    $qualificationHeader = Get-Content -LiteralPath $qualificationPath -Raw | ConvertFrom-Json
    $qualificationStage = if ([string]$qualificationHeader.qualificationStage -eq 'preflight') { 'preflight' } else { 'full-pack' }
    $qualified = & (Join-Path $PSScriptRoot 'validate-qualification.ps1') -ManifestPath $qualificationPath -Stage $qualificationStage
}
$reference = Get-Content (Join-Path $projectRoot 'pack-compatibility-rc2.json') -Raw | ConvertFrom-Json
foreach ($entry in $reference.installedFiles) {
    $installedMod = Join-Path $target ('.minecraft/mods/' + $entry.file)
    if (!(Test-Path -LiteralPath $installedMod) -or
        (Get-FileHash -LiteralPath $installedMod -Algorithm SHA256).Hash -ne $entry.sha256) {
        throw "Installed RC-2 component differs from the pinned baseline: $($entry.file)"
    }
}
$mods = Join-Path $target '.minecraft/mods'
foreach ($old in Get-ChildItem -LiteralPath $mods -Filter 'dynamicinventory-*.jar' -File) {
    try { $handle = [IO.File]::Open($old.FullName, 'Open', 'Read', 'None'); $handle.Dispose() }
    catch { throw 'Close the running test game before replacing its inventory mod JAR.' }
}
& (Join-Path $PSScriptRoot 'gradle.ps1') build writeFixtureClientLaunch -PinventoryProfile=rc2-integration -PinventoryStorageTests --no-daemon --no-configuration-cache
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$jar = Join-Path $projectRoot 'build\libs\dynamicinventory-0.8.0-dev.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "Built mod jar was not found: $jar" }
$builtHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
if (!$VisualPreview -and $builtHash -ne $qualified.jarSha256) {
    throw 'The built mod differs from the qualified artifact. Re-run qualification before deploying.'
}
$backup = Join-Path $target ('.dynamicinventory-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $cfg -Destination (Join-Path $backup 'instance.cfg')
# Preserve the original launcher settings before applying this development launch's memory setting.
Set-Content -LiteralPath $cfg -Value $text -Encoding UTF8
$mods = Join-Path $target '.minecraft/mods'
$previous = @(Get-ChildItem -LiteralPath $mods -Filter 'dynamicinventory-*.jar' -File)
foreach ($old in $previous) { Copy-Item -LiteralPath $old.FullName -Destination (Join-Path $backup $old.Name) }
$modPath = Join-Path $mods 'dynamicinventory-0.8.0-dev.jar'
Copy-Item -LiteralPath $jar -Destination $modPath -Force
if ((Get-FileHash -LiteralPath $modPath -Algorithm SHA256).Hash -ne $builtHash) { throw 'Deployed jar checksum mismatch.' }
# A preview records what was installed without claiming that runtime qualification passed.
@{jarSha256=$builtHash; visualPreview=[bool]$VisualPreview; qualificationManifest=if($VisualPreview){$null}else{$qualificationPath};
    installedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $target '.dynamicinventory-last-deployment.json') -Encoding UTF8
foreach ($old in $previous) {
    if ($old.FullName -ne $modPath) { Remove-Item -LiteralPath $old.FullName }
}
# Preserve existing instance/global user JVM arguments while adding the explicit RC-2 mode.
$text = Get-Content -LiteralPath $cfg -Raw
$text = [regex]::Replace($text, '(?m)^OverrideJavaLocation=.*', 'OverrideJavaLocation=true')
$javaSetting = 'JavaPath=' + $runtimeJava.Replace('\', '/')
$text = [regex]::Replace($text, '(?m)^JavaPath=.*', [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $javaSetting })
$argumentMatch = [regex]::Match($text, '(?m)^JvmArgs=(.*)$')
$jvm = $argumentMatch.Groups[1].Value.Trim()
if ($text -match '(?m)^OverrideJavaArgs=false\s*$') {
    $globalConfig = Join-Path $env:APPDATA 'PrismLauncher/prismlauncher.cfg'
    if (Test-Path -LiteralPath $globalConfig) {
        $globalMatch = [regex]::Match([IO.File]::ReadAllText($globalConfig), '(?m)^JvmArgs=(.*)$')
        $jvm = $globalMatch.Groups[1].Value.Trim()
    }
}
# QSettings quotes string values containing commas. Decode our quoted form before
# editing it, and always write a single quoted QString rather than a QStringList.
if ($jvm.StartsWith('"') -and $jvm.EndsWith('"')) {
    $jvm = $jvm.Substring(1, $jvm.Length - 2).Replace('\"', '"').Replace('\\', '\')
}
$jvm = [regex]::Replace($jvm, '(?:^|\s)-Ddynamicinventory\.profile=\S+', '').Trim()
$jvm = [regex]::Replace($jvm, '(?:^|\s)-agentlib:jdwp=\S+', '').Trim()
$jvm += ' -Ddynamicinventory.profile=rc2-integration'
if ($DebugPort -gt 0) { $jvm += " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:$DebugPort" }
$text = [regex]::Replace($text, '(?m)^OverrideJavaArgs=.*', 'OverrideJavaArgs=true')
$encodedJvm = '"' + $jvm.Trim().Replace('\', '\\').Replace('"', '\"') + '"'
if ($text -match '(?m)^JvmArgs=') {
    $text = [regex]::Replace($text, '(?m)^JvmArgs=.*', [System.Text.RegularExpressions.MatchEvaluator]{ param($match) 'JvmArgs=' + $encodedJvm })
} else { $text += "`nJvmArgs=$encodedJvm`n" }
Set-Content -LiteralPath $cfg -Value $text -Encoding UTF8
if ($VisualPreview) { Write-Host "Installed UNQUALIFIED visual preview in the isolated GTNH $($pack.version) development instance; backup: $backup." }
else { Write-Host "Installed qualified experimental inventory in GTNH $($pack.version); prior jars/config backed up at $backup." }
Write-Host 'Create a NEW disposable world. Wide layouts show native inline Baubles; narrow layouts keep the expanded inventory with an equipment side panel.'
& $launcher --launch $pack.instanceId
