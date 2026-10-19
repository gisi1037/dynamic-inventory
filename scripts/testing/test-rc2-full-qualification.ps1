param(
    [Parameter(Mandatory = $true)]
    [string] $PreflightManifest,
    [string] $EvidenceDirectory = 'docs/qualification/rc2-opening-gate-qualified',
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $BaselineSnapshot = 'docs/qualification/rc2-opening-gate-qualified/pack-upgrade/before.json',
    [ValidateRange(1, 65535)] [int] $Port = 5005,
    [switch] $Resume
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$gameDirectoryFullPath = [IO.Path]::GetFullPath($GameDirectory)
$evidenceInputPath = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) { $EvidenceDirectory } else { Join-Path $projectRoot $EvidenceDirectory }
$preflightInputPath = if ([IO.Path]::IsPathRooted($PreflightManifest)) { $PreflightManifest } else { Join-Path $projectRoot $PreflightManifest }
$baselineInputPath = if ([IO.Path]::IsPathRooted($BaselineSnapshot)) { $BaselineSnapshot } else { Join-Path $projectRoot $BaselineSnapshot }
$evidenceFullPath = [IO.Path]::GetFullPath($evidenceInputPath)
$preflightFullPath = [IO.Path]::GetFullPath($preflightInputPath)
$baselineFullPath = [IO.Path]::GetFullPath($baselineInputPath)
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$world = 'DI-Baseline-Containers-RC2-Qualification'
$expectedFullPackReports = @(
    'full-pack-containers/verification.json',
    'full-pack-frames/verification.json',
    'gt-mui2/verification.json',
    'full-pack-modes/verification.json',
    'full-pack-presentation/verification.json',
    'full-pack-preservation.json'
)
$runReportPath = Join-Path $evidenceFullPath 'full-pack-runner.json'
$clientConfigPath = Join-Path $gameDirectoryFullPath 'config/dynamicinventory-client.cfg'
$optionsPath = Join-Path $gameDirectoryFullPath 'options.txt'
$clientConfigBefore = $null
$clientConfigBeforeBytes = $null
$optionsBeforeBytes = $null
$clientConfigSha256Before = $null
$optionsSha256Before = $null
$initialColumns = 9
$initialRows = 8
$initialScale = 2
$initialWidth = 1920
$initialHeight = 1080
$settingsRestored = $false
$priorRun = $null
$reusedReports = [Collections.Generic.List[string]]::new()
$settingsBaselineScope = if ($Resume) { 'Resumed attempt only; settings before the earlier interrupted attempt are unavailable.' } else { 'Original attempt, including later resumes using its persisted settings backup.' }
$attemptStartedUtc = [datetime]::UtcNow
$runReport = [ordered]@{
    success = $false
    status = 'running'
    world = $world
    evidenceDirectory = $evidenceFullPath
    capturedAtUtc = $attemptStartedUtc.ToString('o')
    startedUtc = $attemptStartedUtc.ToString('o')
    resumed = [bool]$Resume
    reusedReports = @()
}

New-Item -ItemType Directory -Force -Path $evidenceFullPath | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $evidenceFullPath 'pack-upgrade') | Out-Null
# Read and archive the previous attempt BEFORE writing the new running marker.
# A missing or unrelated attempt must never authorize reuse of old report files.
if ($Resume) {
    if (!(Test-Path -LiteralPath $runReportPath -PathType Leaf)) { throw 'Resume requires an existing failed or interrupted runner report.' }
    $priorRun = Get-Content -LiteralPath $runReportPath -Raw | ConvertFrom-Json
    if ($priorRun.status -notin @('failed', 'running') -or $priorRun.world -ne $world -or
        [IO.Path]::GetFullPath([string]$priorRun.evidenceDirectory) -ne $evidenceFullPath -or
        [string]::IsNullOrWhiteSpace([string]$priorRun.beforeInventorySignature)) {
        throw 'The previous attempt is not a resumable run for this evidence directory and world.'
    }
    $archiveRelative = 'pack-upgrade/runner-attempt-' + $attemptStartedUtc.ToString('yyyyMMdd-HHmmss-fffffff') + '.json'
    Copy-Item -LiteralPath $runReportPath -Destination (Join-Path $evidenceFullPath $archiveRelative)
    $runReport.priorAttempt = $archiveRelative
    $since = if ($priorRun.evidenceSinceUtc) { $priorRun.evidenceSinceUtc } elseif ($priorRun.startedUtc) { $priorRun.startedUtc } else { $priorRun.capturedAtUtc }
    $evidenceSinceUtc = ([datetime]$since).ToUniversalTime()
} else { $evidenceSinceUtc = $attemptStartedUtc }
$runReport.evidenceSinceUtc = $evidenceSinceUtc.ToString('o')

