# Shared Inventory and SFM testing

The current release development target is GTNH 2.9.0, using the `gtnh-2.9.0`
profile and the shared build/install tasks in VS Code. Prism controls the game's
Java selection. The no-launch task builds and installs without starting Minecraft.

The earlier nightly development client is Prism instance `GTNH-SFM-Daily-2026-10-27-803`,
using the official `daily-2026-10-27+803` pack. Its Inventory profile is
`daily-2026-10-27-803`. The earlier nightly 795 and RC-2 instances remain separate.

Use the VS Code **GTNH: Build and launch nightly pack** task, or save and close
the shared game, then run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/full-pack.ps1 -ExperimentalInventoryIntegration -VisualPreview -PackConfig pack-daily-2026-10-27-803.json -DebugPort 5007
```

The helper requires SFM's existing instance marker and checks the archive identity
and exact integration JAR hashes. It replaces only the Inventory JAR, backs up the
previous JAR and launcher configuration, and preserves the instance's Java runtime,
SFM mod, world saves and inventory preferences. Add `-NoLaunch` to deploy without
starting the game. Both projects attach to this client on debugger port 5007.
The VS Code **GTNH: Build and install nightly pack (no launch)** task runs that
same build/install command with `-NoLaunch` already selected.

Inventory's stock MUI2 binding keeps the Factory Manager's existing footprint:
three visible storage rows with overflow scrolling and the hotbar underneath.
The terminal, logs and other manager controls retain their native positions.
Only the exact `superfactorymanager` / `sfm_manager` host selects this compact
viewport; other MUI2 screens retain their normal expansion policy.

This is a development test target. Building the profile and checking the manager
does not qualify every machine, gameplay path or multiplayer use. The published
alpha.3 artifact targets the final 2.9.0 pack; build the matching source profile
for the older nightly instance.
