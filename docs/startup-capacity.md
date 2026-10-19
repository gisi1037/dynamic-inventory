# Capacity and layout configuration

The default inventory is now **72 storage slots plus nine hotbar slots**. The player
menu defaults to Compact: nine columns by eight rows. Armor, crafting, Baubles and Backhand are
separate; Backhand retains raw index 36. Existing items keep their logical identities.

## Configuration

Close Minecraft (and the dedicated server, when applicable), then edit the game
directory's `config/dynamicinventory.cfg`:

```text
capacity {
    I:storageSlots=72
}
```

Supported storage capacity is 27–216, excluding the nine hotbar slots. Partial rows
are supported. Restart to apply a change. On multiplayer, the server supplies capacity
during the Forge login handshake before player/container construction. Clients may
have a different local setting; starting a local world restores that local value.
Both sides must run the same compatible mod version and Backhand configuration.

The grid icon changes client presentation, not capacity. Wide, Compact and Tall
presets calculate their rows from the current capacity. Existing custom layouts are
retained; the original unversioned 18×3 default migrates to Compact. Scrolling remains
available when the scaled window cannot fit all eight rows.

When reducing capacity, occupied removed slots are preserved in namespaced save
recovery data. Increasing capacity restores those stacks into their original slots
when vacant. Existing stacks are never overwritten. Keep backups before changing
capacity or removing this development mod; recovery data is not a visible overflow menu.

## GT machine interfaces

The standard MUI1 and MUI2 player-inventory bindings keep nine columns and expand
their panel vertically to display as many storage rows as the scaled window permits.
The hotbar moves below the visible rows, and native machine controls retain their
sizes. The native panel background stretches with the inventory.

When all rows fit, scrolling is unnecessary. With less available scaled height,
the panel displays fewer rows and retains its native scrollbar. Minecraft's larger
GUI scale numbers usually leave *less* space in GUI pixels, so available height—not
the scale setting's label—determines the fallback. Resizing the window recalculates it.

Custom interfaces that bypass the supported player-inventory builders still need
their own adapters. This does not claim universal compatibility with every GT screen.

The machine layout is a per-client preference in `config/dynamicinventory-client.cfg`.
Open the player inventory's grid icon layout dialog and select **Machines: Vertical**
or **Machines: Scroll**. The choice is saved and applies when the next machine inventory
opens, with no server restart. Vertical grows the native panel to use available scaled
screen height and falls back to its scrollbar when necessary. Scroll keeps three rows
visible and uses the scrollbar. Neither option changes inventory capacity.

The same layout dialog has an independent **Containers: Full box** / **Containers: Compact**
choice. It is a per-client preference stored as `B:compactNativePanels=false` in
`config/dynamicinventory-client.cfg`. Full box is the default and joins the native header
and expanded inventory into one rectangle; Compact keeps the existing T-shaped silhouette.
This choice changes panel geometry only. It does not select Vertical or Scroll machine
layout, which remains a separate preference.
