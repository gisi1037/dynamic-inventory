# Extra Utilities

**Loaded ID:** `ExtraUtilities`. **Status:** inspection required.
Future Java owner: `ExtraUtilitiesCompatibility`.

Separate block inventories, item-backed bags and specialized filter/configuration
screens. Audit their actual slot restrictions, native click/transfer code and
serialization. A bag's contents are a foreign inventory, not additional canonical
player slots.

Reuse reviewed native/MUI families and isolate proven Extra Utilities exceptions
in this owner. Preserve restrictions on moving the item that owns an open container;
do not introduce recursive ticking or mirrored mutable copies of its contents.

Verify original/extra-slot access, both transfer directions, full/filtered
destinations, owner-item movement restrictions, nested item NBT, close/reopen,
death/save/reload and dedicated peers.
