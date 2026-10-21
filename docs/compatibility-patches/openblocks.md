# OpenBlocks

**Loaded IDs:** `OpenBlocks`, `OpenMods`. **Status:** backup refusal guard and
grave integer-slot patch implemented. A focused 130-stack death/reload/reclaim smoke
passes in the pinned daily at 216 storage. Full capacity/equipment qualification remains pending.

The existing code owner is
[`OpenBlocksInventoryRestoreMixin`](../../src/main/java/dev/gilbe/dynamicinventory/mixins/openblocks/OpenBlocksInventoryRestoreMixin.java).
It is selected conditionally by `LateMixinsLoader`, with an exact optional version guard.

[`OpenBlocksGraveMixin`](../../src/main/java/dev/gilbe/dynamicinventory/mixins/openblocks/OpenBlocksGraveMixin.java)
substitutes a small grave-only `GenericInventory` subclass. It uses native OpenMods
item encoding but integer `Slot` tags, avoiding signed-byte omissions at 128 and
wrapping at 256. Other OpenMods inventories and backup dumps are untouched.

Legacy bytes are read unsigned when the saved grave has at most 256 slots. Larger
legacy graves have ambiguous indices: every surviving item record is repacked into
a distinct slot, and uncertain `SlotOrigins` are removed from a copy of the tile NBT.
Native OpenBlocks reclaim then uses its normal equipment/inventory fallback. New
integer saves retain exact origin hints, owner and death message. Already discarded
item records cannot be recovered. Keep this patch installed when loading new large
graves; an older unpatched reader still truncates integer indices to bytes.

The library API is compile-only and pinned to OpenModsLib 0.10.14. Neither optional
mod is installed by this dependency. The mixin is selected only when the pack
integration and OpenBlocks are present; startup verifies both mod versions.

Upstream destination: fix the serializer in OpenModsLib and agree the legacy
grave-origin salvage policy with OpenBlocks. Remove the local bridge once that
behavior is available in a qualified pack version.

Minor check: build with `writeInventoryExampleClasspath`, then run
`scripts/testing/test-openblocks-grave-slots.ps1`. This checks NBT boundary/salvage
behavior and the generated tile method mapping, not a live death/reclaim cycle.

The live smoke uses `scripts/testing/test-openblocks-grave-smoke.ps1` in its exact
disposable world at 216 storage. The JDI fixture seeds 130 uniquely tagged stacks on
the server thread; actual death, grave creation, saved-world reload and reclaim
remain native. It verifies all 130 items return to their original raw indices with
identical typed NBT, including packed grave slots 127–129. It does not qualify
every equipment integration, partial reclaim or the 256-slot legacy boundary.

Verify dense contents across 127/128 and the configured upper capacity, typed NBT,
armor/offhand origins, death, saved-world reload and native reclaim. Test matching
and mismatched backup sizes separately. The current backup guard refuses raw API
sizes above 128; the default 24-stack grave test does not qualify larger graves.
Retain exact-version runtime evidence locally when qualifying this integration.
