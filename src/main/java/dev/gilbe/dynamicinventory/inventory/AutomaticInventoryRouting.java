package dev.gilbe.dynamicinventory.inventory;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Automatic destinations only. Explicit cursor placement and panel-to-panel moves remain unrestricted. */
public final class AutomaticInventoryRouting {

    private static final String TAG = "dynamicinventoryMainStorage";
    private static final Map<EntityPlayer, State> STATES = new WeakHashMap<>();

    private static final class State {

        final long revision;
        final int window;
        final int[] pickup, transfer;
        final boolean[] pickupMask = new boolean[InventoryShape.MAX_STORAGE],
            transferMask = new boolean[InventoryShape.MAX_STORAGE];
        boolean dirty = true;

        State(long revision, int window, int[] pickup, int[] transfer) {
            this.revision = revision;
            this.window = window;
            this.pickup = pickup.clone();
            this.transfer = transfer.clone();
            for (int slot : pickup) pickupMask[slot] = true;
            for (int slot : transfer) transferMask[slot] = true;
        }
    }

    private AutomaticInventoryRouting() {}

    public static boolean receive(EntityPlayer player, long revision, int window, int[] pickup, int[] transfer) {
        InventoryShape shape = InventoryLayout.of(player.inventory);
        if (revision != shape.revision() || !valid(pickup, shape.storageSlots())
            || !valid(transfer, shape.storageSlots())) return false;
        synchronized (STATES) {
            STATES.put(player, new State(revision, window, pickup, transfer));
        }
        return true;
    }

    private static boolean valid(int[] slots, int capacity) {
        boolean[] seen = new boolean[capacity];
        for (int slot : slots) {
            if (slot < 0 || slot >= capacity || seen[slot]) return false;
            seen[slot] = true;
        }
        return true;
    }

    /** Persist on the server tick, never mutate player NBT from a network thread. */
    public static void persist(EntityPlayer player) {
        State state;
        synchronized (STATES) {
            state = STATES.get(player);
            if (state == null || !state.dirty) return;
            state.dirty = false;
        }
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound saved = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        saved.setIntArray(TAG, state.pickup.clone());
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, saved);
    }

    public static boolean pickup(InventoryPlayer inventory, int raw) {
        return allowed(inventory, raw);
    }

    public static boolean transfer(InventoryPlayer inventory, int raw) {
        return allowed(inventory, raw);
    }

    private static boolean allowed(InventoryPlayer inventory, int raw) {
        int logical = InventoryLayout.of(inventory)
            .ordinaryIndex(raw);
        if (logical < 0 || HotbarBanks.isReserved(inventory, raw)) return false;
        if (logical < 9) return true; // The active hotbar belongs to Main.
        EntityPlayer player = inventory.player;
        if (player == null) return true;
        State state;
        synchronized (STATES) {
            state = STATES.get(player);
        }
        if (state != null) {
            boolean active = state.revision == InventoryLayout.of(inventory)
                .revision() && player.openContainer != null && state.window == player.openContainer.windowId;
            return (active ? state.transferMask : state.pickupMask)[logical - 9];
        } else {
            NBTTagCompound saved = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            if (!saved.hasKey(TAG, 11)) return true;
            for (int storage : saved.getIntArray(TAG)) if (storage == logical - 9) return true;
        }
        return false;
    }

    public static int firstEmpty(InventoryPlayer inventory) {
        InventoryShape shape = InventoryLayout.of(inventory);
        for (int logical = 0; logical < shape.mainSlots(); logical++) {
            int raw = shape.rawIndex(logical);
            if (pickup(inventory, raw) && inventory.mainInventory[raw] == null) return raw;
        }
        return -1;
    }

    public static int mergeSlot(InventoryPlayer inventory, ItemStack source) {
        InventoryShape shape = InventoryLayout.of(inventory);
        for (int logical = 0; logical < shape.mainSlots(); logical++) {
            int raw = shape.rawIndex(logical);
            if (!pickup(inventory, raw)) continue;
            ItemStack existing = inventory.mainInventory[raw];
            if (existing != null && existing.getItem() == source.getItem()
                && existing.isStackable()
                && existing.stackSize < Math.min(existing.getMaxStackSize(), inventory.getInventoryStackLimit())
                && (!source.getHasSubtypes() || source.getItemDamage() == existing.getItemDamage())
                && ItemStack.areItemStackTagsEqual(existing, source)) return raw;
        }
        return -1;
    }

    /** Restrict an existing player-only range without expanding an undeclared container's capacity. */
    public static int[] nativeRange(Container container, int start, int end, boolean reverse) {
        if (start < 0 || start >= end || end > container.inventorySlots.size()) return null;
        int[] ids = new int[end - start];
        for (int i = 0; i < ids.length; i++) {
            int id = reverse ? end - 1 - i : start + i;
            Slot slot = container.getSlot(id);
            if (!(slot.inventory instanceof InventoryPlayer inventory) || InventoryLayout.of(inventory)
                .ordinaryIndex(slot.getSlotIndex()) < 0) return null;
            ids[i] = id;
        }
        int[] allowed = filter(container, ids);
        return allowed.length == ids.length ? null : allowed;
    }

    /** Keep original ordering and all native machine destinations; filter only automatic player insertions. */
    public static int[] filter(Container container, int[] ids) {
        return Arrays.stream(ids)
            .filter(id -> {
                Slot slot = container.getSlot(id);
                return !(slot.inventory instanceof InventoryPlayer inventory)
                    || transfer(inventory, slot.getSlotIndex());
            })
            .toArray();
    }
}
