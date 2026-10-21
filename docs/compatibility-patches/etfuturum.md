# Et Futurum vanilla replacements

**Loaded ID:** `etfuturum`. **Inspected artifact:** `etfuturum-2.6.64-GTNH.jar`
from the October 19 daily. **Status:** five exact adapters implemented;
focused nightly brewing/enchanting checks passed; full gameplay qualification,
optional anvil testing and visual review are pending.
Owner: [EtFuturumCompatibility](../../src/main/java/dev/gilbe/dynamicinventory/compat/EtFuturumCompatibility.java).

This optional pack integration covers only the replacements below. Registration
requires the pack profile and its exact Et Futurum pin (`2.6.60-GTNH` for RC-2,
`2.6.64-GTNH` for the October 19 nightly). The GUI mixin checks that pin before
application too. Only the reviewed `2.6.64-GTNH` build receives the two new furnace
adapters. It does not redesign every Et Futurum
screen or enable the mod's configurable features. These replacements are distinct
from the [vanilla registrations](vanilla.md).

| Adapter ID suffix (`dynamicinventory:etfuturum/`) | Container | GUI | Appended-slot shift-click policy |
| --- | --- | --- | --- |
| `brewing-stand` | `ContainerNewBrewingStand` | `GuiNewBrewingStand` | Native |
| `enchantment` | `ContainerEnchantment` | `GuiEnchantment` | Registered fuel slot or one-item input |
| `anvil` | `ContainerAnvil` | `GuiAnvil` | To input slots 0 and 1 |
| `blast-furnace` | `ContainerBlastFurnace` | `GuiBlastFurnace` | Native recipe/fuel routing, then hotbar |
| `smoker` | `ContainerSmoker` | `GuiSmoker` | Native recipe/fuel routing, then hotbar |

Container names are relative to `ganymedes01.etfuturum.inventory`; GUI names are
relative to `ganymedes01.etfuturum.client.gui.inventory`. Brewing and enchanting
are separate implementations from vanilla. Anvil extends the vanilla repair
container/screen but is registered by its exact Et Futurum classes. Native result
pickup retains repair costs, input consumption and mod behavior.

Installed-jar bytecode inspection confirms brewing accepts appended player
sources, with potion slots 0–2, ingredient slot 3 and blaze-powder fuel slot 4.
Enchanting routes registered fuel through `EnchantingFuelRegistry.isFuel` into
slot 1; other items target slot 0. Its enchant action requires and consumes
selector + 1 fuel items (1, 2 or 3) in the ordinary fueled, noncreative case.
Anvil does not override `transferStackInSlot` (`func_82846_b`); its inherited
vanilla method excludes appended sources, requiring the explicit input policy.

Appended enchanting sources use the shared transfer helper to respect the input's
one-item limit and the mod's registered-fuel filter. This avoids the native
method's direct insertion of a copied stack. Existing native player sources keep
the mod's original behavior. Its configurable `noFuel` layout also omits fuel
slot 1 while the native return range still starts at 2; fuel-free mode remains
unqualified and needs a separate upstream fix.

## Focused review — pending

Record artifact/profile, capacity, GUI scale and actual GUI/container classes.
Check the shared panel/editor, first/final ordinary slots, two-way transfers,
full destinations and exact count/NBT conservation in a disposable world.

- Brewing: test ingredient, bottle/potion and blaze-powder routing and rejection,
  single-bottle limits, brewing/fuel progress and reopening.
- Enchanting: check item and fuel slots, all three option hit boxes, book viewport,
  XP/fuel consumption, tagged singleton items and stackable-item shift-click.
  Treat the `noFuel` configuration as a separate unqualified case.
- Anvil: check rename field position/focus, input transfers from appended storage,
  costs, result pickup/consumption and reopening after editor changes.
- Confirm opening agreement and action gating still apply to actual replacements,
  including enchanting buttons and anvil rename payloads. Visual success alone
  does not qualify native gameplay callbacks or the entire mod.
