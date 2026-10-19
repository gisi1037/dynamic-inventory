# Discovering and adapting inventory GUIs

This guide is a starting point for developers adding support for an additional inventory GUI. It describes the implemented audit workflow and current adapter boundaries; generated scaffolds do not enable or register support. Inventory and synchronization remain server-authoritative. The public Java API is experimental version 1 for this unreleased development build; multiplayer compatibility uses wire protocol 3, and neither provides a general compatibility guarantee.

Start with the [inventory integration contract](inventory-integration-contract.md) for ownership,
index namespaces, transfer responsibilities, and the runtime opening gate.

Native adapter declarations must be registered during pre-initialization, initialization, or
post-initialization. Dynamic Inventory freezes those registrations at `FMLLoadComplete`; late
adapter registration throws. The protocol declaration sent during login is
`<mod-version>;protocol=3;storage=<count>;adapters=<sha256>`. It compares the effective storage
capacity and a sorted startup digest of adapter IDs, container class names, subclass matching,
expected native slot counts, transfer-policy presence, plain-versus-custom appended-slot factory,
and each adapter's declared compatibility revision. It does not exchange runtime container slot
layouts or hash callback implementation code. The separate per-window opening exchange compares live
native slot maps before releasing that window's standard updates. Presentation-only GUI layouts may still be registered
after startup and are excluded from the digest. Bump `.compatibilityRevision(int)` on both sides
when an adapter's binding, transfer, slot-factory, or other compatibility behavior changes.

For the current data model, save format, native/MUI adapters, and pinned RC-2 test scope, see [project architecture](project-architecture.md). The current full-pack target and installed file hashes are recorded in [`pack-compatibility-rc2.json`](../pack-compatibility-rc2.json).

## Inspect before adapting

Use the live GUI audit to observe one open screen at a time. In the client, press the configurable **Inventory UI audit** key (F9 by default) while the target `GuiContainer` is open; the chat message gives the absolute path of the saved JSON. The harness `ui-audit` action captures the same report from the current GUI. The audit is read-only: it reports the container and screen class, native slot counts, player-backed slot coordinates, adapter ID when available, and gaps/duplicates in detected player indices (duplicate detection covers the full ordinary capacity). It cannot prove how a GUI handles transfers, recipes, rendering, or resize state. Treat it as a discovery aid, not an approval signal.

The audit JSON schema is version 1:

| Field | Meaning |
| --- | --- |
| `schemaVersion`, `capturedAtUtc`, `packTarget` | Report schema, capture time, and tested environment label |
| `containerClass`, `screenClass`, `framework` | Exact classes and `NATIVE`, `MUI1`, or `MUI2` classification observed |
| `slotCount`, `nativeSlotCount`, `externalSlotCount` | Counts from the current container; `nativeSlotCount` may be `null` when unavailable |
| `playerSlots[]` | Observed player slots with `containerSlot`, `rawIndex`, `ordinaryIndex`, `x`, `y`, and `slotClass` |
| `missingLegacyIndices[]`, `duplicateOrdinaryIndices[]` | Audit findings that need resolution before mapping player slots |
| `candidate`, `reasons[]` | Heuristic outcome and explanatory findings; candidate status is not automatic approval |
| `adapterId` | Matching registered adapter ID, even if attachment has not succeeded; `null` for an unknown container |
| `refusalReason` | Local automatic-attachment failure, or `null` when none was recorded; not a multiplayer opening rejection |

The PowerShell generator rejects unknown schema versions, malformed Java class names, invalid counts/indices, and duplicate container-slot observations. It accepts valid fully qualified class names without a built-in mod allowlist, because newly discovered GUIs must be inspectable. It never executes or emits code taken from the JSON. Regardless of `candidate`, the generated output requires manual review and uses exact observed class names. The generator writes only to an external developer output folder and never adds a mixin, runtime registration, allowlist entry, or automatic activation to the shipped mod.

For pack-scale prioritization, `scripts/tools/Find-InventoryUiCandidates.py` scans JAR class-file constant pools and superclass names without defining/loading classes. Point it at a user's mods directory and optionally add the mod artifact being evaluated:

```powershell
python .\scripts\tools\Find-InventoryUiCandidates.py `
  --mods-dir 'C:\path\to\instance\.minecraft\mods' `
  --artifact .\build\libs\dynamicinventory-0.8.0-dev.jar `
  --pack-target 'local-pack-label' `
  --json-out .\.setup\inventory-ui-discovery\candidates.json `
  --csv-out .\.setup\inventory-ui-discovery\candidates.csv
