# Ender IO

**Loaded ID:** `EnderIO`. **Reviewed artifacts:** Ender IO `2.10.51` and EnderCore
`0.5.15`, from the shared GTNH 2.9.0 instance. **Status:** 29 exact inventory
container families plus the alternate TelePad travel GUI implemented; visual and
installed gameplay review pending. Owner: `EnderIoCompatibility`; bridges live in
`mixins/enderio`. Other versions skip these registrations and Mixins.

The batch includes Alloy Smelter, SAG Mill, Painter, Stirling/Combustion/Zombie
variants, Farming Station, Vat, Crafter, Powered Spawner, Slice and Splice, Soul
Binder, Killer Joe, Enchanter, Vacuum Chest, capacitor banks, tanks, buffers,
Attractor, Aversion/Inhibitor/Weather obelisks, Transceiver, Inventory Panel,
Travel Anchor, travel authorization, TelePad, conduit configuration and Dark Steel
Anvil. Ordinary machine variants sharing the same exact container/GUI pair reuse
one adapter. No package-wide or subclass registration is enabled.

EnderCore's overridden merge method translates complete ordinary-player ranges
through the canonical binding. Its base transfer derives ownership from that
binding rather than subtracting the expanded raw array length. Machine-specific
transfer decisions still run natively; added player sources use a hotbar fallback
only when native handling did not change their source. Capacitor Bank transfers
retain the native armor action and use real player/charging-slot roles.
Conduit added sources respect the native visibility of each upgrade slot.

Inventory Panel extraction/crafting slot lists include all bound ordinary storage, excluding reserved hotbar-bank rows. Ghost controls, tooltips and text fields follow the native machine origin.
Conduit tabs keep the modular player section available and rebuild its decorator
when native tab initialization changes the screen size. Native filter objects,
callbacks and packet payloads remain owned by Ender IO.

Power Monitor, Experience Obelisk, Hypercube, Ender IO's remote view and conduit
selector do not expose an ordinary player-slot binding. They retain their native
control screens; no artificial inventory/container is added. Their blocks are
included in the lab as applicable.

The separate storage lab has 39 labeled Ender IO stations, including machine
variants and control-only examples. The supplied wrench, conduit probe and
filters cover item-based controls. See the offline batch notes in
[storage-panel-batches.md](../storage-panel-batches.md). Build and static native
method/field checks do not claim runtime transformation or gameplay qualification.
