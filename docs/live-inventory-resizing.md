# Live inventory resizing (development)

The first implementation uses an operator command:

```text
/dinvsize <player> <storage-slots>
```

Storage can be 27–216 slots. The nine hotbar slots are additional. For example,
`/dinvsize Developer 90` gives that player 90 storage slots and nine hotbar slots.
The startup setting remains the default for players without a saved override.
Capacity belongs to each player; this command does not change other players.

## What happens during a resize

The server checks the requested capacity and requires an empty cursor and empty
player crafting grid. Shrinking requires every removed slot to be empty. It closes
an external container, then checks again because closing a screen can return items.
Retained items keep their logical indices; resizing does not sort or compact them.
Backhand's raw offhand position remains fixed at 36.

The server replaces the backing array and rebuilds its persistent player container.
It sends a capacity revision and a digest of the exact slot map to the client.
The client closes the old screen and rebuilds its own player container. Both sides
withhold inventory interaction until the maps agree and a fresh full snapshot has
been sent. Unknown persistent player-container replacements are refused.

The override and revision are saved alongside the inventory and inherited at
respawn. As with ordinary inventory edits, persistence uses Minecraft's normal
player save lifecycle. A failure after server commitment disconnects the peer;
it does not restore an obsolete backing array.

## Integration rules

- Read `InventoryLayout.of(player.inventory)` for that player's capacity. The
  no-argument layout helpers describe the startup default only.
- A container binding captures a shape and revision when created. Do not reuse it
  after resizing; reopen the host to create a fresh binding.
- Pass the owning `InventoryPlayer` to the MUI2 widget helpers when constructing
  widgets on the server or for another player. Client-only legacy helpers resolve
  the local viewer, with the startup default available before login.
- Request changes through `InventoryResizing.resize` on the server main thread.
  Setting an array length or changing the process-wide default bypasses the safety
  transaction and is unsupported.

This is an internal development lifecycle, not a stable third-party upgrade API.
Upgrade items/Baubles, automatic item relocation on shrink, and arbitrary custom
player-container reconstruction are separate work. Full-pack compatibility still
requires testing inventory-manipulating mods that may cache array sizes or slot IDs.

## Checks

`scripts/testing/test-inventory-shapes.ps1` exhaustively checks all supported
old/new capacity pairs with and without Backhand. `InventoryResizeProtocolFixtures`
checks packet bounds and agreement identities. `StorageHarness resize-state <n>`
asserts the connected client's owned capacity, backing length, slot map, and released
input gate. These checks do not substitute for full-pack lifecycle qualification.

With the two offline fixture peers and dedicated
`DI-Baseline-Live-Resize-Qualification` world already running, execute the focused
lifecycle matrix from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/testing/test-live-capacity.ps1 -DeveloperPort 5015 -ObserverPort 5016
```

This runner seeds and clears disposable test inventories. It checks peer metadata
and the dedicated fixture world before those operations. It covers two-player
isolation, occupied shrink refusal, a resize with an external chest open, persistence,
respawn, dimension travel, and the minimum/maximum capacities. Evidence defaults to
`.setup/live-resize/runtime`. The runner records its actual checks and artifact identity locally. It does not
claim every mod path or full-pack dedicated multiplayer acceptance.