```

The scanner flags direct/transitive native `Container` and `GuiContainer` subclasses, MUI1/MUI2 constant-pool references, and player-builder/sync symbols such as `bindPlayerInventory`. Reports include source JAR paths and SHA-256 hashes plus candidate reasons. Fixed size/count limits bound reads from corrupt or unusually large archives; scan errors are reported. This is structural discovery only: it cannot establish GUI/container pairing, slot mapping, transfer behavior, rendering, or runtime compatibility. Never treat candidates as approved adapters or use them for automatic registration. Review selected screens with the live audit and source/runtime qualification before adding exact registrations.

## Current integration seams

For supported native containers, `ContainerInventoryRegion` maps slots by their canonical backing `InventoryPlayer` and raw slot index, then appends added storage after the original container slots. `ContainerRegionMixin` only widens complete recognized player-region transfer ranges. `BuiltinInventoryUiAdapters` registers exact vanilla chest, workbench, and furnace classes, plus exact Tinkers/Galacticraft classes when the pinned compatibility profile is active. Family-specific transfer behavior preserves furnace filters, crafting callbacks, native recipe behavior, and cached header geometry.

MUI1/MUI2 live player builders use separate logical-index wrappers and native framework slot/sync APIs. The MUI2 `Mui2InventoryWidgets.playerInventory(...)` factory builds live `ItemSlot` widgets; `sync.bindPlayerInventory(player)` registers the matching player sync group. This differs from the optional presentation-only overlay on a native `GuiContainer`, which paints frames and slot backgrounds while native item rendering, hit-testing, and synchronization stay in control. A discovery report showing an MUI screen does not imply that it uses the same builder or binding path as another screen in that mod.

## Generate a review scaffold

For the in-game hotkey workflow, start the VS Code task **GTNH: Generate GUI templates on F9 (RC-2)**, then open the target GUI and press **Inventory UI audit** (F9 by default). Keep the task running while inspecting other interfaces. Each new capture produces its own bundle under `.setup/generated-ui-adapters/`; the task terminal prints the exact directory. Stop the task with Ctrl+C or VS Code's Terminate Task command.

The task uses the local Prism instance named in `pack-baseline.json`. For a source client or another developer's instance, run from the repository root:

```powershell
.\scripts\tools\Watch-InventoryUiTemplates.ps1 `
  -GameDirectory '.\run\rc2-integration\client-developer'
```

The watcher reads F9 audit files and runs the same generator below; it does not need a new game build. It ignores captures already present when started unless `-IncludeExisting` is passed. `-Once` processes existing captures and exits. Completed bundles are preserved on repeated runs; a changed capture gets a new content-hashed directory. Partially written or invalid files cannot activate support or stop subsequent captures from being processed.

Each successful bundle contains the original `audit.json`, a normalized audit summary, manual-review notes, and an inactive `template-export.json` receipt. Native captures also include a commented Java registration scaffold. MUI2 captures include a Java template for binding and creating the shared live player-inventory component; it does not copy machine controls or migrate the original screen. MUI1 captures currently produce notes only. Nothing is copied into `src`, registered, compiled, or enabled automatically.

From the repository root, run the generator with a saved audit JSON:

```powershell
.\scripts\tools\New-InventoryUiAdapter.ps1 `
  -AuditPath .setup\inventory-ui-audit.json `
  -OutputDirectory .setup\generated-ui-adapters
```

The tool validates schema version 1, counts, slot indices, and class names, then writes an audit summary and manual-review notes. Native audits produce a fully commented Java registration example; MUI2 audits produce a player-section template, and MUI1 reports get framework-specific notes. Generated files stay outside `src` and are never compiled or activated by the exporter. Java files use UTF-8 without a byte-order mark on both Windows PowerShell 5.1 and PowerShell 7.

Run `scripts/testing/test-ui-template-tools.ps1` to exercise the developer workflow, partial-file recovery, repeated-export preservation, source-output guard, and generated Java compilation. Its local tooling report is separate from production runtime qualification.

## Generated scaffold and manual review

The generator's output is a starting point for a developer-authored adapter, not a production implementation. It records the exact `containerClass` and `screenClass`, embeds no arbitrary class names beyond validated string constants, and includes review notes for transfer semantics and GUI geometry. Generated source stays outside `src/main/java` until a developer explicitly implements and reviews it.

Before registering a new adapter, determine:

- Which exact slots belong to the player inventory, how raw indices map around optional Backhand offhand, and whether any expected legacy slots are absent or duplicated.
- Which native container methods perform `mergeItemStack`, shift-click, crafting-output, recipe-transfer, and attached-inventory moves. Preserve machine ranges, restrictions, stack limits, callbacks, and native slot IDs.
- Whether the GUI computes its origin from native width/height, caches header/tab/button/border positions, renders a fixed player-grid background, or rebuilds its layout during scrolling/resizing.
- Which mouse/key actions are handled by the native screen or framework, whether scrollable offscreen slots remain available to transfer logic, and who owns slot rendering and synchronization.
- The exact optional mod/profile/version conditions and the evidence needed to demonstrate no item loss, correct cursor state, valid server synchronization, recipe/equipment behavior, and GUI-scale/short-window handling.

