# Extra Utilities

**Loaded ID:** `ExtraUtilities`. **Reviewed artifact:** `extrautilities-1.2.12.jar`.
**Status:** nine exact inventory families implemented; installed visual/gameplay
review pending. Owner: `ExtraUtilitiesCompatibility`; bridges: `mixins/extrautils`.
Other versions skip this integration.

The declarations cover Filing Cabinet (both tiers), Transfer/Retrieval Nodes,
Sorting Pipe, item Trash Can, Golden Bag, item Filter, Mini Chest, all dynamic
Generator variants, and QED/Ender Constructor. Full Chest uses its native shared
chest implementation. The Trading Post is a control-only `GuiScreen` and remains
native; its eventual merchant interface uses the vanilla merchant integration.
Fluid/energy trash cans are passive blocks rather than additional inventory UIs.

Bag contents and item-filter ghost entries retain their native backing inventory,
slot objects and NBT ownership. The item that owns an open bag/filter is locked
when opened from an appended player slot, matching the native hotbar lock.
Ghost-filter actions can change settings while returning null, so no generic
transfer retry is added. Trash deletion and filing-cabinet storage rules remain
native too.

The dynamic-container merge override expands only wholly ordinary player ranges.
Player widget backgrounds are replaced by the modular player's cells, while
machine widgets continue to draw at their native coordinates. No widgets are
reparented and no widget packet IDs change. Filing Cabinet's native sort/scroll
viewport refreshes its displayed machine-slot origins without stretching the
frame to include hidden slots.

The separate lab includes 52 labeled Extra Utilities stations: all 12 generator
variants at 1x, 8x and 64x, both Filing Cabinets, transfer/retrieval nodes, sorting
pipe, trash variants, Mini/Full Chests, QED and Trading Post. A stationary test
villager supplies the Trading Post with an offer. Item-filter tools are in the
supply chest. Golden Bag is disabled by the current pack: support is registered
for enabled packs, but no disabled item is enabled or fabricated for testing.
