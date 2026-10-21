# Reviewed native machine adapters

These integrations retain the mod's original GUI and authoritative container.
They use the existing inventory UI API and native Vertical/Scroll presentation;
they add no per-machine Mixins. The default player section is nine columns with
texture crop 4, and remains editable with F9/F10.

Each registration is exact-class matched and enabled only for the loaded mod
version reviewed below. An absent or different version skips that optional
integration without blocking startup. A compiled GUI class can still be disabled
by the pack configuration or require a particular block arrangement. The live
catalog records opening/transfer checks separately from manual reachability.

Version literals live in `src/main/resources/integration-profiles.properties`:
named profile keys describe required bridges; `reviewed.<modId>` keys describe
the narrower optional contracts in this batch. A different optional version skips
its registration; required bridge mismatches stop startup. Neither policy proves
gameplay compatibility by itself.

| Mod / reviewed version | Native GUIs | Java owner |
| --- | --- | --- |
| Alchemical Grate `1.3.2-GTNH` | Grate; place above an Advanced Alchemical Furnace | `AlchemicalGrateCompatibility` |
| Avaritia `1.100` | Neutronium Compressor, Neutron Collector | `AvaritiaCompatibility` |
| Flood Lights `1.5.6` | Carbon and Electric Floodlight | `FloodLightsCompatibility` |
| Et Futurum `2.6.64-GTNH` | Blast Furnace, Smoker | `EtFuturumCompatibility` |
| Twilight Forest `2.7.45` | Cinder Furnace | `TwilightForestCompatibility` |
| Tinkers' Construct `1.14.119-GTNH` | Furnace Slab | `TinkersConstructCompatibility` |
| HarvestCraft `1.3.15-GTNH` | Churn, Oven, Quern, Animal Trap, Fish Trap, Apiary, Grinder, Presser | `HarvestCraftCompatibility` |
| Thaumcraft `4.2.3.5` | Alchemical Furnace | `ThaumcraftCompatibility` |
| Witchery `0.24.1` | Distillery, Spinning Wheel, Witches' Oven | `WitcheryCompatibility` |

## Selecting transfers

`NativeMachineAdapters` is a small declaration helper for individually reviewed
hosts, not an automatic compatibility detector. Its added-slot policy first
calls the host's own transfer implementation, preserving native recipe, aspect,
fuel, bait and ingredient decisions. Only a failed native transfer falls back
to the hotbar. Original source slots continue through their native callbacks.

The helper now compares a copy of the source after a native null return. An
observable mutation logs a warning and prevents a second hotbar route. This does
not detect all destination/callback side effects or replace review. Shared helper
behavior has compatibility revision 2; presentation revisions are independent.

Before using this helper, confirm all of these from the actual paired hosts:

- The class directly extends `Container` and owns its transfer implementation.
  Inherited vanilla transfer hooks must not re-enter this policy.
- Added source IDs take a safe player-input branch. Native code must return
  `null` only if it moved no items; otherwise hotbar fallback is unsafe.
- Machine-to-player destinations cover all original 36 ordinary player slots.
  The shared merge hook then includes added storage without changing slot IDs.
- Synchronization is ordinary slot snapshots and progress properties. Native
  slot validity, limits, output callbacks and container ownership stay intact.

There are deliberate exceptions. The grate uses `nativeTransfers()` because its
bounds already follow the actual slot count. Flood Lights puts player slots
before the machine slot; added sources explicitly target its filtered slot 36.
The Neutron Collector is output-only, so added sources target the hotbar.

Do not replace recipe-sensitive native routing with a blanket
`toNativeSlots()`: a plain native input `Slot` can accept items that the mod's
shift-transfer recipe checks reject.

## Remaining manual cases

The Magic Box constructs different server/client hosts, so its drawing class
alone is insufficient evidence. HEE Void Chest replaces slots with read-only
slots. Magic Bees' Effect Jar transfer can return `null` after mutating items.
Thaumic Exploration's Think Tank/Auto Crafter use irregular fixed ranges and
special slot semantics. Held-item bags require separate identity/ownership
review. These cases do not receive this shared adapter automatically.

RandomThings Sound Recorder and Thaumic Horizons Soulforge constructors add no
slots; these particular screens do not need an inventory adapter. That statement
does not establish whether they are reachable in a given pack.

For another mod, follow the [adapter guide](../gui-adapter-guide.md), inspect both
GUI-handler branches, and keep registrations in the owning mod's compatibility
file. Update the reviewed version and qualify the real opening before broadening
support; neither a class-name match nor a 36-slot shape is sufficient.