Do not register an unknown GUI by package wildcard, class-name prefix, heuristic, or mod-presence alone. Runtime activation must be explicit and gated by exact supported classes and any required profile/version checks.

## Native registration API

For an approved native adapter, use the explicit public API and exact observed classes. The generator's commented snippet is only a scaffold; it deliberately does not select transfer behavior or layout values:

```java
InventoryUi.register(
    InventoryUiAdapter.builder("example:container", "fully.qualified.Container")
        .gui("fully.qualified.Gui")
        .compatibilityRevision(1)
        .layout(NativeUiLayout.builder()
            .topPadding(0)
            .minimumHeaderHeight(0)
            .backgroundInset(3)
            .build())
        .extraTransfers(InventoryTransfers.toNativeSlots())
        .build());
```

Choose `.extraTransfers(InventoryTransfers.toNativeSlots())` when the adapter must transfer added player slots into native slots; choose `.nativeTransfers()` only when the native container already owns the required extra-slot transfer behavior. Review merge/recipe methods before choosing. `.expectedNativeSlots(int)` is an optional guard for 36–32767 existing slots. Exact container class matching is the default; `.includeSubclasses()` must be requested explicitly. Use `InventoryUi.attach(container, player)` only for a deliberate early attachment; normal registration is attached by the generic container listener and GUI-pre lifecycle. `InventoryUi.getBinding(container)` and `findAdapter(container)` expose the current mapping; `PlayerInventoryBinding` provides native/ordinary counts, logical-to-container slot IDs, hotbar/storage IDs, and the player inventory. Do not discover methods or fields through reflection guesses.

For native machine-to-player shift-clicks implemented by your own `transferStackInSlot`, the public `InventoryTransfers.toPlayerInventory()` policy targets storage first and then hotbar; `toStorage()` excludes hotbar. These are source-slot policies, separate from `.extraTransfers(...)`, which handles added player slots moving into native slots. The [native example](../examples/java/example/NativeInventoryIntegration.java) shows both directions and should be adapted to preserve the target container's crafting, filter, and output behavior.

Transfer policy source and destination IDs are absolute indices in `container.inventorySlots`. A `PlayerInventoryBinding` maps canonical player inventory indices to those container IDs; raw player indices and logical ordinary indices are separate concepts. Built-in policies validate that the binding belongs to the supplied player, mutate the source only when a destination accepts items, call `onSlotChanged()` when a partial source remains, and call `onPickupFromSlot()` after a successful move. They return a copy of the original stack or `null` when nothing moved. They do not call `Slot.onSlotChange(source, original)`, which crafting-result slots may use to consume ingredients or refresh recipes. Keep result slots and any source with subclass-specific transfer callbacks on the native `transferStackInSlot` path.

### Native player-slot factories

An adapter can preserve a mod's player-slot subclass for newly appended storage by supplying `.additionalSlots(PlayerInventorySlotFactory)`. The factory has the public signature `Slot create(Container container, InventoryPlayer canonical, int rawIndex)`. The adapter invokes it for added storage slots only; native machine, chest, crafting, and result slots remain the container's own slots. The default is a plain slot backed by the canonical player inventory.

Return a fresh, unattached slot backed by the exact `canonical` inventory and `rawIndex` passed to the factory. `rawIndex` is the inventory's raw slot index, not a logical ordinary-slot index or a container slot ID. Use a `Slot` subclass when the target mod's player slots add validation, stack limits, lock rules, or callbacks, and preserve those rules in the returned slot. The factory may inspect the container to choose geometry or behavior, but must not mutate the container or its slot list. Dynamic Inventory prepares and validates all requested slots before its own append, so an invalid result cannot leave a partial player region. This does not roll back unrelated side effects performed by a callback; factories must remain pure and return fresh unattached slots.

```java
InventoryUiAdapter.builder("example:machine", "example.machine.ContainerMachine")
    .additionalSlots(MyMachineSlots::createPlayerStorageSlot)
    .extraTransfers(InventoryTransfers.toNativeSlots())
    .build();

private static Slot createPlayerStorageSlot(Container container, InventoryPlayer canonical, int rawIndex) {
    return new Slot(canonical, rawIndex,
        MyMachineSlots.playerX(container, rawIndex), MyMachineSlots.playerY(container, rawIndex)) {
        @Override
        public boolean isItemValid(ItemStack stack) {
            return super.isItemValid(stack) && MyMachineSlots.acceptsPlayerItem(container, stack);
        }
    };
}
```

MUI1/MUI2 audits do not use this native registration path. Follow the actual framework builder, container, and synchronization ownership for that screen.

## MUI2 live-slot builder

