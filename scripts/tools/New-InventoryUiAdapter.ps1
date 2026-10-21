param(
    [Parameter(Mandatory = $true)]
    [string] $AuditPath,
    [string] $OutputDirectory = '.setup/generated-ui-adapters',
    [string] $GeneratedNamespace = 'dev.gilbe.dynamicinventory.discovered',
    [string] $AdapterName = '',
    [string] $AdapterId = '',
    [switch] $Force,
    [switch] $InsertIntoSource,
    [string] $RegistrationFile = 'src/main/java/dev/gilbe/dynamicinventory/compat/BuiltinInventoryUiAdapters.java'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

function Has-Property($Object, [string] $Name) {
    return $null -ne $Object.PSObject.Properties[$Name]
}

function Assert-Integer($Value, [string] $Name, [int] $Minimum, [int] $Maximum) {
    if ($null -eq $Value -or $Value -isnot [ValueType] -or $Value -is [bool]) {
        throw "$Name must be an integer."
    }
    $number = 0L
    if (![long]::TryParse([string] $Value, [ref] $number) -or $number -lt $Minimum -or $number -gt $Maximum) {
        throw "$Name must be between $Minimum and $Maximum."
    }
    return [int] $number
}

function Assert-JavaClassName([string] $Value, [string] $Name) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt 255 -or
        $Value -notmatch '^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$') {
        throw "$Name must be a fully qualified Java class name."
    }
}

function Assert-IntArray($Value, [string] $Name, [int] $Minimum, [int] $Maximum) {
    if ($Value -isnot [array] -or $Value.Count -gt 4096) { throw "$Name must be an array of at most 4096 integers." }
    $seen = @{}
    foreach ($entry in $Value) {
        $index = Assert-Integer $entry $Name $Minimum $Maximum
        if ($seen.ContainsKey([string] $index)) { throw "$Name contains duplicate value $index." }
        $seen[[string] $index] = $true
    }
}

if (!(Test-Path -LiteralPath $AuditPath -PathType Leaf)) { throw "Audit JSON not found: $AuditPath" }
$auditFullPath = (Resolve-Path -LiteralPath $AuditPath).Path
try {
    $audit = Get-Content -LiteralPath $auditFullPath -Raw | ConvertFrom-Json
} catch {
    throw "Could not parse audit JSON: $($_.Exception.Message)"
}

$required = @(
    'schemaVersion', 'capturedAtUtc', 'packTarget', 'containerClass', 'screenClass', 'framework',
    'slotCount', 'nativeSlotCount', 'adapterId', 'playerSlots', 'missingLegacyIndices',
    'duplicateOrdinaryIndices', 'candidate', 'reasons', 'externalSlotCount'
)
foreach ($name in $required) {
    if (!(Has-Property $audit $name)) { throw "Audit JSON is missing required field '$name'." }
}
foreach ($property in $audit.PSObject.Properties) {
    # Additive schema-1 diagnostic; older captures legitimately omit it.
    if ($property.Name -notin ($required + @('refusalReason'))) { throw "Unsupported audit field '$($property.Name)'; review the schema before generating." }
}

if ((Assert-Integer $audit.schemaVersion 'schemaVersion' 1 1) -ne 1) { throw 'Unsupported audit schema.' }
Assert-JavaClassName ([string] $audit.containerClass) 'containerClass'
Assert-JavaClassName ([string] $audit.screenClass) 'screenClass'
Assert-JavaClassName $GeneratedNamespace 'GeneratedNamespace'
if ($audit.framework -notin @('NATIVE', 'MUI1', 'MUI2')) { throw 'framework must be NATIVE, MUI1, or MUI2.' }
if ([string]::IsNullOrWhiteSpace([string] $audit.packTarget) -or ([string] $audit.packTarget).Length -gt 128) {
    throw 'packTarget must be a non-empty string of at most 128 characters.'
}
if ([string]::IsNullOrWhiteSpace([string] $audit.capturedAtUtc) -or ([string] $audit.capturedAtUtc).Length -gt 64) {
    throw 'capturedAtUtc must be a non-empty timestamp string.'
}
$capturedValue = $audit.capturedAtUtc
if ($capturedValue -is [DateTime]) {
    if ($capturedValue.Kind -ne [DateTimeKind]::Utc) { throw 'capturedAtUtc must be expressed in UTC.' }
    $capturedAtUtcNormalized = $capturedValue.ToUniversalTime().ToString('o')
} else {
    $captured = [DateTimeOffset]::MinValue
    if (![DateTimeOffset]::TryParse([string] $capturedValue, [ref] $captured)) {
        throw 'capturedAtUtc must be a parseable timestamp.'
    }
    if ($captured.Offset -ne [TimeSpan]::Zero) { throw 'capturedAtUtc must be expressed in UTC.' }
    $capturedAtUtcNormalized = $captured.ToString('o')
}

