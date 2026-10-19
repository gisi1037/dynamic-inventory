function Get-FixturePathHash {
    param([Parameter(Mandatory)][string] $Path)

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return $null
    }

    $root = [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $items = [Collections.Generic.List[string]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File -Force) {
        $relative = $file.FullName.Substring($root.Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar).Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $items.Add("$relative`t$fileHash")
    }
    $items.Sort([StringComparer]::Ordinal)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($items -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Test-FixturePathUnderRoot {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)][string] $Root)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $fullRoot = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    return $fullPath.StartsWith($fullRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        $fullPath.Equals($fullRoot, [StringComparison]::OrdinalIgnoreCase)
}

function New-FixtureClasspathSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Classpath,
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][string] $SnapshotRoot
    )

    $project = [IO.Path]::GetFullPath($ProjectRoot)
    $snapshot = [IO.Path]::GetFullPath($SnapshotRoot)
    New-Item -ItemType Directory -Force -Path $snapshot | Out-Null
    $snapshotWorkspace = Join-Path $snapshot 'workspace'
    $entries = [Collections.Generic.List[object]]::new()
    $rewritten = [Collections.Generic.List[string]]::new()
    foreach ($rawEntry in $Classpath.Split([IO.Path]::PathSeparator)) {
        if ([string]::IsNullOrWhiteSpace($rawEntry)) { continue }
        $sourcePath = if ([IO.Path]::IsPathRooted($rawEntry)) { [IO.Path]::GetFullPath($rawEntry) }
            else { [IO.Path]::GetFullPath((Join-Path $project $rawEntry)) }
        if (Test-FixturePathUnderRoot -Path $sourcePath -Root $project) {
            $relative = $sourcePath.Substring($project.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar).Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
            if ([string]::IsNullOrWhiteSpace($relative)) { throw 'The project root itself cannot be a fixture classpath entry.' }
            $destination = Join-Path $snapshotWorkspace $relative
            $sourceHash = Get-FixturePathHash -Path $sourcePath
            if (Test-Path -LiteralPath $sourcePath -PathType Container) {
                New-Item -ItemType Directory -Force -Path $destination | Out-Null
                foreach ($sourceFile in Get-ChildItem -LiteralPath $sourcePath -Recurse -File -Force) {
                    $fileRelative = $sourceFile.FullName.Substring($sourcePath.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar).Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
                    $targetFile = Join-Path $destination $fileRelative
                    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $targetFile) | Out-Null
                    Copy-Item -LiteralPath $sourceFile.FullName -Destination $targetFile
                }
                $kind = 'workspace-directory'
            } elseif (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
                Copy-Item -LiteralPath $sourcePath -Destination $destination
                $kind = 'workspace-file'
            } else {
                $kind = 'workspace-absent'
            }
            $runtimeHash = Get-FixturePathHash -Path $destination
            if ([string]$sourceHash -ne [string]$runtimeHash) {
                throw "Workspace output changed while it was being snapshotted: $sourcePath"
            }
            $rewritten.Add($destination)
            $entries.Add([pscustomobject]@{ kind=$kind; originalPath=$sourcePath; snapshotPath=$destination; sourceSha256=$sourceHash; runtimeSha256=$runtimeHash })
        } else {
            $kind = if (Test-Path -LiteralPath $sourcePath -PathType Leaf) { 'external-file' }
                elseif (Test-Path -LiteralPath $sourcePath -PathType Container) { 'external-directory' }
                else { 'external-absent' }
            $externalHash = Get-FixturePathHash -Path $sourcePath
            $rewritten.Add($sourcePath)
            $entries.Add([pscustomobject]@{ kind=$kind; originalPath=$sourcePath; snapshotPath=$sourcePath; sourceSha256=$externalHash; runtimeSha256=$externalHash })
        }
    }
    return [pscustomobject]@{ Classpath=($rewritten -join [IO.Path]::PathSeparator); Entries=@($entries.ToArray()) }
}

function Get-FixtureJavaDetails {
    param([Parameter(Mandatory)][string] $Executable)
    $fullPath = [IO.Path]::GetFullPath($Executable)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw "Java executable is missing: $fullPath" }
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $fullPath
    $start.Arguments = '-version'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw "Could not inspect Java executable: $fullPath" }
        if (-not $process.WaitForExit(10000)) {
            $process.Kill()
            $process.WaitForExit()
            throw "Timed out inspecting Java executable: $fullPath"
        }
        $versionText = ($process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()).Trim()
        if ($process.ExitCode -ne 0) { throw "Could not read Java version from $fullPath" }
        $match = [regex]::Match($versionText, '(?i)version\s+"?([0-9]+(?:\.[0-9]+)*)')
        if (-not $match.Success) { throw "Could not parse Java version from $fullPath" }
        return [pscustomobject]@{ executable=$fullPath; version=$match.Groups[1].Value; sha256=(Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant() }
    } finally {
        $process.Dispose()
    }
}

