package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** Interface Terminal pattern extraction uses actual canonical slots, including expanded storage. */
public final class AppliedEnergisticsPatternTransfers {

    private AppliedEnergisticsPatternTransfers() {}

    public static ItemStack insert(Container container, ItemStack stack) {
        if (stack == null) return null;
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
        if (binding == null) return stack;
        int[] hotbar = binding.getHotbarSlotIds(), storage = binding.getStorageSlotIds();
        int[] destinations = java.util.Arrays.copyOf(hotbar, hotbar.length + storage.length);
        System.arraycopy(storage, 0, destinations, hotbar.length, storage.length);
        ItemStack remainder = stack.copy();
        PlayerInventoryTransfers.mergeAutomaticAll(container, remainder, destinations);
        return remainder.stackSize == 0 ? null : remainder;
    }
}
