package dev.gilbe.dynamicinventory.container;

import java.util.Arrays;
import java.util.stream.IntStream;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

/** Shift-click destinations use logical regions rather than assuming storage has contiguous container IDs. */
public final class PlayerInventoryTransfers {

    private PlayerInventoryTransfers() {}

    public static ItemStack transfer(Container container, EntityPlayer player, int id) {
        InventoryPlayer inventory = player.inventory;
        int[] storage = mainSlots(
            inventory,
            InventoryLayout.HOTBAR_SLOTS,
            InventoryLayout.of(inventory)
                .mainSlots());
        int[] hotbar = mainSlots(inventory, 0, InventoryLayout.HOTBAR_SLOTS);
        int[] allMain = mainSlots(
            inventory,
            0,
            InventoryLayout.of(inventory)
                .mainSlots());
        if (id < 0 || id >= container.inventorySlots.size()) return null;
        // Appended Baubles equipment is manual-only in our player screen, in both
        // directions. Shift-click moves ordinary storage/hotbar items or equips armor.
        if (id >= PlayerContainerLayout.slotCount(inventory)) return null;
        Slot slot = container.getSlot(id);
        if (InventorySlotLocks.locked(slot) || !slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved;
        if (id == PlayerContainerLayout.RESULT) {
            moved = mergeAutomaticAll(container, source, allMain);
            if (moved) slot.onSlotChange(source, original);
        } else if (id < PlayerContainerLayout.LEGACY_STORAGE_START
            || (BackhandCompatibility.enabled() && id == PlayerContainerLayout.OFFHAND)) {
                moved = mergeAutomatic(container, source, allMain);
            } else {
                moved = mergeIntoFilters(container, player, source);
                if (source.stackSize > 0 && equipArmor(container, source)) moved = true;
                else if (source.stackSize > 0) {
                    int mainIndex = PlayerContainerLayout.mainIndex(inventory, id);
                    if (mainIndex < InventoryLayout.HOTBAR_SLOTS) moved = merge(container, source, storage) || moved;
                    else {
                        moved = merge(container, source, hotbar) || moved;
                        if (source.stackSize > 0) {
                            int[] destinations = PanelTransferTargets.quickMove(id, hotbar, storage, 9);
                            moved = merge(
                                container,
                                source,
                                java.util.Arrays.copyOfRange(destinations, hotbar.length, destinations.length))
                                || moved;
                        }
                    }
                }
            }
        if (!moved) return null;
        if (source.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, source);
        return original;
    }

    public static boolean hasMatchingFilter(Container container, EntityPlayer player, ItemStack stack) {
        return emptyFilters(container, player, stack).length > 0;
    }

    public static boolean mergeIntoFilters(Container container, EntityPlayer player, ItemStack stack) {
        int[] filters = emptyFilters(container, player, stack);
        return filters.length > 0 && merge(container, stack, filters);
    }

    /** Keep native extraction rules for special slots and take priority only for ordinary player stacks. */
    public static ItemStack transferToFilter(Container container, EntityPlayer player, int id) {
        if (!InventorySlotLocks.hasTypeLocks(player) || id < 0
            || id >= container.inventorySlots.size()
            || player.inventory.getItemStack() != null) return null;
        Slot source = container.getSlot(id);
        if (source.inventory != player.inventory) return null;
        int logical = InventorySlotLocks.index(player.inventory, source);
        if (logical < 0 || logical >= InventoryLayout.of(player.inventory)
            .mainSlots()) return null;
        int[] filters = emptyFilters(container, player, source.getStack());
        if (filters.length == 0) return null;
        return transferTo(container, player, id, filters);
    }

    private static int[] emptyFilters(Container container, EntityPlayer player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !InventorySlotLocks.hasTypeLocks(player)) return new int[0];
        InventoryShape shape = InventoryLayout.of(player.inventory);
        int[] slots = new int[shape.mainSlots()];
        Arrays.fill(slots, -1);
        for (int id = 0; id < container.inventorySlots.size(); id++) {
            Slot slot = container.getSlot(id);
            int logical = InventorySlotLocks.index(player.inventory, slot);
            if (logical < 0 || logical >= slots.length
                || slots[logical] >= 0
                || InventorySlotLocks.template(player, logical) == null
                || !InventorySlotLocks.permits(player, logical, stack)
                || InventorySlotLocks.contains(player, logical)
                || HotbarBanks.isReserved(player.inventory, shape.rawIndex(logical))
                || !slot.func_111238_b()
                || slot.getHasStack()
                || !hasTransferRoom(slot, stack)) continue;
            slots[logical] = id;
        }
        return Arrays.stream(slots)
            .filter(id -> id >= 0)
            .toArray();
    }

