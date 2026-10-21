# Forestry

**Loaded ID:** `Forestry`. **Status:** inspection required.
Future Java owner: `ForestryCompatibility`.

Separate machine GUIs, specialized crafting/storage and item-backed inventories.
Audit actual player slot construction and native filters/callbacks for each family.
Include backpacks and other non-GUI inventory consumers in the compatibility review;
a visually correct machine does not establish gameplay-wide support.

Reuse common native/MUI binding and layout services. Keep species/item metadata,
slot restrictions and native crafting semantics under Forestry's ownership.
Binnie's Mods has a separate patch file because it owns additional behavior.

Verify typed item NBT, filtered/full destinations, both transfer directions,
item-backed self-movement restrictions, backpack pickup behavior, save/reload and
missing-mod/dedicated launches.
