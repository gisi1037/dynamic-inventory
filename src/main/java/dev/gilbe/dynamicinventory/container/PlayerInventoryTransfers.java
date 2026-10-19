package dev.gilbe.dynamicinventory.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Shift-click destinations use logical regions rather than assuming storage has contiguous container IDs. */
public final class PlayerInventoryTransfers {

    private PlayerInventoryTransfers() {}

    public static ItemStack transfer(Container container, EntityPlayer player, int id) {
        int[] storage = mainSlots(InventoryLayout.HOTBAR_SLOTS, InventoryLayout.mainSlots(), false);
        int[] hotbar = mainSlots(0, InventoryLayout.HOTBAR_SLOTS, false);
        int[] allMain = mainSlots(InventoryLayout.HOTBAR_SLOTS, InventoryLayout.mainSlots(), true);
        if (id < 0 || id >= container.inventorySlots.size()) return null;
        Slot slot = container.getSlot(id);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved;
        if (id == PlayerContainerLayout.RESULT) {
            moved = merge(container, source, reversed(allMain));
            if (moved) slot.onSlotChange(source, original);
        } else if (id < PlayerContainerLayout.LEGACY_STORAGE_START
            || (BackhandCompatibility.enabled() && id == PlayerContainerLayout.OFFHAND)
            || id >= PlayerContainerLayout.slotCount()) {
                moved = merge(container, source, allMain);
            } else {
                moved = equip(container, source);
                if (!moved) {
                    int mainIndex = PlayerContainerLayout.mainIndex(id);
                    moved = merge(container, source, mainIndex < InventoryLayout.HOTBAR_SLOTS ? storage : hotbar);
                }
            }
        if (!moved) return null;
        if (source.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, source);
        return original;
    }

    private static boolean equip(Container container, ItemStack source) {
        for (int id = PlayerContainerLayout.ARMOR_START; id < PlayerContainerLayout.LEGACY_STORAGE_START; id++) {
            Slot slot = container.getSlot(id);
            if (!slot.getHasStack() && slot.isItemValid(source)) return merge(container, source, new int[] { id });
        }
        for (int id = PlayerContainerLayout.slotCount(); id < container.inventorySlots.size(); id++) {
            Slot slot = container.getSlot(id);
            if (slot.isItemValid(source) && merge(container, source, new int[] { id })) return true;
        }
        return false;
    }

    public static ItemStack transferTo(Container container, EntityPlayer player, int id, int[] destinations) {
        Slot slot = container.getSlot(id);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return null;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        if (!merge(container, source, destinations)) return null;
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

    private static int[] mainSlots(int start, int end, boolean includeHotbar) {
        int[] ids = new int[end - start + (includeHotbar ? InventoryLayout.HOTBAR_SLOTS : 0)];
        int next = 0;
        for (int main = start; main < end; main++) ids[next++] = PlayerContainerLayout.containerId(main);
        if (includeHotbar) {
            for (int main = 0; main < InventoryLayout.HOTBAR_SLOTS; main++)
                ids[next++] = PlayerContainerLayout.containerId(main);
        }
        return ids;
    }

    private static int[] reversed(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[source.length - i - 1];
        return result;
    }
}
