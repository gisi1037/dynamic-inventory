package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** Extra hotbars occupy stable ordinary storage cells. No duplicate item inventory or extra capacity. */
public final class HotbarBanks {

    public static final int MAX_BANKS = InventoryShape.MAX_STORAGE / 9;
    private static final String TAG = "dynamicinventoryHotbars";
    private static final java.util.Map<EntityPlayer, Snapshot> SNAPSHOTS = java.util.Collections
        .synchronizedMap(new java.util.WeakHashMap<>());

    private static final class Snapshot {

        final int[] source, active;
        final int capacity, legacy;

        Snapshot(int[] source, int[] active, int capacity, int legacy) {
            this.source = source;
            this.active = active;
            this.capacity = capacity;
            this.legacy = legacy;
        }
    }

    private HotbarBanks() {}

    private static NBTTagCompound data(EntityPlayer player) {
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(TAG, 10)) {
            persisted.setTag(TAG, new NBTTagCompound());
            root.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        return persisted.getCompoundTag(TAG);
    }

    public static int count(EntityPlayer player) {
        return activeSlots(player).length / 9;
    }

    /** Stable storage identities, excluding the real hotbar and Backhand equipment cell. */
    public static int[] slots(EntityPlayer player) {
        return activeSlots(player).clone();
    }

    private static int[] activeSlots(EntityPlayer player) {
        if (player == null) return new int[0];
        int capacity = InventoryLayout.of(player.inventory)
            .storageSlots();
        NBTTagCompound tag = data(player);
        int[] saved = tag.hasKey("slots", 11) ? tag.getIntArray("slots") : null;
        int legacy = saved == null ? tag.getInteger("count") : 0;
        Snapshot cached = SNAPSHOTS.get(player);
        if (cached != null && cached.source == saved && cached.capacity == capacity && cached.legacy == legacy)
            return cached.active;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int[] active = saved == null
            ? java.util.stream.IntStream.range(0, Math.max(0, Math.min(capacity / 9, legacy)) * 9)
                .toArray()
            : java.util.Arrays.stream(saved)
                .filter(i -> i >= 0 && i < capacity && seen.add(i))
                .toArray();
        active = java.util.Arrays.copyOf(active, active.length / 9 * 9);
        SNAPSHOTS.put(player, new Snapshot(saved, active, capacity, legacy));
        return active;
    }

    public static java.util.Set<Integer> excluded(InventoryPlayer inventory) {
        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (int slot : slots(inventory.player)) result.add(slot);
        return result;
    }

    public static int bankIndex(InventoryPlayer inventory, int raw) {
        int storage = InventoryLayout.of(inventory)
            .ordinaryIndex(raw) - 9;
        int[] slots = activeSlots(inventory.player);
        for (int i = 0; i < slots.length; i++) if (slots[i] == storage) return i;
        return -1;
    }

    public static int reserved(InventoryPlayer inventory) {
        return count(inventory.player) * 9;
    }

    public static boolean isReserved(InventoryPlayer inventory, int raw) {
        return bankIndex(inventory, raw) >= 0;
    }

    public static void receive(EntityPlayer player, int count) {
        receive(
            player,
            java.util.stream.IntStream.range(0, Math.max(0, Math.min(MAX_BANKS, count)) * 9)
                .toArray());
    }

    public static void receive(EntityPlayer player, int[] slots) {
        data(player).setInteger("count", slots.length / 9);
        data(player).setIntArray("slots", slots.clone());
    }

    public static void configure(EntityPlayerMP player, int count) {
        if (count < 0 || count > MAX_BANKS) throw new IllegalArgumentException("Invalid hotbar count");
        configure(
            player,
            java.util.stream.IntStream.range(0, Math.max(0, count) * 9)
                .toArray());
    }

    public static void configure(EntityPlayerMP player, int[] proposed) {
        requireReady(player);
        int capacity = InventoryLayout.of(player.inventory)
            .storageSlots();
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        if (proposed.length > capacity || proposed.length % 9 != 0)
            throw new IllegalArgumentException("Not enough storage for that many hotbars");
        for (int slot : proposed) if (slot < 0 || slot >= capacity || !unique.add(slot))
            throw new IllegalArgumentException("Invalid reserved storage slot");
        receive(player, proposed);
        data(player).setInteger("next", 0);
        player.inventory.markDirty();
    }

    /** Direct keys exchange the selected stored row; cycling visits the stored rows in sequence. */
    public static void swap(EntityPlayerMP player, int row) {
        requireReady(player);
        int count = count(player);
        if (count == 0) return;
        int selected = row < 0 ? Math.floorMod(data(player).getInteger("next"), count) : row;
        if (selected < 0 || selected >= count) throw new IllegalArgumentException("Hotbar is not reserved");
        InventoryShape shape = InventoryLayout.of(player.inventory);
        int[] reserved = slots(player);
        ItemStack[] stacks = player.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            int bank = shape.rawIndex(9 + reserved[selected * 9 + i]);
            ItemStack previous = stacks[i];
            stacks[i] = stacks[bank];
            stacks[bank] = previous;
        }
        data(player).setInteger("next", (selected + 1) % count);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    private static void requireReady(EntityPlayerMP player) {
        if (player.isDead || player.getHealth() <= 0
            || player.openContainer != player.inventoryContainer
            || player.inventory.getItemStack() != null
            || player.isUsingItem()
            || !InventoryUi.isOpeningReady(player))
            throw new IllegalStateException(
                "Close other interfaces and put down the cursor item before changing hotbars");
    }
}
