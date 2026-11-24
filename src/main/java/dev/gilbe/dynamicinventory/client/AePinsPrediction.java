package dev.gilbe.dynamicinventory.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.AdaptorIInventory;
import dev.gilbe.dynamicinventory.compat.ae2.PinnedItemTransfer;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.AePinsChannel;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;

/** An optimistic render view. Vanilla packets and unrelated actions always read the real inventory. */
public final class AePinsPrediction {

    private static final class Move {

        final long id;
        final int index, argument;
        final Transfer action;
        final IAEItemStack item;
        final int[] slots;

        Move(long id, int index, Transfer action, int argument, IAEItemStack item, int[] slots) {
            this.id = id;
            this.index = index;
            this.action = action;
            this.argument = argument;
            this.item = item;
            this.slots = slots;
        }
    }

    private final Deque<Move> pending = new ArrayDeque<>();
    private final Map<IAEItemStack, Long> stock = new HashMap<>();
    private GuiContainer screen;
    private InventoryPlayer owner;
    private InventoryBasic contents;
    private ItemStack[] initial;
    private ItemStack cursor, initialCursor;
    private long sequence, revision, deadline;
    private volatile long acknowledged;
    private Thread rendering;

    void receive(long id) {
        acknowledged = id;
    }

    void update(GuiContainer gui, InventoryPlayer player, IntFunction<IAEStack<?>> pins) {
        if (pending.isEmpty()) return;
        if (screen != gui || owner != player
            || player.player.openContainer != gui.inventorySlots
            || revision != InventoryLayout.of(player)
                .revision()
            || System.nanoTime() - deadline >= 0) {
            clear();
            return;
        }
        if (pending.peekFirst().id > acknowledged) return;
        while (!pending.isEmpty() && pending.peekFirst().id <= acknowledged) pending.removeFirst();
        if (pending.isEmpty()) clear();
        else {
            resetView();
            for (Move move : pending) apply(move, pins);
        }
    }

    long predict(GuiContainer gui, InventoryPlayer player, int index, Transfer action, int argument, IAEItemStack item,
        IntFunction<IAEStack<?>> pins, int[] slots) {
        update(gui, player, pins);
        if (pending.size() == 32) return 0;
        if (pending.isEmpty()) {
            screen = gui;
            owner = player;
            revision = InventoryLayout.of(player)
                .revision();
            resetView();
        }
        Move move = new Move(++sequence, index, action, argument, item == null ? null : item.copy(), slots);
        pending.addLast(move);
        deadline = System.nanoTime() + 3_000_000_000L;
        apply(move, pins);
        return sequence;
    }

    void clear() {
        pending.clear();
        stock.clear();
        contents = null;
        initial = null;
        cursor = null;
        initialCursor = null;
        owner = null;
        screen = null;
        rendering = null;
    }

    private void resetView() {
        var shape = InventoryLayout.of(owner);
        contents = new InventoryBasic("", false, shape.mainSlots()) {

            @Override
            public boolean isItemValidForSlot(int logical, ItemStack stack) {
                return AutomaticInventoryRouting.transfer(owner, shape.rawIndex(logical));
            }
        };
        initial = new ItemStack[shape.mainSlots()];
        for (int logical = 0; logical < shape.mainSlots(); logical++) {
            initial[logical] = ItemStack.copyItemStack(owner.mainInventory[shape.rawIndex(logical)]);
            contents.setInventorySlotContents(logical, ItemStack.copyItemStack(initial[logical]));
        }
        initialCursor = ItemStack.copyItemStack(owner.getItemStack());
        cursor = ItemStack.copyItemStack(initialCursor);
        stock.clear();
    }

