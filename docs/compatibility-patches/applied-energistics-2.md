# Applied Energistics 2

**Loaded ID:** `appliedenergistics2`. **Status:** initial native machine/storage/terminal
adapters implemented; visual review and broader gameplay qualification pending.
Java owner: `AppliedEnergisticsCompatibility`, with shared seams in `compat/ae2`
and `mixins/appliedenergistics2`.

## First batch

The named RC-2 profile enables reviewed AE2 `rv3-beta-1080-GTNH`; the named
October 19 nightly profile enables `rv3-beta-1089-GTNH`. An absent mod or a
different version skips this integration and its Mixins. The optional API is
compile-only: it is not bundled or added to the isolated runtime.

Source review used the exact upstream tags
[1080](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/tree/rv3-beta-1080-GTNH)
and [1089](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/tree/rv3-beta-1089-GTNH).
The shared container/slot/transfer seams used here agree between those tags.

| Screen | Exact container / GUI suffix |
| --- | --- |
| Quartz Grindstone | `Grinder` |
| Matter Condenser | `Condenser` |
| ME Chest (cell slot/configuration screen) | `Chest` |
| Sky Stone Chest | `SkyChest` |
| ME Drive | `Drive` |
| Vibration Chamber | `VibrationChamber` |
| Inscriber | `Inscriber` |
| Advanced Inscriber | `AdvancedInscriber` |
| ME IO Port | `IOPort` |
| Crystal Growth Chamber | `CrystalGrowthChamber` |
| Molecular Assembler | `MAC` |
| Wireless Access Point | `Wireless` |
| Security Terminal | `Security` |
| Quantum Link Chamber | `QNB` |
| Spatial IO Port | `SpatialIOPort` |
| Spatial Link Chamber | `SpatialLinkChamber` |
| ME Interface (block and part) | `Interface` |
| Cell Workbench | `CellWorkbench` |
| ME Terminal, core wireless terminal and ME Chest network view | `MEMonitorable` |
| Crafting Terminal | `CraftingTerm` |
| Pattern Terminal | `PatternTerm` |
| Extended Pattern Terminal | `PatternTermEx` |
| Portable Cell | `MEPortableCell` |
| Interface Terminal | `InterfaceTerminal` |
| Crafting Diagnostic Terminal | `CraftingDiagnosticTerminal` |

Each registration matches only `appeng.container.implementations.Container<suffix>`
and `appeng.client.gui.implementations.Gui<suffix>`. It uses the existing native
editor (F10), nine player columns, texture crop 4, and the machine Vertical/Scroll
preference. Native machine slots, restrictions and synchronization remain AE2's.

AE2 casts real slots to `AppEngSlot` and transfers by the slot's player-side flag.
The shared additional-slot factory therefore creates native `SlotPlayerInv` or
`SlotDisabled` instances and preserves AE2's locked raw player indices. This
includes a network tool borrowed from expanded storage by an upgradeable machine.
AE2's own append method sets each new slot's container callback owner. Native slot
counts can vary with the attached toolbox, so there is no fixed-count assumption.

Upgradeable machines put the upgrade cards and borrowed network-tool contents in
separate frames to the right of the main machine/player frame. Cards align with
the machine header; toolbox rows align with the player grid where space permits.
The 3x3 and 5x5 toolbox layouts follow their real backing inventory, and preserve
native slot IDs, restrictions, callbacks and item ownership. Shared palette,
frame style and border width apply to all three frames.

The native editor's Widgets page moves configuration/priority tabs and icon controls as two
independent groups. It adjusts the existing `GuiTabButton`/`GuiImgButton` coordinates from their
captured native origins, preserving their actions, tooltips and click areas. Native toggle/simple
icon buttons share the controls group. Other widget classes (including progress bars and custom
lock buttons) keep their native placement.

