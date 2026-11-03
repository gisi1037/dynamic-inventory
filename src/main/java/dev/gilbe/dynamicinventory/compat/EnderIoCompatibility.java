package dev.gilbe.dynamicinventory.compat;

import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Exact inventory-bearing Ender IO 2.10.51 screens; control-only screens retain their native UI. */
public final class EnderIoCompatibility {

    private static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .build();
    private static final String[][] HOSTS = {
        { "alloy-smelter", "crazypants.enderio.machine.alloy.ContainerAlloySmelter",
            "crazypants.enderio.machine.alloy.GuiAlloySmelter" },
        { "buffer", "crazypants.enderio.machine.buffer.ContainerBuffer",
            "crazypants.enderio.machine.buffer.GuiBuffer" },
        { "capacitor-bank", "crazypants.enderio.machine.capbank.ContainerCapBank",
            "crazypants.enderio.machine.capbank.GuiCapBank" },
        { "crafter", "crazypants.enderio.machine.crafter.ContainerCrafter",
            "crazypants.enderio.machine.crafter.GuiCrafter" },
        { "sag-mill", "crazypants.enderio.machine.crusher.ContainerCrusher",
            "crazypants.enderio.machine.crusher.GuiCrusher" },
        { "enchanter", "crazypants.enderio.machine.enchanter.ContainerEnchanter",
            "crazypants.enderio.machine.enchanter.GuiEnchanter" },
        { "farming-station", "crazypants.enderio.machine.farm.FarmStationContainer",
            "crazypants.enderio.machine.farm.GuiFarmStation" },
        { "combustion-generator", "crazypants.enderio.machine.generator.combustion.ContainerCombustionEngine",
            "crazypants.enderio.machine.generator.combustion.GuiCombustionGenerator" },
        { "stirling-generator", "crazypants.enderio.machine.generator.stirling.StirlingGeneratorContainer",
            "crazypants.enderio.machine.generator.stirling.GuiStirlingGenerator" },
        { "zombie-generator", "crazypants.enderio.machine.generator.zombie.ContainerZombieGenerator",
            "crazypants.enderio.machine.generator.zombie.GuiZombieGenerator" },
        { "inventory-panel", "crazypants.enderio.machine.invpanel.InventoryPanelContainer",
            "crazypants.enderio.machine.invpanel.GuiInventoryPanel" },
        { "killer-joe", "crazypants.enderio.machine.killera.ContainerKillerJoe",
            "crazypants.enderio.machine.killera.GuiKillerJoe" },
        { "attractor", "crazypants.enderio.machine.obelisk.attractor.ContainerAttractor",
            "crazypants.enderio.machine.obelisk.attractor.GuiAttractor" },
        { "aversion-obelisk", "crazypants.enderio.machine.obelisk.aversion.ContainerAversionObelisk",
            "crazypants.enderio.machine.obelisk.aversion.GuiAversionObelisk" },
        { "inhibitor-obelisk", "crazypants.enderio.machine.obelisk.inhibitor.ContainerInhibitorObelisk",
            "crazypants.enderio.machine.obelisk.inhibitor.GuiInhibitorObelisk" },
        { "weather-obelisk", "crazypants.enderio.machine.obelisk.weather.ContainerWeatherObelisk",
            "crazypants.enderio.machine.obelisk.weather.GuiWeatherObelisk" },
        { "painter", "crazypants.enderio.machine.painter.PainterContainer",
            "crazypants.enderio.machine.painter.GuiPainter" },
        { "slice-and-splice", "crazypants.enderio.machine.slicensplice.ContainerSliceAndSplice",
            "crazypants.enderio.machine.slicensplice.GuiSliceAndSplice" },
        { "soul-binder", "crazypants.enderio.machine.soul.ContainerSoulBinder",
            "crazypants.enderio.machine.soul.GuiSoulBinder" },
        { "powered-spawner", "crazypants.enderio.machine.spawner.ContainerPoweredSpawner",
            "crazypants.enderio.machine.spawner.GuiPoweredSpawner" },
        { "tank", "crazypants.enderio.machine.tank.ContainerTank", "crazypants.enderio.machine.tank.GuiTank" },
        { "transceiver", "crazypants.enderio.machine.transceiver.gui.ContainerTransceiver",
            "crazypants.enderio.machine.transceiver.gui.GuiTransceiver" },
        { "vacuum-chest", "crazypants.enderio.machine.vacuum.ContainerVacuumChest",
            "crazypants.enderio.machine.vacuum.GuiVacuumChest" },
        { "vat", "crazypants.enderio.machine.vat.ContainerVat", "crazypants.enderio.machine.vat.GuiVat" },
        { "travel-anchor", "crazypants.enderio.teleport.ContainerTravelAccessable",
            "crazypants.enderio.teleport.GuiTravelAccessable" },
        { "travel-auth", "crazypants.enderio.teleport.ContainerTravelAuth",
            "crazypants.enderio.teleport.GuiTravelAuth" },
        { "telepad", "crazypants.enderio.teleport.telepad.ContainerTelePad",
            "crazypants.enderio.teleport.telepad.GuiTelePad" },
        { "conduit", "crazypants.enderio.conduit.gui.ExternalConnectionContainer",
            "crazypants.enderio.conduit.gui.GuiExternalConnection" } };

