[CmdletBinding()]
param(
    [switch]$PrepareOnly,
    [switch]$NoLaunch,
    [switch]$RunChecks,
    [ValidateRange(0,65535)][int]$DebugPort = 0
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$pack = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'pack-2.9.0.json') | ConvertFrom-Json
if ($DebugPort -eq 0) { $DebugPort = [int]$pack.debugPort }
$taskConfig = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'shared-pack-base.json') | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$taskRuntime = Resolve-JavaRuntime -MinimumMajor 25
$taskPrismRoot = Join-Path ([Environment]::GetFolderPath([Environment+SpecialFolder]::ApplicationData)) 'PrismLauncher/instances'
$taskConfig | Add-Member -NotePropertyMembers @{
    instanceId = $pack.instanceId
    instancePath = Join-Path $taskPrismRoot $pack.instanceId
    debugPort = $DebugPort
    buildJavaHome = $taskRuntime.Home
    projects = @{Inventory = $projectRoot}
    evidenceDirectory = Join-Path $projectRoot '.setup/shared-pack'
    launcher = Join-Path ([Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)) 'Programs/PrismLauncher/prismlauncher.exe'
}
New-Item -ItemType Directory -Force -Path $taskConfig.evidenceDirectory | Out-Null
$taskTarget = [IO.Path]::GetFullPath($taskConfig.instancePath)
$taskInstanceRoot = [IO.Path]::GetFullPath($taskPrismRoot)
if ((Split-Path -Parent $taskTarget) -ne $taskInstanceRoot -or (Split-Path -Leaf $taskTarget) -ne $taskConfig.instanceId) { throw 'Unexpected shared-instance path.' }
$taskMarker = Get-Content -Raw -LiteralPath (Join-Path $taskTarget '.gtnh-shared-migration.json') | ConvertFrom-Json
if ($taskMarker.packRelease -ne '2.9.0' -or $taskMarker.officialArchive.sha256 -ne $taskConfig.packSha256) { throw 'Shared instance has a different pack identity.' }
if ((Get-FileHash -LiteralPath (Join-Path $taskTarget 'mmc-pack.json') -Algorithm SHA256).Hash -ne $taskConfig.componentsSha256) { throw 'Prism components differ from the official2.9 profile.' }
# Prism owns the game's Java selection; only the Gradle build JDK is pinned here.
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Assert-Stopped {
    $taskPort = @(Get-NetTCPConnection -LocalPort $taskConfig.debugPort -State Listen -ErrorAction SilentlyContinue)
    $taskRunning = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object { $_.CommandLine -and ($_.CommandLine.Contains($taskConfig.instanceId) -or $_.CommandLine.Contains($taskTarget)) })
    if ($taskPort.Count -or $taskRunning.Count) { throw 'Save and close the shared2.9 game before building/deploying. No files were replaced.' }
}
function Assert-Memory([long]$MinimumKiB) {
    if ((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory -lt $MinimumKiB) { throw 'Insufficient free RAM. Close the existing game normally before building or launching; no processes are stopped automatically.' }
}
function Get-ModIds([string]$Path) {
    $taskZip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $taskEntry = $taskZip.GetEntry('mcmod.info')
        if (!$taskEntry) { return }
        $taskReader = New-Object IO.StreamReader($taskEntry.Open())
        try { $taskInfo = $taskReader.ReadToEnd() | ConvertFrom-Json } finally { $taskReader.Dispose() }
        if ($taskInfo.PSObject.Properties['modList']) { $taskInfo = $taskInfo.modList }
        foreach ($taskMod in @($taskInfo)) { if ($taskMod.modid) { [string]$taskMod.modid } }
    } finally { $taskZip.Dispose() }
}
foreach ($taskBaseMod in $taskConfig.officialMods) {
    $taskPath = Join-Path $taskTarget $taskBaseMod.path
    if (!(Test-Path -LiteralPath $taskPath) -or (Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash -ne $taskBaseMod.sha256) { throw "Official2.9 component changed: $($taskBaseMod.path)" }
}
if ($PrepareOnly) { Write-Output ('Verified shared2.9 base; game Java is selected in Prism: '+$taskTarget); return }
Assert-Stopped
Assert-Memory 2621440
$taskSelected = @('Inventory')
$taskJobs = @()
foreach ($taskName in $taskSelected) {
    $taskRepo = [string]$taskConfig.projects.$taskName
    $taskArgs = @('assemble','writeInventoryProfile','-PinventoryProfile=gtnh-2.9.0')
    if ($RunChecks) { $taskArgs = @('test') + $taskArgs }
    $taskArgs += @('--no-daemon','--no-configuration-cache','--max-workers=1')
    $taskLog = Join-Path $taskConfig.evidenceDirectory ($taskName.ToLowerInvariant()+'-build-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
    Write-Output ('Building '+$taskName+' for official2.9; Gradle JDK remains25.')
    $taskPreviousJava = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $taskConfig.buildJavaHome
        $taskCommand = @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $taskRepo 'scripts/gradle.ps1')+'"')) + $taskArgs
        $taskProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList $taskCommand -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $taskLog -RedirectStandardError ($taskLog+'.stderr')
        $taskCode = $taskProcess.ExitCode
    } finally { $env:JAVA_HOME = $taskPreviousJava }
    if ($taskCode -ne 0) { throw "$taskName build failed (exit $taskCode); no mod was deployed. See $taskLog and $taskLog.stderr" }
    $taskJar = Join-Path $taskRepo 'build/libs/dynamicinventory-0.8.0-dev.jar'
    $taskModId = 'dynamicinventory'
    if (!(Test-Path -LiteralPath $taskJar) -or @(Get-ModIds $taskJar) -notcontains $taskModId) { throw 'Built artifact does not declare the expected mod ID.' }
    $taskZip = [IO.Compression.ZipFile]::OpenRead($taskJar)
    try { if (@($taskZip.Entries | Where-Object { $_.FullName -match '(?:^|/)(?:PackBenchmark|IntegrationHarness|StorageTestCommand)' }).Count) { throw 'Refusing fixture/benchmark artifact for the shared manual instance.' } } finally { $taskZip.Dispose() }
    $taskJobs += [pscustomobject]@{project=$taskName;sourceJar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLowerInvariant();modId=$taskModId;buildLog=$taskLog;checksRequested=[bool]$RunChecks}
}
Assert-Stopped
$taskBackup = Join-Path $taskTarget ('.development-backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $taskBackup | Out-Null
$taskMods = Join-Path $taskTarget '.minecraft/mods'
foreach ($taskJob in $taskJobs) {
    $taskFileName = if ($taskJob.modId -eq 'superfactorymanager') { 'SuperFactoryManager-development.jar' } else { 'DynamicInventory-development.jar' }
    $taskDestination = Join-Path $taskMods $taskFileName
    # Match by mod ID, never by a broad FactoryManager filename pattern.
    $taskOld = @(Get-ChildItem -LiteralPath $taskMods -Filter '*.jar' -File | Where-Object { $_.Name -like '*FactoryManager*' -or $_.Name -like '*dynamicinventory*' } | Where-Object { @(Get-ModIds $_.FullName) -contains $taskJob.modId })
    if ((Test-Path -LiteralPath $taskDestination) -and $taskDestination -notin @($taskOld.FullName)) { throw 'Destination belongs to another mod.' }
    foreach ($taskPrevious in $taskOld) { Copy-Item -LiteralPath $taskPrevious.FullName -Destination (Join-Path $taskBackup $taskPrevious.Name) }
    Copy-Item -LiteralPath $taskJob.sourceJar -Destination $taskDestination -Force
    if ((Get-FileHash -LiteralPath $taskDestination -Algorithm SHA256).Hash -ne $taskJob.sha256) { throw 'Deployed artifact hash mismatch.' }
    foreach ($taskPrevious in $taskOld) { if ($taskPrevious.FullName -ne $taskDestination) { Remove-Item -LiteralPath $taskPrevious.FullName } }
}
$taskCfg = Join-Path $taskTarget 'instance.cfg'
Copy-Item -LiteralPath $taskCfg -Destination (Join-Path $taskBackup 'instance.cfg')
$taskText = Get-Content -Raw -LiteralPath $taskCfg
$taskJvm = '"-Ddynamicinventory.profile=gtnh-2.9.0 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:'+$taskConfig.debugPort+'"'
$taskText = [regex]::Replace($taskText,'(?m)^JvmArgs=.*', [System.Text.RegularExpressions.MatchEvaluator]{ param($m) 'JvmArgs='+$taskJvm })
[IO.File]::WriteAllText($taskCfg,$taskText,(New-Object System.Text.UTF8Encoding($false)))
$taskPrismJava = [regex]::Match($taskText,'(?m)^JavaPath=(.*)$').Groups[1].Value.Trim().Trim('"')
@{pack='2.9.0';java=$taskPrismJava;javaSelection='Prism instance setting; runtime not verified';deployedAtUtc=[datetime]::UtcNow.ToString('o');jobs=$taskJobs;backup=$taskBackup;qualification='Development smoke qualification pending; not a release certification'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskTarget '.shared-development-deployment.json') -Encoding utf8
Write-Output ('Deployed '+($taskSelected -join ', ')+' into '+$taskConfig.instanceId)
if (!$NoLaunch) {
    if (!(Test-Path -LiteralPath (Join-Path $taskMods 'SuperFactoryManager-development.jar')) -or !(Test-Path -LiteralPath (Join-Path $taskMods 'DynamicInventory-development.jar'))) { throw 'Install both development mods before launching the shared profile.' }
    Assert-Stopped
    Assert-Memory 6291456
    Start-Process -FilePath $taskConfig.launcher -ArgumentList @('--launch',$taskConfig.instanceId) -WindowStyle Hidden
}
