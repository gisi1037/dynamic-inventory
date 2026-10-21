param(
    [Parameter(Mandatory)][string] $GameDirectory,
    [int] $Port = 5006,
    [string] $EvidenceDirectory = 'docs/qualification/iron-chests-smoke'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$WorldName = 'DI-Baseline-IronChest-Smoke-Qualification'
$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
if (Test-Path -LiteralPath $reportPath) { throw 'Use a fresh evidence directory; existing smoke evidence is retained' }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()
$nonce = [guid]::NewGuid().ToString('N')
$report = [ordered]@{success=$false; status='running'; world=$WorldName; capturedAtUtc=[datetime]::UtcNow.ToString('o')}
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
    return ($output -join "`n")
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Contents($State) {
    return ([ordered]@{main=@($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical);
        cursor=$State.cursorOccupied; player=@($State.playerContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical);
        active=@($State.activeContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical)} | ConvertTo-Json -Depth 12 -Compress)
}
function Fresh([string] $Side, [datetime] $Since) {
    $deadline = [datetime]::UtcNow.AddSeconds(10)
    do {
        $file = Get-ChildItem -LiteralPath $diagnostics -Filter "$Side-*.json" |
            Where-Object LastWriteTimeUtc -ge $Since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($file) { return $file }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No fresh $Side snapshot"
}
function Snapshot([string] $Label) {
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        $since = [datetime]::UtcNow
        Harness command '/dinvbaseline' | Out-Null
        $serverFile = Fresh server $since
        $since = [datetime]::UtcNow
        Harness snapshot "iron-$Label" | Out-Null
        $clientFile = Fresh client $since
        $server = Get-Content -LiteralPath $serverFile.FullName -Raw | ConvertFrom-Json
        $client = Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
        Copy-Item -LiteralPath $serverFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-server.json") -Force
        Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-client.json") -Force
        $matched = (Contents $server) -eq (Contents $client)
        if (!$matched) { Start-Sleep -Milliseconds 100 }
    } while (!$matched -and [datetime]::UtcNow -lt $deadline)
    Check ($matched -and !$client.cursorOccupied -and !$server.cursorOccupied) "Exact peer items and empty cursor: $Label"
    return $client
}
function Probe($Stack) {
    return $Stack.item -eq 'minecraft:dirt' -and $Stack.nbtCanonical -and $Stack.nbtCanonical.Contains($nonce)
}
function ProbeSource($State) {
    $sources = @($State.mainInventory | Where-Object { Probe $_ })
    Check ($sources.Count -eq 1 -and $sources[0].count -eq 16) 'Exactly one complete tagged dirt probe remains in player storage'
    return $sources[0].inventoryIndex
}
try {
    $marker = Get-Content -LiteralPath (Join-Path (Split-Path $GameDirectory -Parent) '.dynamicinventory-dev-instance') -Raw | ConvertFrom-Json
    Check ($marker.instanceId -eq (Split-Path (Split-Path $GameDirectory -Parent) -Leaf)) 'Marked disposable pack instance required'
    $report.packTarget = $marker.version
    $runtime = Get-Content -LiteralPath (Join-Path $diagnostics 'runtime.json') -Raw | ConvertFrom-Json
    foreach ($id in @('dynamicinventory','IronChest')) {
        $mod = @($runtime.mods | Where-Object id -eq $id)
        Check ($mod.Count -eq 1 -and [IO.Path]::GetFileName($mod[0].source) -eq $mod[0].source) "Runtime identifies installed $id artifact"
        $report["${id}Artifact"] = @{version=$mod[0].version; sha256=(Get-FileHash -LiteralPath (Join-Path $GameDirectory ('mods/' + $mod[0].source))).Hash}
    }
    Check ($report.IronChestArtifact.version -eq '6.1.13') 'Inspected IronChest 6.1.13 is loaded'
    Harness require-qualification $WorldName | Out-Null
    Harness background | Out-Null
    Harness resume | Out-Null
    Harness window 1920 1080 | Out-Null
    Harness capture iron-initial | Out-Null
    Harness scale 2 | Out-Null
    $initial = Snapshot initial
    Check ($initial.ordinarySlots -eq 81 -and $initial.inventoryApiSlots -eq 86 -and !$initial.hasRecoveryData) 'Default 72 storage, Backhand and empty recovery required before smoke seeding'
    # Only the exact disposable world above is permitted to receive destructive fixture setup.
    Harness command '/clear @p' | Out-Null
    Harness command '/difficulty peaceful' | Out-Null
    Harness command '/gamerule doMobSpawning false' | Out-Null
    Harness command ('/give @p minecraft:dirt 16 0 {dinv_ironchest:"' + $nonce + '"}') | Out-Null
    $seeded = Snapshot seeded
    Check ((ProbeSource $seeded) -lt 9) 'Fresh unique dirt stack starts in the hotbar'
    $nei = Get-Content -LiteralPath (Join-Path $GameDirectory 'config/NEI/client.cfg') -Raw
    $wheelEnabled = $nei -match '(?m)^\s*disableMouseScrollTransfer=false\s*$'
    $wheel = if ($nei -match '(?m)^\s*invertMouseScrollTransfer=true\s*$') { 1 } else { -1 }
    $report.neiWheelConfigured = $wheelEnabled
    $tiers = @(
        @('iron',54),@('gold',81),@('diamond',108),@('copper',45),@('steel',72),@('crystal',108),
        @('obsidian',108),@('dirt',1),@('netherite',135),@('dark-steel',135),@('silver',72))
    $report.tiers = @()
    for ($meta = 0; $meta -lt $tiers.Count; $meta++) {
        $name = $tiers[$meta][0]; $native = [int] $tiers[$meta][1]
        $x = 2 + 3 * ($meta % 6); $z = 2 + 3 * [int][Math]::Floor($meta / 6)
        Harness resume | Out-Null
        Harness command "/setblock $x 4 $z IronChest:BlockIronChest $meta keep" | Out-Null
        Harness command ('/tp @p ' + ($x + 0.5) + ' 4 ' + ($z + 2.5)) | Out-Null
        Harness block-open $x 4 $z | Out-Null
        $mapping = Harness foreign-check
        Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$name exposes canonical player mapping"
        $ids = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
        Check ($ids.Count -eq 81 -and @($ids | Sort-Object -Unique).Count -eq 81) "$name has 81 distinct ordinary slot IDs"
        $opened = Snapshot "$name-opened"
        Check ($opened.activeContainer.class -eq 'cpw.mods.ironchest.ContainerIronChest' -and $opened.screen -eq 'cpw.mods.ironchest.client.GUIChest' -and $opened.activeContainer.slotCount -eq ($native + 81)) "$name preserves native tier capacity and exact GUI/container family"
        Check ($opened.activeContainer.slots[0].inventoryIndex -eq 0 -and $opened.activeContainer.slots[$native - 1].inventoryIndex -eq ($native - 1)) "$name preserves first and final native chest indices"
        Check ($opened.activeContainer.slots[$ids[36]].inventoryIndex -eq 37 -and $opened.activeContainer.slots[$ids[80]].inventoryIndex -eq 81) "$name maps first and final added storage boundaries"
        Harness editor-open | Out-Null
        Harness editor-control 101 | Out-Null
        Check ((Contents (Snapshot "$name-editor-cancel")) -eq (Contents $opened)) "$name supports editor open/cancel without item changes"
        # Opening/editor coverage spans every tier. Exercise transfers on the smallest
        # regular chest, the filtered chest and the largest enabled chest to keep this a smoke.
        if ($meta -notin @(3,7,9)) {
            $report.tiers += @{metadata=$meta; name=$name; nativeSlots=$native; totalSlots=$native+81; x=$x; y=4; z=$z; editor=$true; shiftTransfers=$false}
            continue
        }
        if ($meta -eq 7 -and $opened.activeContainer.slots[0].item) {
            Check ($opened.activeContainer.slots[0].item -eq 'minecraft:written_book') 'DirtChest initial native guidebook is removed normally'
            Harness click 0 0 1 | Out-Null
        }
        $before = Snapshot "$name-ready"
        Check (@($before.activeContainer.slots | Select-Object -First $native | Where-Object item).Count -eq 0) "$name chest starts empty after native guidebook removal"
        $raw = ProbeSource $before
        Harness click $ids[$raw] 0 0 | Out-Null
        Harness click $ids[36] 0 0 | Out-Null
        $extra = Snapshot "$name-extra"
        Check ((Probe $extra.mainInventory[37]) -and $extra.mainInventory[37].count -eq 16) "$name moves the whole tagged stack into appended ordinary storage"
        Harness click $ids[36] 0 1 | Out-Null
        $sent = Snapshot "$name-sent"
        Check ((Probe $sent.activeContainer.slots[0]) -and $sent.activeContainer.slots[0].count -eq 16 -and @($sent.mainInventory | Where-Object { Probe $_ }).Count -eq 0) "$name native shift-transfer moves appended source into chest"
        Harness click 0 0 1 | Out-Null
        $returned = Snapshot "$name-returned"
        $returnedRaw = ProbeSource $returned
        $returnedLogical = $returnedRaw - [int]($returnedRaw -gt 36)
        if ($returnedRaw -ne $raw) {
            Check (!$returned.mainInventory[$raw].item) 'Original dirt cell remains empty before normalization'
            Harness click $ids[$returnedLogical] 0 0 | Out-Null
            Harness click $ids[$raw] 0 0 | Out-Null
        }
        Check ((Contents (Snapshot "$name-normalized")) -eq (Contents $before)) "$name two-way native transfer restores exact original item positions and NBT"
        if ($wheelEnabled -and $meta -in @(3,7,9)) {
            Harness input-wheel $ids[$raw] $wheel | Out-Null
            $one = Snapshot "$name-wheel-sent"
            Check ((Probe $one.activeContainer.slots[0]) -and $one.activeContainer.slots[0].count -eq 1 -and $one.mainInventory[$raw].count -eq 15) "$name NEI wheel transfers exactly one tagged dirt item"
            Harness input-wheel 0 $wheel | Out-Null
            Check ((Contents (Snapshot "$name-wheel-returned")) -eq (Contents $before)) "$name reverse wheel restores exact baseline"
        }
        if ($meta -eq 7) {
            Harness command ('/give @p minecraft:diamond 1 0 {dinv_iron_reject:"' + $nonce + '"}') | Out-Null
            $filterBefore = Snapshot dirt-filter-before
            $diamond = @($filterBefore.activeContainer.slots | Where-Object { $_.item -eq 'minecraft:diamond' -and $_.nbtCanonical.Contains($nonce) })
            Check ($diamond.Count -eq 1) 'Unique non-dirt filter probe exists'
            Harness click $diamond[0].containerSlot 0 1 | Out-Null
            Check ((Contents (Snapshot dirt-filter-after)) -eq (Contents $filterBefore)) 'DirtChest rejects non-dirt shift-transfer without losing or moving items'
        }
        $report.tiers += @{metadata=$meta; name=$name; nativeSlots=$native; totalSlots=$native+81; x=$x; y=4; z=$z; editor=$true; shiftTransfers=$true}
    }
    Harness resume | Out-Null
    $report.success = $true; $report.status = 'passed'
} catch {
    $report.status = 'failed'; $report.error = $_.Exception.Message
    throw
} finally {
    $report.checks = $checks.ToArray(); $report.checkCount = $checks.Count
    $report.scope = 'All11 block tiers: opening, canonical boundaries and editor cancel. Copper/Dirt/DarkSteel: two-way appended-slot shift transfers and configured NEI wheel. DirtChest filter rejection.'
    $report.fullPackQualified = $false
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath
}
Write-Host "PASS Iron Chests smoke: $($checks.Count) checks"