$slotCount = Assert-Integer $audit.slotCount 'slotCount' 1 4096
$externalSlotCount = Assert-Integer $audit.externalSlotCount 'externalSlotCount' 0 $slotCount
if ($null -ne $audit.nativeSlotCount) {
    $nativeSlotCount = Assert-Integer $audit.nativeSlotCount 'nativeSlotCount' 0 $slotCount
} else {
    $nativeSlotCount = $null
}
if ($audit.candidate -isnot [bool]) { throw 'candidate must be a boolean.' }
if ($null -ne $audit.refusalReason) {
    if ($audit.refusalReason -isnot [string] -or $audit.refusalReason.Length -gt 4096) {
        throw 'refusalReason must be null or a string of at most 4096 characters.'
    }
    if ($audit.candidate) { throw 'A refused integration cannot be a structural candidate.' }
}
if ($null -ne $audit.adapterId -and ([string] $audit.adapterId).Length -gt 128) {
    throw 'adapterId must be null or at most 128 characters.'
}
if ([string]::IsNullOrWhiteSpace($AdapterId)) { $AdapterId = [string] $audit.adapterId }
if ([string]::IsNullOrWhiteSpace($AdapterId)) {
    $suggestedId = ([string] $audit.screenClass).Split('.')[-1].ToLowerInvariant() -replace '[^a-z0-9_.-]', '-'
    $AdapterId = 'dynamicinventory:discovered/' + $suggestedId
}
if ($AdapterId -notmatch '^[a-z][a-z0-9_.-]{0,63}:[a-z0-9][a-z0-9_./-]{0,127}$') {
    throw 'AdapterId must be a namespaced identifier using lowercase ASCII letters, digits, dot, underscore, dash, or slash.'
}

if ($audit.playerSlots -isnot [array] -or $audit.playerSlots.Count -gt $slotCount) {
    throw 'playerSlots must be an array no longer than slotCount.'
}
$containerSlots = @{}
foreach ($slot in $audit.playerSlots) {
    foreach ($name in @('containerSlot', 'rawIndex', 'ordinaryIndex', 'x', 'y', 'slotClass')) {
        if (!(Has-Property $slot $name)) { throw "A playerSlots entry is missing '$name'." }
    }
    $containerSlot = Assert-Integer $slot.containerSlot 'playerSlots.containerSlot' 0 ($slotCount - 1)
    if ($containerSlots.ContainsKey([string] $containerSlot)) { throw "Duplicate containerSlot $containerSlot in playerSlots." }
    $containerSlots[[string] $containerSlot] = $true
    [void] (Assert-Integer $slot.rawIndex 'playerSlots.rawIndex' 0 4096)
    if ($null -ne $slot.ordinaryIndex) { [void] (Assert-Integer $slot.ordinaryIndex 'playerSlots.ordinaryIndex' -1 4096) }
    [void] (Assert-Integer $slot.x 'playerSlots.x' -32768 32768)
    [void] (Assert-Integer $slot.y 'playerSlots.y' -32768 32768)
    if ([string]::IsNullOrWhiteSpace([string] $slot.slotClass) -or ([string] $slot.slotClass).Length -gt 255) {
        throw 'playerSlots.slotClass must be a non-empty class name of at most 255 characters.'
    }
}
Assert-IntArray $audit.missingLegacyIndices 'missingLegacyIndices' 0 35
Assert-IntArray $audit.duplicateOrdinaryIndices 'duplicateOrdinaryIndices' 0 4096
if ($audit.reasons -isnot [array] -or $audit.reasons.Count -gt 128) { throw 'reasons must be an array of at most 128 strings.' }
foreach ($reason in $audit.reasons) {
    if ($reason -isnot [string] -or $reason.Length -gt 1024) { throw 'Each reason must be a string of at most 1024 characters.' }
}

if ([string]::IsNullOrWhiteSpace($AdapterName)) {
    $simpleName = ([string] $audit.screenClass).Split('.')[-1]
    $AdapterName = $simpleName + 'AdapterScaffold'
}
if ($AdapterName -notmatch '^[A-Za-z_$][A-Za-z0-9_$]{0,127}$') { throw 'AdapterName must be a Java identifier.' }