For a new MUI2 panel that owns a live MUI2 player inventory, add the factory's `SlotGroupWidget` to the panel and register its matching player sync group before panel construction. The factory preserves MUI2 `player` sync keys (`slot_0` onward); do not add a second `ItemSlot` layer over a native container's slots.

```java
sync.bindPlayerInventory(player);
ModularPanel panel = ModularPanel.defaultPanel("example_inventory", 320, 240)
    .child(Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot).pos(20, 20));
```

The one-argument factory keeps the standard nine-column hotbar and responsive scrollable storage viewport. It uses MUI2's normal `sync.bindPlayerInventory(player)` path, for which Dynamic Inventory registers its canonical expanded player inventory wrapper under MUI2's `player` sync key; new panels should not need custom container overrides just to expose expanded player slots. For a caller-owned fixed layout, use `playerInventory(slotConsumer, columns, visibleRows)`, where columns are 3–18 and visible rows are 1–12; that overload does not join Dynamic Inventory's responsive MUI2 resize handling:

```java
Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot, 12, 6);
```

The consumer may decorate each supplied MUI2 `ItemSlot`, but return it with its sync handler and identity intact. These widgets are live slots backed by the player's inventory, not a visual-only preview. Complete public-API examples are in [`examples/java/example`](../examples/java/example/README.md).

For a complete, independently built Forge addon, use the [MUI2 test mod](../examples/mui2-test-mod/README.md).
It depends on the development JAR, imports only the public widget API, and opens a
live ender-chest/expanded-player GUI with `/dinvexample`. It demonstrates the mod
author's side of integration without adding code to Dynamic Inventory's source sets.

For vanilla-style server openings, registered native containers are attached at the head of `addCraftingToCrafters`, before the full slot sync. On the client, GUI-init pre attaches before panel construction and GUI-init post creates a panel only if both a binding and a matching layout exist. For a custom opening protocol, call `InventoryUi.attach(container, player.inventory)` after creating all native slots and before sending contents. If a container overrides `slotClick` or `addCraftingToCrafters` with custom behavior, add and qualify explicit hooks; the base-container injections may not run through those overrides. Some integration libraries dispatch quick-move/shift-click through a separate wrapper, such as Hodgepodge; integrate and qualify that dispatch path explicitly if it bypasses `Container.slotClick`.

Automatic attachment records local failure through `InventoryUi.getRefusalReason(container)`.
The registered native opening gate separately compares runtime slot fingerprints on both peers
before releasing standard window updates or allowing clicks. Invalid bindings and different runtime
maps close that window on both peers; unknown containers remain unaugmented. Call
`InventoryUi.isOpeningReady(player)` on the game thread immediately before mutation in custom action handlers, after scheduling their work. Custom synchronization
packets and direct inventory writes need explicit integration. See the
[contract](inventory-integration-contract.md) for fingerprint contents, lifecycle, and bounded buffering.
The standalone `attachRegistered` method still does not itself abort an opening, and a null refusal
reason is not compatibility approval.

For GUIs that share an already registered container but need different layout geometry, register a presentation-only entry with `InventoryUi.registerPresentation(adapterId, guiClassName, layout)`. This does not add a second container adapter or change transfer policy. `InventoryUi.findLayout(container, guiClass)` returns the container adapter layout or matching GUI-specific presentation layout. Cached absolute widget origins can implement `NativeUiAnchors.captureHeaderOrigins()` and `positionHeaderOrigins(int[])`; capture a fresh snapshot before translation and restore it when the panel is removed.

## Qualification sequence

1. Capture and review the audit JSON while the intended GUI is open; retain the exact target mod/profile provenance. The API has runtime evidence for the named native, MUI2, capacity, isolated, and full-pack scopes listed below; an audit by itself still does not qualify a GUI.
2. Generate an external scaffold and resolve every `reasons[]`, missing legacy index, duplicate index, and transfer/header question by inspecting source or observed runtime behavior.
3. Implement the narrowest adapter required. Preserve the canonical server container and avoid introducing duplicate item stores or alternate synchronization.
4. Add targeted fixture coverage for slot maps, pickup/place, shift-click in both directions, final/offscreen slots, cursor conservation, recipe/equipment restrictions, and resizing/scrolling.
5. Add an exact-version/profile activation gate and qualify the adapter in the intended pinned runtime. Record the tested artifact checksum and screenshots/reports before considering default activation.


## Coverage and local reports

The API is experimental. Standard MUI1/MUI2 builder coverage does not mean every
custom screen is supported. Check binding, transfer dispatch, filtering, equipment,
custom packets, and lifecycle behavior for the actual host.

Runtime reports, screenshots, launch metadata, and personal settings stay local
and untracked. A successful build or preview is not full-pack qualification.
See [testing](testing.md) for focused checks and runtime fixtures.
