$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $PSScriptRoot 'fixture-artifacts.ps1')
$testRoot = Join-Path $projectRoot ('.setup/fixture-artifacts-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$checks = [Collections.Generic.List[string]]::new()
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    $checks.Add($Message)
}

try {
    $workspace = Join-Path $testRoot 'workspace'
    $snapshotRoot = Join-Path $testRoot 'fixture-launches/abc123'
    $outputDirectory = Join-Path $workspace 'build/classes/java/main'
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    Set-Content -LiteralPath (Join-Path $outputDirectory 'Fixture.class') -Value 'compiled output'
    $devJar = Join-Path $workspace 'build/libs/dynamicinventory-0.8.0-dev-dev.jar'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $devJar) | Out-Null
    Set-Content -LiteralPath $devJar -Value 'development artifact'
    $productionJar = Join-Path $workspace 'build/libs/dynamicinventory-0.8.0-dev.jar'
    Set-Content -LiteralPath $productionJar -Value 'production artifact'
    $externalJar = Join-Path $testRoot 'gradle-cache/dependency.jar'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $externalJar) | Out-Null
    Set-Content -LiteralPath $externalJar -Value 'external dependency'
    $missingOutput = Join-Path $workspace 'build/classes/java/optionalTest'
    $sourceClasspath = @($outputDirectory, $missingOutput, $devJar, $externalJar) -join [IO.Path]::PathSeparator

    $snapshot = New-FixtureClasspathSnapshot -Classpath $sourceClasspath -ProjectRoot $workspace -SnapshotRoot $snapshotRoot
    Check ($snapshot.Entries.Count -eq 4) 'Every classpath entry is captured in launch provenance'
    Check ($snapshot.Entries[0].kind -eq 'workspace-directory' -and
        (Test-Path -LiteralPath (Join-Path $snapshot.Entries[0].snapshotPath 'Fixture.class'))) 'Workspace class output is snapshotted'
    Check ($snapshot.Entries[1].kind -eq 'workspace-absent' -and -not (Test-Path -LiteralPath $snapshot.Entries[1].snapshotPath)) 'Absent optional workspace output stays absent in the snapshot'
    Check ($snapshot.Entries[2].kind -eq 'workspace-file' -and $snapshot.Entries[2].runtimeSha256 -eq (Get-FixturePathHash $devJar)) 'Workspace JAR is copied and hashed'
    Check ($snapshot.Entries[3].kind -eq 'external-file' -and $snapshot.Entries[3].originalPath -eq $externalJar) 'External dependency stays in place and is hashed'

    $projectJarRecord = [pscustomobject]@{
        developmentOriginalPath=$devJar; developmentSnapshotPath=$snapshot.Entries[2].snapshotPath
        developmentSha256=$snapshot.Entries[2].runtimeSha256; productionOriginalPath=$productionJar
        productionSha256=(Get-FixturePathHash $productionJar)
    }
    $syntheticJava = Join-Path $testRoot 'synthetic-java.exe'
    Set-Content -LiteralPath $syntheticJava -Value 'synthetic runtime identity'
    $manifestPath = Join-Path $snapshotRoot 'launch-manifest.json'
    $manifest = [ordered]@{
        schemaVersion=1; launchId='abc123'; profile='rc2-integration';
        classpathEntries=@($snapshot.Entries); projectJars=@($projectJarRecord);
        fixtureDirectoryHashes=@([pscustomobject]@{snapshotPath=$snapshot.Entries[0].snapshotPath;sha256=$snapshot.Entries[0].runtimeSha256});
        snapshotClasspath=$snapshot.Classpath;
        java=@{executable=$syntheticJava;version='25';sha256=(Get-FixturePathHash $syntheticJava)}
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $peerPath = Join-Path $testRoot 'peer.json'
    $peer = @{
        profile='rc2-integration'; launchId='abc123'; launchManifest=$manifestPath;
        pid=4242; debugPort=5005; gameDirectory=(Join-Path $testRoot 'fixture-client');
        sourceHashes=@($snapshot.Entries | ForEach-Object { [pscustomobject]@{snapshotPath=$_.snapshotPath;sha256=$_.runtimeSha256} })
    }
    $peer | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $peerPath -Encoding UTF8
    $valid = Test-FixtureLaunchRecord -PeerMetadataPath $peerPath -Profile 'rc2-integration'
    Check ($valid.valid -and $valid.entryCount -eq 4) 'Recorded snapshot and external hashes validate for the configured profile'
    $manifestResult = Test-FixtureLaunchManifest -ManifestPath $manifestPath -Profile 'rc2-integration'
    Check ($manifestResult.entryCount -eq 4) 'The standalone manifest validator accepts direct launch records'

    function Get-Process {
        [CmdletBinding()]
        param([int] $Id)
        return [pscustomobject]@{ Id=$Id; ProcessName='java' }
    }
    function Get-NetTCPConnection {
        [CmdletBinding()]
        param([int] $LocalPort, [string] $State)
        $owner = if ($LocalPort -eq 5009 -and $script:wrongServerListener) { 5253 }
            elseif ($LocalPort -eq 5009) { 5252 } else { 4242 }
        return @([pscustomobject]@{ LocalPort=$LocalPort; State='Listen'; OwningProcess=$owner })
    }
    . (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
    $script:wrongServerListener = $false
    Add-Content -LiteralPath $devJar -Value 'rebuilt development artifact'
    Add-Content -LiteralPath $productionJar -Value 'rebuilt production artifact'
    $evidenceDirectory = Join-Path $testRoot 'evidence'
    $captured = Start-QualificationEvidence -ProjectRoot $workspace -Port 5005 `
        -GameDirectory $peer.gameDirectory -Profile 'rc2-integration' -EvidenceDirectory $evidenceDirectory `
        -PeerMetadataPath $peerPath
    Check ($captured.artifactSha256 -eq $projectJarRecord.productionSha256 -and
        $captured.developmentArtifactSha256 -eq $projectJarRecord.developmentSha256) 'Qualification evidence retains recorded artifact hashes after workspace JAR rebuilds'
    Check ((Test-Path -LiteralPath (Join-Path $evidenceDirectory 'launch-manifest.json')) -and
        (Test-Path -LiteralPath (Join-Path $evidenceDirectory 'peer-metadata.json')) -and
        (Get-FileHash -LiteralPath (Join-Path $evidenceDirectory 'launch-manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant() -eq $captured.launchManifestSha256) 'Qualification evidence copies and hashes the validated launch record'
    $serverManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $serverManifest | Add-Member -NotePropertyName gameDirectory -NotePropertyValue (Join-Path $testRoot 'fixture-server') -Force
    $serverManifest | Add-Member -NotePropertyName pid -NotePropertyValue 5252 -Force
    $serverManifest | Add-Member -NotePropertyName debugPort -NotePropertyValue 5009 -Force
    $serverManifestPath = Join-Path $testRoot 'server-launch-manifest.json'
    $serverManifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $serverManifestPath -Encoding UTF8
    $dedicated = Start-QualificationEvidence -ProjectRoot $workspace -Port 5005 `
        -GameDirectory $peer.gameDirectory -Profile 'rc2-integration' -EvidenceDirectory $evidenceDirectory `
        -PeerMetadataPath $peerPath -ServerGameDirectory $serverManifest.gameDirectory `
        -ServerLaunchManifest $serverManifestPath
    Check ($dedicated.serverLaunchId -eq 'abc123' -and $dedicated.serverArtifactSha256 -eq $captured.artifactSha256 -and
        (Test-Path -LiteralPath (Join-Path $evidenceDirectory 'server-launch-manifest.json'))) 'Dedicated evidence validates and copies the matching live server launch record'
    $script:wrongServerListener = $true
    $wrongListenerRejected = $false
    try {
        Start-QualificationEvidence -ProjectRoot $workspace -Port 5005 -GameDirectory $peer.gameDirectory `
            -Profile 'rc2-integration' -EvidenceDirectory $evidenceDirectory -PeerMetadataPath $peerPath `
            -ServerGameDirectory $serverManifest.gameDirectory -ServerLaunchManifest $serverManifestPath | Out-Null
    } catch { $wrongListenerRejected = $true }
    $serverFailureReport = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Raw | ConvertFrom-Json
    Check ($wrongListenerRejected -and $serverFailureReport.success -eq $false -and $serverFailureReport.status -eq 'running') 'A server debugger listener owned by another PID is rejected and leaves evidence non-passing'
    $script:wrongServerListener = $false
    function Assert-EvidenceFailure([string] $case, [int] $port, [string] $gameDirectory, [string] $profile, [string] $metadataPath) {
        $failed = $false
        try {
            Start-QualificationEvidence -ProjectRoot $workspace -Port $port -GameDirectory $gameDirectory `
                -Profile $profile -EvidenceDirectory $evidenceDirectory -PeerMetadataPath $metadataPath | Out-Null
        } catch { $failed = $true }
        $report = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Raw | ConvertFrom-Json
        Check ($failed -and $report.success -eq $false -and $report.status -eq 'running') "$case is refused with evidence marked running"
    }
    Assert-EvidenceFailure 'Wrong debugger port' 5006 $peer.gameDirectory 'rc2-integration' $peerPath
    Assert-EvidenceFailure 'Wrong game directory' 5005 (Join-Path $testRoot 'other-client') 'rc2-integration' $peerPath
    Assert-EvidenceFailure 'Wrong configured profile' 5005 $peer.gameDirectory 'isolated' $peerPath
    Assert-EvidenceFailure 'Missing peer record' 5005 $peer.gameDirectory 'rc2-integration' (Join-Path $testRoot 'missing-peer.json')

    Add-Content -LiteralPath (Join-Path $outputDirectory 'Fixture.class') -Value 'later rebuild'
    $validAfterSourceChange = Test-FixtureLaunchRecord -PeerMetadataPath $peerPath -Profile 'rc2-integration'
    Check ($validAfterSourceChange.valid) 'Changes to build output after launch do not change the snapshot record'

    $wrongProfileRejected = $false
    try { Test-FixtureLaunchRecord -PeerMetadataPath $peerPath -Profile 'isolated' | Out-Null } catch { $wrongProfileRejected = $true }
    Check ($wrongProfileRejected) 'A profile mismatch is rejected'

    $savedManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $savedManifest.classpathEntries = @()
    $savedManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $emptyClasspathRejected = $false
    try { Test-FixtureLaunchManifest -ManifestPath $manifestPath -Profile 'rc2-integration' | Out-Null } catch { $emptyClasspathRejected = $true }
    Check ($emptyClasspathRejected) 'A manifest with no runtime classpath is rejected'
    $savedManifest.classpathEntries = @($snapshot.Entries)
    $savedManifest.projectJars = @()
    $savedManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $missingJarRecordRejected = $false
    try { Test-FixtureLaunchManifest -ManifestPath $manifestPath -Profile 'rc2-integration' | Out-Null } catch { $missingJarRecordRejected = $true }
    Check ($missingJarRecordRejected) 'A manifest without production/development JAR hashes is rejected'
    $savedManifest.projectJars = @($projectJarRecord)
    $savedManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    Add-Content -LiteralPath $syntheticJava -Value 'replaced executable'
    $javaMutationRejected = $false
    try { Test-FixtureLaunchManifest -ManifestPath $manifestPath -Profile 'rc2-integration' | Out-Null } catch { $javaMutationRejected = $true }
    Check ($javaMutationRejected) 'A changed Java executable is rejected by its recorded hash'
    Add-Content -LiteralPath (Join-Path $snapshot.Entries[0].snapshotPath 'Fixture.class') -Value 'snapshot tamper'
    $snapshotMutationRejected = $false
    try { Test-FixtureLaunchRecord -PeerMetadataPath $peerPath -Profile 'rc2-integration' | Out-Null } catch { $snapshotMutationRejected = $true }
    Check ($snapshotMutationRejected) 'A changed launch snapshot is rejected'

    Write-Output ("PASS: {0} fixture launch provenance checks" -f $checks.Count)
    $checks | ForEach-Object { Write-Output "  $_" }
} finally {
    Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
}