if ([IO.Path]::IsPathRooted($OutputDirectory)) { $outputFull = [IO.Path]::GetFullPath($OutputDirectory) }
else { $outputFull = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory)) }
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'src'))
$sourcePrefix = $sourceRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
if ($outputFull.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $outputFull.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must remain outside src; generated scaffolds are never runtime-registered.'
}

$auditStem = [IO.Path]::GetFileNameWithoutExtension($auditFullPath)
$outStem = $AdapterName
$sourcePath = Join-Path $outputFull ($outStem + '.java')
$notesPath = Join-Path $outputFull ($outStem + '-manual-review.md')
$summaryPath = Join-Path $outputFull ($outStem + '-audit-summary.json')
$writeSource = $audit.framework -in @('NATIVE', 'MUI2')
$expectedNativeSlotsLine = if ($null -ne $nativeSlotCount -and $nativeSlotCount -ge 36 -and $nativeSlotCount -le 32767) {
    "                .expectedNativeSlots($nativeSlotCount)`n"
} else {
    ''
}
$destinations = @($notesPath, $summaryPath)
if ($writeSource) { $destinations += $sourcePath }
if (!$Force) {
    foreach ($destination in $destinations) {
        if (Test-Path -LiteralPath $destination) { throw "Output already exists (use -Force to replace): $destination" }
    }
}

New-Item -ItemType Directory -Force -Path $outputFull | Out-Null
$java = @"
package $GeneratedNamespace;

/**
 * Generated native adapter registration scaffold only. This method is never called by the mod.
 * Review transfer semantics and layout values, then explicitly register from the approved loader.
 */
public final class $AdapterName {
    private $AdapterName() {}

    /** Manual opt-in hook; not called by any loader. */
    public static void register() {
        /*
        // TODO resolve every audit reason and validate transfer/recipe/header behavior first.
        InventoryUi.register(
            InventoryUiAdapter.builder("$AdapterId", "$($audit.containerClass)")
                .gui("$($audit.screenClass)")
$expectedNativeSlotsLine                .layout(NativeUiLayout.builder()
                    .topPadding(0) // TODO measure tab/header padding.
                    .minimumHeaderHeight(0) // TODO measure native header geometry.
                    .backgroundInset(0) // TODO choose per-frame clipping after inspection.
                    .build())
                // Choose exactly one after inspection:
                // .extraTransfers(InventoryTransfers.toNativeSlots())
                // .nativeTransfers()
                .build());
        */
    }
}
"@

if ($audit.framework -eq 'MUI2') {
    $java = @"
package $GeneratedNamespace;

import net.minecraft.entity.player.EntityPlayer;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;

/**
 * Unregistered player-section template for $($audit.screenClass).
 * This does not reproduce the machine controls or replace the observed GUI.
 */
public final class $AdapterName {
    private $AdapterName() {}

    /** Call from the reviewed MUI2 panel builder before panel construction. */
    public static SlotGroupWidget playerInventory(EntityPlayer player, PanelSyncManager sync) {
        // TODO reuse an existing player binding instead if this builder already creates one.
        sync.bindPlayerInventory(player);
        // TODO add this group once, position it below machine controls, and qualify panel sizing.
        // Keep normal MUI2 container ownership, filters, and transfer behavior.
        return Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot);
    }
}
"@
}

$legacyMissing = if ($audit.missingLegacyIndices.Count) { ($audit.missingLegacyIndices -join ', ') } else { 'none reported' }
$duplicates = if ($audit.duplicateOrdinaryIndices.Count) { ($audit.duplicateOrdinaryIndices -join ', ') } else { 'none reported' }
$reasonText = if ($audit.reasons.Count) { ($audit.reasons | ForEach-Object { "- $_" }) -join "`n" } else { '- none reported' }
$notes = @"
# Manual review: $AdapterName

Generated from audit $auditStem on $($audit.capturedAtUtc) for pack target $($audit.packTarget). Framework: $($audit.framework). Candidate flag: $($audit.candidate); this is only a heuristic.

## Exact target (not activated)

- Container: $($audit.containerClass)
- Screen: $($audit.screenClass)
- Total slots: $slotCount; native slots: $(if ($null -eq $nativeSlotCount) { 'unknown' } else { $nativeSlotCount }); external slots: $externalSlotCount
- Missing legacy ordinary indices: $legacyMissing
- Duplicate ordinary indices: $duplicates
- Native count below 36: $(if ($null -ne $nativeSlotCount -and $nativeSlotCount -lt 36) { 'this target cannot directly attach an incomplete legacy region; the scaffold omits expectedNativeSlots because the API guard requires at least 36 native slots. Do not register until the complete legacy player region is present.' } else { 'no' })

