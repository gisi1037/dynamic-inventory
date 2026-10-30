# Inventory UI API examples

These short source examples use only Dynamic Inventory's public `api` packages and
normal Minecraft/Forge/MUI2 APIs. They are reference snippets, not a standalone mod
or part of the shipped runtime.

For a runnable, separately built addon, see the [MUI2 test mod](../../mui2-test-mod/README.md).
It opens an ender-chest/player-inventory GUI with `/dinvexample` and consumes the
public API from a development JAR.

Compile them against the matching Forge 1.7.10, GTNH RC-2, ModularUI2, and
Dynamic Inventory API artifacts for the profile being targeted. The native example
registers during mod initialization on client and server, before a matching container
is opened. Its `toNativeSlots()` policy sends added player-slot quick moves to the
original machine slots, respecting their validation and stack limits. Use
`nativeTransfers()` only when the existing container already handles added source IDs.
Review crafting/output callbacks and special input behavior before choosing a policy.

Native policies use absolute container slot IDs for their source and destinations;
`PlayerInventoryBinding` separately maps logical player indices to those IDs. Built-in
policies return a copy of the original stack when an item moved, or `null` when nothing
moved, and perform the source slot's normal pickup/update callbacks. They do not call
`Slot.onSlotChange`, which some crafting-result slots use to consume ingredients or
refresh recipes. Keep those slots on the container's native transfer path.

Adapters that need a mod's native player-slot subclass can opt into
`InventoryUiAdapter.Builder.additionalSlots(PlayerInventorySlotFactory)`. The factory
receives the container, the canonical `InventoryPlayer`, and each raw player inventory
index, and must return a fresh unattached slot backed by that exact inventory and index.
It creates only appended storage slots; native container slots remain untouched. Use the
slot subclass to preserve custom restrictions, stack limits, lock rules, or callbacks. Do
not mutate the container inside the factory; the adapter creates and validates all
requested slots before appending any of them.

Until an artifact is published, build this repository and use
`build/libs/dynamicinventory-0.8.0-dev-dev.jar` in another Forge development workspace;
the ordinary `dynamicinventory-0.8.0-dev.jar` is the game artifact. For example:

```groovy
dependencies {
    implementation(files('../dynamic-inventory/build/libs/dynamicinventory-0.8.0-dev-dev.jar'))
}
```

Adjust the local checkout path and retain your normal Forge and MUI2 dependencies.
Register the native integration in a mod with `required-after:dynamicinventory` on
both sides. From this repository, compile the examples without starting the game:

```powershell
.\scripts\tools\Test-InventoryUiExamples.ps1
```

The MUI2 examples create panels with live player `ItemSlot` widgets. Call
`PanelSyncManager.bindPlayerInventory(player)` for the same player before building
the panel so the widget sync keys have a registered player inventory. The responsive
factory is the normal choice; the fixed columns/rows overload leaves viewport sizing
to the caller.

The API is experimental version 1 in this unreleased development build; no compatibility
guarantee is implied. These examples do not imply runtime qualification for other mods
or GUIs. Review native
transfer callbacks, filters, output slots, and synchronization ownership, then qualify
the target profile before enabling integration.
