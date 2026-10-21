param(
    [int] $Port = 5005,
    [string] $GameDirectory = "$env:APPDATA/PrismLauncher/instances/GTNH-Dynamic-Inventory-Dev-RC2/.minecraft",
    [string] $EvidenceDirectory = 'docs/qualification/glass-ui/container-frames',
    [string] $WorldName = 'DI-Baseline-Containers-RC2-Qualification'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$java = 'C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$screenshots = Join-Path $GameDirectory 'screenshots'
$clientConfig = Join-Path $GameDirectory 'config/dynamicinventory-client.cfg'
$marker = Join-Path (Split-Path $GameDirectory -Parent) '.dynamicinventory-dev-instance'
$packTarget = (Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).version
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
    if (!$file) { throw "No fresh container-frame snapshot: $Label" }
    Copy-Item $file.FullName (Join-Path $EvidenceDirectory "$Label.json") -Force
    return Get-Content $file.FullName -Raw | ConvertFrom-Json
}
function MainSignature($State) {
    return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function ContainerSignature($State) {
    return ($State.activeContainer.slots | Select-Object containerSlot,inventoryIndex,inventoryClass,slotClass,role,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)
}
function Set-ContainerMode([bool] $Compact) {
    $wanted = if ($Compact) { 'true' } else { 'false' }
    Harness resume | Out-Null
    Harness capture container-frames-mode | Out-Null
    Harness layout-control 100 | Out-Null
    $current = Harness machine-mode
    if ($current -notmatch "compactNativePanels=$wanted") {
        Harness layout-control 111 | Out-Null
        $current = Harness machine-mode
    }
    Check ($current -match "compactNativePanels=$wanted") "Container layout selects $(if ($Compact) {'Compact'} else {'Full box'})"
    if (!(Test-Path -LiteralPath $clientConfig)) { throw "Missing client config: $clientConfig" }
    $config = Get-Content -LiteralPath $clientConfig -Raw
    Check ($config -match "(?i)B:compactNativePanels=$wanted\b") "Client config persists compactNativePanels=$wanted"
    Harness layout-control 108 | Out-Null
    Harness resume | Out-Null
}
function Open-Family([string] $Family) {
    Harness resume | Out-Null
    Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
    Harness command '/setblock 2 4 0 minecraft:air' | Out-Null
    switch ($Family) {
        'chest' {
            Harness command '/setblock 2 4 0 minecraft:chest' | Out-Null
            Harness block-open 2 4 0 | Out-Null
        }
        'galacticraft' {
            Harness capture gc-tab | Out-Null
            Harness tab-click micdoodle8.mods.galacticraft.core.client.gui.screen.InventoryTabGalacticraft | Out-Null
        }
        'tinkers-compressed' {
            Harness command '/setblock 1 4 0 avaritiaddons:CompressedChest' | Out-Null
            Harness command '/setblock 2 4 0 TConstruct:CraftingStation' | Out-Null
            Harness block-open 2 4 0 | Out-Null
        }
        default { throw "Unknown container family: $Family" }
    }
}
function Test-Family([string] $Family, [string] $Mode) {
    Open-Family $Family
    $mapping = Harness foreign-check
    if ($mapping -notmatch 'foreign=([^ ]+) count=(\d+)') { throw "$Family did not report its native container type and slot count" }
    $containerType = $Matches[1]
    $slotCount = [int] $Matches[2]
    $guiMatch = [regex]::Match($mapping, '(?m)^GUI=([^\r\n]+)')
    if (!$guiMatch.Success) { throw "$Family did not report its native GUI type" }
    $guiType = $guiMatch.Groups[1].Value.Trim()
    switch ($Family) {
        'chest' {
            Check ($containerType -eq 'net.minecraft.inventory.ContainerChest' -and $guiType -eq 'net.minecraft.client.gui.inventory.GuiChest') `
                'Chest opens the native ContainerChest/GuiChest family'
            Check ($slotCount -eq 108) 'Chest contains its native 27 slots plus all 81 player slots'
        }
        'galacticraft' {
            Check ($containerType -eq 'micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory' `
                -and $guiType -eq 'micdoodle8.mods.galacticraft.core.client.gui.container.GuiExtendedInventory') `
                'Galacticraft tab opens its native extended inventory container and GUI'
            Check ($slotCount -eq 95) 'Galacticraft container retains 50 native slots plus 45 appended player slots'
        }
        'tinkers-compressed' {
            Check ($containerType -eq 'tconstruct.tools.inventory.CraftingStationContainer' `
                -and $guiType -eq 'tconstruct.tools.gui.CraftingStationGui') `
                'Compressed chest opens through the native Tinkers Crafting Station container and GUI'
            Check ($slotCount -gt 200) 'Tinkers station has the compressed chest attached (native plus player slots exceed 200)'
        }
    }
    Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$Family exposes the canonical ordinary slot mapping in $Mode mode"
    $ids = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
    Check ($ids.Count -eq 81) "$Family exposes all 81 canonical ordinary slots in $Mode mode"
    $before = Snapshot "$Family-$Mode-before"
    Check (!$before.cursorOccupied) "$Family starts $Mode mode with an empty cursor"
    $auditOutput = Harness ui-audit
    $auditPathMatch = [regex]::Match($auditOutput, '(?m)^uiAudit=(.+)$')
    if (!$auditPathMatch.Success) { throw "No saved audit for $Family" }
    $auditPath = $auditPathMatch.Groups[1].Value.Trim()
    $audit = Get-Content -LiteralPath $auditPath -Raw | ConvertFrom-Json
    Check ($audit.candidate -and [bool]$audit.adapterId -and @($audit.playerSlots | Where-Object ordinaryIndex -ge 0).Count -eq 81) `
        "$Family live production audit identifies all ordinary slots and its registered adapter in $Mode mode"
    Copy-Item $auditPath (Join-Path $EvidenceDirectory "$Family-$Mode-audit.json") -Force
    & ./scripts/tools/New-InventoryUiAdapter.ps1 -AuditPath $auditPath -OutputDirectory ".setup/ui-api-scaffolds/pack-$Family-$Mode" -Force | Out-Null
    $checks.Add("$Family live production audit is accepted by the scaffold generator in $Mode mode")

    foreach ($scale in @(1, 2, 3, 0)) {
        Harness scale $scale | Out-Null
        $mapping = Harness foreign-check
        Check ($mapping -match 'ordinaryIds=\[([^\]]+)\]') "$Family retains canonical slots at GUI scale $scale in $Mode mode"
        $scaleIds = @($Matches[1].Split(',') | ForEach-Object { [int] $_.Trim() })
        Check ($scaleIds.Count -eq 81) "$Family retains all 81 ordinary slots at GUI scale $scale in $Mode mode"
        $afterScale = Snapshot "$Family-$Mode-scale-$scale"
        Check (!$afterScale.cursorOccupied -and (MainSignature $afterScale) -eq (MainSignature $before)) `
            "$Family scale $scale preserves exact player contents and empty cursor in $Mode mode"
        if ($Family -eq 'galacticraft' -and $Mode -eq 'full-box') {
            $output = Harness button-geometry
            $match = [regex]::Match($output, '(?m)^buttonGeometry=(\{.+\})$')
            if (!$match.Success) { throw 'Missing Galacticraft tab geometry' }
            $geometry = $match.Groups[1].Value | ConvertFrom-Json
            $tabs = @($geometry.buttons | Where-Object { $_.visible -and $_.class -match 'InventoryTab|de\.eydamos\.backpack\.gui\.tab\.' })
            Check ($tabs.Count -ge 2) "Galacticraft exposes its native tabs at scale $scale"
            for ($tab=0; $tab -lt $tabs.Count; $tab++) {
                Check ($tabs[$tab].xPosition -eq $geometry.guiLeft + $tab*28 -and $tabs[$tab].yPosition -eq $geometry.guiTop) `
                    "Galacticraft tab $tab follows the expanded outer frame at scale $scale"
            }
        }
        if ($Family -eq 'tinkers-compressed') {
            $chestSlots = @($afterScale.activeContainer.slots | Where-Object {
                $_.slotClass -match 'ChestSlot' -and $_.inventoryIndex -eq 0
            } | Sort-Object containerSlot)
            if ($chestSlots.Count -ne 1) { throw "Expected one first Tinkers ChestSlot at scale $scale; found $($chestSlots.Count)" }
            $chestSlot = $chestSlots[0]
            Check ($chestSlot.containerSlot -eq 46) 'First compressed-chest slot remains native container slot 46'
            Check ($chestSlot.x -ge 0 -and $chestSlot.y -ge 0 -and $chestSlot.x -lt 3000 -and $chestSlot.y -lt 2000) `
                "First compressed-chest slot has bounded visible coordinates at GUI scale $scale in $Mode mode"
            $chestFilename = "step3-container-frames-$Mode-$Family-scale-$scale-chest-slot.png"
            Harness clear-chat | Out-Null
            Harness screenshot-hover $chestSlot.containerSlot $chestFilename | Out-Null
            $chestScreenshot = Join-Path $screenshots $chestFilename
            if (!(Test-Path -LiteralPath $chestScreenshot)) { throw "Missing chest-slot hover screenshot: $chestScreenshot" }
            Copy-Item $chestScreenshot (Join-Path $EvidenceDirectory $chestFilename) -Force
            $chestStack = $before.activeContainer.slots | Where-Object {
                $_.containerSlot -eq $chestSlot.containerSlot
            } | Select-Object -First 1
            if (!$before.mainInventory[0].item -or $chestStack.item) {
                throw 'Queued compressed-chest round trip requires a populated first hotbar slot and an empty first chest slot'
            }
            $initialContainer = ContainerSignature $before
            Harness input-click $ids[0] | Out-Null
            $pickedUp = Snapshot "$Family-$Mode-scale-$scale-picked-up"
            Check ($pickedUp.cursorOccupied) "$Family queued pickup holds the first hotbar stack at GUI scale $scale"
            Harness input-click $chestSlot.containerSlot | Out-Null
            $placed = Snapshot "$Family-$Mode-scale-$scale-placed-in-chest"
            Check (!$placed.cursorOccupied -and $placed.activeContainer.slots[$chestSlot.containerSlot].item -eq $before.mainInventory[0].item) `
                "$Family queued pickup places the expected item into the first chest slot at GUI scale $scale"
            Harness input-click $chestSlot.containerSlot | Out-Null
            Harness input-click $ids[0] | Out-Null
            $returned = Snapshot "$Family-$Mode-scale-$scale-round-trip"
            Check (!$returned.cursorOccupied -and (MainSignature $returned) -eq (MainSignature $before) `
                -and (ContainerSignature $returned) -eq $initialContainer) `
                "$Family queued chest round trip restores player and chest contents with an empty cursor at GUI scale $scale"
        }
        $filename = "step3-container-frames-$Mode-$Family-scale-$scale.png"
        Harness clear-chat | Out-Null
        Harness screenshot-hover $scaleIds[0] $filename | Out-Null
        $screenshot = Join-Path $screenshots $filename
        if (!(Test-Path -LiteralPath $screenshot)) { throw "Missing hover screenshot: $screenshot" }
        Copy-Item $screenshot (Join-Path $EvidenceDirectory $filename) -Force
        $checks.Add("$Family first ordinary slot has hover screenshot at GUI scale $scale in $Mode mode")
    }
    Harness resume | Out-Null
}

