package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.util.Platform;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** Transfers for AE2 pattern slots and the expanded player inventory. */
public final class AppliedEnergisticsPatternTransfers {

    private AppliedEnergisticsPatternTransfers() {}

    public static boolean canLoadPattern(Container container, ItemStack stack) {
        if (!(container instanceof ContainerPatternTerm) || stack == null) return false;
        PlacableItemType type;
        if (stack.getItem() instanceof ICraftingPatternItem) type = PlacableItemType.ENCODED_PATTERN;
        else if (AEApi.instance()
            .definitions()
            .materials()
            .blankPattern()
            .isSameAs(stack)) type = PlacableItemType.BLANK_PATTERN;
        else return false;

        for (Slot slot : container.inventorySlots) {
            if (slot instanceof SlotRestrictedInput restricted && restricted.getItemType() == type
                && patternRoom(restricted, stack) > 0) return true;
        }
        return false;
    }

    public static ItemStack loadBlankPatterns(SlotRestrictedInput slot, ItemStack source) {
        int amount = Math.min(source.stackSize, patternRoom(slot, source));
        if (amount <= 0) return source;
        ItemStack existing = slot.getStack();
        ItemStack filled = source.copy();
        filled.stackSize = amount + (existing == null ? 0 : existing.stackSize);
        // Wireless terminals save their pattern slot when the stack is replaced.
        slot.putStack(filled);
        if (amount == source.stackSize) return null;
        ItemStack remainder = source.copy();
        remainder.stackSize -= amount;
        return remainder;
    }

    private static int patternRoom(SlotRestrictedInput slot, ItemStack stack) {
        if (!slot.isItemValid(stack)) return 0;
        ItemStack existing = slot.getStack();
        if (existing != null && !Platform.isSameItemPrecise(existing, stack)) return 0;
        int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
        return Math.max(0, limit - (existing == null ? 0 : existing.stackSize));
    }

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
