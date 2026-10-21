# Applied Energistics 2

**Loaded ID:** `appliedenergistics2`. **Status:** inspection required; this file
does not register AE2 containers. Future Java owner: `AppliedEnergisticsCompatibility`.

Start with one terminal and one ordinary machine container. Audit their actual
player slots, synchronization, transfers and item-backed screen lifecycle.
Separate real player/storage slots from network views, virtual entries and
configuration slots. Never treat every displayed entry as a normal ItemStack slot.

Use the native inventory-region API for a compatible real player region. If custom
binding or synchronization prevents that, isolate the smallest AE2-specific bridge
in this owner/package. Add a Mixin only for the proven missing hook. Preserve AE2's
network operations and click semantics rather than recreating them in our renderer.

Verify both transfer directions, filtered/full destinations, terminal actions,
configuration-slot behavior, item-backed self-movement restrictions, reconnect and
two-player independence. Review each additional behavioral family before enabling it.