Inscriber, Advanced Inscriber and Crystal Growth Chamber extend `AEBaseGui` directly,
so they do not inherit `GuiUpgradeable`'s accepted-upgrade tooltip. For registered hosts,
the base GUI bridge supplies that missing hover tooltip on empty, enabled upgrade slots.
It uses the actual `IUpgradeInventory` limits and registered card names; occupied slots keep
their ordinary item tooltip. Screens which already inherit the native tooltip retain it.

`compat/ae2/AppliedEnergisticsBackground` clips the old player grid from the narrow
main texture; `AppliedEnergisticsSidePanels` places the real side slots and draws
their separate backgrounds. Side slots no longer force a wider/taller main frame.
Split-frame screens use presentation revision 2, so older editor geometry is not
reapplied to the newly separated sections. Other AE2 layouts retain revision 1.
Security's encoder now has its own frame, retaining native input/output slots and
arrow artwork. The ME Interface's frame fill also covers the one-pixel boundary
below its native texture, avoiding a transparent horizontal seam.

All transfers stay native; there is no generic destination or hotbar fallback.
Such a fallback could duplicate a native action that changes items but returns
null. AE2's custom server packet dispatcher also checks the existing opening gate
for registered hosts, while base custom synchronization waits for agreement before
advancing its caches. Unregistered AE2 screens keep their previous behavior.

## Security and virtual configuration grids

Security Terminal keeps a nine-column network browser with AE2's own scrollbar.
Its encoder, biometric-card slots and permissions remain native. A small client bridge
anchors its absolute search field to the machine section.

Cell Workbench retains its 63 native virtual configuration cells, cell slot, upgrade restrictions
and action/synchronization handlers. The native editor reserves room for these virtual cells
and the Interface's optional pattern rows even when Section gap is zero. Interface and Cell
Workbench use the same separate upgrade/toolbox frames as the Inscriber.

`AeGuiInputMixin` translates only AE2's native input-origin reads for virtual-slot dragging and
its scrollbar. `AeCellWorkbenchGuiMixin` applies the same origin to bookmark drops. Native slot
IDs, virtual indices and packet payloads do not change. Optional/ghost-slot background drawing
temporarily uses native slot coordinates, then restores displayed positions before real items
and clicks are processed. The client bridges are side/version guarded; unregistered screens
keep their original input origins.

The added seven screens need visual and gameplay review in the installed pack, especially
Security browser/permission/encoding actions, Cell Workbench ghost filters/bookmark drops,
Interface pattern-capacity changes, and Spatial Link teleporting. Compile and frame/input
mapping checks are not a substitute for those checks.

## Initial verification

Build/style checks pass, and the source compiles against both pinned AE2 APIs.
The artifact check verifies that the vanilla-named synchronization override has
its required production refmap; a development-only successful compile is not enough.

A focused installed-nightly integrated-game smoke check passed at the default
72 storage slots: Sky Stone Chest opening, all 81 canonical ordinary slots,
transfer of a typed-NBT stack from the final expanded slot into the chest and
back, and exact client/server item agreement. The Inscriber also opened with
its borrowed toolbox, kept the expanded-slot network tool in native `SlotDisabled`,
and refused its shift-transfer. Both screens opened/cancelled the layout editor.
The read-only harness action `ae2-slot-check` checks native player flags, lock
types and callback owners; use it only in a disposable qualification world.

The installed nightly disables `GrindStone`, so its natural opening was skipped;
the declaration is available if a pack enables that feature. Other first-batch
screens still need manual layout/gameplay review. This smoke does not qualify
every machine, recipe/wheel action, capacity change or dedicated-server path.

## Core terminal integration

The seven additional registrations above cover the core ME, crafting, pattern,
extended processing-pattern, portable-cell, interface and diagnostic families.
Core wireless terminals and ME Chest network views share the MEMonitorable host.
Wireless Crafting Terminal and other addon mods are not included by this declaration.
No package-wide or AEBaseContainer subclass registration is used.

