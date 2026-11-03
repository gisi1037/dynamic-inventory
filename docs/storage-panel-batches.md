# Storage panel test batch 1

## Player visual review — 2026-11-02

Manual visual testing was reported complete for all chest interfaces, Forestry
interfaces and the previously supported AE2 interfaces. The standalone Network
Tool was reported missing separately; its new adapter and the Advanced Network
Tool adapter remain pending installed review. This is visual acceptance, not a
claim that every transfer, recipe or multiplayer path has been qualified.

The first expansion of modular panel support covers the eleven Iron Chests block
tiers, GTNH's Baby Chest and Avaritiaddons' Compressed Chest. They retain their
existing native adapters, item storage and transfer rules. Revision 2 admits
their exact GUI/container pairs to the same client/server panel-host policy used
for vanilla interfaces. Unregistered hosts and subclasses remain excluded.

Use the shared SFM nightly 803 instance. Run
`scripts/testing/setup-storage-panel-lab.ps1` once to create
`DI-Baseline-Storage-Panel-Lab-Qualification` with thirteen labeled stations and
starter stacks. The helper refuses to overwrite an existing lab. Reopen that
world for later build/test/fix iterations. Netherite and Silver recipes remain
disabled by the pack; their placed test blocks do not alter that configuration.

The focused smoke is `scripts/testing/test-storage-panel-lab.ps1`. It checks
actual opening agreement, the canonical player-slot mapping, the panel menu,
one-click panel creation, slot allocation, dragging, persistence and live slot
identity across the thirteen variants, with isolated layout preferences.

Storage headers crop before the old player-inventory artwork. Baby Chest keeps
its chest illustration over the current theme fill, and regular Iron Chests use
eight-pixel side and bottom slot padding. Compressed Chest excludes the original
notched lower frame. Native texture UVs remain unchanged for resource packs.
`StoragePanelGeometryFixtures` checks these boundaries without Minecraft; the
lab smoke also checks the actual Iron Chest slot margins in the running client.
Visually check attached and detached modes when changing a header crop.

For manual testing, left-click the split icon to detach; right-click it for panel
settings. Try one-slot panels, resizing with Shift, moving panels past the chest,
global/default layouts and transfers with a full hotbar. DirtChest should still
accept only dirt. Note the chest name when reporting a problem so the next
iteration can target that variant. Iron Chests tiers currently share one
per-interface profile because they use one native GUI/container family.

This is a focused testing batch, not a claim of complete support for every mod.
Keep new groups small enough to review in game before extending the host list.

## Batch 2: HarvestCraft processing

The next modular group covers HarvestCraft's Churn, Oven, Quern, Animal Trap,
Fish Trap, Apiary, Grinder and Presser. Shared nightly 803 disables the Grinder
block; seven working stations and a disabled marker occupy the next two rows
of the same lab, at Z=28 and Z=36. Use
`scripts/testing/add-harvestcraft-panel-lab.ps1` once, then
`scripts/testing/test-harvestcraft-panel-lab.ps1` for the focused smoke.
The first script checks that the target area is empty and preserves existing
stations and items. See [HarvestCraft](compatibility-patches/harvestcraft.md).

`scripts/testing/test-panel-main-routing.ps1` uses a separate disposable world
and isolated layout file for Main/hotbar transfers, full destinations, subpanel
returns, finite/infinite NEI spawning, typed NBT, and global/custom GUI scaling.
Its temporary client cheat-mode selection is restored after each spawn; NEI's
existing server permission checks remain in charge.

## Batch 3: Applied Energistics 2

The 27 exact AE2 machine/terminal/tool adapters now share the modular panel
host, including the ordinary wired/core wireless/portable terminal families.
Upgradeable machines retain their separate native upgrade/toolbox frames; their
bounds reserve space in the inventory-panel fitter. Native slot flags and locked
player slots remain AE2's. AE2 addon terminal families remain separate contracts.

The labeled stations occupy Z=48, 56, 64, 72 and 80, behind HarvestCraft. Six
north-facing terminals have isolated powered glass-cable networks with a Drive
and Interface. A chest at (16, 4, 80) holds a charged Portable Cell, core Wireless
Terminal, storage cells and a Network Tool. Add a cell to a Drive for network
item testing; link the Wireless Terminal via Security before using it.

`scripts/testing/add-ae2-panel-lab.ps1` prepares the station commands from the
installed registry, checks empty targets and refuses repeated installation. It
uses AE2's native API on the server thread to place terminal parts, preserving
player inventory contents. Verification checks only blocks and part presence;
the player reviews the UIs. See [AE2](compatibility-patches/applied-energistics-2.md).


## Batch 4: Et Futurum, Ender IO and Extra Utilities

GTNH 2.9.0 now registers 45 inventory-bearing container families across these
three mods, with exact alternate GUI presentations where needed. Control-only
screens retain native behavior rather than receiving artificial player slots.

The existing separate `DI-Baseline-Storage-Panel-Lab-Qualification` save now has
116 new labeled stations at X=-24..24, Y=4, Z=144..272: 25 Et Futurum, 39 Ender IO
and 52 Extra Utilities. Tools and filters are in the chest at (24,4,104). Golden
Bag is disabled by the pack and is marked accordingly. The Trading Post has a
stationary test trader. Empty machine inventories initialize through their native
block tile factories on first access; shulker types, conduit contents, signs,
supply chest and test trader have explicit saved NBT.

Gilbert requested offline preparation while keeping the multiplayer client open.
`scripts/testing/prepare-native-ui-lab.py` uses the test save's own Forge block/item
IDs, refuses occupied targets or an ungenerated chunk, backs up the affected
region files and updates existing chunks. It verifies every placed block/metadata
from disk and preserves existing chunk payloads, inventories, entities, level.dat
and player files. Evidence and backups are under `.setup/three-mod-ui-batch/`.
The helper requires `nbtlib`; the task used an isolated dependency directory.

The new artifact is installed in `GTNH-Dev-Shared-2.9.0-J26`. Runtime/visual review
is pending: the development client was left stopped as requested. Its diagnostic
port is 5011 for this run, keeping the multiplayer client's port 5010 separate.
The shared installer accepts `-DebugPort 5011` without changing Prism's Java
selection. No public repository upload or release is part of this batch.