    private EnderIoCompatibility() {}

    public static void registerInventoryAdapters() {
        for (String[] host : HOSTS) InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:enderio/" + host[0], host[1])
                .gui(host[2])
                .compatibilityRevision(2)
                .layout(LAYOUT)
                .extraTransfers((container, player, id, binding) -> {
                    if (host[0].equals("conduit")) {
                        int[] targets = java.util.stream.IntStream.range(0, binding.getNativeSlotCount())
                            .filter(
                                target -> binding.logicalFromContainerId(target) < 0
                                    && container.getSlot(target).xDisplayPosition >= 0)
                            .toArray();
                        return InventoryTransfers.toSlots(targets)
                            .transfer(container, player, id, binding);
                    }
                    ItemStack before = container.getSlot(id)
                        .getStack();
                    if (before == null) return null;
                    before = before.copy();
                    ItemStack moved = container.transferStackInSlot(player, id);
                    // Native machine decisions run once. Never retry an action that changed its source.
                    if (moved != null || !ItemStack.areItemStacksEqual(
                        before,
                        container.getSlot(id)
                            .getStack()))
                        return moved;
                    if (host[0].startsWith("travel-") || host[0].equals("telepad")) return null;
                    return InventoryTransfers.toHotbar()
                        .transfer(container, player, id, binding);
                })
                .build());
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:enderio/dark-steel-anvil",
                    "crazypants.enderio.block.ContainerDarkSteelAnvil")
                .gui("net.minecraft.client.gui.GuiRepair")
                .compatibilityRevision(2)
                .extraTransfers(InventoryTransfers.toSlots(0, 1))
                .build());
        InventoryUi.registerPresentation(
            "dynamicinventory:enderio/travel-anchor",
            "crazypants.enderio.teleport.telepad.GuiAugmentedTravelAccessible",
            LAYOUT);
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        if (adapter.getCompatibilityRevision() < 2) return false;
        if (adapter.getId()
            .equals("dynamicinventory:enderio/dark-steel-anvil"))
            return container.equals("crazypants.enderio.block.ContainerDarkSteelAnvil");
        for (String[] host : HOSTS) if (adapter.getId()
            .equals("dynamicinventory:enderio/" + host[0]) && container.equals(host[1])
            && host[2].equals(adapter.getGuiClassName())) return true;
        return false;
    }

    static boolean alternateScreen(InventoryUiAdapter adapter, String screen) {
        return adapter.getId()
            .equals("dynamicinventory:enderio/travel-anchor")
            && screen.equals("crazypants.enderio.teleport.telepad.GuiAugmentedTravelAccessible");
    }
}
