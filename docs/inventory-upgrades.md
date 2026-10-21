# Inventory upgrades

Two experimental items use the same server-authorized transaction as `/dinvsize`.
Both sides must install the matching build. The nine hotbar slots never change.

* **Inventory Capacity Module** (`dynamicinventory:capacity_module`): right-click
  while holding it to permanently add storage. The server consumes exactly one
  module after a successful resize, including in creative mode. A refused request
  consumes nothing. Defaults: nine slots per module, at most sixteen modules.
* **Inventory Expansion Belt** (`dynamicinventory:inventory_belt`): equip in a
  Baubles belt slot for nine temporary storage slots. Multiple belts do not stack.
  This item is registered only when Baubles is installed. Permanent modules work
  without Baubles.

No recipes are registered. Pack developers can supply progression-appropriate
recipes through their normal recipe tools. Items are available in Miscellaneous
creative inventory, NEI and `/give`. Their original 16×16 placeholder textures use
shaded metal, circuit-board colors and leather to fit the pack's item style.

## Server configuration

`config/dynamicinventory-upgrades.cfg` is read at startup:

| Category | Setting | Default | Meaning |
| --- | --- | --- | --- |
| modules | enabled | true | Allow consuming permanent modules |
| modules | slotsPerModule | 9 | Storage slots added per future module (1–189) |
| modules | modulesPerPlayer | 16 | Lifetime consumption limit (0–189) |
| belt | enabled | true | Apply equipped belt bonuses |
| belt | bonusSlots | 9 | Temporary storage bonus (1–189) |

Total storage is capped at 216 slots. A module is refused if its complete permanent
increase would exceed that bound, or if it would add no usable capacity. Changing
the per-module setting affects future purchases; existing permanent capacity is
retained. Lowering the purchase limit never revokes previously purchased capacity.
Changing/disabling the belt recalculates its temporary bonus safely. The server's
settings are authoritative; tooltips intentionally do not quote a client's local
bonus values.

## Safety and persistence

Permanent base capacity and modules consumed are stored in Forge's
`PlayerPersisted.dynamicinventoryUpgrades` player data, separately from the
effective inventory shape. They survive saving, reconnecting and death. The belt
bonus is derived from actual equipment, so login and dimension travel cannot add
it repeatedly. Existing players start from their current capacity when they first
use a module or belt. Players who never use either retain existing startup behavior.

Reconciliation runs on the server at the end of a player tick, outside slot and
Baubles callbacks. Equipment is checked every ten ticks; no container is rebuilt
when capacity is unchanged. The existing resize agreement locks input until client
and server have accepted the same slot map. Put down cursor stacks and clear the
player's 2×2 crafting grid before resizing. An open external GUI is closed safely.

Normal belt removal is blocked if any slot that would be removed contains an item.
If another mod forcibly removes equipment, or a changed server configuration
reduces its bonus, occupied capacity is retained temporarily. A chat message
explains the refusal; reconciliation retries after those slots, the cursor and the
crafting grid are clear. The belt carries server-authored prediction hints in ordinary item NBT so the
client also refuses unsafe removal; those hints never authorize server capacity.
No items are dropped or silently rearranged. A forced
removal can therefore leave temporary extra capacity until it can shrink safely.

`/dinvsize <player> <storage-slots>` still sets **total** storage. For an upgraded
player it adjusts the permanent base after subtracting the currently equipped
bonus; it does not reset the lifetime module count. Remove the belt before setting
totals below `27 + belt bonus`. Unknown persistent player-container replacements
still require a resize integration.

## Development

`UpgradeCapacity` is the pure cap/limit policy. `InventoryUpgrades` coordinates
server ticks and persistent ownership; `BaubleUpgradeSupport` is the isolated,
optional equipment adapter. All array, container and network changes continue to
go through `InventoryResizing`. Do not resize synchronously from item/slot callbacks.

`UpgradeCapacityFixtures` checks policy boundaries. `/dinvupgradetest` is a runtime
fixture available only in development builds and specifically named disposable
upgrade qualification worlds; it is excluded from the production JAR.
