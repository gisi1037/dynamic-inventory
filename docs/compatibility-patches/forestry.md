# Forestry

**Loaded ID:** `Forestry`. **Reviewed version:** `4.11.39`.
**Status:** stationary-machine integration; player visual review completed by Gilbert
on 2026-11-02. Broader gameplay/server qualification remains separate.
Java owner: `ForestryCompatibility`. Mixins: `mixins/forestry/`.

The integration registers 21 exact native container/GUI pairs. It does not enable
the whole `ContainerForestry` hierarchy. Missing Forestry or another version skips
both the declarations and their mixins. Binnie's Mods remains a separate integration.

| Family | Screens |
| --- | --- |
| Core | Analyzer |
| Apiculture | Bee housing (apiary/bee house), alveary, hygroregulator, sieve, swarmer |
| Energy | Biogas engine, electrical engine, peat engine, generator |
| Factory | Bottler, carpenter, centrifuge, fabricator, fermenter, moistener, rain tank, squeezer, still, worktable |
| Farming | Multifarm |

These screens use nine player columns, native texture crop 4, the common frame
style/editor, and the Machines Vertical/Scroll preference. Native machine slots
and the original 36 player slot IDs retain their positions in the container;
additional ordinary player slots are appended. Item positions are never repacked
when visible rows change.

`ForestrySlotUtilMixin` extends the shared shift-click routing using the canonical
binding. Forestry still performs its own machine filtering, phantom exclusion,
stack merging, extraction callbacks, and worktable crafting/quest callbacks.
Appended player slots are excluded from Forestry's machine destination loop.
Native synchronization and custom-channel GUI actions wait for opening agreement.
Widget origins and ledger hit testing follow the native section; ledger drawing
escapes the sheet crop so side tabs remain visible.

## Deliberately deferred

- Backpacks, naturalist backpacks/storage, and handheld tools: held-item locks,
  hidden pages, and Forestry's six-bit item-slot addressing need separate review.
- Bee-house minecarts: entity-host lifecycle.
- Escritoire and postal screens: minigame/paging, custom selections, and item-backed
  letters need their own review.
- Backpack automatic pickup and other non-GUI inventory consumers are not certified
  by these machine adapters.

## Focused checks

The installed nightly loaded all 21 GUI/container pairs and the shared production
mixins. A detached native carpenter fixture checked both transfer directions through
the final expanded slot, typed NBT and item counts, full/filtered destinations, and
phantom/output exclusion. A real carpenter window opened in a disposable world
and attached without a refusal. Opening agreement and native side-ledger hit testing
passed, and the final render retained the side tabs and inventory caption. These
checks do not certify every recipe or machine.

For manual review, open each family in both Vertical and Scroll modes, check tank
and circuit tooltips, expand/click side ledgers, and test worktable recipe memory,
crafting and recipe conflicts. Confirm NEI recipe filling, ownership restrictions,
and live resizing/reopening with actual stored items before treating the group as
fully qualified.

Source review: [Forestry 4.11.39](https://github.com/GTNewHorizons/ForestryMC/tree/4.11.39).
