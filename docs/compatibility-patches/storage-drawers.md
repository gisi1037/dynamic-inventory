# Storage Drawers

**Loaded ID:** `StorageDrawers`. **Status:** inspection required.
Future Java owner: `StorageDrawersCompatibility`.

Audit drawer configuration/upgrade GUIs and direct player-inventory interactions
separately. Identify which controls are actual ItemStack slots and which describe
drawer contents or quantities. Do not model every displayed stored count as an
ordinary player slot or change drawer limits to match the player's stack limit.

Expand only reviewed player-backed regions. Preserve native upgrades, quantities,
filters, permissions and insertion/extraction operations. Add a targeted consumer
bridge only if the normal player inventory API is insufficient.

Verify full/filtered destinations, direct interactions from original/extra slots,
upgrade controls, large stored quantities, both transfer directions and save/reload.