    private void apply(Move move, IntFunction<IAEStack<?>> pins) {
        var item = move.item;
        if (item == null) {
            depositPanel(move);
            return;
        }
        var key = item.copy()
            .setStackSize(1);
        IAEStack<?> current = move.action == Transfer.DEPOSIT_TERMINAL_ALL ? null : pins.apply(move.index);
        long available = stock.getOrDefault(
            key,
            current instanceof IAEItemStack pin && pin.isSameType(item) ? current.getStackSize() : item.getStackSize());
        int argument = move.argument;
        int max = item.getItemStack()
            .getMaxStackSize();
        int moved = switch (move.action) {
            case STACK, ALL, SCROLL_ITEMS -> argument < 0 ? depositSlots(item, -argument, true)
                : -withdraw(
                    item,
                    Math.min(
                        available,
                        move.action == Transfer.STACK ? max
                            : move.action == Transfer.ALL ? contents.getSizeInventory() * max : argument));
            case DEPOSIT_SLOT -> cursor == null
                ? deposit(item, screen.inventorySlots.getSlot(argument), Integer.MAX_VALUE)
                : 0;
            case SCROLL_DEPOSIT_SLOT -> cursor == null
                ? deposit(
                    item,
                    screen.inventorySlots.getSlot(AePinsChannel.scrollSlot(argument)),
                    AePinsChannel.scrollAmount(argument))
                : 0;
            case SCROLL_WITHDRAW_SLOT -> cursor == null ? -withdrawToSlot(item, argument, available) : 0;
            case DEPOSIT_ALL, DEPOSIT_TERMINAL_ALL -> cursor == null ? depositSlots(item, Integer.MAX_VALUE, false) : 0;
            case SPACE_PANEL -> cursor == null ? depositPanel(move) : 0;
            case PICKUP, SPLIT, SCROLL_CURSOR -> moveCursor(item, move.action, argument, available, max);
            case CRAFT -> 0;
        };
        if (move.action != Transfer.DEPOSIT_TERMINAL_ALL) stock.put(key, Math.max(0, available + moved));
    }

    private int depositPanel(Move move) {
        int moved = 0;
        for (int id : move.slots) {
            Slot slot = screen.inventorySlots.getSlot(id);
            if (PinnedItemTransfer.bogoPinned(owner.player, screen.inventorySlots, slot)) continue;
            if (move.item == null) {
                int logical = PinnedItemTransfer.playerIndex(owner.player, slot);
                if (logical >= 0) contents.setInventorySlotContents(logical, null);
            } else moved += deposit(move.item, slot, Integer.MAX_VALUE);
        }
        return moved;
    }

    private int withdraw(IAEItemStack item, long amount) {
        if (amount <= 0) return 0;
        ItemStack requested = item.getItemStack();
        requested.stackSize = (int) amount;
        ItemStack excess = new AdaptorIInventory(contents).addItems(requested);
        return (int) amount - (excess == null ? 0 : excess.stackSize);
    }

    private int withdrawToSlot(IAEItemStack item, int argument, long available) {
        Slot slot = screen.inventorySlots.getSlot(AePinsChannel.scrollSlot(argument));
        int logical = PinnedItemTransfer.playerIndex(owner.player, slot);
        if (logical < 0) return 0;
        ItemStack stack = contents.getStackInSlot(logical);
        int amount = (int) Math.min(
            available,
            Math.min(AePinsChannel.scrollAmount(argument), PinnedItemTransfer.scrollPullRoom(slot, stack, item)));
        if (amount > 0) stack.stackSize += amount;
        return amount;
    }

