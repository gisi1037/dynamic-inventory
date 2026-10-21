param(
    [Parameter(Mandatory)][string] $GameDirectory,
    [int] $Port = 5006,
    [string] $EvidenceDirectory = '.setup/iron-grave-smoke/runtime'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$world = 'DI-Baseline-Large-Grave-Smoke-2-Qualification'
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()
$report = [ordered]@{ success=$false; status='running'; world=$world; fullPackQualified=$false }
$reportPath = Join-Path $EvidenceDirectory 'verification.json'
$report | ConvertTo-Json | Set-Content -LiteralPath $reportPath
function Harness {
    $lines = @(& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' `
        '-Ddinv.packInput=true' -cp .setup StorageHarness @args)
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $args" }
    $lines | ForEach-Object { Write-Host $_ }
    return $lines
}
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Signature($State) {
    return (@($State.playerContainer.slots | Where-Object { $_.item } |
        Select-Object containerSlot,inventoryIndex,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 12 -Compress)
}
function Probes($State) {
    return (@($State.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' } |
        Sort-Object inventoryIndex | Select-Object inventoryIndex,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 12 -Compress)
}
function Snapshot([string] $Label) {
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    do {
        $since = [datetime]::UtcNow
        $null = Harness command '/dinvbaseline'
        $null = Harness snapshot "large-grave-$Label"
        $states = @{}
        foreach ($side in @('server','client')) {
            $file = Get-ChildItem -LiteralPath $diagnostics -Filter "$side-*.json" |
                Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if (!$file) { throw "Missing fresh $side snapshot" }
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $EvidenceDirectory "$Label-$side.json") -Force
            $states[$side] = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        }
        $same = (Signature $states.server) -eq (Signature $states.client)
    } while (!$same -and [datetime]::UtcNow -lt $deadline)
    Check $same "Exact client/server items agree at $Label"
    Check (!$states.client.cursorOccupied -and !$states.server.cursorOccupied) "Cursor empty at $Label"
    Check (!$states.client.hasRecoveryData -and !$states.server.hasRecoveryData) "No recovery at $Label"
    return $states.client
}
function Grave([string] $Label) {
    $lines = Harness grave-info 100 6 0
    $lines | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$Label-grave.txt")
    $raw = @($lines | Where-Object { $_ -like 'graveNbt=*' })[0].Substring(9)
    return ($raw | ConvertFrom-Json)
}
try {
    $null = Harness require-qualification $world
    $null = Harness background
    $null = Harness resume
    foreach ($command in @('/difficulty peaceful','/gamerule doMobSpawning false',
        '/gamerule keepInventory false','/gamerule openblocks:spawn_graves true','/clear @p',
        '/tp @p 100.5 4 0.5')) {
        $null = Harness command $command
    }
    $null = Harness seed-large-grave
    $null = Harness capture large-grave-seeded
    $seeded = Snapshot seeded
    Check ($seeded.inventoryApiSlots -eq 230) 'Actual cold-start capacity is216 storage plus native roles'
    $probes = @($seeded.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' })
    Check ($probes.Count -eq 130) 'Seeded130 distinct NBT stacks before real death'
    Check ($null -eq (Grave before-death)) 'Fresh world has no existing grave at test site'
    $null = Harness resume
    foreach ($command in @('/setblock 100 5 0 minecraft:stone 0 keep','/tp @p 100.5 6 0.5','/gamemode 0 @p','/kill')) {
        $null = Harness command $command
    }
    Start-Sleep -Seconds 2
    $dead = Grave dead
    Check ($null -ne $dead) 'Actual player death created OpenBlocks grave'
    $items = @($dead.entries.Items.values)
    Check (@($items | Where-Object { $_.entries.tag.entries.dinv_grave_smoke.value -eq '"large-grave-130"' }).Count -eq 130) 'Grave retains every seeded stack'
    Check (@($items | Where-Object { $_.entries.Slot.type -ne 3 }).Count -eq 0) 'Native grave writes integer slot tags'
    foreach ($slot in @(127,128,129)) {
        Check (@($items | Where-Object { $_.entries.Slot.value -eq "$slot" }).Count -eq 1) "Grave has unique slot$slot"
    }
    $null = Harness respawn
    Start-Sleep -Seconds 2
    $null = Harness command '/tp @p 100.5 4 2.5'
    $null = Harness capture large-grave-after-death
    $empty = Snapshot after-death
    Check (@($empty.playerContainer.slots | Where-Object { $_.item -and $_.nbtCanonical -like '*large-grave-130*' }).Count -eq 0) 'Death removed all130 player probes'
    $null = Harness disconnect-safe $world
    $null = Harness world $world
    $null = Harness require-qualification $world
    $loaded = Grave reloaded
    Check (($loaded | ConvertTo-Json -Depth 70 -Compress) -eq ($dead | ConvertTo-Json -Depth 70 -Compress)) 'Full canonical grave NBT survives saved-world reload'
    $null = Harness resume
    $null = Harness hotbar-select 8
    $null = Harness sneak true
    $null = Harness block-open 100 6 0
    $null = Harness sneak false
    $null = Harness capture large-grave-reclaimed
    $reclaimed = Snapshot reclaimed
    Check ((Probes $reclaimed) -eq (Probes $seeded)) 'All130 exact typed-NBT stacks reclaimed to original ordinary indices'
    Check ($null -eq (Grave reclaimed)) 'Native reclaim removed completed grave'
    $report.success = $true
    $report.status = 'passed'
} catch {
    $report.status = 'failed'
    $report.failure = $_.Exception.Message
    throw
} finally {
    $report.checks = $checks
    $report.jarSha256 = (Get-FileHash -LiteralPath (Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar')).Hash
    $report.completedAtUtc = [datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath
}
Write-Host "PASS large-grave smoke: $($checks.Count) checks. Full grave qualification remains pending."
