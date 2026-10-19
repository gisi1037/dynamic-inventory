package example;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;

/** Minimal examples for a developer-owned native container integration. */
public final class NativeInventoryIntegration {

    private NativeInventoryIntegration() {}

    /** Call during mod initialization on both client and server. */
    public static void register() {
        InventoryUi.register(
            InventoryUiAdapter.builder("example:machine", "example.machine.ContainerMachine")
                .gui("example.machine.GuiMachine")
                .layout(NativeUiLayout.builder().topPadding(0).minimumHeaderHeight(0).backgroundInset(3).build())
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
    }

    /** Use from the native container's reviewed transferStackInSlot implementation for machine-to-player moves. */
    public static ItemStack moveMachineSlotToPlayer(Container container, EntityPlayer player, int sourceSlot) {
        PlayerInventoryBinding binding = InventoryUi.getBinding(container);
        if (binding == null || sourceSlot < 0 || sourceSlot >= binding.getNativeSlotCount()) return null;
        return InventoryTransfers.toPlayerInventory().transfer(container, player, sourceSlot, binding);
    }

    /** Optional alternative when machine output should exclude the hotbar. */
    public static ItemStack moveMachineSlotToStorage(Container container, EntityPlayer player, int sourceSlot) {
        PlayerInventoryBinding binding = InventoryUi.getBinding(container);
        if (binding == null || sourceSlot < 0 || sourceSlot >= binding.getNativeSlotCount()) return null;
        return InventoryTransfers.toStorage().transfer(container, player, sourceSlot, binding);
    }
}
