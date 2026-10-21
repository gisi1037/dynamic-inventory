param(
    [ValidateSet('Developer','Observer')] [string] $PlayerName = 'Developer',
    [ValidateSet('isolated','rc2-mui2','rc2-backhand','rc2-integration','daily-2026-10-19')] [string] $Profile = 'rc2-mui2',
    [string] $PeerMetadataPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $PSScriptRoot 'fixture-artifacts.ps1')
if ([string]::IsNullOrWhiteSpace($PeerMetadataPath)) {
    $PeerMetadataPath = Join-Path $projectRoot ".setup/peer-$PlayerName.json"
} elseif (-not [IO.Path]::IsPathRooted($PeerMetadataPath)) {
    $PeerMetadataPath = Join-Path $projectRoot $PeerMetadataPath
}
$result = Test-FixtureLaunchRecord -PeerMetadataPath $PeerMetadataPath -Profile $Profile
Write-Output ("PASS: fixture launch {0} for {1}; {2} classpath entries verified." -f $result.launchId, $result.profile, $result.entryCount)
