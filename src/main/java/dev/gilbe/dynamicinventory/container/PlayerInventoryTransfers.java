package dev.gilbe.dynamicinventory.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Shift-click destinations use logical regions rather than assuming storage has contiguous container IDs. */
public final class PlayerInventoryTransfers {

    private PlayerInventoryTransfers() {}

    public static ItemStack transfer(Container container, EntityPlayer player, int id) {
        InventoryPlayer inventory = player.inventory;
        int[] storage = mainSlots(
            inventory,
            InventoryLayout.HOTBAR_SLOTS,
            InventoryLayout.of(inventory)
                .mainSlots(),
            false);
        int[] hotbar = mainSlots(inventory, 0, InventoryLayout.HOTBAR_SLOTS, false);
        int[] allMain = mainSlots(
            inventory,
            InventoryLayout.HOTBAR_SLOTS,
            InventoryLayout.of(inventory)
                .mainSlots(),
            true);
        if (id < 0 || id >= container.inventorySlots.size()) return null;
        // Appended Baubles equipment is manual-only in our player screen, in both
        // directions. Shift-click moves ordinary storage/hotbar items or equips armor.
        if (id >= PlayerContainerLayout.slotCount(inventory)) return null;
        Slot slot = container.getSlot(id);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved;
        if (id == PlayerContainerLayout.RESULT) {
            moved = merge(container, source, reversed(allMain));
            if (moved) slot.onSlotChange(source, original);
        } else if (id < PlayerContainerLayout.LEGACY_STORAGE_START
            || (BackhandCompatibility.enabled() && id == PlayerContainerLayout.OFFHAND)) {
                moved = merge(container, source, allMain);
            } else {
                moved = equipArmor(container, source);
                if (!moved) {
                    int mainIndex = PlayerContainerLayout.mainIndex(inventory, id);
                    if (mainIndex < InventoryLayout.HOTBAR_SLOTS) moved = merge(container, source, storage);
                    else {
                        moved = merge(container, source, hotbar);
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

    private static boolean equipArmor(Container container, ItemStack source) {
        for (int id = PlayerContainerLayout.ARMOR_START; id < PlayerContainerLayout.LEGACY_STORAGE_START; id++) {
            Slot slot = container.getSlot(id);
            if (!slot.getHasStack() && slot.isItemValid(source)) return merge(container, source, new int[] { id });
        }
        return false;
    }

    public static ItemStack transferTo(Container container, EntityPlayer player, int id, int[] destinations) {
        return transferTo(container, player, id, destinations, destinations.length);
    }

    public static ItemStack transferTo(Container container, EntityPlayer player, int id, int[] destinations,
        int preferred) {
        Slot slot = container.getSlot(id);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved = merge(container, source, java.util.Arrays.copyOf(destinations, preferred));
        if (source.stackSize > 0 && preferred < destinations.length)
            moved = merge(container, source, java.util.Arrays.copyOfRange(destinations, preferred, destinations.length))
                || moved;
        if (!moved) return null;
        if (source.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, source);
        return original;
    }

    public static boolean merge(Container container, ItemStack source, int[] destinations) {
        int originalCount = source.stackSize;
        // Merge across the entire region before choosing an empty slot, including across the old/new boundary.
        if (source.isStackable()) {
            for (int id : destinations) {
                Slot slot = container.getSlot(id);
                if (reserved(slot)) continue;
                ItemStack existing = slot.getStack();
                if (existing == null || !slot.isItemValid(source)
                    || existing.getItem() != source.getItem()
                    || (source.getHasSubtypes() && existing.getItemDamage() != source.getItemDamage())
                    || !ItemStack.areItemStackTagsEqual(existing, source)) continue;
                int limit = Math.min(slot.getSlotStackLimit(), source.getMaxStackSize());
                int count = Math.min(source.stackSize, Math.max(0, limit - existing.stackSize));
                if (count > 0) {
                    existing.stackSize += count;
                    source.stackSize -= count;
                    slot.onSlotChanged();
                }
                if (source.stackSize == 0) return true;
            }
        }
        for (int id : destinations) {
            if (source.stackSize == 0) break;
            Slot slot = container.getSlot(id);
            if (reserved(slot)) continue;
            if (slot.getHasStack() || !slot.isItemValid(source)) continue;
            int count = Math.min(source.stackSize, Math.min(slot.getSlotStackLimit(), source.getMaxStackSize()));
            if (count <= 0) continue;
            ItemStack inserted = source.copy();
            inserted.stackSize = count;
            slot.putStack(inserted);
            source.stackSize -= count;
        }
        return source.stackSize != originalCount;
    }

    public static boolean mergeAutomatic(Container container, ItemStack source, int[] destinations) {
        return merge(
            container,
            source,
            dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting.filter(container, destinations));
    }

    /** Native pattern extraction clears its source only on full success, so partial insertion is unsafe. */
    public static boolean mergeAutomaticAll(Container container, ItemStack source, int[] destinations) {
        int[] allowed = java.util.Arrays
            .stream(dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting.filter(container, destinations))
            .distinct()
            .toArray();
        long room = 0;
        for (int id : allowed) {
            Slot slot = container.getSlot(id);
            if (reserved(slot) || !slot.isItemValid(source)) continue;
            ItemStack existing = slot.getStack();
            int limit = Math.min(slot.getSlotStackLimit(), source.getMaxStackSize());
            if (existing == null) room += Math.max(0, limit);
            else if (source.isStackable() && existing.getItem() == source.getItem()
                && (!source.getHasSubtypes() || existing.getItemDamage() == source.getItemDamage())
                && ItemStack.areItemStackTagsEqual(existing, source)) room += Math.max(0, limit - existing.stackSize);
            if (room >= source.stackSize) return merge(container, source, allowed);
        }
        return false;
    }

    private static int[] mainSlots(InventoryPlayer inventory, int start, int end, boolean includeHotbar) {
        int[] ids = new int[end - start + (includeHotbar ? InventoryLayout.HOTBAR_SLOTS : 0)];
        int next = 0;
        for (int main = start; main < end; main++) ids[next++] = PlayerContainerLayout.containerId(inventory, main);
        if (includeHotbar) {
            for (int main = 0; main < InventoryLayout.HOTBAR_SLOTS; main++)
                ids[next++] = PlayerContainerLayout.containerId(inventory, main);
        }
        return ids;
    }

    private static boolean reserved(Slot slot) {
        return slot.inventory instanceof InventoryPlayer inventory
            && dev.gilbe.dynamicinventory.inventory.HotbarBanks.isReserved(inventory, slot.getSlotIndex());
    }

    private static int[] reversed(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[source.length - i - 1];
        return result;
    }
}
