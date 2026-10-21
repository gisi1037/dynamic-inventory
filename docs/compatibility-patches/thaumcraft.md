# Thaumcraft

**Loaded ID:** `Thaumcraft`. **Status:** exact Alchemical Furnace adapter added for
loaded version `4.2.3.5`; other screens still require inspection.
Java owner: `ThaumcraftCompatibility`. See the
[native machine contract](native-machines.md) for version and transfer rules.

For another inventory-bearing workstation, separate its real player/input/output
slots from research, selection and other custom controls. Inspect its native click,
recipe and synchronization paths before enabling an adapter.

Use exact native registrations for reviewed containers. Preserve Thaumcraft's
gameplay rules and actions; our API should describe the player region without
reimplementing research or crafting. Keep add-on integrations separately gated.

Verify recipe callbacks, restricted slots, shift-click/wheel in both directions,
full destinations, focus/equipment interactions where applicable, typed NBT,
screen lifecycle and dedicated peers.
