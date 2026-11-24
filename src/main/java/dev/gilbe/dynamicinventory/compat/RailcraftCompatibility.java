package dev.gilbe.dynamicinventory.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;

public final class RailcraftCompatibility {

    private static final String ID = "dynamicinventory:railcraft/trading-station";
    private static final String CONTAINER = "mods.railcraft.common.gui.containers.ContainerTradeStation";
    private static final String GUI = "mods.railcraft.client.gui.GuiTradeStation";
    private static final int FIRST_INPUT = 9, END_INPUT = 19, FIRST_PLAYER = 25;

    private RailcraftCompatibility() {}

    static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter.builder(ID, CONTAINER)
                .gui(GUI)
                .expectedNativeSlots(61)
                .layout(NativeMachineAdapters.LAYOUT)
                .compatibilityRevision(2)
                .nativeTransfers()
                .build());
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        return CONTAINER.equals(container) && ID.equals(adapter.getId())
            && CONTAINER.equals(adapter.getContainerClassName())
            && GUI.equals(adapter.getGuiClassName());
    }

    public static boolean tradeStation(Container container) {
        return CONTAINER.equals(
            container.getClass()
                .getName());
    }

    /** Native recipes occupy slots 0–8 and native outputs occupy 19–24. Only the stockpile accepts input. */
    public static ItemStack transfer(Container container, EntityPlayer player, int source,
        PlayerInventoryBinding binding) {
        if (source < FIRST_INPUT || source >= container.inventorySlots.size()) return null;
        if (source < FIRST_PLAYER) return InventoryTransfers.toPlayerInventory()
            .transfer(container, player, source, binding);
        int ordinary = binding.logicalFromContainerId(source);
        if (ordinary < 0) return null;
        // Railcraft stops at the first input that accepts anything. Preserve that and its hotbar fallback.
        for (int input = FIRST_INPUT; input < END_INPUT; input++) {
            ItemStack moved = InventoryTransfers.toSlots(input)
                .transfer(container, player, source, binding);
            if (moved != null) return moved;
        }
        return (ordinary < 9 ? InventoryTransfers.toStorage() : InventoryTransfers.toHotbar())
            .transfer(container, player, source, binding);
    }
}
