# Contributing

This is an experimental Forge 1.7.10 project targeting GTNH 2.9.0-RC-2.
Use the wrapper and JDK 25. Build output, game files, credentials, raw test captures,
and personal editor settings must stay untracked.
Run `python scripts/tools/check_public_tree.py` after staging changes to check the
tracked tree for local-only files and common credential/path patterns.

## Local checks

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 spotlessApply build packTestClasses -PinventoryProfile=rc2-integration -PinventoryStorageTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 --project-dir=examples/mui2-test-mod build
```

Use the isolated profile for core-only work. Add targeted runtime checks for the
behavior changed; GUI screenshots alone do not establish transfer or multiplayer
correctness. See [testing](docs/testing.md) for scope and command fixtures.

## Integration rules

Read the [contract](docs/inventory-integration-contract.md) before adding an adapter.
Keep logical player indices, canonical raw indices, and container slot IDs distinct.
Preserve native validation, stack limits, callbacks, and machine behavior. Prefer
public APIs and shared framework paths over another GUI-specific mixin.

Native registrations must be explicit and present on both sides before load-complete.
F9 output is a review scaffold, not permission to activate support. Test both transfer
directions, final/offscreen storage, cursor conservation, resizing, and refusal paths.
Keep crafting-result callbacks on their reviewed native path.

Keep code straightforward and comment ownership, index translations, lifecycle
constraints, and the reason for exceptions. Add tests that exercise behavior rather
than repeat implementation details. Do not copy another project's code or assets
without a compatible license and retained attribution.

## Pull requests

Explain the behavior changed, supported dependency versions, checks actually run,
and any remaining limitations. Keep changes focused. The CI build compiles fixtures
and checks tooling; it does not run an authenticated full pack or prove every GUI.
Do not commit local reports as release qualification. Full release claims require
matching artifact evidence from the relevant integrated, dedicated, and pack tests.

Contributions to project code are submitted under GPL-3.0-only unless clearly marked
as third-party material under a compatible license.