    private static int[] filtersForPlayer(Container container, ItemStack source) {
        for (Object entry : container.inventorySlots) {
            if (((Slot) entry).inventory instanceof InventoryPlayer inventory)
                return emptyFilters(container, inventory.player, source);
        }
        return new int[0];
    }

    private static boolean equipArmor(Container container, ItemStack source) {
        for (int id = PlayerContainerLayout.ARMOR_START; id < PlayerContainerLayout.LEGACY_STORAGE_START; id++) {
            Slot slot = container.getSlot(id);
            if (!slot.getHasStack() && slot.isItemValid(source)) return merge(container, source, new int[] { id });
        }
        return false;
    }

    /** Detached player panels equip armor before applying their storage/hotbar routing. */
    public static ItemStack transferToArmor(Container container, EntityPlayer player, int id) {
        if (container != player.inventoryContainer || PlayerContainerLayout.mainIndex(player.inventory, id) < 0)
            return null;
        Slot source = container.getSlot(id);
        if (InventorySlotLocks.locked(source) || !source.getHasStack() || !source.canTakeStack(player)) return null;
        for (int armor = PlayerContainerLayout.ARMOR_START; armor
            < PlayerContainerLayout.LEGACY_STORAGE_START; armor++) {
            Slot destination = container.getSlot(armor);
            if (!destination.getHasStack() && destination.isItemValid(source.getStack()))
                return transferTo(container, player, id, new int[] { armor });
        }
        return null;
    }

    public static ItemStack transferTo(Container container, EntityPlayer player, int id, int[] destinations) {
        return transferTo(container, player, id, destinations, destinations.length);
    }

