# GregTech

**Loaded ID:** `gregtech`. **Status:** shared MUI1/MUI2 player-inventory hooks exist;
this is not a claim that every GT screen uses those builders or is qualified.
Future GT-specific Java owner: `GregTechCompatibility`.

Keep generic player binding, geometry and synchronization in the existing
[`compat/mui1`](../../src/main/java/dev/gilbe/dynamicinventory/compat/mui1) and
[`compat/mui2`](../../src/main/java/dev/gilbe/dynamicinventory/compat/mui2) code.
Only actual GT exceptions belong in the GT file. Do not create duplicate per-machine
adapters for screens already served by a reviewed shared builder.

Audit custom player bindings, machine controls and explicit resize ownership.
Preserve GT slot restrictions, recipes, progress and machine/network state. For
newly owned screens prefer the shared live MUI2 player-inventory component.

Verify both transfer directions, restricted inputs and outputs, locked slots,
recipe actions, scrolling at small viewports, screen resize and dedicated peers.
Qualify exceptional containers separately from standard framework coverage.
