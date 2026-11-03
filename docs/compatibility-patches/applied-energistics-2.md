# Applied Energistics 2

**Loaded ID:** `appliedenergistics2`. **Status:** native machine/storage/terminal
adapters implemented; Gilbert completed visual review of the previously supported
screens on 2026-11-02. Newly added standalone tools and broader gameplay qualification
remain pending.
Java owner: `AppliedEnergisticsCompatibility`, with shared seams in `compat/ae2`
and `mixins/appliedenergistics2`.

## Modular panels (nightly 803)

The pinned `rv3-beta-1093-GTNH` integration enables shared modular inventory panels
for the 27 exact machine/terminal/tool pairs listed below. Compatibility revision 3
requires both peers to agree on the modular-panel integration. Attached mode
stays the default; detach, split panels, profiles, global inheritance and Main-only
NEI spawning use the same controller as the other reviewed native screens.
Native machine/network actions and AE2's AppEngSlot flags, locked slots and
callback owners remain intact.
Open AE2 containers bypass player-panel Shift-click routing and retain AE2's native
machine/network transfer behavior, including when the player inventory is detached.

Upgrade, view-cell, encoder and borrowed network-tool frames retain their actual
slots and join the detached fitter's obstacle list. Their frames still draw after
the main native sheet. Optional/ghost artwork uses native slot coordinates only
during that sheet's draw, restoring displayed coordinates before item rendering.
AE2 keeps the real viewport for terminal scissor calculations.

`scripts/testing/add-ae2-panel-lab.ps1` appends labeled stations behind the
HarvestCraft rows in the existing disposable lab. The six terminal parts have
isolated powered cable networks with a Drive and Interface. The supply chest has
a charged Portable Cell/core Wireless Terminal, storage cells and a network tool;
link the Wireless Terminal through Security before wireless testing. Pack-disabled
blocks receive markers. Placement verification reads blocks/parts without opening
UIs; visual and gameplay review belongs to the player.

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
| Network Tool | `NetworkTool` |
| Advanced Network Tool | `AdvancedNetworkTool` |
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

The standalone Network Tool and Advanced Network Tool now use these same modular
panels. Their native 3×3/5×5 upgrade grids, held-tool lock and facade toggle remain
owned by AE2. These two additions still need installed visual/gameplay review.

On 2026-11-02 Gilbert reported visual testing complete for the other AE2 screens.
This records player visual review; it does not imply dedicated-server or exhaustive
gameplay qualification.

AE2 casts real slots to `AppEngSlot` and transfers by the slot's player-side flag.
The shared additional-slot factory therefore creates native `SlotPlayerInv` or
`SlotDisabled` instances and preserves AE2's locked raw player indices. This
includes a network tool borrowed from expanded storage by an upgradeable machine.
AE2's own append method sets each new slot's container callback owner. Native slot
counts can vary with the attached toolbox, so there is no fixed-count assumption.

Upgradeable machines put the upgrade cards and borrowed network-tool contents in
separate frames to the right of the main machine/player frame. Cards align with
the machine header; the borrowed toolbox occupies the next column to their right,
also aligned with the machine header. Both frames are included in the reserved
machine width and detached-panel collision bounds, keeping the player band clear.
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

Native transfer callbacks retain ownership of their sources; there is no retry
after a native action. On the reviewed 2.9.0 Interface Terminal, pattern extraction's
player-insertion helper uses the bound canonical player slots rather than the raw
inventory adaptor, which includes Backhand. It checks that the entire stack fits
before insertion because the native caller clears its pattern source only on full
success. Expanded Main storage is included, detached subpanel destinations and
reserved hotbars are excluded, and encoded pattern data is preserved. Attached
mode permits all ordinary storage destinations.
AE2's custom server packet dispatcher also checks the existing opening gate
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
The pin-state button defaults to the next free row in AE2's outer left control column,
rather than below the browser where it could overlap a detached inventory panel.
The Universal Wireless Terminal's magnet mode, magnet filter and restock controls
share that column below Pins. The separate Interface Terminal host uses the same
wireless controls column. Alternate terminal modes reflow from live visibility
after state synchronization, preventing Crafting and Level buttons from overlapping
other modes. The primary left column ends with the four alternate destinations in
native order: Crafting, Pattern, Extended Pattern, Interface, Level, excluding the
active mode. The Universal Wireless Interface Terminal reserves a ten-row browser
minimum when there is room for that column and at least one player row; wired
Interface Terminals keep their existing row policy. Very short viewports retain
the outer-column fallback rather than clipping navigation below the screen. All state variants retain their native button objects,
visibility and click handlers.
Upgrade side frames follow AE2's enabled optional slots, so the Cell Workbench
shows five card slots for a standard cell and updates when a different cell is inserted.
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