## Transfer review

- Inspect exact native `transferStackInSlot`, merge, crafting-output, and recipe-transfer methods; record JVM descriptors and the source/target ranges.
- Preserve native machine/equipment filters, slot limits, crafting callbacks, output consumption, and shift-click ordering.
- Confirm the added player region does not expand native machine ranges or alter established container IDs.

## GUI and input review

- Record background/foreground draw methods, GUI origin calculations, fixed-grid texture regions, and any cached header/tab/button/border/slider fields.
- Check whether resize, scroll, or a draw callback mutates native slot coordinates or GUI dimensions, and preserve/restores those semantics explicitly.
- Keep native input, hit testing, live item rendering, container synchronization, and transfer behavior in control until a reviewed API contract states otherwise.

## Audit reasons

$reasonText

## Lifecycle and integration review

1. Resolve every audit reason and confirm the target mod/profile/version manually.
2. Implement an explicit adapter for these exact container and screen classes.
3. Add focused tests for slot maps, pickup/place, shift-click, cursor conservation, filters/callbacks, resize/scroll, and synchronization.
4. Register only through `InventoryUi.register` after qualification; a presentation-only GUI for an already registered container may use `InventoryUi.registerPresentation(adapterId, guiClassName, layout)` without adding another container adapter.

For vanilla-style server opening, the container lifecycle attaches a registered container at the head of `addCraftingToCrafters`, before the full slot sync. The client GUI-init pre event attaches before the panel is created; GUI-init post builds presentation only when a binding and layout are present. A custom opening protocol should call `InventoryUi.attach(container, player.inventory)` after creating all native slots and before sending contents. If a container overrides `slotClick` or `addCraftingToCrafters` with a custom protocol, add and qualify explicit hooks; the base-container injections may not run through those overrides.

For cached absolute GUI origins, implement `NativeUiAnchors.captureHeaderOrigins()` and `positionHeaderOrigins(int[])`. Capture a fresh snapshot before translating and restore it when the panel is removed. `InventoryUi.findLayout(container, guiClass)` returns the base adapter layout or a registered presentation-only layout for that exact GUI class.

$(if ($audit.framework -eq 'MUI2') { '## MUI2 builder path' + "`n`n" + 'A Java player-section template is emitted separately. It is not registered or called by any loader and does not migrate machine controls. Inspect the exact MUI2 builder/binding method and sync/container ownership. Reuse an existing player binding instead of binding twice; add the shared group once and qualify its position, panel dimensions, and transfer behavior. Do not use native `InventoryUi` registration for this framework target.' } elseif ($audit.framework -eq 'MUI1') { '## MUI1 builder path' + "`n`n" + 'Do not assume this is a native `GuiContainer` screen. Inspect the exact public MUI1 player/container builder and its `Scrollable`/`SlotWidget` binding before adding hooks; preserve MUI1 transfer priority and offscreen capacity behavior. This exporter provides review notes only for MUI1.' } else { '## Native registration scaffold' + "`n`n" + 'A Java registration snippet is emitted separately, fully commented and not called by any loader. The layout values and transfer strategy are placeholders requiring review. Use `registerPresentation` only when this GUI shares an already registered container; do not register the same container twice.' })

This scaffold is generated outside `src/`, is not compiled, and has no runtime effect.
"@

$normalized = [ordered]@{
    schemaVersion = 1
    capturedAtUtc = $capturedAtUtcNormalized
    packTarget = [string] $audit.packTarget
    containerClass = [string] $audit.containerClass
    screenClass = [string] $audit.screenClass
    framework = [string] $audit.framework
    slotCount = $slotCount
    nativeSlotCount = $nativeSlotCount
    adapterId = $AdapterId
    refusalReason = $audit.refusalReason
    playerSlots = @($audit.playerSlots)
    missingLegacyIndices = @($audit.missingLegacyIndices)
    duplicateOrdinaryIndices = @($audit.duplicateOrdinaryIndices)
    candidate = [bool] $audit.candidate
    reasons = @($audit.reasons)
    externalSlotCount = $externalSlotCount
}

