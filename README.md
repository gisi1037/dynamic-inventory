# Dynamic Inventory

An experimental Forge 1.7.10 mod for larger player inventories in GT New Horizons.
The pinned compatibility targets are **GTNH 2.9.0-RC-2** and
**daily-2026-10-19 build 795**; other nightlies require requalification. This is a development
snapshot, not a stable release or a claim of support for every interface in the pack.

The default is **72 storage slots plus the nine-slot hotbar**. The server selects
initial capacity at startup; experimental live resizing safely rebuilds and agrees
the slot map before releasing contents or input. Client layout preferences change
presentation without creating another inventory.

An **Inventory Capacity Module** permanently adds storage, and an optional Baubles
**Inventory Expansion Belt** adds temporary storage while equipped. Both default to
nine slots. Server configuration controls bonuses, limits and enablement; pack
authors supply recipes. Occupied slots are never discarded when removing a bonus.
See [inventory upgrades](docs/inventory-upgrades.md) and
[live resizing](docs/live-inventory-resizing.md).

Native container adapters and shared MUI1/MUI2 bindings expose the canonical player
inventory in supported interfaces. A conservative scroll presentation can reuse
reviewed vanilla storage hosts; unknown container handling still requires review.
Custom synchronization, equipment, crafting results and transfer rules remain native.
The in-game editor adjusts presentation, not item ownership or compatibility rules.

## Download and install

The first testing release is [0.8.0-alpha.1](https://github.com/gisi1037/dynamic-inventory/releases/tag/v0.8.0-alpha.1).
Install **`dynamicinventory-0.8.0-alpha.1.jar`** in the `mods` folder on both client
and server, replacing any older Dynamic Inventory JAR. The `-dev` and `-sources`
JARs are developer downloads; do not install those in Minecraft. Back up your save
and try this experimental release in a disposable world first.

Select the matching pack profile in the launcher's **Java/JVM arguments** on both
sides, before starting Minecraft:

| Pack | Required JVM argument |
| --- | --- |
| GTNH 2.9.0-RC-2 | `-Ddynamicinventory.profile=rc2-integration` |
| daily-2026-10-19 build 795 | `-Ddynamicinventory.profile=daily-2026-10-19` |

Use the pack's supported Java runtime. Other pack builds require requalification;
version guards deliberately reject mismatched integration dependencies. The
starting capacity is configured in `config/dynamicinventory.cfg`; module and belt
rules are in `config/dynamicinventory-upgrades.cfg`. See [configuration](docs/startup-capacity.md)
and [inventory upgrades](docs/inventory-upgrades.md).

## Build

Install JDK 25 and use the Gradle wrapper. The build produces Java 8-compatible
mod classes; the RC-2 Java 17–26 runtime is the development target.

```sh
VERSION=0.8.0-dev ./gradlew build
```

On Windows, from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 build
```

Artifacts are written to `build/libs/`:

- `dynamicinventory-0.8.0-dev.jar`: install in Minecraft.
- `dynamicinventory-0.8.0-dev-dev.jar`: compile an external mod against the API.
- `dynamicinventory-0.8.0-dev-sources.jar`: matching source.

The mod is required on client and server. Use matching versions and compatibility
profiles. Back up saves and use disposable worlds during development. Do not remove
an expanded-inventory mod from a real save without migrating its additional items.
Artifacts and API coordinates have not been published to Maven.

## Development environments

The default Gradle profile is an isolated small Forge environment. Optional pinned
library profiles are `rc2-mui2`, `rc2-backhand`, and `rc2-integration`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 runClient25 -PinventoryProfile=rc2-integration -PinventoryStorageTests
```

The fixture switch adds development commands only; they are excluded from the
production JAR. Running a dedicated server requires accepting Minecraft's EULA
in its generated game directory. It is never accepted automatically.

VS Code tasks cover builds, development clients, debugging, F9 template generation,
and the external example. Configure `JAVA_HOME` locally; no user-specific JDK paths
are checked into the editor settings. See [contributing](CONTRIBUTING.md).

The optional Prism launcher helper prepares a separate marked RC-2 instance from
`pack-baseline.json`. A local UI preview can be built and launched with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\full-pack.ps1 -ExperimentalInventoryIntegration -VisualPreview
```

Close that development instance before replacing its JAR. The helper backs up its
previous mod and settings. A preview is not runtime qualification; the qualified
launch path requires locally generated, matching artifact reports.

## Integrate from another mod

For a developer-owned MUI2 screen, use the normal player binding and the public
live-slot factory:

```java
sync.bindPlayerInventory(player);
panel.child(Mui2InventoryWidgets.playerInventory((index, slot) -> slot).pos(8, 112));
```

Import `dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets`.
MUI2 owns the container, live slots, input, and synchronization. Hotbar and storage
share one transfer group, so quick-move does not bounce items within player storage.

The [standalone MUI2 example](examples/mui2-test-mod/README.md) builds independently
against the development JAR. Install its JAR on both sides and run `/dinvexample`
to open your ender chest above the expanded player inventory. Its source has no
addon mixins or imports from implementation packages.

For existing native GUIs, use explicit adapter registrations and reviewed transfer
policies. The F9 audit/template workflow assists inspection; it never enables an
unknown GUI automatically.

- [GUI adapter guide](docs/gui-adapter-guide.md)
- [Inventory integration contract](docs/inventory-integration-contract.md)
- [Architecture](docs/project-architecture.md)
- [Configuration](docs/startup-capacity.md)
- [Testing](docs/testing.md)
- [Short Java examples](examples/java/example/README.md)

## Current limits

Capacity is 27–216 storage slots. Startup defaults, live operator commands, permanent
modules and an optional Baubles belt are supported. The hotbar remains nine slots.
The creative item catalog has not been migrated.
Coverage of standard framework builders does not imply that all custom machine
screens work. The native opening agreement covers registered native windows and
standard packets; arbitrary custom actions and synchronization need integration.
Non-GUI inventory consumers and other nightly builds need their own compatibility
checks. Selected grave lifecycle cases passed on the pinned daily; other equipment
mods and large backup restores remain separate work. Performance has not been
benchmarked against an unmodified pack.

## License

Project code is licensed under **GPL-3.0-only**; see [LICENSE](LICENSE).
Third-party build infrastructure retains its own notices; see [NOTICE](NOTICE).
Minecraft, Forge, MUI libraries, and other dependency mods are not bundled or relicensed.