function Evidence-Path([string] $Relative) {
    $path = [IO.Path]::GetFullPath((Join-Path $evidenceFullPath $Relative))
    if (!$path.StartsWith($evidenceFullPath.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'A saved resume path leaves the selected evidence directory.'
    }
    return $path
}

function Reuse-Report([string] $Relative, [switch] $AttachedChest) {
    if (!$Resume) { return $false }
    $path = Evidence-Path $Relative
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { return $false }
    $file = Get-Item -LiteralPath $path
    $report = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($report.success -ne $true -or $report.jarSha256 -ne $expectedJarHash -or $file.LastWriteTimeUtc -lt $evidenceSinceUtc) { return $false }
    if ($AttachedChest -and ($report.checkCount -ne 410 -or @($report.views).Count -ne 16)) { return $false }
    $reusedReports.Add($Relative)
    $runReport.reusedReports = @($reusedReports.ToArray())
    Write-Host "REUSE passed report from the matched earlier attempt: $Relative"
    return $true
}

function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "StorageHarness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}

function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}

function EquipmentSignature($State) {
    # The persistent player container exposes armor and the installed Baubles
    # bridge even when another container is open. Compare contents, not geometry.
    return ($State.playerContainer.slots | Where-Object { $_.role -like 'armor-*' -or $_.role -eq 'external' } |
        Select-Object containerSlot,inventoryIndex,inventoryClass,role,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}

function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot $Label | Out-Null
    $diagnostics = Join-Path $gameDirectoryFullPath 'diagnostics/dynamicinventory'
    $file = Get-ChildItem -LiteralPath $diagnostics -Filter 'client-*.json' -File |
        Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw "No fresh client snapshot was written for $Label." }
    $target = Join-Path $evidenceFullPath "pack-upgrade/$Label.json"
    Copy-Item -LiteralPath $file.FullName -Destination $target -Force
    return Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
}

function Get-IntConfig([string] $Text, [string] $Name, [int] $Fallback) {
    $match = [regex]::Match($Text, "(?m)^\s*I:$([regex]::Escape($Name))=(\d+)\s*$")
    if ($match.Success) { return [int]$match.Groups[1].Value }
    return $Fallback
}

function Get-BoolConfig([string] $Text, [string] $Name, [bool] $Fallback) {
    $match = [regex]::Match($Text, "(?im)^\s*B:$([regex]::Escape($Name))=(true|false)\s*$")
    if ($match.Success) { return $match.Groups[1].Value -ieq 'true' }
    return $Fallback
}

function Get-OptionInt([string] $Text, [string] $Name, [int] $Fallback) {
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):(-?\d+)\s*$")
    if ($match.Success) { return [int]$match.Groups[1].Value }
    return $Fallback
}

