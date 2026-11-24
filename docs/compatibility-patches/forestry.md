# Forestry

**Loaded ID:** `Forestry`. **Reviewed versions:** `4.11.40` for GTNH 2.9.0
(with `4.11.39` also accepted) and `4.11.41` for GTNH 2.9.1.
Gilbert completed visual review of the original stationary machines on 2026-11-02.
The additional hosts and detached layouts still need in-game visual review.
Java owner: `ForestryCompatibility`. Mixins: `mixins/forestry/`.

The integration registers 35 exact inventory containers and 38 container/GUI pairs.
Missing Forestry or an unreviewed version skips its declarations and mixins.

| Family | Screens |
| --- | --- |
| Core | Analyzer, Escritoire, Naturalist Chest, Soldering Iron |
| Apiculture | Bee housing (apiary/bee house), alveary, hygroregulator, sieve, swarmer, bee-house and apiary minecarts, Habitat Locator, Imprinter, Beealyzer |
| Energy | Biogas engine, electrical engine, peat engine, generator |
| Factory | Bottler, carpenter, centrifuge, fabricator, fermenter, moistener, rain tank, squeezer, still, worktable |
| Farming | Multifarm |
| Item storage and genetics | Backpacks (both tiers), Naturalist Backpack, Treealyzer, Flutterlyzer |
| Food and mail | Infuser, Letter, Mailbox, Stamp Collector, Trade Station |

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

Item-backed screens preserve Forestry's owner-slot lock and can address the full
expanded inventory. The paged Naturalist screens keep Forestry's page controls
and hidden slots. Catalogue, account setup, and handbook screens have no player
inventory to expand. Binnie's Mods has its own containers.

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
