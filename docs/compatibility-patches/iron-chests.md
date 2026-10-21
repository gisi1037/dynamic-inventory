# Iron Chests

**Loaded ID:** `IronChest`. **Pinned version:** `6.1.13` in RC-2 and the October 19 daily.
**Status:** native adapter implemented; focused daily runtime smoke passed for all 11 block tiers.
Owner: [IronChestCompatibility](../../src/main/java/dev/gilbe/dynamicinventory/compat/IronChestCompatibility.java).

Every block tier uses the exact container `cpw.mods.ironchest.ContainerIronChest`
and GUI `cpw.mods.ironchest.client.GUIChest`. One adapter registers this inspected
pair with native transfers, zero header padding and zero background inset. It
uses the shared native panel and in-game editor without replacing chest slots,
ownership, packets or validation. No arbitrary subclasses are included.

| Block metadata | Tier | Native slots | Grid |
| --- | --- | --- | --- |
| 0 | Iron | 54 | 9 × 6 |
| 1 | Gold | 81 | 9 × 9 |
| 2 | Diamond | 108 | 12 × 9 |
| 3 | Copper | 45 | 9 × 5 |
| 4 | Steel | 72 | 9 × 8 |
| 5 | Crystal | 108 | 12 × 9 |
| 6 | Obsidian | 108 | 12 × 9 |
| 7 | DirtChest 9000 | 1 | One centered slot |
| 8 | Netherite | 135 | 15 × 9 |
| 9 | Dark Steel | 135 | 15 × 9 |
| 10 | Silver | 72 | 9 × 8 |

The `WOOD` enum entry is an upgrade sentinel with no block GUI. Native transfer
ranges use the tier's slot count as the chest/player boundary and the live
container slot count as the player end, including appended ordinary storage.
`ValidatingSlot` and the native transfer method retain DirtChest's dirt-only rule;
its initial guidebook remains a native item to remove normally.

Netherite and Dark Steel render a full-UV Tessellator background, while other
tiers use a texture blit. Shared native scissor clipping and viewport translation
handle both rendering paths. Existing pack settings remain authoritative: the
daily enables Steel/Dark Steel and disables Netherite/Silver. Registration does
not enable disabled tiers or change recipes. Iron Chest Minecarts is a separate
mod and remains outside this block-container adapter.

All tiers share the editor target because they have the same adapter, GUI and
container classes. A saved edit therefore applies across the family; it is not
an independent preset for each tier. Native chest slot counts and geometry
still come from each chest. Per-tier editing would need an explicit variant key.

Runtime validation must cover tier opening/mapping, smallest/largest layouts,
first/final chest and player slots, full/partial transfers in both directions,
DirtChest rejection of non-dirt, resize/editor click boundaries and exact typed
NBT conservation. Attached Tinkers station behavior is a separate integration.

The initial smoke passed 149 checks at the default 72 storage slots: all 11 tiers
opened with exact canonical mappings and supported editor open/cancel. Copper,
DirtChest and Dark Steel passed appended-slot shift transfers and NEI wheel
transfers in both directions, with exact typed-NBT conservation. DirtChest rejected
the non-dirt probe. This is focused coverage at 1920×1080 and GUI scale 2; further
layout/resize checks and visual tuning remain separate from the registration.
Retain exact-version runtime evidence locally when qualifying this integration.