function Test-FixtureLaunchManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $ManifestPath,
        [Parameter(Mandatory)][string] $Profile
    )

    $manifestFullPath = [IO.Path]::GetFullPath($ManifestPath)
    if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) { throw "Launch manifest is missing: $manifestFullPath" }
    $manifest = Get-Content -LiteralPath $manifestFullPath -Raw | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1 -or [string]::IsNullOrWhiteSpace([string]$manifest.launchId) -or [string]$manifest.profile -ne $Profile) {
        throw 'Launch manifest has an invalid schema, launch identity, or profile.'
    }
    $manifestPid = $manifest.PSObject.Properties['pid']
    if ($null -ne $manifestPid -and [int]$manifestPid.Value -le 0) { throw 'Launch manifest PID must be positive.' }
    $manifestPort = $manifest.PSObject.Properties['debugPort']
    if ($null -ne $manifestPort -and ([int]$manifestPort.Value -lt 1 -or [int]$manifestPort.Value -gt 65535)) {
        throw 'Launch manifest debugger port is invalid.'
    }
    $manifestGameDirectory = $manifest.PSObject.Properties['gameDirectory']
    if ($null -ne $manifestGameDirectory -and [string]::IsNullOrWhiteSpace([string]$manifestGameDirectory.Value)) {
        throw 'Launch manifest game directory is empty.'
    }
    $expectedEntries = @($manifest.classpathEntries)
    if ($expectedEntries.Count -eq 0 -or [string]::IsNullOrWhiteSpace([string]$manifest.snapshotClasspath)) {
        throw 'Launch manifest has no runtime classpath entries.'
    }
    $runtimePaths = ([string]$manifest.snapshotClasspath).Split([IO.Path]::PathSeparator)
    if ($runtimePaths.Count -ne $expectedEntries.Count) { throw 'Launch manifest classpath does not match its recorded entries.' }
    for ($index = 0; $index -lt $expectedEntries.Count; $index++) {
        if ([string]$runtimePaths[$index] -ne [string]$expectedEntries[$index].snapshotPath) {
            throw 'Launch manifest classpath order/path does not match its recorded entries.'
        }
    }
    if (@($manifest.projectJars).Count -eq 0) { throw 'Launch manifest has no production/development JAR records.' }
    if ($manifest.java.sha256 -notmatch '^[0-9a-f]{64}$' -or $manifest.java.version -notmatch '^\d+(?:\.\d+)*$' -or
        [string]::IsNullOrWhiteSpace([string]$manifest.java.executable) -or
        -not (Test-Path -LiteralPath ([string]$manifest.java.executable) -PathType Leaf)) {
        throw 'Launch manifest does not record a valid Java executable hash and version.'
    }
    $actualJavaHash = (Get-FileHash -LiteralPath ([string]$manifest.java.executable) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualJavaHash -ne [string]$manifest.java.sha256) { throw 'Recorded Java executable hash no longer matches.' }

    foreach ($entry in $expectedEntries) {
        $path = [string]$entry.snapshotPath
        $actual = Get-FixturePathHash -Path $path
        if ([string]$entry.runtimeSha256 -ne [string]$actual) { throw "Fixture launch file changed since snapshot: $path" }
    }
    $expectedDirectories = @($expectedEntries | Where-Object { $_.kind -eq 'workspace-directory' })
    $directoryRecords = @($manifest.fixtureDirectoryHashes)
    if ($directoryRecords.Count -ne $expectedDirectories.Count) { throw 'Launch manifest directory hashes do not cover all workspace output directories.' }
    foreach ($directory in $expectedDirectories) {
        $record = @($directoryRecords | Where-Object { [string]$_.snapshotPath -eq [string]$directory.snapshotPath })
        if ($record.Count -ne 1 -or [string]$record[0].sha256 -ne [string]$directory.runtimeSha256) {
            throw "Launch manifest directory hash does not match: $($directory.snapshotPath)"
        }
    }
    foreach ($jar in @($manifest.projectJars)) {
        if ([string]$jar.developmentSha256 -notmatch '^[0-9a-f]{64}$' -or
            [string]$jar.productionSha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'Launch manifest has an incomplete production/development JAR hash pair.'
        }
        $development = @($expectedEntries | Where-Object { [string]$_.snapshotPath -eq [string]$jar.developmentSnapshotPath })
        if ($development.Count -ne 1 -or [string]$development[0].runtimeSha256 -ne [string]$jar.developmentSha256) {
            throw 'Development JAR hash does not match its runtime snapshot.'
        }
    }
    return [pscustomobject]@{ manifest=$manifest; manifestPath=$manifestFullPath; entryCount=$expectedEntries.Count }
}

function Test-FixtureLaunchRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $PeerMetadataPath,
        [Parameter(Mandatory)][string] $Profile
    )

    $peer = Get-Content -LiteralPath $PeerMetadataPath -Raw | ConvertFrom-Json
    if ([string]$peer.profile -ne $Profile) { throw "Peer metadata profile '$($peer.profile)' does not match '$Profile'." }
    if ([string]::IsNullOrWhiteSpace([string]$peer.launchManifest)) { throw 'Peer metadata has no launch manifest path.' }
    $checked = Test-FixtureLaunchManifest -ManifestPath ([string]$peer.launchManifest) -Profile $Profile
    $manifest = $checked.manifest
    if ([string]$peer.launchId -ne [string]$manifest.launchId) { throw 'Peer metadata and launch manifest identity do not match.' }
    $recordedSources = @($peer.sourceHashes)
    $expectedEntries = @($manifest.classpathEntries)
    if ($expectedEntries.Count -ne $recordedSources.Count) { throw 'Peer metadata source hash list does not cover the launch classpath.' }
    for ($index = 0; $index -lt $expectedEntries.Count; $index++) {
        $entry = $expectedEntries[$index]
        $path = [string]$entry.snapshotPath
        $peerEntry = $recordedSources[$index]
        if ([string]$peerEntry.snapshotPath -ne $path -or [string]$peerEntry.sha256 -ne [string]$entry.runtimeSha256) {
            throw "Peer source hash does not match launch manifest entry: $path"
        }
    }
    return [pscustomobject]@{ valid=$true; profile=$Profile; launchId=[string]$manifest.launchId; manifestPath=$checked.manifestPath; entryCount=$expectedEntries.Count }
}
