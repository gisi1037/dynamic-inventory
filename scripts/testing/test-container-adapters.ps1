param(
    [int] $Port = 5007,
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $PlayerName = 'Developer',
    [string] $EvidenceDirectory = 'docs/qualification/startup-capacity/integrated',
    [string] $ServerGameDirectory = '',
    [string] $ServerLaunchManifest = '',
    [int] $StorageSlots = 72,
    [ValidateSet('rc2-integration','daily-2026-10-19')] [string] $Profile = 'rc2-integration',
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$serverDiagnostics = if ($ServerGameDirectory) { Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory' } else { $diagnostics }
$evidenceFullPath = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) { [IO.Path]::GetFullPath($EvidenceDirectory) }
    else { [IO.Path]::GetFullPath((Join-Path $projectRoot $EvidenceDirectory)) }
New-Item -ItemType Directory -Force -Path $evidenceFullPath | Out-Null
$verificationPath = Join-Path $evidenceFullPath 'verification.json'
@{success=$false; status='running'; capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath $verificationPath -Encoding UTF8
$serverManifestFullPath = ''
if ($ServerGameDirectory) {
    if ([string]::IsNullOrWhiteSpace($ServerLaunchManifest)) {
        $serverPeerPath = Join-Path $projectRoot ".setup/server-$Profile.json"
        if (Test-Path -LiteralPath $serverPeerPath -PathType Leaf) {
            $serverPeer = Get-Content -LiteralPath $serverPeerPath -Raw | ConvertFrom-Json
            $ServerLaunchManifest = [string]$serverPeer.launchManifest
        }
    }
    if ([string]::IsNullOrWhiteSpace($ServerLaunchManifest)) {
        throw 'Dedicated source qualification requires the running server launch manifest.'
    }
    $serverManifestFullPath = if ([IO.Path]::IsPathRooted($ServerLaunchManifest)) { [IO.Path]::GetFullPath($ServerLaunchManifest) }
        else { [IO.Path]::GetFullPath((Join-Path $projectRoot $ServerLaunchManifest)) }
}
$qualification = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port `
    -GameDirectory $GameDirectory -Profile $Profile -EvidenceDirectory $evidenceFullPath `
    -ServerGameDirectory $ServerGameDirectory -ServerLaunchManifest $serverManifestFullPath
$checks = [Collections.Generic.List[string]]::new()
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Server([string] $Action) {
    $since = [datetime]::UtcNow
    Harness command "/dinvadapters $Action" | Out-Null
    $path = Join-Path $serverDiagnostics "qualification/$PlayerName-adapters-$Action.json"
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        if ((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since) {
            $report = Get-Content $path -Raw | ConvertFrom-Json
            Check $report.success "Server $Action passed"
            Copy-Item $path (Join-Path $EvidenceDirectory "$($script:family)-$Action.json") -Force
            return $report
        }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No fresh server result: $Action"
}
function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot $Label | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw 'No fresh client snapshot' }
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function SaveView([string] $Name) {
    Harness clear-chat | Out-Null
    Harness screenshot "step3-$Name.png" | Out-Null
    Copy-Item (Join-Path $GameDirectory "screenshots/step3-$Name.png") (Join-Path $EvidenceDirectory "$Name.png") -Force
}
if ($ServerGameDirectory) { Harness require-dedicated | Out-Null }
else { Harness require-qualification $WorldName | Out-Null }
Harness background | Out-Null
Harness resume | Out-Null
Harness window 1920 1080 | Out-Null
$script:family = 'vanilla'
$selftest = Server selftest
Check ($selftest.passed.Count -ge 274) 'Vanilla server container tests cover canonical mapping and actual slot-click behavior'
foreach ($script:family in @('chest','double-chest','workbench','furnace')) {
    Server $family | Out-Null
    Check ((Harness foreign-check) -match 'ordinaryIds=') "$family exposes all $($StorageSlots + 9) canonical ordinary slots"
    foreach ($scale in @(1,2,3,0)) {
        Harness scale $scale | Out-Null
        Harness foreign-check | Out-Null
        $checks.Add("$family fits GUI scale $scale with all canonical slots")
    }
    $before = Snapshot "$family-before-click"
    if ($family -in @('workbench','furnace')) {
        Harness native-recipe-check | Out-Null
        $checks.Add("$family registered recipe transfer and ghost follow the native input slots")
    }
    Harness input-click 0 | Out-Null
    Harness input-click 0 | Out-Null
    $after = Snapshot "$family-after-click"
    Check (!( $after.cursorOccupied)) "$family real mouse pickup/return leaves cursor empty"
    Check ((MainSignature $before) -eq (MainSignature $after)) "$family real mouse click preserves exact player contents"
    SaveView "container-$family"
    Harness resume | Out-Null
}
foreach ($script:family in @('mui1','mui2')) {
    Harness command "/dinvnative $family" | Out-Null
    $mapping = Harness foreign-check
    Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$family stock public binding exposes configured canonical slots"
    $ids = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
    Check ($ids.Count -eq $StorageSlots + 9) "$family agrees with server capacity"
    Server stress-seed | Out-Null
    Harness click $ids[0] 0 1 | Out-Null
    $stress = Server stress-check
    $client = Snapshot "$family-stress"
    Check ((MainSignature $client) -eq (MainSignature $stress.inventory)) "$family hotbar quick-move policy preserves exact client/server contents"
    $output = Server output-seed
    Harness click $output.outputId 0 1 | Out-Null
    $server = Server output-check
    $client = Snapshot "$family-output"
    Check ((MainSignature $client) -eq (MainSignature $server.inventory)) "$family machine output reaches offscreen final extra and synchronizes"
    $before = MainSignature $client
    $geometry = Harness scroll-info
    Check ($geometry -match 'actual=(\d+).*visible=(\d+)') "$family exposes its native viewport dimensions"
    $targetScroll = [math]::Max(0, [int]$Matches[1] - [int]$Matches[2])
    $events = if ($family -eq 'mui1') { [int][math]::Ceiling($targetScroll / 6.0) } else { [int][math]::Ceiling($targetScroll / 18.0) }
    for ($i = 0; $i -lt $events; $i++) { Harness input-wheel $ids[9] -1 | Out-Null }
    $scrollDeadline = [datetime]::UtcNow.AddSeconds(3)
    do {
        $scroll = Harness scroll-info
        if ($scroll -match 'scroll=(\d+)' -and [int]$Matches[1] -ge $targetScroll) { break }
        Start-Sleep -Milliseconds 150
    } while ([datetime]::UtcNow -lt $scrollDeadline)
    Check ($scroll -match 'scroll=(\d+)' -and [int]$Matches[1] -ge $targetScroll) "$family real wheel input reaches the final rows"
    Harness input-click $ids[9] | Out-Null
    $held = Snapshot "$family-held"
    Check $held.cursorOccupied "$family visible scrolled slot accepts real mouse pickup"
    Harness input-click $ids[9] | Out-Null
    $after = Snapshot "$family-returned"
    Check (!$after.cursorOccupied -and (MainSignature $after) -eq $before) "$family real mouse placement returns exact item contents"
    SaveView "container-$family-scrolled"
    foreach ($scale in @(1,2,3,0)) {
        Harness scale $scale | Out-Null
        Harness foreign-check | Out-Null
        if ($scale -eq 1) {
            $expanded = Harness scroll-info
            Check ($expanded -match 'actual=(\d+).*visible=(\d+)' -and [int]$Matches[1] -eq [int]$Matches[2]) "$family stretches vertically to show all rows when the scaled window fits"
            Harness input-click $ids[-1] | Out-Null
            $held = Snapshot "$family-expanded-last-held"
            Check $held.cursorOccupied "$family expanded final row accepts real mouse pickup"
            Harness input-click $ids[-1] | Out-Null
            $restored = Snapshot "$family-expanded-last-returned"
            Check (!$restored.cursorOccupied -and (MainSignature $restored) -eq $before) "$family expanded final row placement preserves exact contents"
            SaveView "container-$family-expanded"
        }
        $checks.Add("$family canonical slot set survives GUI scale $scale")
    }
    Harness resume | Out-Null
}
@{success=$true; prototype='0.8.0-dev'; storageSlots=$StorageSlots; checks=$checks; vanillaAssertions=$selftest.passed.Count;
    dedicatedServer=[bool]$ServerGameDirectory; jarSha256=$qualification.artifactSha256;
    developmentArtifactSha256=$qualification.developmentArtifactSha256;
    sourceLaunchId=$qualification.launchId; sourceLaunchProfile=$qualification.launchProfile;
    sourceLaunchManifest=$qualification.launchManifest; sourceLaunchManifestSha256=$qualification.launchManifestSha256;
    serverLaunchId=$qualification.serverLaunchId; serverLaunchManifest=$qualification.serverLaunchManifest;
    serverLaunchManifestSha256=$qualification.serverLaunchManifestSha256;
    serverArtifactSha256=$qualification.serverArtifactSha256;
    serverDevelopmentArtifactSha256=$qualification.serverDevelopmentArtifactSha256;
    provenanceScope='immutable-source-launch-record';
    capturedAtUtc=[datetime]::UtcNow.ToString('o');
    scope='Vanilla containers and stock MUI1/MUI2 public bindings; actual full-pack adapters require separate qualification'} |
    ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $verificationPath -Encoding UTF8
Write-Host "PASS container adapter qualification: $($checks.Count) checks and $($selftest.passed.Count) vanilla assertions."
