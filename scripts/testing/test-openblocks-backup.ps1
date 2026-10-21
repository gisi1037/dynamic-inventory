param(
    [Parameter(Mandatory)][string] $GameDirectory,
    [Parameter(Mandatory)][string] $WorldName,
    [int] $Port = 5006,
    [ValidateRange(0,8)][int] $ProbeRawIndex = 1,
    [string] $ClasspathManifest = '.setup/inventory-example-classpath.json',
    [string] $EvidenceDirectory = 'docs/qualification/openblocks-backup'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$checks = [Collections.Generic.List[string]]::new()
if ($WorldName -notmatch '^DI-Baseline-[A-Za-z0-9_.-]*Qualification$') { throw 'Disposable qualification world required' }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
$report = [ordered]@{success=$false; status='running'; world=$WorldName; capturedAtUtc=[datetime]::UtcNow.ToString('o')}
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath
function Harness {
    $output = & $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $output | ForEach-Object { Write-Host $_ }
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function FreshReport([string] $Side, [datetime] $Since) {
    $deadline = [datetime]::UtcNow.AddSeconds(10)
    do {
        $file = Get-ChildItem -LiteralPath $diagnostics -Filter "$Side-*.json" |
            Where-Object LastWriteTimeUtc -ge $Since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($file) { return $file }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    throw "No fresh $Side snapshot"
}
function Contents($State) {
    return ([ordered]@{main=@($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical);
        cursor=$State.cursorOccupied; recovery=$State.hasRecoveryData;
        player=@($State.playerContainer.slots | Select-Object containerSlot,inventoryIndex,item,count,damage,nbtCanonical);
        active=@($State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,item,count,damage,nbtCanonical)} |
        ConvertTo-Json -Depth 12 -Compress)
}
function Snapshot([string] $Label) {
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    do {
        $since = [datetime]::UtcNow
        Harness command '/dinvbaseline'
        $serverFile = FreshReport server $since
        $since = [datetime]::UtcNow
        Harness snapshot "openblocks-$Label"
        $clientFile = FreshReport client $since
        $server = Get-Content -LiteralPath $serverFile.FullName -Raw | ConvertFrom-Json
        $client = Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
        Copy-Item -LiteralPath $serverFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-server.json") -Force
        Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-client.json") -Force
        $matched = (Contents $server) -eq (Contents $client)
        if (!$matched) { Start-Sleep -Milliseconds 150 }
    } while (!$matched -and [datetime]::UtcNow -lt $deadline)
    Check $matched "Exact client/server contents agree at $Label"
    Check (!$server.cursorOccupied -and !$client.cursorOccupied) "Cursor empty at $Label"
    return $client
}
function PlayerSlot($State, [int] $Raw) {
    $slots = @($State.activeContainer.slots | Where-Object {
        $_.role -in @('hotbar','storage','additional-storage') -and $_.inventoryIndex -eq $Raw
    })
    if ($slots.Count -ne 1) { throw "Ambiguous player raw index $Raw" }
    return $slots[0]
}
function SameItem($Stack) {
    return $Stack.item -eq $probe.item -and $Stack.damage -eq $probe.damage -and $Stack.nbtCanonical -eq $probe.nbtCanonical
}
function NewLogText([string] $Path, [long] $Offset) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        if ($stream.Length -lt $Offset) { throw 'Game log rotated during negative restore; cannot establish fresh evidence' }
        [void] $stream.Seek($Offset, [IO.SeekOrigin]::Begin)
        $reader = [IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}
try {
    $instance = Split-Path $GameDirectory -Parent
    $marker = Get-Content -LiteralPath (Join-Path $instance '.dynamicinventory-dev-instance') -Raw | ConvertFrom-Json
    $pins = @('pack-baseline.json','pack-daily-2026-10-19.json' | ForEach-Object {
        Get-Content -LiteralPath $_ -Raw | ConvertFrom-Json
    } | Where-Object { $_.instanceId -eq $marker.instanceId -and $_.archiveSha256 -eq $marker.archiveSha256 })
    Check ($pins.Count -eq 1 -and (Split-Path $instance -Leaf) -eq $marker.instanceId) 'Project marker matches a pinned disposable pack instance'
    $report.packTarget = $pins[0].version
    $runtimeFile = Get-Item -LiteralPath (Join-Path $diagnostics 'runtime.json')
    $runtime = Get-Content -LiteralPath $runtimeFile.FullName -Raw | ConvertFrom-Json
    foreach ($modId in @('dynamicinventory','OpenBlocks')) {
        $mod = @($runtime.mods | Where-Object id -eq $modId)
        Check ($mod.Count -eq 1 -and [IO.Path]::GetFileName($mod[0].source) -eq $mod[0].source) "Runtime identifies one installed $modId artifact"
        $jar = Get-Item -LiteralPath (Join-Path $GameDirectory ('mods/' + $mod[0].source))
        Check ($jar.LastWriteTimeUtc -le $runtimeFile.LastWriteTimeUtc) "$modId artifact predates the running startup manifest"
        $report["${modId}Artifact"] = @{file=$jar.FullName; sha256=(Get-FileHash -LiteralPath $jar.FullName).Hash; version=$mod[0].version}
    }
    Harness require-qualification $WorldName
    Harness background
    Harness capture openblocks-backup
    Harness window 1920 1080
    Harness scale 2
    $before = Snapshot before
    Check ($before.screen -eq 'dev.gilbe.dynamicinventory.client.PlayerInventoryScreen' -and $before.activeContainer.windowId -eq 0) 'Actual player GUI is open on the persistent player window'
    Check ($before.expandedPlayerLayoutMatches -and $before.ordinarySlots -gt 36 -and $before.offhandIndex -eq 36) 'Expanded ordinary storage and fixed Backhand offhand are active'
    Check ($before.inventoryApiSlots -eq 86) 'This bounded backup fixture targets the default 82 raw main plus four armor slots'
    Check ($before.player -match '^[A-Za-z0-9_]{1,16}$') 'Snapshot supplies a safe actual player identity'
    $probe = $before.mainInventory[$ProbeRawIndex]
    $lastRaw = $before.mainInventory.Count - 1
    Check ($probe.item -and @($before.mainInventory | Where-Object { SameItem $_ }).Count -eq 1 -and !$before.mainInventory[$lastRaw].item) 'Unique existing hotbar stack and empty final expanded slot are available'
    $source = PlayerSlot $before $ProbeRawIndex
    $destination = PlayerSlot $before $lastRaw
    if ($destination.x -lt 0 -or $destination.y -lt 0) {
        Harness scrollbar-bottom
        $before = Snapshot before
        $source = PlayerSlot $before $ProbeRawIndex
        $destination = PlayerSlot $before $lastRaw
    }
    Check ($source.x -ge 0 -and $source.y -ge 0 -and $destination.x -ge 0 -and $destination.y -ge 0) 'Both real click targets are visible'
    # OpenBlocks uses ISaveHandler.getMapFileFromName: world/data/inventory-...dat.
    $data = Join-Path $GameDirectory "saves/$WorldName/data"
    Check (Test-Path -LiteralPath $data -PathType Container) 'Requested integrated world data directory exists'
    $pattern = "inventory-$($before.player)-*-command-*.dat"
    $existing = @(Get-ChildItem -LiteralPath $data -Filter $pattern | ForEach-Object Name)
    Harness command "/ob_inventory store $($before.player)"
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    do {
        $fresh = @(Get-ChildItem -LiteralPath $data -Filter $pattern | Where-Object { $_.Name -notin $existing })
        if ($fresh.Count -eq 1 -and $fresh[0].Length -gt 0) { break }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $deadline)
    Check ($fresh.Count -eq 1 -and $fresh[0].Length -gt 0) 'Actual OpenBlocks command creates exactly one native disk backup'
    Check ((Contents (Snapshot stored)) -eq (Contents $before)) 'Disk store leaves complete baseline items unchanged before mutation'
    $dump = $fresh[0]
    $dumpId = $dump.BaseName.Substring('inventory-'.Length)
    $report.dump = @{path=$dump.FullName; sha256=(Get-FileHash -LiteralPath $dump.FullName).Hash; id=$dumpId}
    $report.recoveryCommand = "/ob_inventory restore $($before.player) $dumpId"
    Copy-Item -LiteralPath $dump.FullName -Destination (Join-Path $EvidenceDirectory $dump.Name)
    Harness input-click $source.containerSlot
    Harness input-click $destination.containerSlot
    $moved = Snapshot moved
    Check (!$moved.mainInventory[$ProbeRawIndex].item -and (SameItem $moved.mainInventory[$lastRaw]) -and $moved.mainInventory[$lastRaw].count -eq $probe.count) 'Actual mouse input moves the exact existing stack into final expanded storage'
    Check ((Contents $moved) -ne (Contents $before)) 'Restore starts from a deliberately changed inventory'
    Harness command $report.recoveryCommand
    $restored = Snapshot restored
    Check ((Contents $restored) -eq (Contents $before)) 'Actual disk restore recovers all exact baseline main, offhand, armor, equipment and cursor contents'
    Check ((Get-FileHash -LiteralPath $dump.FullName).Hash -eq $report.dump.sha256) 'Native recovery backup remains unchanged on disk'
    # No mismatch command is allowed without proof that this exact running class contains the production guard.
    Harness openblocks-guard-info
    $report.productionMismatchGuard = $true
    $exported = Get-Content -LiteralPath $ClasspathManifest -Raw | ConvertFrom-Json
    $classpath = @($exported.classpath -split [IO.Path]::PathSeparator | Where-Object {
        $_ -and [IO.Path]::GetFileName($_) -notlike 'jabel-javac-plugin-*'
    }) -join [IO.Path]::PathSeparator
    Check (![string]::IsNullOrWhiteSpace($classpath)) 'Exported offline Minecraft NBT classpath is available'
    $helper = Join-Path '.setup' ('openblocks-dump-fixture-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $helper | Out-Null
    & 'C:/Program Files/Java/jdk-25/bin/javac.exe' -proc:none -cp $classpath -d $helper scripts/testing/OpenBlocksDumpFixture.java
    if ($LASTEXITCODE -ne 0) { throw 'Offline NBT helper compilation failed' }
    $fixtures = @(& $java -cp ($helper + [IO.Path]::PathSeparator + $classpath) OpenBlocksDumpFixture $dump.FullName 86)
    if ($LASTEXITCODE -ne 0) { throw 'Offline NBT negative dump generation failed' }
    $fixtures | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'negative-dumps.txt')
    Check ($fixtures.Count -eq 2) 'Offline helper verified exactly two fresh dumps with only Inventory.size changed'
    $report.negativeDumps = @()
    foreach ($fixture in $fixtures) {
        Check ($fixture -match '^(41|87)\t(inventory-dinv-negative-[a-f0-9-]+-(41|87)\.dat)$') 'Negative dump has expected unique filename and bounded size'
        $size = [int] $Matches[1]
        $name = $Matches[2]
        $negativePath = Join-Path $data $name
        $negativeId = [IO.Path]::GetFileNameWithoutExtension($name).Substring('inventory-'.Length)
        # Forge's full log includes mod WARNs; latest.log filters these refusal messages.
        $log = Join-Path $GameDirectory 'logs/fml-client-latest.log'
        $offset = (Get-Item -LiteralPath $log).Length
        Harness command "/ob_inventory restore $($before.player) $negativeId"
        $unchanged = Snapshot "rejected-$size"
        Check ((Contents $unchanged) -eq (Contents $before)) "Rejected $size-slot backup leaves exact client/server baseline and cursor unchanged"
        $deadline = [datetime]::UtcNow.AddSeconds(10)
        do {
            $newLog = NewLogText $log $offset
            $evidence = @($newLog -split "`n" | Where-Object {
                $_.Contains($negativeId) -and $_.Contains("inventory size $size differs from 86") -and
                    $_.Contains('Cannot restore OpenBlocks backup')
            })
            if ($evidence.Count) { break }
            Start-Sleep -Milliseconds 100
        } while ([datetime]::UtcNow -lt $deadline)
        $newLog | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "rejected-$size-log.txt")
        Check ($evidence.Count -gt 0) "Fresh game log proves production guard rejected this exact $size-slot disk dump"
        $report.negativeDumps += @{size=$size; path=$negativePath; sha256=(Get-FileHash -LiteralPath $negativePath).Hash; guardMessage=$evidence[0]}
    }
    Check ((Get-FileHash -LiteralPath $dump.FullName).Hash -eq $report.dump.sha256) 'Negative fixture generation and rejection preserve the native recovery backup'
    $report.success = $true
    $report.status = 'passed'
} catch {
    # Keep actual game contents and the native recovery dump for diagnosis; never guess a rollback.
    $report.status = 'failed'
    $report.error = $_.Exception.Message
    throw
} finally {
    $report.checks = $checks.ToArray()
    $report.checkCount = $checks.Count
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report.scope = 'Actual 86-slot OpenBlocks disk restore after real mouse movement; actual guarded rejection of 41/87-slot dumps with exact unchanged snapshots and unique fresh log evidence'
    $report.pending = @('Actual grave death, reload and reclaim','OpenMods signed-byte boundary for inventory API size above 128')
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath
}
Write-Host "PASS actual OpenBlocks backup: $($checks.Count) checks"
