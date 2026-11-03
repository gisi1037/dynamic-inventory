package dev.gilbe.dynamicinventory.inventory;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Predict permutations, then replay unacknowledged input over the server's authoritative snapshot. */
public final class HotbarPrediction {

    public static final int MAX_PENDING = 32;

    public static final class Swap {

        public final long sequence;
        public final int row;

        private Swap(long sequence, int row) {
            this.sequence = sequence;
            this.row = row;
        }
    }

    private EntityPlayer owner;
    private long revision, sequence;
    private boolean supported;
    private final Deque<Swap> pending = new ArrayDeque<>();

    public synchronized void reset() {
        owner = null;
        supported = false;
        pending.clear();
    }

    public synchronized void refresh(EntityPlayer player) {
        if (owner != player || player == null
            || revision != InventoryLayout.of(player.inventory)
                .revision())
            reset();
    }

    public synchronized void enable(EntityPlayer player, long revision, int next) {
        refresh(player);
        if (player == null || revision != InventoryLayout.of(player.inventory)
            .revision()) return;
        owner = player;
        this.revision = revision;
        supported = true;
        if (pending.isEmpty()) HotbarBanks.next(player, next);
    }

    public synchronized boolean supported(EntityPlayer player) {
        refresh(player);
        return supported;
    }

    public synchronized Swap predict(EntityPlayer player, int row) {
        if (!supported(player) || pending.size() >= MAX_PENDING || HotbarBanks.count(player) == 0) return null;
        HotbarBanks.requireReady(player);
        int selected = row < 0 ? HotbarBanks.next(player) : row;
        HotbarBanks.exchange(player, selected);
        Swap swap = new Swap(++sequence, selected);
        pending.addLast(swap);
        return swap;
    }

    public synchronized boolean pending(EntityPlayer player) {
        refresh(player);
        return !pending.isEmpty();
    }

    public synchronized boolean protects(EntityPlayer player, int raw) {
        return pending(player) && (raw >= 0 && raw < 9 || HotbarBanks.isReserved(player.inventory, raw));
    }

    public synchronized void reconcile(EntityPlayer player, long sequence, long revision, int next, int[] slots,
        ItemStack[] stacks) {
        refresh(player);
        if (owner != player || this.revision != revision
            || pending.isEmpty()
            || pending.stream()
                .noneMatch(swap -> swap.sequence == sequence))
            return;
        if (stacks.length != 9 + slots.length) throw new IllegalArgumentException("Invalid hotbar snapshot");
        InventoryShape shape = InventoryLayout.of(player.inventory);
        for (int slot : slots) if (slot < 0 || slot >= shape.storageSlots())
            throw new IllegalArgumentException("Hotbar snapshot exceeds capacity");
        while (!pending.isEmpty() && pending.peekFirst().sequence <= sequence) pending.removeFirst();
        HotbarBanks.receive(player, slots);
        HotbarBanks.next(player, next);
        for (int i = 0; i < stacks.length; i++) {
            int raw = i < 9 ? i : shape.rawIndex(9 + slots[i - 9]);
            player.inventory.mainInventory[raw] = ItemStack.copyItemStack(stacks[i]);
        }
        for (Swap swap : pending) if (swap.row < slots.length / 9) HotbarBanks.exchange(player, swap.row);
        player.inventory.markDirty();
    }
}
