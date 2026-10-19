param(
    [int] $Port = 5007,
    [ValidateSet('Developer','Observer')] [string] $PlayerName = 'Developer',
    [string] $GameDirectory = 'run/rc2-integration/client-developer',
    [string] $EvidenceDirectory = 'docs/qualification/stabilization/pick-block',
    [ValidateSet('isolated','rc2-integration')] [string] $Profile = 'rc2-integration',
    [string] $PeerMetadataPath = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$verification = Join-Path $EvidenceDirectory 'verification.json'
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
$launchEvidence = Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port -GameDirectory $GameDirectory `
    -Profile $Profile -EvidenceDirectory $EvidenceDirectory -PeerMetadataPath $PeerMetadataPath
$checks = [Collections.Generic.List[string]]::new()
function Harness {
    & $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
}
function FreshReport([string] $Pattern, [datetime] $Since) {
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    do {
        $file = Get-ChildItem -LiteralPath $diagnostics -Filter $Pattern |
            Where-Object LastWriteTimeUtc -gt $Since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($file) { return $file }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No fresh $Pattern"
}
function Snapshot([string] $Label, [switch] $Server) {
    $since = [datetime]::UtcNow
    if ($Server) { Harness command '/dinvbaseline' }
    else { Harness snapshot "pick-block-$Label" }
    $file = FreshReport $(if ($Server) { 'server-*.json' } else { 'client-*.json' }) $since
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
}
function Signature($Slot) {
    return $Slot | Select-Object item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function AssertPick([string] $Label, $Before, [int] $ChangedIndex) {
    $server = Snapshot "$Label-server" -Server
    $client = Snapshot "$Label-client"
    Check ($server.mainInventory.Count -eq $Before.mainInventory.Count) "$Label preserves capacity"
    for ($raw = 0; $raw -lt $server.mainInventory.Count; $raw++) {
        $actual = $server.mainInventory[$raw]
        Check ((Signature $actual) -eq (Signature $client.mainInventory[$raw])) "$Label raw $raw agrees across client and server"
        if ($raw -eq $ChangedIndex) {
            Check ($actual.item -eq 'minecraft:diamond_block' -and $actual.count -eq 1) "$Label reaches the intended hotbar slot"
        } else {
            Check ((Signature $actual) -eq (Signature $Before.mainInventory[$raw])) "$Label leaves raw $raw unchanged"
        }
    }
}

Harness require-qualification
Harness background
Harness resume
Harness command '/gamemode creative'
Harness command "/tp $PlayerName 0.5 6 0.5"
# The fresh world's random spawn can be outside chunk 0. Wait for the teleport's
# chunk load before placing the target; an air pick would exercise no packet.
Start-Sleep -Milliseconds 1000
Harness command '/setblock 0 6 0 minecraft:air'
Harness command '/setblock 0 7 0 minecraft:air'
Harness command '/setblock 0 5 0 minecraft:diamond_block'
Start-Sleep -Milliseconds 500
foreach ($selected in @(0,4,8)) {
    $since = [datetime]::UtcNow
    Harness command '/dinvqualify pick-seed'
    $seed = Join-Path $diagnostics "qualification/$PlayerName-server-pick-seed.json"
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    while (!(Test-Path -LiteralPath $seed) -or (Get-Item -LiteralPath $seed).LastWriteTimeUtc -le $since) {
        if ([datetime]::UtcNow -ge $deadline) { throw 'No fresh pick-block seed report' }
        Start-Sleep -Milliseconds 100
    }
    $before = Get-Content -LiteralPath $seed -Raw | ConvertFrom-Json
    Check ([bool]$before.success) "Seed for selected hotbar $selected succeeded"
    Start-Sleep -Milliseconds 200
    Harness pick-block 0 5 0 $selected
    AssertPick "full-hotbar-$selected" $before.inventory $selected
}
# Forge chooses the first empty hotbar slot, regardless of the previous selection.
Harness command "/clear $PlayerName"
Start-Sleep -Milliseconds 300
$empty = Snapshot 'empty-before' -Server
Harness pick-block 0 5 0 8
AssertPick 'empty-hotbar' $empty 0

# Picking an item already on the hotbar should select it without altering any stack.
$existing = Snapshot 'existing-before' -Server
Harness pick-block 0 5 0 4
AssertPick 'existing-hotbar' $existing -1
# The harness reopens this disposable flat world at y=4. Remove the elevated pick
# target before leaving, otherwise the next run can spawn inside that solid block.
Harness command '/setblock 0 5 0 minecraft:air'
Harness command "/tp $PlayerName 0.5 4 0.5"
Harness command '/gamemode survival'

$report = @{
    success = $true; scope = 'source-integrated'; suite = 'creative-pick-block'
    capturedAtUtc = [datetime]::UtcNow.ToString('o'); checks = @($checks); checkCount = $checks.Count
    creativeInventoryScreenQualified = $false
}
foreach ($key in $launchEvidence.Keys) { $report[$key] = $launchEvidence[$key] }
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $verification
Write-Host "Creative pick-block PASS: $($checks.Count) checks"
