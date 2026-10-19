# Developer testing

Tests have distinct scopes: compilation, pure geometry, source-runtime behavior,
and installed full-pack behavior. Record what actually ran. A green CI build is
not evidence that every pack GUI, custom packet, or inventory consumer works.

## Build and tooling

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 build packTestClasses writeFixtureClientLaunch -PinventoryProfile=rc2-integration -PinventoryStorageTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 --project-dir=examples/mui2-test-mod build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\tools\Test-InventoryUiExamples.ps1 -EvidencePath .setup/examples.json
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\tools\Test-InventoryArtifact.ps1 -EvidencePath .setup/artifact.json
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\testing\test-qualification-validator.ps1
```

The qualification-validator unit test creates synthetic temporary reports. Those
files test validation rules only; they do not qualify a game build. No real run
reports are included in this repository.

`test-fixture-artifacts.ps1` checks snapshots/provenance using synthetic fixtures.
`test-inventory-artifact.ps1` checks malformed and contaminated JAR rejection.
`test-ui-template-tools.ps1` exercises discovery/template export under Windows
PowerShell 5.1 and PowerShell 7. It resolves its own development classpath.

## Focused MUI2 transfer regression

The opt-in pack fixture can run the real MUI2 container-transfer regression on a
local dedicated server without opening a client GUI. It tests hotbar, first and
last expanded storage, both transfer directions, partial merges, a full chest,
and cursor preservation. It also runs attached-chest layout calculations across
small and compressed chest sizes, capacities, and screen bounds.

Build the `rc2-integration` profile with `-PinventoryStorageTests`, then start its
disposable server with JVM property `-Ddynamicinventory.mui2TransferRegression=true`.
For example, set `JAVA_TOOL_OPTIONS` for that terminal process only before invoking
`runServer25`, then remove that environment variable afterward. Read the fresh
`diagnostics/dynamicinventory/mui2-api-selftest.json` under that server's game directory.
Accept Minecraft's EULA yourself and keep the server bound to localhost. This is
source-runtime behavior evidence, not an installed full-pack or real mouse-input test.

## Manual GUI checks

Use a disposable world. In the separately installed example, `/dinvexample` opens
an ender-chest/player screen. Test pickup/place, shift-click and wheel transfer both
ways, last/offscreen player slots, close/reopen, GUI scales, and window resizing.
Check real item counts and nested NBT, not only visible icons. Repeat on a dedicated
server with matching dependencies before claiming multiplayer support.

For crafting stations, cover normal, double, and compressed attached chests,
independent scrolling, crafting-result callbacks, and optional description panels.
For filtered machines, retain input/output restrictions and full-destination refusal.
Sorting must keep hotbar and equipment out of ordinary storage sorting.

## Runtime suites and evidence

`scripts/testing/` contains opt-in integrated/dedicated and pack suites. They require
running local fixtures, known disposable-world names, generated launch metadata,
and suite-specific arguments. Inspect each script's parameters before running it.
The Java `StorageHarness` uses JDWP; it is a local testing tool, not part of the mod.
Do not attach it to a real-world session without reviewing the actions it performs.

Generated reports may contain absolute paths, player identifiers, world data, or
personal settings. Their default `docs/qualification/` output is ignored by Git.
Keep captures local; publish only reviewed summaries stripped of personal data.
The qualified full-pack launcher still requires a complete local report set with
matching artifact hashes. Removing archived evidence does not bypass that gate.

For broader compatibility, test actual pickup/ticking/death/respawn, equipment,
grave and backup/restore behavior, and item/cursor conservation. The presence of
fixture source is not proof those scenarios passed on a particular artifact.