function Restore-ClientSettings {
    if ($null -eq $clientConfigBeforeBytes -or $null -eq $optionsBeforeBytes) { return }

    # Return to the player screen, then restore each preference through the same
    # settings dialog the tests use so the live game and saved configuration agree.
    Harness capture rc2-full-qualification-restore-settings | Out-Null
    Harness layout-control 100 | Out-Null
    $mode = Harness machine-mode
    $preferences = @(
        @{ property = 'compactNativePanels'; id = 111; value = (Get-BoolConfig $clientConfigBefore 'compactNativePanels' $false) },
        @{ property = 'expandMachineInventory'; id = 110; value = (Get-BoolConfig $clientConfigBefore 'expandMachineInventory' $true) },
        @{ property = 'mui2Presentation'; id = 112; value = (Get-BoolConfig $clientConfigBefore 'mui2Presentation' $false) }
    )
    foreach ($preference in $preferences) {
        $pattern = [regex]::Escape([string]$preference.property) + '=true'
        $current = $mode -match $pattern
        if ($current -ne [bool]$preference.value) {
            Harness layout-control $preference.id | Out-Null
            $mode = Harness machine-mode
            if (($mode -match $pattern) -ne [bool]$preference.value) {
                throw "Could not restore $($preference.property) to $($preference.value)."
            }
        }
    }
    Harness layout-control 108 | Out-Null
    Harness layout-set $initialColumns $initialRows | Out-Null
    Harness scale $initialScale | Out-Null
    Harness window $initialWidth $initialHeight | Out-Null
    [IO.File]::WriteAllBytes($optionsPath, $optionsBeforeBytes)
    [IO.File]::WriteAllBytes($clientConfigPath, $clientConfigBeforeBytes)
    $script:settingsRestored = $true
}

