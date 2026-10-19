param(
    [ValidateSet('isolated','rc2-integration')] [string] $Profile = 'rc2-integration',
    [ValidateRange(1,65535)] [int] $DebugPort = 5009
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $PSScriptRoot 'fixture-artifacts.ps1')
$setupRoot = Join-Path $projectRoot '.setup'
$metadataPath = Join-Path $setupRoot "$Profile-server-launch.json"
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "Offline server launch settings are missing: $metadataPath. Export them with Gradle's writeFixtureServerLaunch task first."
}
$launch = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ([string]$launch.profile -ne $Profile -or $launch.offlineOnly -ne $true -or
    [string]::IsNullOrWhiteSpace([string]$launch.gameDirectory) -or
    [string]$launch.classpath -notlike "*$projectRoot*") {
    throw 'Expected offline Gradle server launch settings for this workspace and profile.'
}

$gameDirectory = [IO.Path]::GetFullPath([string]$launch.gameDirectory)
$expectedDirectory = [IO.Path]::GetFullPath((Join-Path $projectRoot "run/$Profile/server"))
if (-not $gameDirectory.Equals($expectedDirectory, [StringComparison]::OrdinalIgnoreCase) -or
    -not (Test-FixturePathUnderRoot -Path $gameDirectory -Root $projectRoot)) {
    throw "Server game directory must be the isolated workspace path $expectedDirectory; received $gameDirectory."
}
$eulaPath = Join-Path $gameDirectory 'eula.txt'
$propertiesPath = Join-Path $gameDirectory 'server.properties'
if (-not (Test-Path -LiteralPath $eulaPath -PathType Leaf) -or
    (Get-Content -LiteralPath $eulaPath -Raw) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
    throw 'The server must already have eula=true in its existing eula.txt; this launcher does not change it.'
}
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) { throw 'Existing server.properties is required.' }
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match '^\s*([^#=\s][^=]*)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
if ($properties['server-ip'] -ne '127.0.0.1' -or $properties['server-port'] -ne '25566' -or
    $properties['online-mode'] -ne 'false' -or $properties['level-name'] -notmatch '^DI-Baseline-[A-Za-z0-9_.-]+Qualification$') {
    throw 'Existing server.properties must bind localhost:25566, use offline mode, and name a DI-Baseline-...Qualification world.'
}

$peerPath = Join-Path $setupRoot "server-$Profile.json"
if (Test-Path -LiteralPath $peerPath -PathType Leaf) {
    $oldPeer = Get-Content -LiteralPath $peerPath -Raw | ConvertFrom-Json
    $oldProcess = Get-Process -Id ([int]$oldPeer.pid) -ErrorAction SilentlyContinue
    if ($oldProcess -and $oldProcess.ProcessName -match '^javaw?$') {
        throw "The $Profile dedicated fixture server is still running (PID $($oldPeer.pid)); stop it before launching another."
    }
}
foreach ($port in @($DebugPort, 25566)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use; refusing a duplicate fixture server launch."
    }
}

$launchId = [guid]::NewGuid().ToString('N')
$launchRoot = Join-Path $projectRoot ".setup/fixture-launches/$launchId"
$snapshot = New-FixtureClasspathSnapshot -Classpath ([string]$launch.classpath) -ProjectRoot $projectRoot -SnapshotRoot $launchRoot
$java = Get-FixtureJavaDetails -Executable ([string]$launch.executable)
$projectJars = [Collections.Generic.List[object]]::new()
foreach ($entry in $snapshot.Entries | Where-Object { $_.kind -eq 'workspace-file' -and $_.originalPath -match '\\build\\libs\\dynamicinventory-.+\.jar$' }) {
    if ($entry.originalPath -match '-dev-dev\.jar$') {
        $productionPath = $entry.originalPath -replace '-dev-dev\.jar$', '-dev.jar'
        $projectJars.Add([pscustomobject]@{
            developmentOriginalPath=$entry.originalPath; developmentSnapshotPath=$entry.snapshotPath
            developmentSha256=$entry.runtimeSha256; productionOriginalPath=$productionPath
            productionSha256=(Get-FixturePathHash -Path $productionPath)
        })
    }
}
if ($projectJars.Count -ne 1 -or @($projectJars | Where-Object {
    [string]::IsNullOrWhiteSpace([string]$_.developmentSha256) -or [string]::IsNullOrWhiteSpace([string]$_.productionSha256)
}).Count -gt 0) {
    throw 'Could not record exactly one production/development JAR hash pair for this server launch.'
}
$directoryHashes = @($snapshot.Entries | Where-Object { $_.kind -eq 'workspace-directory' } | ForEach-Object {
    [pscustomobject]@{ originalPath=$_.originalPath; snapshotPath=$_.snapshotPath; sha256=$_.runtimeSha256 }
})
$jvmArgs = @($launch.jvmArgs | Where-Object { $_ -notlike '-agentlib:jdwp=*' })
$jvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:$DebugPort"
$arguments = $jvmArgs + @('-cp', $snapshot.Classpath, $launch.mainClass) + @($launch.gameArgs)
$argumentFile = Join-Path $setupRoot "server-$Profile.args"
$argumentLines = $arguments | ForEach-Object { '"' + $_.Replace('\','\\').Replace('"','\"') + '"' }
[IO.File]::WriteAllLines($argumentFile, [string[]]$argumentLines, [Text.UTF8Encoding]::new($false))

$process = Start-Process -FilePath ([string]$launch.executable) -ArgumentList ('"@' + $argumentFile + '"') `
    -WorkingDirectory $gameDirectory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $setupRoot "server-$Profile.log") `
    -RedirectStandardError (Join-Path $setupRoot "server-$Profile-error.log")
try {
    $manifestPath = Join-Path $launchRoot 'launch-manifest.json'
    $manifest = [ordered]@{
        schemaVersion=1; launchId=$launchId; createdAtUtc=[datetime]::UtcNow.ToString('o')
        profile=$Profile; pid=$process.Id; debugPort=$DebugPort; gameDirectory=$gameDirectory
        serverPort=25566; levelName=$properties['level-name']
        originalClasspath=[string]$launch.classpath; snapshotClasspath=$snapshot.Classpath
        classpathEntries=@($snapshot.Entries); projectJars=@($projectJars.ToArray())
        fixtureDirectoryHashes=$directoryHashes; java=$java
    }
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    @{pid=$process.Id; profile=$Profile; gameDirectory=$gameDirectory; debugPort=$DebugPort;
        launchManifest=$manifestPath; launchId=$launchId;
        sourceHashes=@($snapshot.Entries | ForEach-Object { [pscustomobject]@{originalPath=$_.originalPath; snapshotPath=$_.snapshotPath; sha256=$_.runtimeSha256} })} |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $peerPath -Encoding UTF8
} catch {
    Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
    throw
}
Write-Host "Started local fixture server $Profile (PID $($process.Id), game port 25566, debugger $DebugPort). Launch record: $manifestPath"
