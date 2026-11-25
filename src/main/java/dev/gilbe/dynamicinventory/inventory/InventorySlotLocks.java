package dev.gilbe.dynamicinventory.inventory;

import java.util.Arrays;
import java.util.BitSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Persistent positions in the player's inventory. Equipment IDs are independent of inventory capacity. */
public final class InventorySlotLocks {

    private static final String TAG = "dynamicinventorySlotLocks";
    private static final String TYPES = "dynamicinventorySlotTypes";
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
        if (locked(container, slot, player)) return true;
        if (slot < 0 || slot >= container.inventorySlots.size()) return false;
        Slot target = container.getSlot(slot);
        if (mode == 2 && button >= 0 && button < InventoryShape.HOTBAR_SLOTS) {
            return locked(player.inventory, button) || !permits(player, button, target.getStack())
                || !permits(player, index(player.inventory, target), player.inventory.getStackInSlot(button));
        }
        return mode == 0 && !permits(player, index(player.inventory, target), player.inventory.getItemStack());
    }

    /** Match item and subtype while allowing damage and mutable item data such as charge to change. */
    public static boolean permits(EntityPlayer player, int index, ItemStack stack) {
        if (stack == null) return true;
        ItemStack type = template(player, index);
        return type == null || type.getItem() == stack.getItem()
            && (!type.getHasSubtypes() || type.getItemDamage() == stack.getItemDamage());
    }

    public static boolean accepts(InventoryPlayer inventory, int raw, ItemStack stack) {
        int id = index(inventory, raw);
        return !contains(inventory.player, id) && permits(inventory.player, id, stack);
    }

    public static ItemStack template(EntityPlayer player, int index) {
        if (player == null || index < 0 || index >= LIMIT) return null;
        NBTTagCompound saved = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!saved.hasKey(TYPES, 10)) return null;
        NBTTagCompound types = saved.getCompoundTag(TYPES);
        String key = Integer.toString(index);
        if (!types.hasKey(key, 10)) return null;
        NBTTagCompound entry = types.getCompoundTag(key);
        Object item = Item.itemRegistry.getObject(entry.getString("item"));
        return item instanceof Item type ? new ItemStack(type, 1, entry.getInteger("meta")) : null;
    }

    public static boolean hasTypeLocks(EntityPlayer player) {
        return player != null && !player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getCompoundTag(TYPES)
            .hasNoTags();
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

    static void exchangeFullLocks(EntityPlayer player, int first, int second) {
        if (contains(player, first) == contains(player, second)) return;
        BitSet locks = entries(player);
        locks.flip(first);
        locks.flip(second);
        receive(
            player,
            locks.stream()
                .toArray());
    }

    public static void toggle(EntityPlayer player, int index) {
        if (index < 0 || index >= LIMIT) return;
        BitSet entries = entries(player);
        entries.flip(index);
        if (entries.get(index)) setType(player, index, null);
        receive(
            player,
            entries.stream()
                .toArray());
    }

    public static void toggleType(EntityPlayer player, int index, ItemStack example) {
        if (index < 0 || index >= LIMIT) return;
        if (template(player, index) != null) setType(player, index, null);
        else if (example != null && example.getItem() != null) {
            if (contains(player, index)) toggle(player, index);
            setType(player, index, example);
        }
    }

    public static int[] typeSnapshot(EntityPlayer player) {
        java.util.stream.IntStream.Builder data = java.util.stream.IntStream.builder();
        for (int index = 0; index < LIMIT; index++) {
            ItemStack stack = template(player, index);
            if (stack != null) {
                data.add(index);
                data.add(Item.getIdFromItem(stack.getItem()));
                data.add(stack.getItemDamage());
            }
        }
        return data.build()
            .toArray();
    }

    public static void receive(EntityPlayer player, int[] sorted, int[] types) {
        receive(player, sorted);
        player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .removeTag(TYPES);
        for (int i = 0; i < types.length; i += 3) {
            Item item = Item.getItemById(types[i + 1]);
            if (item != null) setType(player, types[i], new ItemStack(item, 1, types[i + 2]));
        }
    }

    private static void setType(EntityPlayer player, int index, ItemStack stack) {
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound saved = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound types = saved.getCompoundTag(TYPES);
        String key = Integer.toString(index);
        if (stack == null) types.removeTag(key);
        else {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("item", Item.itemRegistry.getNameForObject(stack.getItem()));
            entry.setInteger("meta", stack.getHasSubtypes() ? stack.getItemDamage() : 0);
            types.setTag(key, entry);
        }
        saved.setTag(TYPES, types);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, saved);
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