try {
    if (-not (Test-Path -LiteralPath $preflightFullPath -PathType Leaf)) { throw "Preflight manifest is missing: $preflightFullPath" }
    if (-not (Test-Path -LiteralPath $baselineFullPath -PathType Leaf)) { throw "Before snapshot is missing: $baselineFullPath" }
    if (-not (Test-Path -LiteralPath $clientConfigPath -PathType Leaf)) { throw "Dynamic Inventory client config is missing: $clientConfigPath" }
    if (-not (Test-Path -LiteralPath $optionsPath -PathType Leaf)) { throw "Minecraft options file is missing: $optionsPath" }

    $preflight = & (Join-Path $projectRoot 'scripts/validate-qualification.ps1') -ManifestPath $preflightFullPath -Stage preflight
    if ($preflight.qualificationStage -ne 'preflight' -or $preflight.packTarget -ne '2.9.0-RC-2' -or
        $preflight.prototype -ne '0.8.0-dev' -or $preflight.fullPackSmokeTestAllowed -ne $true) {
        throw 'The selected manifest is not an allowed 0.8.0-dev / GTNH 2.9.0-RC-2 preflight.'
    }
    $expectedJarHash = [string]$preflight.jarSha256
    if ($expectedJarHash -notmatch '^[A-Fa-f0-9]{64}$') { throw 'The preflight manifest has no valid production JAR SHA-256.' }
    $installedJar = Join-Path $gameDirectoryFullPath 'mods/dynamicinventory-0.8.0-dev.jar'
    if (-not (Test-Path -LiteralPath $installedJar -PathType Leaf)) { throw "Installed production JAR is missing: $installedJar" }
    $installedJarHash = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
    if ($installedJarHash -ne $expectedJarHash) { throw 'The installed full-pack JAR does not match the selected successful preflight manifest.' }
    if ($Resume) {
        if ($priorRun.preflightJarSha256 -ne $expectedJarHash) { throw 'Resume cannot reuse reports from a different artifact.' }
        # Resume always retains that attempt's inventory baseline, even if the
        # caller supplied the default deployment snapshot or a different file.
        $baselineFullPath = Evidence-Path ([string]$priorRun.baselineSnapshot)
        if (!(Test-Path -LiteralPath $baselineFullPath -PathType Leaf)) { throw 'The original attempt inventory baseline is missing.' }
    }

    $sourceEvidenceRoot = Split-Path -Parent $preflightFullPath
    foreach ($relative in @($preflight.requiredReports)) {
        $sourceReport = Join-Path $sourceEvidenceRoot ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
        $targetReport = Join-Path $evidenceFullPath ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
        if (-not (Test-Path -LiteralPath $sourceReport -PathType Leaf)) { throw "Preflight report is missing: $sourceReport" }
        if ([IO.Path]::GetFullPath($sourceReport) -ne [IO.Path]::GetFullPath($targetReport)) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $targetReport) | Out-Null
            Copy-Item -LiteralPath $sourceReport -Destination $targetReport -Force
        }
    }

    $baseline = Get-Content -LiteralPath $baselineFullPath -Raw | ConvertFrom-Json
    if ($baseline.reason -ne 'rc2-requalification-before' -or $baseline.modVersion -ne '0.8.0-dev' -or
        $baseline.logicalSide -ne 'client' -or $baseline.cursorOccupied -ne $false) {
        throw 'The before snapshot is not the saved RC-2 requalification client baseline.'
    }
    if ($Resume -and (MainSignature $baseline) -ne [string]$priorRun.beforeInventorySignature) {
        throw 'The original attempt inventory baseline has changed; resume is refused.'
    }
    # New runs can use a fresh baseline. Resumes keep the previous attempt's exact baseline.
    $runBaselinePath = Join-Path $evidenceFullPath 'pack-upgrade/run-baseline.json'
    if ([IO.Path]::GetFullPath($baselineFullPath) -ne [IO.Path]::GetFullPath($runBaselinePath)) {
        Copy-Item -LiteralPath $baselineFullPath -Destination $runBaselinePath -Force
    }
    $runReport.baselineSnapshot = 'pack-upgrade/run-baseline.json'
    $clientConfigBeforeBytes = [IO.File]::ReadAllBytes($clientConfigPath)
    $optionsBeforeBytes = [IO.File]::ReadAllBytes($optionsPath)
    $clientConfigBefore = [IO.File]::ReadAllText($clientConfigPath)
    $optionsBefore = [IO.File]::ReadAllText($optionsPath)
    $initialColumns = Get-IntConfig $clientConfigBefore 'columns' 9
    $initialRows = Get-IntConfig $clientConfigBefore 'visibleRows' 8
    $initialScale = Get-OptionInt $optionsBefore 'guiScale' 2
    $clientConfigSha256Before = (Get-FileHash -LiteralPath $clientConfigPath -Algorithm SHA256).Hash
    $optionsSha256Before = (Get-FileHash -LiteralPath $optionsPath -Algorithm SHA256).Hash

    if ($Resume -and $priorRun.settingsBackup) {
        $settingsManifestPath = Evidence-Path ([string]$priorRun.settingsBackup)
        if (!(Test-Path -LiteralPath $settingsManifestPath -PathType Leaf)) { throw 'The persisted settings backup is missing.' }
        $saved = Get-Content -LiteralPath $settingsManifestPath -Raw | ConvertFrom-Json
        if ($saved.jarSha256 -ne $expectedJarHash -or $saved.world -ne $world -or
            [IO.Path]::GetFullPath([string]$saved.gameDirectory) -ne $gameDirectoryFullPath -or
            [string]$saved.inventorySignature -ne (MainSignature $baseline)) {
            throw 'The persisted settings backup does not belong to this artifact, instance, world and baseline.'
        }
        $savedConfigPath = Evidence-Path ([string]$saved.clientConfigFile)
        $savedOptionsPath = Evidence-Path ([string]$saved.optionsFile)
        if ((Get-FileHash -LiteralPath $savedConfigPath -Algorithm SHA256).Hash -ne $saved.clientConfigSha256 -or
            (Get-FileHash -LiteralPath $savedOptionsPath -Algorithm SHA256).Hash -ne $saved.optionsSha256) {
            throw 'The persisted settings backup checksum differs.'
        }
        $clientConfigBeforeBytes = [IO.File]::ReadAllBytes($savedConfigPath)
        $optionsBeforeBytes = [IO.File]::ReadAllBytes($savedOptionsPath)
        $clientConfigBefore = [IO.File]::ReadAllText($savedConfigPath)
        $optionsBefore = [IO.File]::ReadAllText($savedOptionsPath)
        $clientConfigSha256Before = [string]$saved.clientConfigSha256
        $optionsSha256Before = [string]$saved.optionsSha256
        $initialColumns = Get-IntConfig $clientConfigBefore 'columns' 9
        $initialRows = Get-IntConfig $clientConfigBefore 'visibleRows' 8
        $initialScale = Get-OptionInt $optionsBefore 'guiScale' 2
        $settingsBaselineScope = [string]$saved.scope
        $runReport.settingsBackup = [string]$priorRun.settingsBackup
    } else {
        # Save bytes BEFORE running a suite. A JVM/debugger interruption must not
        # lose the settings needed by a later resume. First-time legacy resumes
        # can only promise preservation from this resumed attempt onward.
        $backupRelative = 'pack-upgrade/settings-' + $attemptStartedUtc.ToString('yyyyMMdd-HHmmss-fffffff')
        New-Item -ItemType Directory -Path (Evidence-Path $backupRelative) | Out-Null
        $configRelative = "$backupRelative/dynamicinventory-client.cfg"
        $optionsRelative = "$backupRelative/options.txt"
        [IO.File]::WriteAllBytes((Evidence-Path $configRelative), $clientConfigBeforeBytes)
        [IO.File]::WriteAllBytes((Evidence-Path $optionsRelative), $optionsBeforeBytes)
        $saved = [ordered]@{
            jarSha256 = $expectedJarHash
            world = $world
            gameDirectory = $gameDirectoryFullPath
            inventorySignature = (MainSignature $baseline)
            clientConfigFile = $configRelative
            optionsFile = $optionsRelative
            clientConfigSha256 = $clientConfigSha256Before
            optionsSha256 = $optionsSha256Before
            scope = $settingsBaselineScope
            capturedAtUtc = $attemptStartedUtc.ToString('o')
        }
        $runReport.settingsBackup = "$backupRelative/manifest.json"
        $saved | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Evidence-Path $runReport.settingsBackup) -Encoding UTF8
    }
    $runReport.settingsBaselineScope = $settingsBaselineScope
    $runReport.beforeInventorySignature = (MainSignature $baseline)
    $runReport.preflightManifest = [IO.Path]::GetRelativePath($projectRoot, $preflightFullPath)
    $runReport.preflightJarSha256 = $expectedJarHash
    $runReport | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $runReportPath -Encoding UTF8
    if ($initialColumns -lt 1 -or $initialRows -lt 1 -or $initialScale -lt 0) { throw 'Saved client layout or GUI scale is invalid.' }

    # Capture the current framebuffer dimensions before the chest-layout matrix
    # switches between its two required test windows.
    Harness require-qualification $world | Out-Null
    Harness screenshot step3-rc2-before-requalification.png | Out-Null
    $beforePng = Join-Path $gameDirectoryFullPath 'screenshots/step3-rc2-before-requalification.png'
    if (-not (Test-Path -LiteralPath $beforePng -PathType Leaf)) { throw 'Could not capture the current game window size.' }
    $pngBytes = [IO.File]::ReadAllBytes($beforePng)
    $initialWidth = [Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($pngBytes, 16))
    $initialHeight = [Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($pngBytes, 20))
    if ($initialWidth -lt 640 -or $initialWidth -gt 1920 -or $initialHeight -lt 480 -or $initialHeight -gt 1080) {
        throw "Current game window ($initialWidth x $initialHeight) is outside the supported full-pack test range."
    }
    Copy-Item -LiteralPath $beforePng -Destination (Join-Path $evidenceFullPath 'window-before.png') -Force

    $loaded = Snapshot 'after-load'
    if ($loaded.cursorOccupied -or (MainSignature $loaded) -ne (MainSignature $baseline)) {
        throw 'The live world inventory does not match the saved before snapshot exactly.'
    }
    $runReport.beforeInventorySignature = (MainSignature $baseline)
    $runReport.preflightManifest = [IO.Path]::GetRelativePath($projectRoot, $preflightFullPath)
    $runReport.preflightJarSha256 = $expectedJarHash
    $runReport.world = $world
    $runReport.checks = @('Installed RC-2 JAR equals the successful preflight artifact', 'Live world starts with the saved exact item/count/damage/NBT snapshot and empty cursor')

    if (!(Reuse-Report 'attached-chest-layout/verification.json' -AttachedChest)) {
        & (Join-Path $PSScriptRoot 'test-attached-chest-layout.ps1') -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'attached-chest-layout') -IncludeAlternateLayouts
    }
    if (!(Reuse-Report 'full-pack-containers/verification.json')) {
        & (Join-Path $PSScriptRoot 'test-container-full-pack.ps1') -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'full-pack-containers')
    }
    if (!(Reuse-Report 'full-pack-frames/verification.json')) {
        & (Join-Path $PSScriptRoot 'test-container-frames.ps1') -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'full-pack-frames')
    }
    if (!(Reuse-Report 'gt-mui2/verification.json')) {
        & (Join-Path $PSScriptRoot 'test-gt-mui2-api.ps1') -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'gt-mui2')
    }
    if (!(Reuse-Report 'full-pack-modes/verification.json')) {
        & (Join-Path $PSScriptRoot 'test-machine-modes.ps1') -FullPack -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'full-pack-modes')
    }
    if (!(Reuse-Report 'full-pack-presentation/verification.json')) {
        & (Join-Path $PSScriptRoot 'test-mui2-presentation.ps1') -Port $Port -GameDirectory $gameDirectoryFullPath `
            -EvidenceDirectory (Join-Path $evidenceFullPath 'full-pack-presentation') -ExpectedPlayerSlots 109
    }

    Restore-ClientSettings
    $playerName = [string]$baseline.player
    if ([string]::IsNullOrWhiteSpace($playerName)) { throw 'Before snapshot does not identify the qualification player.' }
    Harness resume | Out-Null
    Harness command "/tp $playerName 9.5 4 2.5" | Out-Null
    Harness block-open 9 4 0 | Out-Null
    $final = Snapshot 'after-all-regressions'
    $preserved = !$final.cursorOccupied -and (MainSignature $final) -eq (MainSignature $baseline)
    if (!$preserved) { throw 'Final live inventory or cursor differs from the exact saved RC-2 before snapshot.' }
    if (@($baseline.playerContainer.slots | Where-Object role -Like 'armor-*').Count -ne 4 -or
        @($baseline.playerContainer.slots | Where-Object role -eq 'external').Count -ne 18 -or
        (EquipmentSignature $final) -ne (EquipmentSignature $baseline)) {
        throw 'Armor or Baubles equipment differs from the saved pinned RC-2 before snapshot.'
    }
    $finalHash = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
    if ($finalHash -ne $expectedJarHash) { throw 'The installed artifact changed during the full-pack qualification run.' }
    $clientConfigSha256After = (Get-FileHash -LiteralPath $clientConfigPath -Algorithm SHA256).Hash
    if ($clientConfigSha256After -ne $clientConfigSha256Before) { throw 'Dynamic Inventory client settings were not restored byte-for-byte.' }
    $optionsSha256After = (Get-FileHash -LiteralPath $optionsPath -Algorithm SHA256).Hash
    if ($optionsSha256After -ne $optionsSha256Before) { throw 'Minecraft options, including GUI scale and resource-pack selection, were not restored byte-for-byte.' }

    $preservation = [ordered]@{
        success = $true
        fullPackAcceptanceComplete = $false
        world = $world
        jarSha256 = $finalHash
        capturedAtUtc = [datetime]::UtcNow.ToString('o')
        settingsBaselineScope = $settingsBaselineScope
        settingsBackup = $runReport.settingsBackup
        checks = @(
            'All 82 raw main inventory entries preserve exact item/count/damage/NBT from the saved before snapshot through the complete RC-2 regression set',
            'All four armor and eighteen Baubles equipment entries preserve exact item/count/damage/NBT',
            'Cursor is empty after the final native TConstruct station reopen',
            ('Dynamic Inventory client settings and Minecraft options match their persisted byte snapshots. Settings scope: ' + $settingsBaselineScope)
        )
        scope = 'Full-pack RC-2 requalification preservation for the pinned disposable world and this artifact; this is not universal GUI or nightly acceptance.'
    }
    $preservation | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $evidenceFullPath 'full-pack-preservation.json') -Encoding UTF8

    $playerSlots = @($final.activeContainer.slots | Where-Object role -in @('hotbar', 'storage', 'additional-storage'))
    $hoverSlot = $playerSlots | Where-Object { $_.x -ge 0 -and $_.y -ge 0 } | Select-Object -First 1
    if ($null -eq $hoverSlot) { throw 'Final attached station has no visible player slot for the review screenshot.' }
    Harness clear-chat | Out-Null
    Harness screenshot-hover $hoverSlot.containerSlot step3-rc2-full-qualification-final.png | Out-Null
    $finalScreenshot = Join-Path $gameDirectoryFullPath 'screenshots/step3-rc2-full-qualification-final.png'
    Copy-Item -LiteralPath $finalScreenshot -Destination (Join-Path $evidenceFullPath 'final-attached-station.png') -Force

    $sourceManifest = Get-Content -LiteralPath $preflightFullPath -Raw | ConvertFrom-Json
    $fullPackManifest = $sourceManifest | ConvertTo-Json -Depth 32 | ConvertFrom-Json
    $fullPackManifest.qualificationStage = 'full-pack'
    $fullPackManifest.fullPackSmokeTestPassed = $true
    $fullPackManifest.fullPackAcceptanceComplete = $false
    $fullPackManifest.capturedAtUtc = [datetime]::UtcNow.ToString('o')
    $fullPackManifest.scope = 'Selected actual GTNH 2.9.0-RC-2 full-pack checks only: native TConstruct containers and compressed-chest layout, container frames, GT MUI2 API and buffer mode, MUI2 presentation, and exact inventory/settings preservation for the pinned disposable world and this artifact. This does not claim all-GUI or nightly acceptance.'
    $fullPackManifest.requiredReports = @($sourceManifest.requiredReports) + $expectedFullPackReports
    $fullPackManifest | ConvertTo-Json -Depth 32 | Set-Content -LiteralPath (Join-Path $evidenceFullPath 'build-verification.json') -Encoding UTF8
    & (Join-Path $projectRoot 'scripts/validate-qualification.ps1') -ManifestPath (Join-Path $evidenceFullPath 'build-verification.json') -Stage full-pack | Out-Null

    $runReport.success = $true
    $runReport.status = 'passed'
    $runReport.finalJarSha256 = $finalHash
    $runReport.finalInventorySignature = (MainSignature $final)
    $runReport.finalScreen = [string]$final.screen
    $runReport.clientConfigSha256 = $clientConfigSha256After
    $runReport.optionsSha256 = $optionsSha256After
    $runReport.requiredFullPackReports = $expectedFullPackReports
    $runReport.scope = 'Selected actual GTNH 2.9.0-RC-2 full-pack regression families on the pinned disposable world and matched artifact only; this is not all-GUI or nightly acceptance.'
    $runReport.completedAtUtc = [datetime]::UtcNow.ToString('o')
    Write-Host "PASS RC-2 full qualification; all six full-pack reports validate for $finalHash. The TConstruct station remains open for review."
} catch {
    $runReport.success = $false
    $runReport.status = 'failed'
    $runReport.error = $_.Exception.Message
    $runReport.completedAtUtc = [datetime]::UtcNow.ToString('o')
    throw
} finally {
    try {
        if (-not $settingsRestored) { Restore-ClientSettings }
    } catch {
        $runReport.settingsRestoreError = $_.Exception.Message
        $runReport.success = $false
        $runReport.status = 'failed'
    }
    if ($null -ne $optionsBeforeBytes) { [IO.File]::WriteAllBytes($optionsPath, $optionsBeforeBytes) }
    if ($null -ne $clientConfigBeforeBytes) { [IO.File]::WriteAllBytes($clientConfigPath, $clientConfigBeforeBytes) }
    $runReport.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $runReport | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $runReportPath -Encoding UTF8
}
