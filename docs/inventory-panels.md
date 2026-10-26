# Inventory panels

Attached inventory stays the default. Separate panels are currently enabled for
the main player inventory and a single vanilla chest. Other machine GUIs keep
their attached layout until qualified. No storage capacity is granted by panels.

Click the small panel icon to detach/reattach; right-click it for settings. Drag a
title to move a panel and a corner to resize. The frame follows the pointer while
whole rows/columns fit inside it; overflow scrolls. Main keeps the nine-slot hotbar.

In **Panels**, add or select a named panel, enter a name, and adjust its slot count.
Shift changes nine slots at a time. Main's count is editable too. The top `n/x`
counter must allocate all storage before Apply is enabled; **Assign remaining
slots to Main** balances the total. Removing a panel returns its slots to Main.
Zero-slot draft panels are listed but do not create empty floating windows.

Names and storage-slot assignments are shared across supported interfaces.
**Scope** controls geometry/appearance: global defaults or this interface. The
Layout, Position and Style pages affect the selected panel. **Save as global** is
explicit; **Use global** removes the selected interface override. Player settings
also provide resets for the global layout, all machine overrides and mod groups.

Resize and movement never reorder item stacks or change server slots. Membership
uses stable storage indices, so editing another panel does not shift unchanged
panels. Added capacity goes to Main; a temporarily unavailable reservation returns
when capacity grows. Native sorting still sorts the entire inventory, across all
panels; these are not item-type filters. Armor, offhand, crafting and Baubles remain
in their native sections. A separate Baubles pane stays in place and is included in the fitter and NEI exclusions. If floating panels cannot fit, the complete attached
inventory is displayed without overwriting the saved preferences.

For texture packs, choose **Style > Frame: Original** to use the active inventory
texture for edges, corners and fill. Slot artwork always uses the active atlas.
HD textures and transparency are supported. Unified bevel/flat styles also remain
available and inherit basic colors from the active texture, with an optional
explicit theme. See [resource packs](glass-ui-integration.md).

NEI search results exclude the panel rectangles. Bookmarks keep one native grid
with its recipe groups and input mapping: it uses a narrower or shorter free area
when covered. This is conservative rectangular fitting, not full bookmark wrapping.

Swappable reserved hotbars are planned separately. They will take nine ordinary
storage slots per extra hotbar, with a revealable editor panel and configurable
swap hotkeys. Actual stack swaps require a server transaction and are not enabled
by this layout feature.

## Validation

The October 26 patch passed builds for isolated, RC-2 Backhand, RC-2 integration
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
