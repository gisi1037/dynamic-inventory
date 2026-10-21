# Vanilla inventory screens

**Status:** adapters and targeted GUI hooks implemented; focused nightly smoke
passed. Full gameplay qualification and the user's visual review are pending.
Owner: [VanillaInventoryCompatibility](../../src/main/java/dev/gilbe/dynamicinventory/compat/VanillaInventoryCompatibility.java).

These 11 exact vanilla container/GUI families use the shared player panel and
in-game layout editor. Native machine slots, controls, item rules and callbacks
remain owned by Minecraft. Registrations also apply in the isolated development
profile. Existing chest, workbench and furnace adapter IDs remain unchanged so
their saved editor layouts still apply.

The inspected October 19 daily substitutes Et Futurum brewing and enchanting
screens; its anvil still opens the vanilla classes. Three optional replacement
registrations are documented in [Et Futurum](etfuturum.md); enabling a vanilla adapter
does not cover a replacement class or prove that the vanilla GUI was exercised.

| Adapter ID suffix (`dynamicinventory:`) | Container | GUI | Appended-slot shift-click policy |
| --- | --- | --- | --- |
| `chest` | `ContainerChest` | `inventory.GuiChest` | Native |
| `workbench` | `ContainerWorkbench` | `inventory.GuiCrafting` | To hotbar |
| `furnace` | `ContainerFurnace` | `inventory.GuiFurnace` | Smeltable to input, fuel to fuel slot, otherwise hotbar |
| `dispenser` | `ContainerDispenser` | `inventory.GuiDispenser` | Native |
| `hopper` | `ContainerHopper` | `GuiHopper` | Native |
| `brewing-stand` | `ContainerBrewingStand` | `inventory.GuiBrewingStand` | Native |
| `enchantment` | `ContainerEnchantment` | `GuiEnchantment` | Native |
| `beacon` | `ContainerBeacon` | `inventory.GuiBeacon` | Native |
| `horse` | `ContainerHorseInventory` | `inventory.GuiScreenHorseInventory` | Native |
| `anvil` | `ContainerRepair` | `GuiRepair` | To input slots 0 and 1 |
| `merchant` | `ContainerMerchant` | `GuiMerchant` | To hotbar; no automatic trade-input fill |

Container names above are in `net.minecraft.inventory`; GUI names are relative
to `net.minecraft.client.gui`. Exact classes are registered, not arbitrary mod
subclasses. Dropper reuses dispenser; ordinary, trapped, double, ender and chest
minecart inventories reuse chest; hopper minecarts reuse hopper. Horse equipment
and chested donkey/mule variants reuse the horse family. Variants sharing an
adapter and GUI also share an editor target; they do not receive separate presets.

Native transfers retain enchanting's one-item insertion, brewing ingredient and
bottle restrictions, beacon payment/count checks, and horse armor/saddle/storage
priority. Anvil and merchant native methods reject appended source IDs through
fixed upper bounds, so only those sources need explicit policies. Native result
pickup still owns anvil XP/input consumption and merchant trade consumption and
usage callbacks. Shared merge hooks expand recognized player destination ranges;
hopper and horse native transfers already use the live container end.

Four targeted GUI hooks anchor controls that need more than shared slot/button
translation: anvil name-field position, merchant trade previews and hover regions,
enchanting option hit boxes and book viewport, and beacon buttons added after
initialization or rebuilt when selecting powers. They preserve native interaction
behavior rather than replacing these controls.

The opening agreement remains required before input and initial contents apply.
The server also gates the windowless vanilla `MC|ItemName`, `MC|TrSel` and
`MC|Beacon` payloads against the current opening; enchanting buttons use the
existing guarded `C11PacketEnchantItem` path.

Trading also needs one server opening-order hook: vanilla registers its listener
before sending its open-window packet. For the registered vanilla merchant only,
listener registration moves after the open/trade-list packets so the client has
the host before receiving the opening offer. The agreement still precedes contents.

Creative inventory remains deferred under the prior scope decision. The ordinary
player inventory already has its custom screen. Non-inventory screens, such as
signs, books and command blocks, are not redesigned by this batch.

## Focused checks and remaining review

The October 19 nightly smoke exercised 15 openings: chest, ender chest, workbench,
furnace, dispenser, dropper, hopper, brewing, enchanting, anvil, beacon, merchant,
horse, donkey and mule. Every case checked the adapter, 81 ordinary player slots,
opening agreement and editor open/cancel. Tagged appended-source transfers ran
for dispenser, hopper, brewing, enchanting, anvil, beacon, merchant, horse and
donkey; native input returns were also checked except merchant's hotbar route.
Snapshots checked exact client/server item count, damage, NBT and empty cursors.

These were incremental focused runs while fixing replacement detection and the
merchant opening order, not a full pack requalification. The nightly exercised
Et Futurum brewing/enchanting and vanilla anvil; pure vanilla brewing/enchanting,
optional Et Futurum anvil, other capacities, minecart/double-chest variants and
the gameplay actions below still require separate review. The local test world
is `DI-Baseline-Vanilla-Guis-Qualification`.

Use a disposable qualification world and record the tested artifact, profile,
storage capacity, resolution and GUI scale. Review each family above and the
related variants that matter to the pack; registration alone is not a runtime pass.

- Check native content, player inventory, hotbar, labels and NEI search visibility
  at the intended GUI scales. Use the editor to tune margins and layout; verify
  preview, save/reopen, cancel and resize retain correct click targets.
- Click first/final native and player slots, including appended storage. Check
  normal clicks, shift-clicks and supported wheel transfers both ways, partially
  full/full destinations, empty cursor and exact item count/NBT conservation.
- Workbench/furnace: check crafting result consumption, smelting output/XP, fuel
  routing, and NEI recipe fill/return without locking subsequent clicks.
- Brewing/enchanting: reject invalid ingredients, retain bottle limits, insert one
  enchantment item, and check potion pickup, enchant option selection, XP use and
  book rendering after moving the native header.
- Anvil/merchant: type and retain a rename, check costs and result consumption;
  switch trades, inspect previews/tooltips and complete a trade without losing or
  duplicating payment. Appended merchant stacks should shift to hotbar.
- Beacon: check valid versus invalid payment, single-item behavior, power buttons,
  confirmation/cancel and repeated power selection after layout changes.
- Horse: check saddle/armor eligibility and priority, chested storage, equipment
  removal and reopening; compare unchested and chested variants.
- Developer checks: verify initial synchronization and pending-action rejection,
  including the three `MC|` payloads, plus close/reopen and NEI detours. Record
  failures and evidence before promoting this batch to qualified support.
