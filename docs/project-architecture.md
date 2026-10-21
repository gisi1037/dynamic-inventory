# Architecture

## Canonical inventory and index spaces

`InventoryPlayer.mainInventory` holds the authoritative ordinary player items.
Dynamic Inventory expands that storage; GUI widgets are views of the same items,
not a second store. Server inventory state governs clicks and synchronization.

Three index spaces must stay separate:

1. Logical ordinary indices: hotbar first, then storage, excluding equipment.
2. Canonical raw inventory indices: include Backhand's reserved offhand at raw 36.
3. Container slot IDs: include machine, crafting, equipment, and player slots.

`InventoryLayout` translates between logical and raw indices. Native
`ContainerInventoryRegion` and public `PlayerInventoryBinding` map logical indices
to container IDs. Preserve existing native slot IDs; append added player slots.

## Capacity and persistence

`InventoryCapacity` supplies a startup default; immutable per-player `InventoryShape`
records actual capacity, offhand mapping and revision. Live resizing uses a server
transaction and a separate `DI|Size` agreement before input and contents are released.
Default storage is 72, supported range 27-216, with nine hotbar slots.
Armor and mod equipment retain their own ownership.

`InventoryCodec` preserves legacy item representation and writes additional items
in namespaced versioned data. Saved live overrides retain effective capacity.
Startup capacity reductions quarantine displaced stacks in recovery data; live
shrinks instead refuse occupied removed slots. `InventoryUpgrades` separately owns
permanent base capacity and the consumed-module count in Forge's `PlayerPersisted`
tag. Equipment bonuses are derived from actual Baubles slots, avoiding double grants.

Standalone inventory NBT roundtrips use a metadata record at unused byte slot 255.
Nested item NBT is copied to preserve snapshot independence.

## Native containers

Public adapters match reviewed container/GUI classes. `ContainerInventoryRegion`
validates canonical slot ownership, records the native map, and prepares appended
storage slots before mutation. A caller can supply a slot factory to preserve
validation, limits, and callbacks for added player storage.

`ContainerRegionMixin` widens only complete recognized player destination ranges.
Generic transfer policies handle ordinary storage; specialized output/crafting
callbacks remain with the host. Unknown classes are not automatically augmented.

The runtime opening gate compares registered-native slot fingerprints before
releasing standard updates or accepting clicks. Startup declarations and per-window
fingerprints serve different purposes. See the [contract](inventory-integration-contract.md).

## GUI ownership

`NativeInventoryPanel` decorates supported native screens in place. The original
container and screen classes retain their slot interactions, native headers, and
packets. `AttachedChestLayout` budgets two panels for modern Tinkers crafting stations:
attached storage above, player inventory and crafting below. Small and compressed
chests use the same responsive rectangular path. Classic station mode retains its
native layout. Rectangular column selection never adds inventory capacity.

MUI1 and MUI2 bridges use their frameworks' live-slot and sync APIs. The public
MUI2 factory returns an inventory widget group; `sync.bindPlayerInventory(player)`
registers its matching expanded player mapping. Hotbar and storage share the normal
MUI2 player transfer group, while layout and sorting still distinguish their roles.
This prevents quick-move from selecting the other part of the player's inventory.

The standard MUI2 factory participates in responsive resizing. Its fixed-column
variant leaves geometry to the host. An optional native-screen MUI2 presentation
overlay paints frames/backgrounds only; it does not replace native items or input.
Do not add live MUI2 slots over native container slots.

## Source layout

| Directory | Responsibility |
| --- | --- |
| `src/main/java/.../api` | Public integration types and MUI2 widget factory |
| `inventory`, `persistence` | Capacity, indices, serialization, recovery |
| `container`, `network` | Native slot mapping, transfers, opening agreement |
| `client`, `compat`, `mixins` | Rendering and reviewed framework/mod hooks |
| `src/storageTest`, `src/backhandTest`, `src/packTest` | Opt-in runtime fixtures; excluded from production JAR |
| `src/rc2Compat` | Small pinned-library development environment support |
| `scripts` | Build, launch, discovery, and test tools |
| `examples/mui2-test-mod` | Independent third-party consumer mod |

## Compatibility boundary

A shared GUI framework reduces repeated integration work. It cannot infer machine
recipes, filters, custom packet semantics, grave serialization, or arbitrary mods'
assumptions about inventory size. Keep the core independent of UI libraries and
qualify each reviewed behavior family. The pinned RC-2 artifact list is in
`pack-compatibility-rc2.json`; it is a dependency reference, not proof of universal support.
