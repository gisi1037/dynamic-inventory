package dev.gilbe.dynamicinventory.compat;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryTransferPolicy;
import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Small shared declarations for individually reviewed machines, not a runtime compatibility detector. */
final class NativeMachineAdapters {

    // Shared behavior is part of the peer agreement, even though callback class names are not hashed.
    static final int TRANSFER_REVISION = 2;
    static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .build();

    private NativeMachineAdapters() {}

    static void furnace(String id, String container, String gui) {
        machine(id, container, gui, 39);
    }

    static void machine(String id, String container, String gui, int nativeSlots) {
        InventoryUi.register(
            InventoryUiAdapter.builder(id, container)
                .gui(gui)
                .expectedNativeSlots(nativeSlots)
                .layout(LAYOUT)
                .compatibilityRevision(TRANSFER_REVISION)
                .extraTransfers(nativeThenHotbar())
                .build());
    }

    /**
     * Only for reviewed hosts with their OWN transfer implementation directly on Container, where
     * null means no items moved. Native code retains recipe/fuel decisions; unsuccessful added sources
     * use the hotbar. Never use this for inherited vanilla transfer hooks: those re-enter our policy.
     */
    static InventoryTransferPolicy nativeThenHotbar() {
        return (container, player, id, binding) -> {
            if (binding == null || container == null
                || player == null
                || binding.getPlayerInventory() != player.inventory
                || id < binding.getNativeSlotCount()
                || id >= container.inventorySlots.size()
                || container.getClass()
                    .getSuperclass() != Container.class)
                return null;
            Slot source = container.getSlot(id);
            if (!source.getHasStack() || !source.canTakeStack(player)) return null;
            ItemStack before = source.getStack()
                .copy();
            ItemStack moved = container.transferStackInSlot(player, id);
            if (moved != null) return moved;
            // A violated native contract must not trigger a second route. This catches source
            // changes only; destination/callback side effects still require source review.
            if (!ItemStack.areItemStacksEqual(before, source.getStack())) {
                DynamicInventory.LOG.warn(
                    "Native transfer changed its source but returned null: {} slot {}",
                    container.getClass()
                        .getName(),
                    id);
                return null;
            }
            return InventoryTransfers.toHotbar()
                .transfer(container, player, id, binding);
        };
    }
}
