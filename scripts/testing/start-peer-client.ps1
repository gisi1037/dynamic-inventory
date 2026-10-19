param(
    [ValidateSet('Developer','Observer')] [string] $PlayerName = 'Developer',
    [ValidateSet('isolated','rc2-mui2','rc2-backhand','rc2-integration')] [string] $Profile = 'rc2-mui2',
    [ValidateRange(0,65535)] [int] $DebugPort = 0
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $PSScriptRoot 'fixture-artifacts.ps1')
$existingMetadata = Join-Path $projectRoot ".setup/peer-$PlayerName.json"
if (Test-Path -LiteralPath $existingMetadata) {
    $existing = Get-Content -LiteralPath $existingMetadata -Raw | ConvertFrom-Json
    $running = Get-Process -Id $existing.pid -ErrorAction SilentlyContinue
    if ($running -and $running.ProcessName -match '^javaw?$') {
        throw "The $PlayerName fixture JVM is still running (PID $($existing.pid)); close it before launching another."
    }
}
$metadataPath = Join-Path $projectRoot ".setup/$Profile-offline-client-launch.json"
$launch = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ($launch.profile -ne $Profile -or !$launch.offlineOnly -or $launch.classpath -notlike "*$projectRoot*") {
    throw 'Expected offline Gradle launch settings for this workspace and profile'
}
$launchId = [guid]::NewGuid().ToString('N')
$launchRoot = Join-Path $projectRoot ".setup/fixture-launches/$launchId"
$snapshot = New-FixtureClasspathSnapshot -Classpath ([string]$launch.classpath) -ProjectRoot $projectRoot -SnapshotRoot $launchRoot
$java = Get-FixtureJavaDetails -Executable $launch.executable
$projectJars = [Collections.Generic.List[object]]::new()
foreach ($entry in $snapshot.Entries | Where-Object { $_.kind -eq 'workspace-file' -and $_.originalPath -match '\\build\\libs\\dynamicinventory-.+\.jar$' }) {
    if ($entry.originalPath -match '-dev-dev\.jar$') {
        $productionPath = $entry.originalPath -replace '-dev-dev\.jar$', '-dev.jar'
        $developmentHash = $entry.runtimeSha256
        $productionHash = Get-FixturePathHash -Path $productionPath
        $projectJars.Add([pscustomobject]@{ developmentOriginalPath=$entry.originalPath; developmentSnapshotPath=$entry.snapshotPath; developmentSha256=$developmentHash; productionOriginalPath=$productionPath; productionSha256=$productionHash })
    }
}
if ($projectJars.Count -eq 0 -or @($projectJars | Where-Object { [string]::IsNullOrWhiteSpace([string]$_.developmentSha256) -or [string]::IsNullOrWhiteSpace([string]$_.productionSha256) }).Count -gt 0) {
    throw 'Could not record both production and development JAR hashes for this fixture launch.'
}
$fixtureDirectoryHashes = @($snapshot.Entries | Where-Object { $_.kind -eq 'workspace-directory' } | ForEach-Object {
    [pscustomobject]@{ originalPath=$_.originalPath; snapshotPath=$_.snapshotPath; sha256=$_.runtimeSha256 }
})
$launchManifestPath = Join-Path $launchRoot 'launch-manifest.json'
$launchManifest = [ordered]@{
    schemaVersion = 1
    launchId = $launchId
    createdAtUtc = [datetime]::UtcNow.ToString('o')
    profile = $Profile
    originalClasspath = [string]$launch.classpath
    snapshotClasspath = $snapshot.Classpath
    classpathEntries = @($snapshot.Entries)
    projectJars = @($projectJars.ToArray())
    fixtureDirectoryHashes = $fixtureDirectoryHashes
    java = $java
}
$launchManifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $launchManifestPath -Encoding UTF8
$directory = Join-Path $projectRoot "run/$Profile/client-$($PlayerName.ToLowerInvariant())"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
if (!$DebugPort) { $DebugPort = if ($PlayerName -eq 'Developer') { 5005 } else { 5006 } }
if (Get-NetTCPConnection -State Listen -LocalPort $DebugPort -ErrorAction SilentlyContinue) {
    throw "Debugger port $DebugPort is already in use; refusing a duplicate fixture launch."
}
$hash = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$PlayerName"))
$hash[6] = ($hash[6] -band 15) -bor 48
$hash[8] = ($hash[8] -band 63) -bor 128
$hex = [BitConverter]::ToString($hash).Replace('-','').ToLowerInvariant()
$uuid = '{0}-{1}-{2}-{3}-{4}' -f $hex.Substring(0,8),$hex.Substring(8,4),$hex.Substring(12,4),$hex.Substring(16,4),$hex.Substring(20,12)
$gameArgs = @($launch.gameArgs)
foreach ($marker in @('--username','--uuid','--gameDir','--accessToken')) {
    if (@($gameArgs | Where-Object { $_ -eq $marker }).Count -ne 1) { throw "Expected one $marker" }
}
$gameArgs[[array]::IndexOf($gameArgs, '--username') + 1] = $PlayerName
$gameArgs[[array]::IndexOf($gameArgs, '--uuid') + 1] = $uuid
$gameArgs[[array]::IndexOf($gameArgs, '--gameDir') + 1] = $directory
if ($gameArgs[[array]::IndexOf($gameArgs, '--accessToken') + 1] -ne '0') { throw 'Offline dummy token required' }
$jvmArgs = @($launch.jvmArgs | Where-Object { $_ -notlike '-agentlib:jdwp=*' })
$jvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:$debugPort"
$arguments = $jvmArgs + @('-cp', $snapshot.Classpath, $launch.mainClass) + $gameArgs
$argumentFile = Join-Path $projectRoot ".setup/peer-$PlayerName.args"
# Java argument-file quoting, independent of shell interpretation and Windows argv parsing.
$lines = $arguments | ForEach-Object { '"' + $_.Replace('\','\\').Replace('"','\"') + '"' }
[IO.File]::WriteAllLines($argumentFile, [string[]]$lines, [Text.UTF8Encoding]::new($false))
$process = Start-Process -FilePath $launch.executable -ArgumentList ('"@' + $argumentFile + '"') -WorkingDirectory $directory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $projectRoot ".setup/peer-$PlayerName.log") `
    -RedirectStandardError (Join-Path $projectRoot ".setup/peer-$PlayerName-error.log")
@{pid=$process.Id; player=$PlayerName; uuid=$uuid; debugPort=$debugPort; gameDirectory=$directory;
    profile=$Profile; launchManifest=$launchManifestPath; launchId=$launchId;
    sourceHashes=@($snapshot.Entries | ForEach-Object { [pscustomobject]@{originalPath=$_.originalPath; snapshotPath=$_.snapshotPath; sha256=$_.runtimeSha256} })} |
    ConvertTo-Json | Set-Content (Join-Path $projectRoot ".setup/peer-$PlayerName.json")
Write-Host "Started local fixture client $PlayerName (PID $($process.Id), debugger $debugPort). Launch record: $launchManifestPath"