    public static ItemStack transferTo(Container container, EntityPlayer player, int id, int[] destinations,
        int preferred) {
        Slot slot = container.getSlot(id);
        if (InventorySlotLocks.locked(slot) || !slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved = merge(container, source, destinations, 0, preferred);
        if (source.stackSize > 0 && preferred < destinations.length)
            moved = merge(container, source, destinations, preferred, destinations.length) || moved;
        if (!moved) return null;
        if (source.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, source);
        return original;
    }

    public static boolean merge(Container container, ItemStack source, int[] destinations) {
        return merge(container, source, destinations, 0, destinations.length);
    }

    private static boolean merge(Container container, ItemStack source, int[] destinations, int start, int end) {
        int originalCount = source.stackSize;
        // Merge across the entire region before choosing an empty slot, including across the old/new boundary.
        if (source.isStackable()) {
            for (int i = start; i < end; i++) {
                Slot slot = container.getSlot(destinations[i]);
                if (reserved(slot)) continue;
                ItemStack existing = slot.getStack();
                if (existing == null || !slot.isItemValid(source)) continue;
                int count = Math.min(source.stackSize, mergeRoom(slot, source, existing));
                if (count > 0) {
                    existing.stackSize += count;
                    source.stackSize -= count;
                    slot.onSlotChanged();
                }
                if (source.stackSize == 0) return true;
            }
        }
        for (int i = start; i < end; i++) {
            if (source.stackSize == 0) break;
            Slot slot = container.getSlot(destinations[i]);
            if (reserved(slot)) continue;
            if (slot.getHasStack() || !slot.isItemValid(source)) continue;
            int count = Math.min(source.stackSize, mergeRoom(slot, source, null));
            if (count <= 0) continue;
            ItemStack inserted = source.copy();
            inserted.stackSize = count;
            slot.putStack(inserted);
            source.stackSize -= count;
        }
        return source.stackSize != originalCount;
    }

    public static boolean mergeAutomatic(Container container, ItemStack source, int[] destinations) {
        return mergeToPlayer(container, source, AutomaticInventoryRouting.filter(container, destinations));
    }

    /** Routed player targets are in logical order. Fill the active hotbar before touching Main storage. */
    public static boolean mergeToPlayer(Container container, ItemStack source, int[] destinations) {
        boolean preferred = merge(container, source, filtersForPlayer(container, source));
        if (source.stackSize == 0) return preferred;
        int hotbar = hotbarPrefix(container, destinations);
        boolean moved = merge(container, source, destinations, 0, hotbar);
        return source.stackSize > 0 && merge(container, source, destinations, hotbar, destinations.length) || moved
            || preferred;
    }

    public static int hotbarPrefix(Container container, int[] destinations) {
        int end = 0;
        while (end < destinations.length) {
            Slot slot = container.getSlot(destinations[end]);
            if (!(slot.inventory instanceof InventoryPlayer) || slot.getSlotIndex() < 0
                || slot.getSlotIndex() >= InventoryLayout.HOTBAR_SLOTS) break;
            end++;
        }
        return end;
    }

    /** Native pattern extraction clears its source only on full success, so partial insertion is unsafe. */
    public static boolean mergeAutomaticAll(Container container, ItemStack source, int[] destinations) {
        int[] allowed = java.util.Arrays.stream(AutomaticInventoryRouting.filter(container, destinations))
            .distinct()
            .toArray();
        int[] capacity = IntStream.concat(Arrays.stream(filtersForPlayer(container, source)), Arrays.stream(allowed))
            .distinct()
            .toArray();
        return remainingAfterMerge(container, source, capacity) == 0 && mergeToPlayer(container, source, allowed);
    }

    /** Simulate the same slot rules as merge for a unique, already routed destination list. */
    public static int remainingAfterMerge(Container container, ItemStack source, int[] destinations) {
        int remaining = source.stackSize;
        for (int id : destinations) {
            Slot slot = container.getSlot(id);
            if (reserved(slot) || !slot.isItemValid(source)) continue;
            remaining -= Math.min(remaining, mergeRoom(slot, source, slot.getStack()));
            if (remaining == 0) break;
        }
        return remaining;
    }

    public static boolean hasTransferRoom(Slot slot, ItemStack stack) {
        return slot.isItemValid(stack) && mergeRoom(slot, stack, slot.getStack()) > 0;
    }

    private static int mergeRoom(Slot slot, ItemStack source, ItemStack existing) {
        int limit = Math.min(slot.getSlotStackLimit(), source.getMaxStackSize());
        if (existing == null) return Math.max(0, limit);
        if (existing == source || !source.isStackable()
            || existing.getItem() != source.getItem()
            || (source.getHasSubtypes() && existing.getItemDamage() != source.getItemDamage())
            || !ItemStack.areItemStackTagsEqual(existing, source)) return 0;
        return Math.max(0, limit - existing.stackSize);
    }

    private static int[] mainSlots(InventoryPlayer inventory, int start, int end) {
        int[] ids = new int[end - start];
        for (int main = start; main < end; main++)
            ids[main - start] = PlayerContainerLayout.containerId(inventory, main);
        return ids;
    }

    private static boolean reserved(Slot slot) {
        return InventorySlotLocks.locked(slot) || slot.inventory instanceof InventoryPlayer inventory
            && dev.gilbe.dynamicinventory.inventory.HotbarBanks.isReserved(inventory, slot.getSlotIndex());
    }

}