$utf8NoBom = [Text.UTF8Encoding]::new($false)
if ($writeSource) { [IO.File]::WriteAllText($sourcePath, $java, $utf8NoBom) }
[IO.File]::WriteAllText($notesPath, $notes, $utf8NoBom)
[IO.File]::WriteAllText($summaryPath, ($normalized | ConvertTo-Json -Depth 12), $utf8NoBom)
if ($writeSource) { Write-Output "Unregistered Java template: $sourcePath" }
else { Write-Output "Framework-specific review notes: $notesPath" }
Write-Output "Review notes: $notesPath"
Write-Output "Audit summary: $summaryPath"
if ($InsertIntoSource -and $audit.framework -eq 'NATIVE') {
    if (![IO.Path]::IsPathRooted($RegistrationFile)) { $RegistrationFile = Join-Path $projectRoot $RegistrationFile }
    $registrationPath = [IO.Path]::GetFullPath($RegistrationFile)
    $projectPrefix = $projectRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
    if (!$registrationPath.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetExtension($registrationPath) -ne '.java') {
        throw 'RegistrationFile must be an existing Java file inside this workspace.'
    }
    # One lock across all watchers/output directories. The game never writes project source itself.
    $lockPath = Join-Path $projectRoot '.setup/ui-source-insert.lock'
    New-Item -ItemType Directory -Force -Path (Split-Path $lockPath -Parent) | Out-Null
    $sourceLock = [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    $temporarySource = $null
    try {
        $source = [IO.File]::ReadAllText($registrationPath)
        $marker = '        // F9-SCAFFOLDS: New native adapter suggestions are inserted above this marker, fully commented.'
        if ([regex]::Matches($source, [regex]::Escape($marker)).Count -ne 1) { throw 'Expected one F9 source insertion marker.' }
        # Reviewed adapters live in per-mod files beside this dispatcher. Include child packages
        # so moving a declaration cannot make F9 suggest a conflicting second registration.
        # Match the exact Java string; commented suggestions still count as declarations.
        $containerLiteral = '"' + $audit.containerClass + '"'
        $alreadyDeclared = $source.Contains($containerLiteral)
        if (!$alreadyDeclared) {
            $templatePrefix = $outputFull.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
            foreach ($module in Get-ChildItem -LiteralPath (Split-Path $registrationPath -Parent) -Filter '*.java' -File -Recurse) {
                # An exported template is not a declaration in the integration source.
                if ($module.FullName.StartsWith($templatePrefix, [StringComparison]::OrdinalIgnoreCase)) { continue }
                if ([IO.File]::ReadAllText($module.FullName).Contains($containerLiteral)) {
                    $alreadyDeclared = $true
                    break
                }
            }
        }
        if ($alreadyDeclared) {
            Write-Output 'Compatibility source already contains this container; keeping its existing adapter/scaffold.'
        } else {
            $expression = [regex]::Match($java, '(?ms)^        /\*\r?\n.*?InventoryUi\.register\(.*?\.build\(\)\);\r?\n        \*/')
            if (!$expression.Success) { throw 'Missing native registration scaffold.' }
            $newline = if ($source.Contains("`r`n")) { "`r`n" } else { "`n" }
            $block = "        // F9 suggestion: $AdapterId. Review transfers and layout before enabling." + $newline +
                $expression.Value.Replace("`r`n", "`n").Replace("`n", $newline) + $newline + $newline
            # Only the validated, generator-owned commented expression enters source. No audit reason text/code is copied.
            $next = $source.Replace($marker, $block + $marker)
            $temporarySource = Join-Path (Split-Path $registrationPath -Parent) ('.f9-' + [guid]::NewGuid().ToString('N') + '.tmp')
            [IO.File]::WriteAllText($temporarySource, $next, $utf8NoBom)
            if ([IO.File]::ReadAllText($registrationPath) -cne $source) { throw 'Source changed during insertion; retry capture.' }
            # PowerShell 5 converts $null to an empty path here; use a real backup path for .NET Framework.
            $sourceBackup = $temporarySource + '.bak'
            [IO.File]::Replace($temporarySource, $registrationPath, $sourceBackup)
            [IO.File]::Delete($sourceBackup)
            $temporarySource = $null
            Write-Output "Commented registration inserted: $registrationPath"
        }
    } finally {
        if ($temporarySource -and [IO.File]::Exists($temporarySource)) { [IO.File]::Delete($temporarySource) }
        $sourceLock.Dispose()
    }
} elseif ($InsertIntoSource) {
    Write-Output 'This framework needs a builder integration; its template/notes remain in the generated bundle.'
} else {
    Write-Output 'No source files were changed; the scaffold is not registered or enabled.'
}
