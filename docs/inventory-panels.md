# Inventory panels

Attached inventory stays the default. Separate panels are enabled for the main
player inventory and all eleven vanilla container families: chests, crafting,
furnaces, dispensers/droppers, hoppers, brewing, enchanting, beacons, anvils,
merchants and horses. Double/trapped/ender chests, chest/hopper minecarts and
chested donkey/mule inventories share their native family. The pack's Et Futurum
brewing, enchanting and anvil replacements are admitted explicitly too. Other
modded machine GUIs keep their attached layout. No storage capacity is granted
by panels. See the [vanilla UI lab](vanilla-ui-lab.md) for the test world.

Attached inventory bands use the same compact side padding as the player
inventory. Extra scrollbar space is reserved only when storage rows overflow;
an inventory that fits in full no longer widens the surrounding frame for an
unused scrollbar. Native machine controls still retain their required width.

Click the small panel icon to detach/reattach; right-click it for settings. Drag a
title to move a panel and a corner to resize. The frame follows the pointer while
whole rows/columns fit inside it; overflow scrolls. Main keeps all nine hotbar
slots and wraps them into additional rows when its storage grid is narrower than nine columns.
Dragging and corner resizing follow the current cursor each rendered frame;
clicks and the saved layout are still committed through the normal input events.
Hold **Shift** during a corner resize to snap to the nearest complete rows and
columns, including the space for Main's wrapped hotbar. The opposite corner
stays fixed, and neighboring frames and screen edges still limit the size.
Release Shift to return to smooth resizing; it can be toggled during the gesture.
An incomplete final storage row in Main is centered beneath the full rows,
including when scrolled into view. Side-panel grids keep their row alignment.
In the player inventory's top-right grid-icon menu, **Last row: Centered / Left** changes this
client preference for attached and detached main inventories. It saves immediately
and defaults to centered. The detached crafting/equipment section has eight extra
pixels of bottom padding, included in its collision bounds.

In **Panels & layout**, add or select a named panel and edit slot counts directly
in the list. The selected panel's name, width, height, X/Y position and visibility
are editable alongside that list. Switching panels loads that panel's own values;
sizes and positions are independent. Editing a side panel's count automatically
takes slots from Main or returns them to Main. Main shows the remainder; requests
larger than its available capacity are rejected. Each side panel has an **X** in
the list to remove it directly and return its slots to Main.
Zero-slot draft panels are listed but do not create empty floating windows.
All detached panels can be one slot wide when there is enough height; a narrow
Main reserves the height required for its wrapped hotbar. Title drags can pass
through inventory panels and machine/equipment sections while held. On release,
the panel settles at the nearest unobstructed position, with a one-pixel gap;
it never shrinks or returns to its previous edge. Nearby edges and centers align
within three GUI pixels, with faint guides while dragging. Resizing stops at
neighboring frames, while an already-touching edge can resize away or parallel
to its neighbor.
Hidden panels retain their slots and items; hiding Main
leaves a settings icon available to restore it.

The menu edits this interface's layout, including names, panel list, allocations,
geometry and appearance. The combined overview and the separate Style page affect the selected panel. The
**Main: Attached/Detached** control switches the complete inventory presentation.
**Make global default** copies the complete current draft into the global default
and makes this interface inherit it. **Reset to global default** clears every panel's override for this interface, including
panel list, slot allocations, breakout mode, geometry and visibility. Other interfaces keep their overrides. Player settings
also provide resets for the global layout, all machine overrides and mod groups.
Inherited panel groups retain their offset from the machine's bottom center,
adapting to different machine sizes and screen dimensions. Custom overrides
keep their own positions. A subtle **blue** outline around the split button means
Global; an **orange** outline means Custom. Hover the button for an explanation.
Applying an unchanged layout
does not create a custom override.
Schema 6 stores the reference machine and viewport when **Make global default**
is clicked. Older defaults continue to load; save the default once after updating
to establish its reference. The schema 5 file is backed up before migration.

In **Profiles**, type a name and choose **Save profile** to keep the current
panel draft as a reusable layout. The same name offers **Update profile**.
Click **Load: name** to make a saved layout the global default and inherit it
on the current interface; other custom overrides are preserved. **X** removes
the saved profile without changing the active layout. Profiles include all
panel names, slot reservations, geometry, visibility, styling and the reference
machine; they exclude ordinary player preferences such as AE2 rows and centering.
Up to 32 profiles are saved in `config/dynamicinventory/panel-layout-presets.json`.
Schema 5 migrates older shared lists into complete layouts for their known
interfaces. An attached global default starts with Main only. The original file
is retained as an exact schema-version backup on the first save.

Hold **Shift** to reveal the compact **W −/+** and **H −/+** toolbar beside a
side panel. Hover to preview the storage slots borrowed from or returned to Main.
The raised buttons use the active resource pack's native Minecraft widget texture.
Adding a column preserves each existing row; growth fills a partial final row as
needed. The hotbar and other side panels keep their identities. Controls disable
when Main lacks slots, a minimum dimension is reached, or another frame/screen
edge blocks expansion. Only ordinary player-storage presentation changes; no
server item-stack transaction is needed.

