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
        int[] active = saved == null
            ? java.util.stream.IntStream.range(0, Math.max(0, Math.min(capacity / 9, legacy)) * 9)
                .toArray()
            : completeRows(saved, capacity);
        SNAPSHOTS.put(player, new Snapshot(saved, active, capacity, legacy));
        return active;
    }

    private static int[] completeRows(int[] saved, int capacity) {
        int[] result = new int[Math.min(saved.length, capacity)];
        java.util.BitSet assigned = new java.util.BitSet(capacity);
        java.util.BitSet row = new java.util.BitSet(capacity);
        int length = 0;
        for (int offset = 0; offset + 9 <= saved.length; offset += 9) {
            row.clear();
            for (int i = offset; i < offset + 9; i++) {
                int slot = saved[i];
                if (slot < 0 || slot >= capacity || assigned.get(slot) || row.get(slot)) break;
                row.set(slot);
            }
            if (row.cardinality() != 9) continue;
            System.arraycopy(saved, offset, result, length, 9);
            assigned.or(row);
            length += 9;
        }
        return java.util.Arrays.copyOf(result, length);
    }

    /** The persisted rows include complete assignments that are temporarily outside a smaller inventory. */
    private static int[] configuredSlots(EntityPlayer player) {
        NBTTagCompound tag = data(player);
        if (tag.hasKey("slots", 11)) return completeRows(tag.getIntArray("slots"), InventoryShape.MAX_STORAGE);
        return java.util.stream.IntStream.range(0, Math.max(0, Math.min(MAX_BANKS, tag.getInteger("count"))) * 9)
            .toArray();
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
        // The editor submits usable rows. Keep dormant rows in their original positions unless a
        // newly assigned visible row explicitly uses one of their cells.
        int[] configured = configuredSlots(player);
        java.util.stream.IntStream.Builder rows = java.util.stream.IntStream.builder();
        int next = 0;
        for (int offset = 0; offset < configured.length; offset += 9) {
            int end = offset + 9;
            if (java.util.Arrays.stream(configured, offset, end)
                .anyMatch(slot -> slot >= capacity)) {
                if (java.util.Arrays.stream(configured, offset, end)
                    .noneMatch(unique::contains)) for (int i = offset; i < end; i++) rows.add(configured[i]);
            } else if (next < proposed.length) for (int i = 0; i < 9; i++) rows.add(proposed[next++]);
        }
        while (next < proposed.length) rows.add(proposed[next++]);
        receive(
            player,
            rows.build()
                .toArray());
        data(player).setInteger("next", 0);
        player.inventory.markDirty();
    }

    /** Direct keys exchange the selected stored row; cycling visits the stored rows in sequence. */
    public static void swap(EntityPlayerMP player, int row) {
        swap(player, row, false);
    }

    public static void swap(EntityPlayerMP player, int row, boolean ignoreLocks) {
        requireReady(player);
        exchange(player, row, ignoreLocks);
        player.inventoryContainer.detectAndSendChanges();
    }

    public static int next(EntityPlayer player) {
        int count = count(player);
        return count == 0 ? 0 : Math.floorMod(data(player).getInteger("next"), count);
    }

    public static void next(EntityPlayer player, int next) {
        data(player).setInteger("next", next);
    }

    /** Same permutation for server execution and local prediction; no packets or container callbacks. */
    public static void exchange(EntityPlayer player, int row) {
        exchange(player, row, false);
    }

    public static void exchange(EntityPlayer player, int row, boolean ignoreLocks) {
        int count = count(player);
        if (count == 0) return;
        int selected = row < 0 ? next(player) : row;
        if (selected < 0 || selected >= count) throw new IllegalArgumentException("Hotbar is not reserved");
        InventoryShape shape = InventoryLayout.of(player.inventory);
        int[] reserved = slots(player);
        ItemStack[] stacks = player.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            int logical = 9 + reserved[selected * 9 + i];
            int bank = shape.rawIndex(logical);
            if (!ignoreLocks && (InventorySlotLocks.locked(player.inventory, i)
                || InventorySlotLocks.locked(player.inventory, bank))) continue;
            if (!InventorySlotLocks.permits(player, i, stacks[bank])
                || !InventorySlotLocks.permits(player, logical, stacks[i])) continue;
            ItemStack previous = stacks[i];
            stacks[i] = stacks[bank];
            stacks[bank] = previous;
            if (ignoreLocks) InventorySlotLocks.exchangeFullLocks(player, i, logical);
        }
        data(player).setInteger("next", (selected + 1) % count);
        player.inventory.markDirty();
    }

    public static void requireReady(EntityPlayer player) {
        if (player.isDead || player.getHealth() <= 0
            || player.openContainer != player.inventoryContainer
            || player.inventory.getItemStack() != null
            || player.isUsingItem()
            || !InventoryUi.isOpeningReady(player))
            throw new IllegalStateException(
                "Close other interfaces and put down the cursor item before changing hotbars");
    }
}
