# Vanilla modular inventory lab

World: **DI-Baseline-Vanilla-UI-Lab-Qualification**, in the nightly 795 instance.
It is a separate flat survival world with cheats, peaceful difficulty, fixed noon,
disabled mob spawning, test materials and enchanting XP. Spawn is `(0, 4, -8)`;
walk south through the labeled stations. Existing saves and panel preferences
are not used as a world template.

| Z | Stations, west to east (X = -12, -4, 4, 12) |
| --- | --- |
| 0 | Single chest, double chest, trapped chest, ender chest |
| 8 | Crafting table, furnace, dispenser, dropper |
| 16 | Hopper, brewing stand, enchanting table with bookshelves, anvil |
| 24 | Beacon with iron base, command block, sign, jukebox |
| 34 | Villager with a repeatable trade, saddled horse, chested donkey, chested mule |
| 44 | Chest minecart at X=-4, hopper minecart at X=4 |

Left-click the inventory's split icon to detach or reattach; right-click for the
same named-panel, allocation, geometry, appearance and profile controls used by
the player inventory. Factory defaults remain attached. Native machine slots,
input rules, result callbacks and controls stay with their original container.

Sneak-right-click a tame horse/donkey/mule to inspect its inventory, or mount it
and press E. Villagers and minecarts open with right-click. Command blocks require
creative mode (`/gamemode 1`); replace a sign to open its editor. Sign, book and
command editors have no embedded player inventory and retain their native UI.
The creative item catalogue also retains its native UI. A jukebox has no screen.

The nightly replaces brewing and enchanting with Et Futurum. Those exact screen
pairs are covered alongside the original vanilla screens; arbitrary modded
subclasses are not admitted by inheriting a vanilla container name.

## Rebuilding and focused checks

With the local test client at its main menu and debug port 5006 enabled:

```powershell
scripts/testing/setup-vanilla-ui-lab.ps1
scripts/testing/test-vanilla-modular-panels.ps1
```

Setup refuses to replace an open world, loads the station chunks before placing
blocks, and saves/reopens the completed lab. Use a fresh world name to create a
second lab. `-PrepareOnly` only generates station/command files. The explicit
`-PopulateExisting` switch is for recovering a partially populated lab; do not
use it on a completed lab because it summons additional entity stations.

The checks require this lab, temporarily use an isolated panel preference file,
then restore the original preferences after each host. Output is under
`.setup/vanilla-ui-lab/`. They check actual station openings, canonical slot
mapping, opening agreement, named panels, allocation, dragging, persistence and
Slot/container identity. The development-only `VanillaPanelClientFixtures` also
draws thirteen original vanilla variants attached, detached, split and reattached
and verifies unchanged item contents. It never clicks a machine slot.

These are focused presentation checks, not a complete gameplay or multiplayer
qualification of crafting, enchanting, trading, repairs or every resource pack.