Resize and movement never reorder item stacks or change server slots. Membership
uses stable storage indices, so editing another panel does not shift unchanged
panels. Added capacity goes to Main; a temporarily unavailable reservation returns
when capacity grows. Native sorting still sorts the entire inventory, across all
panels; these are not item-type filters. Armor, offhand, crafting and Baubles remain
in their native sections. A separate Baubles pane stays in place and is included in the fitter and NEI exclusions. If floating panels cannot fit, the complete attached
inventory is displayed without overwriting the saved preferences.
With detached player panels, Baubles grows horizontally within the crafting
section's height; it scrolls within that height if the screen cannot fit all columns.
Opening it keeps the player inventory anchored and expands to the right, subject
to the screen edge. Floating panels refit around the live crafting/equipment
panes, inventory tabs and sidebar controls rather than covering them. Fitting
does not overwrite their saved preferred positions.
Shift-clicking a detached subpanel, including Mouse Tweaks' held-Shift sweep, targets Main's hotbar and assigned storage
slots, including when the hotbar is full. The server validates the current window,
capacity revision and ownership of every destination before moving any items.
The source and other subpanels are excluded.

For texture packs, choose **Style > Frame: Original** to use the active inventory
texture for edges, corners and fill. Slot artwork always uses the active atlas,
including its outer grid edges for packs that draw a joined outline rather than
a complete bevel around each slot. Single rows, narrow columns and partial rows
retain their bottom and right edges without blending transparent pixels twice.
HD textures and transparency are supported. Unified bevel/flat styles also remain
available and inherit basic colors from the active texture, with an optional
explicit theme. See [resource packs](glass-ui-integration.md).
Each panel also has **Style > Minimum bevel margin** (4–18 pixels). This controls
the minimum space reserved around its grid; the painted bevel width and crop
remain separate, and sufficient room for that frame and scrollbar is retained.

NEI search results exclude the panel rectangles. Bookmarks keep one native grid
with its recipe groups and input mapping: it uses a narrower or shorter free area
when covered. This is conservative rectangular fitting, not full bookmark wrapping.

**Player settings > Swappable hotbars** reserves nine existing storage slots per
extra row from Main. Subpanel slot identities and allocations are retained;
adding a row is refused if Main has fewer than nine available slots. Reservations
store explicit storage identities, so cells are not renumbered when a row is added
or removed. Existing saved hotbar rows retain their stored contents.
The rows are hidden from the native player/chest inventory by default;
**Hotbar panel: Shown** reveals a draggable editor using the existing container's
real slots. Items can be rearranged there normally. Normal pickup and shared
automatic transfers skip reserved cells. Releasing a row returns those cells and
their contents to ordinary storage.

Bind **Cycle reserved hotbars**, **Swap reserved hotbar 1**, and subsequent row
keys in Minecraft Controls under **Dynamic Inventory Hotbars**. The player settings
screen also lets you bind the cycle key and select a row to bind directly. Keys
start unbound to avoid pack conflicts. Cycling exchanges the main hotbar with
stored rows in sequence; a row key exchanges it with that particular stored row.
**Show/hide reserved hotbars** opens the inventory with the editor visible or
hidden. These gameplay keys operate while no other screen is open. Swaps run on
the server and require an empty cursor, no item in use and the player's own
container. Reservations persist in Forge's persisted player data. The initial
editor integration covers the player inventory and supported native container
screens; other UI frameworks retain their existing presentation.

The player settings menu contains Wide/Compact presets, column and visible-row
controls, last-row centering and AE2 row reservations. The old Tall preset,
machine Vertical/Scroll toggle, container Full box/Compact toggle, experimental
Native/MUI2 renderer switch and duplicate Panel layouts button have been removed.
Machines expand automatically and scroll when needed. The shared MUI integration
APIs and per-panel styling remain available. Panel settings are accessed by
right-clicking the detach widget. Retired configuration keys are removed on load.
Clicking outside the panel settings menu closes it like Cancel or Escape,
discarding unapplied edits. The modal consumes the click and release so the
inventory beneath it is not activated.

## Validation

The earlier October 26 base passed builds for isolated, RC-2 Backhand, RC-2 integration
and the October 19 nightly profiles, plus RC-2/nightly source fixtures. Installed
nightly checks covered named panels on the player inventory and single chest,
real cross-panel transfers, sort/config controls, bookmark fitting, Baubles-pane
relayout and native resource frames. The broader installed run passed 116 native
container checks, 16 transfer checks, 13 GT MUI2 API checks, 196 frame checks,
24 machine-mode checks, 230 attached-chest checks, 115 MUI2 presentation checks
and NEI recipe fill/crafting on all three supported crafting hosts. Tiered belts
and the UHV controller also passed safe-shrink, save/reload and death checks.

This records focused nightly coverage. Other modded hosts retain their existing
attached inventory; arbitrary resource packs and multiplayer combinations still
need their own qualification.

The subsequent hotbar/menu changes passed nightly compilation and Checkstyle,
11,832 direct/cycle identity checks with Backhand offhand coverage, bounded packet
fixtures, and geometry/registry regressions. Focused installed checks covered
full-hotbar subpanel-to-Main transfers in both the player inventory and a chest,
cycle and direct row bindings, native slot rearrangement, pickup exclusion,
full-capacity reservation, wrapped editor hit mapping in a small window, and
save/reload across a complete client restart. Raw left/right outside clicks
cancelled the settings draft on both hosts without changing items or containers.
The October 27 regressions additionally exercise Mouse Tweaks' actual controller
transfer path over nine subpanel stacks with a full hotbar, stable subpanel
membership when reserving rows from Main, saved allocation edits with reserved
cells, fixed Baubles anchoring, and live header movement/collision refitting.

The vanilla extension passed compilation/Checkstyle, 82 bounded packet cases,
thirteen transformed original-vanilla screen variants (including double chest
and chested horse), and nineteen actual nightly block/entity openings with named
panel allocation, dragging, persistence and slot identity checks. The nightly
openings exercised Et Futurum brewing/enchanting and Railcraft's chest cart using
the native chest GUI. Optional Et Futurum anvil, multiplayer and full machine
gameplay actions still need separate qualification.
