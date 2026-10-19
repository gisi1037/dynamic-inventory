. (Join-Path $PSScriptRoot 'fixture-artifacts.ps1')

function Get-QualificationFullPath([string] $Path, [string] $ProjectRoot) {
    $absolute = if ([IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $ProjectRoot $Path }
    return [IO.Path]::GetFullPath($absolute).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
}

function Get-QualificationJarPair($Manifest) {
    $pairs = @($Manifest.projectJars)
    if ($pairs.Count -ne 1) { throw 'Expected exactly one recorded Dynamic Inventory production/development JAR pair.' }
    return $pairs[0]
}

function Start-QualificationEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][int] $Port,
        [Parameter(Mandatory)][string] $GameDirectory,
        [Parameter(Mandatory)][ValidateSet('isolated','rc2-integration')][string] $Profile,
        [Parameter(Mandatory)][string] $EvidenceDirectory,
        [string] $PeerMetadataPath = '',
        [string] $ServerGameDirectory = '',
        [string] $ServerLaunchManifest = ''
    )

    $evidence = Get-QualificationFullPath $EvidenceDirectory $ProjectRoot
    New-Item -ItemType Directory -Force -Path $evidence | Out-Null
    # Invalidate an older successful run before inspecting provenance or invoking a runtime command.
    @{success=$false; status='running'; capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'verification.json')
    try {
        $game = Get-QualificationFullPath $GameDirectory $ProjectRoot
        if ([string]::IsNullOrWhiteSpace($PeerMetadataPath)) {
            $candidates = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot '.setup') -Filter 'peer-*.json' -File |
                Where-Object {
                    try {
                        $candidate = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
                        [int]$candidate.debugPort -eq $Port -and
                            (Get-QualificationFullPath ([string]$candidate.gameDirectory) $ProjectRoot) -eq $game
                    } catch { $false }
                })
            if ($candidates.Count -ne 1) { throw "Expected one peer launch record for debugger $Port and game directory '$game'; found $($candidates.Count)." }
            $PeerMetadataPath = $candidates[0].FullName
        } else { $PeerMetadataPath = Get-QualificationFullPath $PeerMetadataPath $ProjectRoot }
        if (-not (Test-Path -LiteralPath $PeerMetadataPath -PathType Leaf)) { throw "Peer launch record is missing: $PeerMetadataPath" }
        $peer = Get-Content -LiteralPath $PeerMetadataPath -Raw | ConvertFrom-Json
        if ([int]$peer.debugPort -ne $Port -or
            (Get-QualificationFullPath ([string]$peer.gameDirectory) $ProjectRoot) -ne $game) {
            throw 'Peer launch record does not match the requested debugger port and game directory.'
        }
        $launch = Test-FixtureLaunchRecord -PeerMetadataPath $PeerMetadataPath -Profile $Profile
        $process = Get-Process -Id ([int]$peer.pid) -ErrorAction Stop
        $listener = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Where-Object OwningProcess -eq ([int]$peer.pid))
        if ($process.ProcessName -notmatch '^javaw?$' -or $listener.Count -eq 0) {
            throw 'The recorded fixture JVM is not listening on the requested debugger port.'
        }
        $manifest = Get-Content -LiteralPath $launch.manifestPath -Raw | ConvertFrom-Json
        $pair = Get-QualificationJarPair $manifest
        Copy-Item -LiteralPath $PeerMetadataPath -Destination (Join-Path $evidence 'peer-metadata.json') -Force
        Copy-Item -LiteralPath $launch.manifestPath -Destination (Join-Path $evidence 'launch-manifest.json') -Force
        $result = [ordered]@{
            launchId=$launch.launchId; launchProfile=$Profile; launchManifest='launch-manifest.json'
            artifactSha256=[string]$pair.productionSha256; developmentArtifactSha256=[string]$pair.developmentSha256
            fixtureDirectoryHashes=@($manifest.fixtureDirectoryHashes)
            launchManifestSha256=(Get-FileHash -LiteralPath (Join-Path $evidence 'launch-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        if ($ServerGameDirectory) {
            if ([string]::IsNullOrWhiteSpace($ServerLaunchManifest)) {
                throw 'Dedicated qualification requires -ServerLaunchManifest for the running server.'
            }
            $serverPath = Get-QualificationFullPath $ServerLaunchManifest $ProjectRoot
            Test-FixtureLaunchManifest -ManifestPath $serverPath -Profile $Profile | Out-Null
            $server = Get-Content -LiteralPath $serverPath -Raw | ConvertFrom-Json
            if ([string]::IsNullOrWhiteSpace([string]$server.gameDirectory) -or
                (Get-QualificationFullPath ([string]$server.gameDirectory) $ProjectRoot) -ne
                    (Get-QualificationFullPath $ServerGameDirectory $ProjectRoot)) {
                throw 'Server launch manifest does not match the requested server game directory.'
            }
            if ([int]$server.pid -le 0 -or [int]$server.debugPort -lt 1 -or [int]$server.debugPort -gt 65535) {
                throw 'Server launch manifest has no valid running JVM PID/debugger port; restart the recorded server fixture.'
            }
            $serverProcess = Get-Process -Id ([int]$server.pid) -ErrorAction Stop
            $serverListener = @(Get-NetTCPConnection -LocalPort ([int]$server.debugPort) -State Listen -ErrorAction SilentlyContinue |
                Where-Object OwningProcess -eq ([int]$server.pid))
            if ($serverProcess.ProcessName -notmatch '^javaw?$' -or $serverListener.Count -eq 0) {
                throw 'The recorded server JVM is not listening on its recorded debugger port.'
            }
            $serverPair = Get-QualificationJarPair $server
            if ($serverPair.productionSha256 -ne $pair.productionSha256 -or
                $serverPair.developmentSha256 -ne $pair.developmentSha256) {
                throw 'Client and server launch records identify different production/development artifact pairs.'
            }
            Copy-Item -LiteralPath $serverPath -Destination (Join-Path $evidence 'server-launch-manifest.json') -Force
            $result['serverLaunchId'] = [string]$server.launchId
            $result['serverLaunchManifest'] = 'server-launch-manifest.json'
            $result['serverLaunchManifestSha256'] = (Get-FileHash -LiteralPath (Join-Path $evidence 'server-launch-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
            $result['serverArtifactSha256'] = [string]$serverPair.productionSha256
            $result['serverDevelopmentArtifactSha256'] = [string]$serverPair.developmentSha256
        }
        return $result
    } catch {
        throw "Fixture provenance could not be verified: $($_.Exception.Message) Restart the fixture with the recorded source launcher, then rerun qualification."
    }
}
