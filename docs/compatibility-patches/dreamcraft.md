# GTNH Core / Dreamcraft

**Loaded ID:** `dreamcraft`. **Status:** baby-chest adapter registered.

Code owner:
[`DreamcraftCompatibility`](../../src/main/java/dev/gilbe/dynamicinventory/compat/DreamcraftCompatibility.java).
Keep its native adapter ID stable so existing saved layouts continue to resolve.
The exact optional version check uses the `NewHorizonsCoreMod` profile pin.

Use the common chest inventory-region and layout services. Keep baby-chest-specific
registration here; do not infer support for all CoreMod interfaces. Additional
machines should first be classified as standard MUI or native families.

Verify small chest capacities, correct inactive visual padding, both transfer
directions, full destinations, attached crafting-station layout and editor save/reopen.
Removing this optional integration must not load Dreamcraft API classes.
