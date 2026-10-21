# Per-mod compatibility patches

Use one compatibility file per owning mod, and a package when that integration
outgrows one file. This directory holds the patch scope and acceptance notes;
it is not a runtime configuration format or a set of automatically applied diffs.
An entry here does not enable support or mean the whole mod needs a rewrite.

Existing registrations have real Java owners. Pending entries deliberately have
no empty runtime classes, guessed class targets, dependencies, or enabled Mixins.
Create their Java owner when the first reviewed patch is ready. This keeps the
shipped code small and the difference between planned and working support clear.

| Mod / loaded ID | Patch file | Current implementation |
| --- | --- | --- |
| Minecraft / `minecraft` | [Vanilla](vanilla.md) | Shared inventory adapters; targeted native GUI hooks; batch review pending |
| Et Futurum / `etfuturum` | [Et Futurum](etfuturum.md) | Brewing, enchanting, anvil; reviewed Blast Furnace and Smoker adapters |
| OpenBlocks / `OpenBlocks` | [OpenBlocks](openblocks.md) | Backup guard; large-grave serialization gap |
| Applied Energistics 2 / `appliedenergistics2` | [AE2](applied-energistics-2.md) | Inspection required |
| GregTech / `gregtech` | [GregTech](gregtech.md) | Shared MUI1/MUI2 player hooks; custom paths need review |
| Tinkers' Construct / `TConstruct` | [Tinkers](tinkers-construct.md) | Crafting-station and reviewed Furnace Slab adapters; targeted native hooks |
| Galacticraft / `GalacticraftCore` | [Galacticraft](galacticraft.md) | Equipment adapter and targeted native hook |
| Avaritiaddons / `avaritiaddons` | [Avaritiaddons](avaritiaddons.md) | Compressed-chest adapter |
| GTNH Core / `dreamcraft` | [Dreamcraft](dreamcraft.md) | Baby-chest adapter |
| Ender IO / `EnderIO` | [Ender IO](ender-io.md) | Inspection required |
| Forestry / `Forestry` | [Forestry](forestry.md) | Inspection required |
| Binnie's Mods / `BinnieCore` | [Binnie](binnie.md) | Inspection required; separate from Forestry |
| Thaumcraft / `Thaumcraft` | [Thaumcraft](thaumcraft.md) | Reviewed Alchemical Furnace; other hosts need inspection |
| Iron Chests / `IronChest` | [Iron Chests](iron-chests.md) | Exact native adapter for all 11 chest tiers |
| Storage Drawers / `StorageDrawers` | [Storage Drawers](storage-drawers.md) | Inspection required |
| Extra Utilities / `ExtraUtilities` | [Extra Utilities](extra-utilities.md) | Inspection required |

Backhand, Baubles, BogoSorter and NEI already have dedicated bridges/helpers in
`src/main/java/dev/gilbe/dynamicinventory/compat/`; retain their ownership rather
than duplicating them in each machine integration. MUI1 and MUI2 remain shared
framework integrations in `compat/mui1` and `compat/mui2`.

The [reviewed native machine batch](native-machines.md) lists the exact optional
versions, GUI families and transfer contracts for the additional ordinary machines.

The owner's Spice of Life food-container integration lives in
`compat/SpiceOfLifeCompatibility.java`, keeping its existing adapter ID and policy.
It activates only for the named fixture versions (`2.2.10-carrot` in RC-2 and
`2.2.12-carrot` in the nightly). The inspected container bytecode agrees between
those versions; held-item ownership, number-key swaps, and real transfer/reopen
qualification remain pending. It is not part of the 21-adapter machine batch.

## Implementing a patch

1. Audit the actual screen/container and its non-GUI inventory consumers. F9 is
   discovery, not evidence that native transfer callbacks or special slots are safe.
2. Reuse the [inventory UI API](../gui-adapter-guide.md) and shared framework hooks.
   An adapter registration is ordinary Java code, not a new Mixin. Add a targeted
   Mixin only when the existing hook cannot express the required behavior.
3. Put native registrations in `compat/<Mod>Compatibility.java`, called from
   `BuiltinInventoryUiAdapters` behind the exact loaded-mod check. Keep optional
   API imports out of the dispatcher and client types out of common classes.
   Select any optional Mixins in `LateMixinsLoader` behind the same profile/mod
   rules. Add and test version guards before treating a new integration as supported.
4. Preserve existing adapter IDs and compatibility revisions for presentation-only
   moves. Bump the revision on both peers for changes to binding/transfer behavior.
   Retain the opening gate and exact container matching; do not allow an entire mod
   or package merely because one screen passes.
5. Check real clicks, shift-click/wheel in both directions, full/filtered destinations,
   typed NBT, callbacks and save/reload where relevant. Record artifact, profile,
   mod version and tested capacity. Missing-mod and dedicated launches must not
   link absent APIs or client classes.

F9 suggestions still enter the commented intake in `BuiltinInventoryUiAdapters`.
The generator also checks per-mod files so moving an adapter does not generate a
duplicate. After review, move its registration into the owning file. See the
[integration contract](../inventory-integration-contract.md) for the underlying rules.