Harness require-qualification $WorldName | Out-Null
Harness background | Out-Null
Harness resume | Out-Null
Harness command '/tp @p 0 4 0' | Out-Null
Harness command '/setblock 1 4 0 minecraft:air' | Out-Null
Harness command '/setblock 2 4 0 minecraft:air' | Out-Null

$families = @('chest', 'galacticraft', 'tinkers-compressed')
$modes = @(
    @{ name = 'full-box'; compact = $false },
    @{ name = 'compact'; compact = $true }
)
foreach ($mode in $modes) {
    Set-ContainerMode ([bool] $mode.compact)
    foreach ($family in $families) { Test-Family $family $mode.name }
}

# Restore Full box through the real dialog control so the saved preference and UI agree.
Set-ContainerMode $false
$installedJar = Join-Path $GameDirectory 'mods/dynamicinventory-0.8.0-dev.jar'
if (!(Test-Path -LiteralPath $installedJar)) { throw "Missing installed production JAR: $installedJar" }
@{
    success = $true
    prototype = '0.8.0-dev'
    packTarget = $packTarget
    modes = @('full-box', 'compact')
    families = $families
    scales = @(1, 2, 3, 0)
    checks = $checks
    jarSha256 = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
    scope = 'Actual full-pack chest, Galacticraft tab, and adjacent Tinkers compressed-chest/crafting-station frames; frame-mode persistence, GUI bounds, canonical slots, scale-by-scale player inventory and cursor conservation, and hovered first-slot screenshots. Window size is left unchanged.'
} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
Write-Host "PASS full-pack container frames: $($checks.Count) checks."
