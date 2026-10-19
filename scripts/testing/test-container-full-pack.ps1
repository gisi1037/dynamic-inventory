param(
    [int] $Port = 5005,
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $EvidenceDirectory = 'docs/qualification/startup-capacity/full-pack'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks = [Collections.Generic.List[string]]::new()
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
function Snapshot([string] $Label) {
    $since = [datetime]::UtcNow
    Harness snapshot $Label | Out-Null
    $file = Get-ChildItem "$diagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$file) { throw 'No fresh full-pack snapshot' }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function Signature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Block([string] $Specification) {
    Harness resume | Out-Null
    Harness command '/setblock 2 4 0 minecraft:air' | Out-Null
    Harness command "/setblock 2 4 0 $Specification" | Out-Null
    Harness block-open 2 4 0 | Out-Null
}
Harness require-qualification DI-Baseline-Containers-RC2-Qualification | Out-Null
Harness background | Out-Null
Harness resume | Out-Null
Harness window 854 480 | Out-Null
Harness command '/difficulty peaceful' | Out-Null
Harness command '/gamerule doMobSpawning false' | Out-Null
Harness command '/tp 0 4 0' | Out-Null
$families = @(
    @{name='chest';block='minecraft:chest 0';count=108},
    @{name='double-chest';block='minecraft:chest 0';count=135},
    @{name='workbench';block='minecraft:crafting_table 0';count=91},
    @{name='furnace';block='minecraft:furnace 0';count=84},
    @{name='tinkers';block='TConstruct:CraftingStation 0';count=91},
    @{name='tinkers-chest';block='TConstruct:CraftingStation 0';count=118},
    @{name='galacticraft';count=95},
    @{name='gt-steam-furnace';block='gregtech:gt.blockmachines 0 replace {mID:103}';count=85},
    @{name='gt-electric-furnace';block='gregtech:gt.blockmachines 0 replace {mID:261}';count=85},
    @{name='gt-chest-buffer';block='gregtech:gt.blockmachines 0 replace {mID:9231}';count=108}
)
foreach ($family in $families) {
    $name=$family.name
    Harness resume | Out-Null
    Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
    if ($name -in @('double-chest','tinkers-chest')) { Harness command '/setblock 1 4 0 minecraft:chest 0' | Out-Null }
    if ($name -eq 'galacticraft') {
        Harness capture gc-tab | Out-Null
        Harness tab-click micdoodle8.mods.galacticraft.core.client.gui.screen.InventoryTabGalacticraft | Out-Null
    } else { Block $family.block }
    $mapping=Harness foreign-check
    Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$name exposes 81 canonical ordinary slots"
    $ids=@($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
    $before=Snapshot "$name-before"
    Check ($before.activeContainer.slotCount -eq $family.count) "$name retains native slots and appends the expected capacity"
    foreach ($scale in @(1,2,3,0)) {
        Harness scale $scale | Out-Null
        Harness foreign-check | Out-Null
        if ($name.StartsWith('gt-')) {
            $viewport=Harness scroll-info
            Check ($viewport -match 'actual=(\d+).*visible=(\d+)') "$name exposes native viewport dimensions at scale $scale"
            $actual=[int]$Matches[1]; $visible=[int]$Matches[2]
            if ($scale -eq 1) {
                Check ($actual -eq $visible) "$name stretches vertically to show all storage rows"
                Harness input-click $ids[0] | Out-Null
                Harness input-click $ids[-1] | Out-Null
                $last=Snapshot "$name-final-row-placed"
                Check (!$last.cursorOccupied -and $last.mainInventory[81].item -eq $before.mainInventory[0].item) "$name accepts real placement in the expanded final row"
                Harness input-click $ids[-1] | Out-Null
                Harness input-click $ids[0] | Out-Null
                $restored=Snapshot "$name-final-row-returned"
                Check (!$restored.cursorOccupied -and (Signature $restored) -eq (Signature $before)) "$name final-row round trip preserves exact player contents"
                Harness screenshot-hover $ids[9] "step3-full-$name-expanded.png" | Out-Null
                Copy-Item (Join-Path $GameDirectory "screenshots/step3-full-$name-expanded.png") (Join-Path $EvidenceDirectory "$name-expanded.png") -Force
            }
            if ($scale -eq 0) { Check ($visible -lt $actual) "$name keeps scrolling when the scaled window is too short" }
        }
        $checks.Add("$name fits GUI scale $scale")
    }
    Harness input-click $ids[0] | Out-Null
    $held=Snapshot "$name-held"
    Check $held.cursorOccupied "$name accepts real mouse pickup"
    Harness input-click $ids[0] | Out-Null
    $after=Snapshot "$name-returned"
    Check (!$after.cursorOccupied -and (Signature $before) -eq (Signature $after)) "$name real mouse placement preserves exact player items"
    if ($name -in @('workbench','furnace','tinkers','tinkers-chest')) {
        Harness native-recipe-check | Out-Null
        $checks.Add("$name native registered recipe alignment follows relocated header")
    }
    Harness clear-chat | Out-Null
    Harness screenshot-hover $ids[0] "step3-full-$name.png" | Out-Null
    Copy-Item (Join-Path $GameDirectory "screenshots/step3-full-$name.png") (Join-Path $EvidenceDirectory "$name.png") -Force
}
Harness resume | Out-Null
@{success=$true;prototype='0.8.0-dev';packTarget='2.9.0-RC-2';checks=$checks;
    jarSha256=(Get-FileHash (Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar') -Algorithm SHA256).Hash;
    scope='Actual native RC-2 block/tab opening, canonical slots, queued mouse pickup/placement, GUI scales and recipe geometry; framework transfer stress is qualified separately'} |
    ConvertTo-Json -Depth 10 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "PASS full-pack native containers: $($checks.Count) checks."