    private int moveCursor(IAEItemStack item, Transfer action, int steps, long available, int max) {
        boolean different = cursor != null && !item.isSameType(cursor);
        if (different && !(action == Transfer.SCROLL_CURSOR && steps > 0)) return 0;
        if (action == Transfer.SCROLL_CURSOR ? steps > 0 : cursor != null) {
            if (cursor == null) return 0;
            int amount = Math.min(
                cursor.stackSize,
                action == Transfer.SCROLL_CURSOR ? steps : action == Transfer.SPLIT ? 1 : cursor.stackSize);
            cursor.stackSize -= amount;
            if (cursor.stackSize == 0) cursor = null;
            // Shift-wheel can insert a different held item. Only its cursor count changes here.
            return different ? 0 : amount;
        }
        int room = max - (cursor == null ? 0 : cursor.stackSize);
        int amount = (int) Math.min(available, action == Transfer.SCROLL_CURSOR ? Math.min(room, -steps) : room);
        if (action == Transfer.SPLIT) amount = (amount + 1) / 2;
        if (amount <= 0) return 0;
        if (cursor == null) {
            cursor = item.getItemStack();
            cursor.stackSize = 0;
        }
        cursor.stackSize += amount;
        return -amount;
    }

    private int depositSlots(IAEItemStack item, int amount, boolean mainOnly) {
        int remaining = amount;
        for (Slot slot : PinnedItemTransfer.sourceSlots(owner.player, screen.inventorySlots, mainOnly)) {
            if (slot != null) remaining -= deposit(item, slot, remaining);
            if (remaining == 0) break;
        }
        return amount - remaining;
    }

    private int deposit(IAEItemStack item, Slot slot, int amount) {
        int logical = PinnedItemTransfer.playerIndex(owner.player, slot);
        if (logical < 0) return 0;
        ItemStack stack = contents.getStackInSlot(logical);
        if (stack == null || !item.isSameType(stack)) return 0;
        int moved = Math.min(stack.stackSize, amount);
        contents.decrStackSize(logical, moved);
        return moved;
    }

    ItemStack cursor(GuiContainer gui, InventoryPlayer inventory) {
        ItemStack actual = inventory.getItemStack();
        return gui == screen && inventory == owner && !ItemStack.areItemStacksEqual(cursor, initialCursor) ? cursor
            : actual;
    }

    ItemStack slot(GuiContainer gui, InventoryPlayer inventory, Slot slot) {
        int logical = gui == screen && inventory == owner ? PinnedItemTransfer.playerIndex(owner.player, slot) : -1;
        return logical >= 0 ? displayed(logical, slot.getStack()) : slot.getStack();
    }

    boolean protects(GuiContainer gui, InventoryPlayer inventory, int slotId, int mode) {
        if (gui != screen || inventory != owner) return false;
        if (!ItemStack.areItemStacksEqual(cursor(gui, inventory), owner.getItemStack())) return true;
        if (mode == 5 || mode == 6 || mode == 2) return true;
        if (slotId < 0 || slotId >= gui.inventorySlots.inventorySlots.size()) return false;
        Slot slot = gui.inventorySlots.getSlot(slotId);
        return !ItemStack.areItemStacksEqual(slot(gui, inventory, slot), slot.getStack());
    }

    void render(GuiContainer gui, boolean active) {
        if (!active) rendering = null;
        else if (gui == screen) rendering = Thread.currentThread();
    }

    ItemStack renderStack(InventoryPlayer inventory, int raw, ItemStack original) {
        if (rendering != Thread.currentThread() || inventory != owner) return original;
        if (raw == -1) return ItemStack.areItemStacksEqual(cursor, initialCursor) ? original : cursor;
        int logical = InventoryLayout.of(owner)
            .ordinaryIndex(raw);
        return logical < 0 || logical >= initial.length ? original : displayed(logical, original);
    }

    IAEStack<?> renderPin(GuiScreen gui, IAEStack<?> original) {
        if (gui != screen || !(original instanceof IAEItemStack item)) return original;
        Long available = stock.get(item);
        return available == null || available == item.getStackSize() ? original
            : item.copy()
                .setStackSize(available);
    }

    private ItemStack displayed(int logical, ItemStack original) {
        ItemStack predicted = contents.getStackInSlot(logical);
        return ItemStack.areItemStacksEqual(predicted, initial[logical]) ? original : predicted;
    }
}
