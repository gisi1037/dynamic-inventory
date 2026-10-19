# External MUI2 test mod

A separate Forge 1.7.10 mod using Dynamic Inventory's public API and MUI2.
Run `/dinvexample` in a world to open your real ender chest above your expanded
player inventory. Items stay in their existing saved inventories. The command
requires no cheats and only accesses the opening player's own inventories.

The complete GUI is in [ExampleGui.java](src/main/java/example/dynamicinventory/ExampleGui.java).
Its two integration calls are:

```java
sync.bindPlayerInventory(data.getPlayer());
panel.child(Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot).pos(8, 112));
```

Everything else is normal MUI2 opening, labels, and ender-chest slot construction.
There are no addon mixins, custom containers, custom packets, copied player items,
or imports from Dynamic Inventory implementation packages. MUI2 owns live slots
and synchronization. No native GUI adapter registration is needed for this path.

## Build from this checkout

From the **repository root**, run these commands in PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 --project-dir=examples/mui2-test-mod build
```

The first command builds Dynamic Inventory and its development JAR. The second
runs an independent Gradle project, compiling against that JAR and pinned RC-2
MUI2 `2.3.92-1.7.10`. It does not compile against the main project's class directories
or bundle either dependency. Output: `build/libs/dinvmui2example-1.0.0.jar`
**inside this example directory**. Install that ordinary JAR, not the `-dev` or
`-sources` JAR, alongside Dynamic Inventory `0.8.0-dev` and MUI2 on both client
and server. Restart Minecraft after installation.

For a small development client, the same independent build also supports:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 --project-dir=examples/mui2-test-mod runClient25
```

To copy this project outside the repository, use the Gradle 9.7.1 wrapper from the
root project (or your existing GTNH mod project) and JDK 25. Pass
`-PdynamicInventoryDevJar=C:/path/to/dynamicinventory-0.8.0-dev-dev.jar` to your
Gradle invocation. Use the development JAR for compilation, and the normal
production JAR for an obfuscated Minecraft installation. Nothing has been
published to Maven yet.

## Quick manual check

Use a test world and expendable items. Open `/dinvexample`, move a stack from
expanded player storage to the ender chest, then shift-click it back. Check the
last player storage slots by scrolling, close/reopen, and verify the same items
in the normal player and ender-chest screens. Change GUI scale/window size and
check that the storage viewport remains usable. Test a dedicated server with the
same three mod versions on both sides before treating multiplayer as verified.

This is an integration example, not approval of arbitrary MUI2 screens or a new
full-pack qualification. It has no custom machine filters, crafting outputs, or
extra action packets to qualify.
