package dev.gilbe.dynamicinventory.inventory;

import java.util.Arrays;
import java.util.BitSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;

/** Persistent positions in the player's inventory. Equipment IDs are independent of inventory capacity. */
public final class InventorySlotLocks {

    private static final String TAG = "dynamicinventorySlotLocks";
    private static final int ARMOR = InventoryShape.MAX_STORAGE + InventoryShape.HOTBAR_SLOTS;
    private static final int OFFHAND = ARMOR + InventoryShape.ARMOR_SLOTS;
    public static final int LIMIT = OFFHAND + 1;

    private InventorySlotLocks() {}

    /** Slot types backed by framework item handlers expose their canonical player index here. */
    public interface Backing {

        int dynamicinventory$lockIndex(InventoryPlayer inventory);
    }

    public static int index(InventoryPlayer inventory, Slot slot) {
        if (slot == null) return -1;
        if (slot.inventory == inventory) return index(inventory, slot.getSlotIndex());
        return slot instanceof Backing backing ? backing.dynamicinventory$lockIndex(inventory) : -1;
    }

    public static int index(InventoryPlayer inventory, int raw) {
        InventoryShape shape = InventoryLayout.of(inventory);
        int ordinary = shape.ordinaryIndex(raw);
        if (ordinary >= 0) return ordinary;
        if (shape.hasOffhand() && raw == InventoryShape.OFFHAND_INDEX) return OFFHAND;
        int armor = raw - shape.rawMainSlots();
        return armor >= 0 && armor < InventoryShape.ARMOR_SLOTS ? ARMOR + armor : -1;
    }

    public static boolean locked(InventoryPlayer inventory, int raw) {
        return inventory.player != null && contains(inventory.player, index(inventory, raw));
    }

    public static boolean locked(Slot slot, EntityPlayer player) {
        return player != null && contains(player, index(player.inventory, slot));
    }

    public static boolean locked(Slot slot) {
        return slot.inventory instanceof InventoryPlayer inventory && locked(inventory, slot.getSlotIndex());
    }

    public static boolean locked(Container container, int slot, EntityPlayer player) {
        return slot >= 0 && slot < container.inventorySlots.size() && locked(container.getSlot(slot), player);
    }

    public static boolean blocksClick(Container container, EntityPlayer player, int slot, int button, int mode) {
        return locked(container, slot, player)
            || mode == 2 && button >= 0 && button < InventoryShape.HOTBAR_SLOTS && locked(player.inventory, button);
    }

    public static boolean contains(EntityPlayer player, int index) {
        if (player == null || index < 0 || index >= LIMIT) return false;
        NBTTagCompound root = player.getEntityData();
        return root.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10) && Arrays.binarySearch(
            root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getIntArray(TAG),
            index) >= 0;
    }

    public static int[] snapshot(EntityPlayer player) {
        return entries(player).stream()
            .toArray();
    }

    public static void toggle(EntityPlayer player, int index) {
        if (index < 0 || index >= LIMIT) return;
        BitSet entries = entries(player);
        entries.flip(index);
        receive(
            player,
            entries.stream()
                .toArray());
    }

    public static void receive(EntityPlayer player, int[] sorted) {
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound saved = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        saved.setIntArray(TAG, sorted);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, saved);
    }

    private static BitSet entries(EntityPlayer player) {
        BitSet entries = new BitSet(LIMIT);
        for (int index : player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getIntArray(TAG)) {
            if (index >= 0 && index < LIMIT) entries.set(index);
        }
        return entries;
    }
}
