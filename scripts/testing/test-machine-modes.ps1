param(
    [int] $Port = 5007,
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $EvidenceDirectory = 'docs/qualification/startup-capacity/machine-modes',
    [switch] $FullPack,
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$screenshots = Join-Path $GameDirectory 'screenshots'
$evidenceFullPath = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) { [IO.Path]::GetFullPath($EvidenceDirectory) }
    else { [IO.Path]::GetFullPath((Join-Path $projectRoot $EvidenceDirectory)) }
New-Item -ItemType Directory -Force -Path $evidenceFullPath | Out-Null
$verificationPath = Join-Path $evidenceFullPath 'verification.json'
@{success=$false; status='running'; provenanceScope=if ($FullPack) {'installed-file-only'} else {'immutable-source-launch-record'};
    capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath $verificationPath -Encoding UTF8
if ($FullPack) {
    $installedJar = Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar'
    if (-not [IO.Path]::IsPathRooted($installedJar)) { $installedJar = Join-Path $projectRoot $installedJar }
    $installedJar = [IO.Path]::GetFullPath($installedJar)
    if (-not (Test-Path -LiteralPath $installedJar -PathType Leaf)) { throw "Missing installed production JAR: $installedJar" }
    $artifactSha256 = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
    $developmentArtifactSha256 = $null
    $sourceLaunch = $null
} else {
    $sourceLaunch = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port `
        -GameDirectory $GameDirectory -Profile 'rc2-integration' -EvidenceDirectory $evidenceFullPath
    $artifactSha256 = [string]$sourceLaunch.artifactSha256
    $developmentArtifactSha256 = [string]$sourceLaunch.developmentArtifactSha256
}
$checks = [Collections.Generic.List[string]]::new()

function Harness {
    $arguments = @('--add-modules', 'jdk.jdi', "-Ddinv.port=$Port")
    if ($FullPack) { $arguments += @('-Ddinv.srgMapping=.setup/mcp-srg.srg', '-Ddinv.packInput=true') }
    $arguments += @('-cp', '.setup', 'StorageHarness')
    $output = & $java @arguments @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot $Label | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw "No fresh client snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Save-View([string] $Name, [int] $SlotId) {
    $filename = "step3-machine-mode-$Name.png"
    Harness clear-chat | Out-Null
    Harness screenshot-hover $SlotId $filename | Out-Null
    Copy-Item (Join-Path $screenshots $filename) (Join-Path $EvidenceDirectory $filename) -Force
}
function Test-Family([string] $Family, [string] $Mode) {
    if ($FullPack) {
        Harness resume | Out-Null
        Harness block-open 2 4 0 | Out-Null
    } else {
        Harness command "/dinvnative $Family" | Out-Null
    }

    $mapping = Harness foreign-check
    Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$Family canonical ordinary slot mapping is available in $Mode mode"
    $ids = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
    Check ($ids.Count -eq 81) "$Family exposes all 81 canonical ordinary slots in $Mode mode"
    $before = Snapshot "$Family-$Mode-before"
    Check (!$before.cursorOccupied) "$Family starts $Mode mode with an empty cursor"

    Harness scale 1 | Out-Null
    $fullHeight = Harness scroll-info
    Check ($fullHeight -match 'actual=(\d+).*visible=(\d+)') "$Family reports viewport dimensions at scale 1"
    $actual = [int]$Matches[1]
    $visible = [int]$Matches[2]
    Check ($actual -eq $visible) "$Family automatic expansion shows the full viewport when the screen fits"
    Check ((MainSignature (Snapshot "$Family-$Mode-scale1")) -eq (MainSignature $before)) "$Family scale 1 preserves all player contents in $Mode mode"

    Harness scale 0 | Out-Null
    $short = Harness scroll-info
    Check ($short -match 'actual=(\d+).*visible=(\d+)') "$Family reports viewport dimensions at short-window scale"
    $shortActual = [int]$Matches[1]
    $shortVisible = [int]$Matches[2]
    Check ($shortVisible -lt $shortActual) "$Family short-window scale retains scrolling in $Mode mode"
    $lastScroll = [math]::Max(0, $shortActual - $shortVisible)
    if ($lastScroll -gt 0) {
        $speed = if ($Family -eq 'mui1') { 6 } else { 18 }
        $events = [int][math]::Ceiling($lastScroll / [double]$speed)
        for ($i = 0; $i -lt $events; $i++) { Harness input-wheel $ids[9] -1 | Out-Null }
        $deadline = [datetime]::UtcNow.AddSeconds(4)
        do {
            $scrolled = Harness scroll-info
            if ($scrolled -match 'scroll=(\d+)' -and [int]$Matches[1] -ge $lastScroll) { break }
            Start-Sleep -Milliseconds 150
        } while ([datetime]::UtcNow -lt $deadline)
        Check ($scrolled -match 'scroll=(\d+)' -and [int]$Matches[1] -ge $lastScroll) "$Family wheel input reaches the final rows in $Mode mode"
    }

    Harness foreign-check | Out-Null
    Check ((MainSignature (Snapshot "$Family-$Mode-scale0")) -eq (MainSignature $before)) "$Family short-window resize preserves all player contents in $Mode mode"

    Save-View "$Mode-$Family" $ids[0]
    Harness resume | Out-Null
}

if ($FullPack) {
    Harness require-qualification $WorldName | Out-Null
    Harness resume | Out-Null
    # This suite owns its disposable fixture; do not depend on a previous suite leaving a GT buffer here.
    Harness command '/tp @p 0 4 2' | Out-Null
    Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
    Harness command '/setblock 2 4 0 gregtech:gt.blockmachines 0 replace {mID:9231}' | Out-Null
}
Harness background | Out-Null
Harness resume | Out-Null
Harness window 854 480 | Out-Null
$families = if ($FullPack) { @('gt-buffer') } else { @('mui1', 'mui2') }
foreach ($family in $families) { Test-Family $family 'automatic' }

@{success=$true; prototype='0.8.0-dev'; fullPack=[bool]$FullPack; modes=@('automatic'); families=$families;
    checks=$checks; jarSha256=$artifactSha256; provenanceScope=if ($FullPack) {'installed-file-only'} else {'immutable-source-launch-record'};
    developmentArtifactSha256=$developmentArtifactSha256;
    sourceLaunchId=if ($sourceLaunch) {$sourceLaunch.launchId} else {$null};
    sourceLaunchProfile=if ($sourceLaunch) {$sourceLaunch.launchProfile} else {$null};
    sourceLaunchManifest=if ($sourceLaunch) {$sourceLaunch.launchManifest} else {$null};
    sourceLaunchManifestSha256=if ($sourceLaunch) {$sourceLaunch.launchManifestSha256} else {$null};
    capturedAtUtc=[datetime]::UtcNow.ToString('o');
    scope='Automatic machine expansion and scrolling, MUI1/MUI2 native viewport behavior or one actual full-pack GT buffer, and player inventory conservation'} |
    ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $verificationPath -Encoding UTF8
Write-Host "PASS automatic machine layout: $($checks.Count) checks."