Monitorable terminals retain nine columns and AE2's own scrollbar. Small mode keeps
a fixed browser count; Tall reserves its minimum, fits player rows, then uses spare
height for the browser. Interface Terminal follows the same fitting policy, while
the diagnostic list retains five rows. View cells remain separate side panels.
Native search, sorting, pins, network actions, virtual slot
indices, crafting/encoding packets and item-backed locks remain AE2's responsibility.
The expanded real player inventory uses the existing Vertical/Scroll preference.

Custom terminal rendering/input reads use the translated native header origin.
Absolute search fields move with that section. Extended Pattern Terminal controls
retain AE2's live inverted-layout positioning before editor widget offsets apply.
NEI positioned ingredient overlays receive the same header offset; recipe transfer
continues through AE2's native handler. The true window dimensions are preserved
for the Interface Terminal's custom scissor calculations.

These are initial adapters, not full gameplay qualification. Manually check search
focus, network scrolling, both transfer directions, crafting and encoding, inverted
processing patterns, NEI overlay/fill, view cells, interface filtering, diagnostic
selection, portable/wireless held-item locks and reconnect. Review addon families
separately before enabling them.

The terminal batch passes build/style, RC-2 API compilation, existing headless
editor geometry checks and packaged production-refmap checks. An installed-nightly
class-loading check passes for all seven terminal GUI/container pairs plus the
seven preceding machine/configuration pairs. That check caught and corrected an
inherited-field Mixin error in Extended Pattern Terminal before handoff. It does
not exercise terminal gameplay or confirm visual placement.


## Terminal height and transfers

Client config `dynamicinventory-client.cfg`, category `ae2Terminal`, provides
`smallRows` (default 3) and `tallMinimumRows` (default 6). Restart the client
after editing the file externally, or use the player configuration menu's
Inventory layout → AE2 terminals → Small rows / Tall minimum controls
and reopen the terminal without restarting. Both modes preserve nine columns. Player storage
uses a scrollbar if the selected terminal rows and full storage cannot fit.
On exceptionally short windows the terminal minimum is reduced to preserve at
least one player row, subject to AE2's three-row browser minimum. Top/bottom
reserves are at least 8/28 scaled pixels to leave NEI's search bar accessible.
The default section gap trims six pixels of spare native label padding.

AE2's `AdaptorPlayerInventory` previously exposed only 36 ordinary slots, so its
network extraction simulation considered expanded storage full too early. The
version-guarded adapter hook exposes the owning inventory's live ordinary capacity
and maps each access through that inventory's shape. Both normal and storage-first
orders exclude offhand/armor. Simulation and insertion continue to use the same
native AE2 adaptor; extraction is not retried with a second transfer algorithm.

The Interface Terminal clips its old stepped player-frame corner below the list,
using the same boundary for the native draw and the replacement frame fill.

The native editor's Widgets page also provides independent Pins X/Y offsets for
the actual pin-state button. Saved layouts without these fields keep zero offsets.
Crafting Tweaks buttons anchor to the real crafting matrix, or the reviewed virtual
pattern-grid origin, rather than assuming native container slot 1 is the matrix.
Diagnostic Terminal crops the old 208px texture's transparent right strip and stepped
player cap; its replacement inventory begins after the actual five-row list.

NotEnoughEnergistics owns the Combine Items button's live position. The decorator
excludes that exact widget from captured/restored button origins, retaining NEE's
mode-dependent placement for both drawing and clicks instead of resetting its icon
to its initial processing-layout position each frame.

Baubles right-click fallback preserves items with native use actions (including
wireless terminals). It returns a non-null zero-sized stack after equipping the
final held item, as required by vanilla's item-use manager. Installed-nightly checks
cover belt equip/consumption and unlinked Universal Wireless Terminal use in creative
mode, with matching client/server held/equipment state. Linked addon terminal GUI
integration is not qualified by that crash regression check.
