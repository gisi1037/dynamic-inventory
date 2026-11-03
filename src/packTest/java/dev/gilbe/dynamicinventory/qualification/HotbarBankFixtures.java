package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.HotbarPrediction;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.network.HotbarSwapReply;

/** Headless tests execute the production reservation/swap logic and canonical merge with fake connected state. */
public final class HotbarBankFixtures {

    private HotbarBankFixtures() {}

    public static final class Player extends EntityPlayerMP {

        private Player() {
            super(null, null, null, null);
        }

    }

    private static final class Inventory extends InventoryPlayer implements ExpandedInventoryAccess {

        private InventoryShape shape;

        private Inventory(Player player, boolean offhand) {
            super(player);
            shape = new InventoryShape(216, offhand, 0);
            mainInventory = new ItemStack[shape.rawMainSlots()];
        }

        @Override
        public InventoryShape dynamicinventory$getShape() {
            return shape;
        }

        @Override
        public void dynamicinventory$setShape(InventoryShape value) {
            shape = value;
        }

        @Override
        public boolean dynamicinventory$hasCapacityOverride() {
            return false;
        }

        @Override
        public void dynamicinventory$setCapacityOverride(boolean value) {}

        @Override
        public NBTTagCompound dynamicinventory$getRecovery() {
            return null;
        }

        @Override
        public void dynamicinventory$setRecovery(NBTTagCompound value) {}
    }

    private static final class Host extends Container implements ExpandedContainerAccess {

        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
            return true;
        }

        @Override
        public void detectAndSendChanges() {}

        @Override
        public void dynamicinventory$removeListener(net.minecraft.inventory.ICrafting listener) {}

        @Override
        public ContainerInventoryRegion dynamicinventory$region() {
            return null;
        }

        @Override
        public void dynamicinventory$region(ContainerInventoryRegion value) {}

        @Override
        public Slot dynamicinventory$append(Slot slot) {
            return addSlotToContainer(slot);
        }
    }

    public static void main(String[] args) throws Exception {
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new java.io.File(".setup"));
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        sun.misc.Unsafe allocator = (sun.misc.Unsafe) access.get(null);
        Item item = new Item();
        // The headless process has no LaunchClassLoader/active FML mod. Supply vanilla's
        // namespaced registry so the real item packet codec can round-trip fixture NBT.
        Field registry = Item.class.getDeclaredField("itemRegistry");
        allocator.putObject(
            allocator.staticFieldBase(registry),
            allocator.staticFieldOffset(registry),
            new net.minecraft.util.RegistryNamespaced());
        Item.itemRegistry.addObject(32000, "dynamicinventory:hotbar_fixture", item);
        int checks = 0;
        for (boolean offhand : new boolean[] { false, true })
            for (int count = 1; count <= HotbarBanks.MAX_BANKS; count++) {
                Player player = (Player) allocator.allocateInstance(Player.class);
                net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(player);
                watcher.addObject(6, Float.valueOf(20));
                Field watched = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
                watched.setAccessible(true);
                watched.set(player, watcher);
                Inventory inventory = new Inventory(player, offhand);
                player.inventory = inventory;
                Host container = new Host();
                player.inventoryContainer = player.openContainer = container;
                for (int logical = 0; logical < inventory.shape.mainSlots(); logical++) {
                    ItemStack stack = new ItemStack(item, logical % 64 + 1);
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("identity", logical);
                    stack.setTagCompound(tag);
                    inventory.mainInventory[inventory.shape.rawIndex(logical)] = stack;
                    container.dynamicinventory$append(new Slot(inventory, inventory.shape.rawIndex(logical), 0, 0));
                }
                ItemStack[] before = inventory.mainInventory.clone();
                if (offhand) before[36] = inventory.mainInventory[36] = new ItemStack(item, 37);
                HotbarBanks.configure(player, count);
                check(
                    HotbarBanks.count(player) == count && HotbarBanks.reserved(inventory) == count * 9,
                    "reservation count");
                for (int row = 0; row < count; row++) {
                    HotbarBanks.swap(player, row);
                    for (int i = 0; i < 9; i++) check(
                        inventory.mainInventory[i] == before[inventory.shape.rawIndex(9 + row * 9 + i)],
                        "direct row identity");
                    HotbarBanks.swap(player, row);
                    check(
                        java.util.Arrays.equals(before, inventory.mainInventory),
                        "direct swap is lossless and reversible");
                    checks += 10;
                }
                HotbarBanks.configure(player, count);
                for (int step = 0; step < count + 1; step++) {
                    HotbarBanks.swap(player, -1);
                    int bank = step == count ? 0 : 9 + step * 9;
                    for (int i = 0; i < 9; i++) check(
                        inventory.mainInventory[i] == before[inventory.shape.rawIndex(bank + i)],
                        "cycle visits every row then original hotbar");
                    checks += 9;
                }
                if (offhand) check(inventory.mainInventory[36] == before[36], "offhand unaffected");
                ItemStack cursor = new ItemStack(item);
                inventory.setItemStack(cursor);
                ItemStack[] locked = inventory.mainInventory.clone();
                try {
                    HotbarBanks.swap(player, 0);
                    throw new AssertionError("occupied cursor accepted");
                } catch (IllegalStateException expected) {
                    check(java.util.Arrays.equals(locked, inventory.mainInventory), "refusal preserves stacks");
                }
                inventory.setItemStack(null);
                // Normal merges must never fill a reserved hotbar cell, even when it is empty.
                inventory.mainInventory[inventory.shape.rawIndex(9)] = null;
                ItemStack source = new ItemStack(item, 5);
                check(
                    !PlayerInventoryTransfers.merge(container, source, new int[] { 9 }) && source.stackSize == 5,
                    "automatic transfer skips reserved rows");
                HotbarBanks.configure(player, 0);
                check(
                    PlayerInventoryTransfers.merge(container, source, new int[] { 9 }) && source.stackSize == 0,
                    "released row returns to storage");
                // Explicit panel destinations must continue into Main when the hotbar is full.
                for (int i = 0; i < 9; i++) inventory.mainInventory[i] = new ItemStack(item, 64);
                inventory.mainInventory[inventory.shape.rawIndex(9)] = null;
                inventory.mainInventory[inventory.shape.rawIndex(10)] = new ItemStack(item, 61);
                inventory.mainInventory[inventory.shape.rawIndex(11)] = new ItemStack(item, 8);
                int[] targets = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 9 };
                check(
                    PlayerInventoryTransfers.transferTo(container, player, 11, targets) != null,
                    "full hotbar panel transfer");
                check(inventory.mainInventory[inventory.shape.rawIndex(11)] == null, "last subpanel stack removed");
                check(
                    inventory.mainInventory[inventory.shape.rawIndex(10)].stackSize == 64,
                    "merge before empty Main slot");
                check(
                    inventory.mainInventory[inventory.shape.rawIndex(9)].stackSize == 5,
                    "remainder reaches Main storage");
                inventory.mainInventory[inventory.shape.rawIndex(11)] = new ItemStack(item, 8);
                inventory.mainInventory[inventory.shape.rawIndex(9)].stackSize = 64;
                check(PlayerInventoryTransfers.transferTo(container, player, 11, targets) == null, "full Main refusal");
                check(inventory.mainInventory[inventory.shape.rawIndex(11)].stackSize == 8, "full Main retains source");
                HotbarBanks.configure(player, count);
                NBTTagCompound persisted = (NBTTagCompound) player.getEntityData()
                    .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG)
                    .copy();
                player.getEntityData()
                    .removeTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
                check(HotbarBanks.count(player) == 0, "new player defaults to no reservation");
                player.getEntityData()
                    .setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG, persisted);
                check(HotbarBanks.count(player) == count, "Forge persisted player data retains reservation");
                inventory.shape = new InventoryShape(27, offhand, 1);
                check(HotbarBanks.count(player) == Math.min(count, 3), "capacity reduction clamps reservation");
                try {
                    HotbarBanks.configure(player, 4);
                    throw new AssertionError("oversized reservation accepted");
                } catch (IllegalArgumentException expected) {}
                inventory.shape = new InventoryShape(216, offhand, 2);
                if (count == 2) prediction(player, inventory, item);
                HotbarBanks.configure(player, 0);
                dev.gilbe.dynamicinventory.client.PlayerPanelSet panels = dev.gilbe.dynamicinventory.client.PlayerPanelSet.DEFAULT
                    .add();
                String side = panels.entries.get(1).id;
                panels = panels.replace(
                    panels.get(side)
                        .withSlots(java.util.Arrays.asList(0, 2, 27, 100, 215)));
                java.util.List<Integer> main = panels.members("main", 216);
                int[] selected = new int[9];
                for (int i = 0; i < 9; i++) selected[i] = main.get(main.size() - 1 - i);
                ItemStack[] unchanged = inventory.mainInventory.clone();
                HotbarBanks.configure(player, selected);
                check(java.util.Arrays.equals(unchanged, inventory.mainInventory), "reservation never relocates items");
                check(
                    panels.members(side, 216, HotbarBanks.excluded(inventory))
                        .equals(panels.members(side, 216)),
                    "subpanel identities survive Main reservation, including tail slots");
                check(
                    panels.members("main", 216, HotbarBanks.excluded(inventory))
                        .size() == 202,
                    "only Main loses nine cells");
                dev.gilbe.dynamicinventory.client.PlayerPanelSet resized = panels
                    .allocate(new int[] { 202, 5 }, 216, HotbarBanks.excluded(inventory));
                check(
                    resized.get(side).slots.equals(panels.get(side).slots),
                    "saving allocations retains side slots beyond available-count boundary");
                HotbarBanks.swap(player, 0);
                for (int i = 0; i < 9; i++) check(
                    inventory.mainInventory[i] == unchanged[inventory.shape.rawIndex(9 + selected[i])],
                    "explicit noncontiguous bank swap");
                HotbarBanks.swap(player, 0);
                check(
                    java.util.Arrays.equals(unchanged, inventory.mainInventory),
                    "explicit bank roundtrip preserves all items");
                try {
                    HotbarBanks.configure(player, new int[] { 0, 0, 1, 2, 3, 4, 5, 6, 7 });
                    throw new AssertionError("duplicate bank identities accepted");
                } catch (IllegalArgumentException expected) {}
            }
        System.out.println(
            "PASS " + checks
                + " direct/cycle identity checks plus prediction/reconciliation, reservation, full-range offhand safety, cursor refusal and merge exclusion");
    }

    private static void prediction(Player player, Inventory inventory, Item item) throws Exception {
        long revision = inventory.shape.revision();
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        sun.misc.Unsafe allocator = (sun.misc.Unsafe) access.get(null);
        net.minecraft.network.NetHandlerPlayServer connection = (net.minecraft.network.NetHandlerPlayServer) allocator
            .allocateInstance(net.minecraft.network.NetHandlerPlayServer.class);
        connection.playerEntity = player;
        java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> create = cpw.mods.fml.common.network.simpleimpl.MessageContext.class
            .getDeclaredConstructor(net.minecraft.network.INetHandler.class, cpw.mods.fml.relauncher.Side.class);
        create.setAccessible(true);
        cpw.mods.fml.common.network.simpleimpl.MessageContext context = create
            .newInstance(connection, cpw.mods.fml.relauncher.Side.SERVER);
        for (int row = 0; row < 2; row++) {
            dev.gilbe.dynamicinventory.network.HotbarRequest request = new dev.gilbe.dynamicinventory.network.HotbarRequest();
            request.fromBytes(
                io.netty.buffer.Unpooled.buffer()
                    .writeInt(1)
                    .writeInt(row)
                    .writeLong(revision)
                    .writeLong(row + 1));
            new dev.gilbe.dynamicinventory.network.HotbarRequest.ServerHandler().onMessage(request, context);
        }
        Field pendingField = dev.gilbe.dynamicinventory.network.HotbarRequest.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Player, java.util.Deque<dev.gilbe.dynamicinventory.network.HotbarRequest>> queued = (java.util.Map<Player, java.util.Deque<dev.gilbe.dynamicinventory.network.HotbarRequest>>) pendingField
            .get(null);
        java.util.Deque<dev.gilbe.dynamicinventory.network.HotbarRequest> requests = queued.remove(player);
        check(requests.size() == 2, "Server retains both swaps received in one tick");
        for (int row = 0; row < 2; row++) {
            io.netty.buffer.ByteBuf payload = io.netty.buffer.Unpooled.buffer();
            requests.removeFirst()
                .toBytes(payload);
            payload.readInt();
            check(payload.readInt() == row, "Server request queue preserves input order");
        }
        int[] slots = { 27, 0, 28, 100, 215, 5, 7, 12, 14, 200, 1, 29, 101, 214, 6, 8, 13, 15 };
        HotbarBanks.configure(player, slots);
        HotbarPrediction prediction = new HotbarPrediction();
        check(prediction.predict(player, 0) == null, "Older peers require no speculative inventory change");
        prediction.enable(player, inventory.shape.revision(), 0);
        ItemStack offhand = inventory.shape.hasOffhand() ? inventory.mainInventory[36] : null;
        ItemStack[] before = inventory.mainInventory.clone();
        HotbarPrediction.Swap first = prediction.predict(player, -1);
        check(first != null && first.row == 0, "Cycle selects first bank locally");
        for (int i = 0; i < 9; i++) check(
            inventory.mainInventory[i] == before[inventory.shape.rawIndex(9 + slots[i])],
            "Hotbar changes immediately, before a reply");
        HotbarSwapReply firstReply = HotbarSwapReply.capture(player, first.sequence);
        io.netty.buffer.ByteBuf wire = io.netty.buffer.Unpooled.buffer();
        firstReply.toBytes(wire);
        HotbarSwapReply decoded = new HotbarSwapReply();
        decoded.fromBytes(wire);
        check(decoded.sequence == first.sequence, "Acknowledgement wire retains request identity");
        same(decoded.stacks, firstReply.stacks, "Acknowledgement wire retains counts and item NBT");
        HotbarPrediction.Swap second = prediction.predict(player, -1);
        check(second.row == 1, "A second cycle advances immediately while first is pending");
        ItemStack[] twoSwaps = copy(inventory.mainInventory);
        firstReply.stacks[0] = new ItemStack(item, 57);
        twoSwaps[inventory.shape.rawIndex(9 + slots[9])] = new ItemStack(item, 57);
        prediction.reconcile(player, first.sequence, firstReply.revision, firstReply.next, slots, firstReply.stacks);
        same(inventory.mainInventory, twoSwaps, "Delayed first acknowledgement replays later input");
        check(prediction.pending(player), "Later input remains pending");
        HotbarSwapReply secondReply = HotbarSwapReply.capture(player, second.sequence);
        prediction
            .reconcile(player, second.sequence, secondReply.revision, secondReply.next, slots, secondReply.stacks);
        check(!prediction.pending(player), "Final acknowledgement drains prediction");
        ItemStack[] authoritative = copy(inventory.mainInventory);
        prediction.reconcile(player, first.sequence, firstReply.revision, firstReply.next, slots, firstReply.stacks);
        same(inventory.mainInventory, authoritative, "Stale acknowledgement cannot roll inventory back");
        HotbarSwapReply refusal = HotbarSwapReply.capture(player, 1);
        HotbarPrediction.Swap rejected = prediction.predict(player, 0);
        prediction.reconcile(player, rejected.sequence, refusal.revision, refusal.next, slots, refusal.stacks);
        same(inventory.mainInventory, authoritative, "Rejected request restores authoritative contents");
        HotbarPrediction.Swap last = null;
        for (int i = 0; i < HotbarPrediction.MAX_PENDING; i++) last = prediction.predict(player, -1);
        check(
            last != null && prediction.predict(player, -1) == null,
            "Rapid input is bounded without overwriting requests");
        HotbarSwapReply batch = HotbarSwapReply.capture(player, last.sequence);
        prediction.reconcile(player, last.sequence, batch.revision, batch.next, slots, batch.stacks);
        check(!prediction.pending(player), "One ordered server batch acknowledges all rapid swaps");
        if (inventory.shape.hasOffhand())
            check(inventory.mainInventory[36] == offhand, "Prediction never exchanges offhand");
        inventory.setItemStack(new ItemStack(item));
        ItemStack[] locked = copy(inventory.mainInventory);
        try {
            prediction.predict(player, 0);
            throw new AssertionError("Prediction accepted an occupied cursor");
        } catch (IllegalStateException expected) {
            same(inventory.mainInventory, locked, "Prediction respects native cursor restriction");
        }
        inventory.setItemStack(null);
        prediction.predict(player, 0);
        prediction.refresh(null);
        check(
            !prediction.pending(player) && !prediction.supported(player),
            "Disconnect clears pending input and capability");
        prediction.enable(player, inventory.shape.revision(), 0);
        prediction.predict(player, 0);
        inventory.shape = inventory.shape.resized(207);
        check(!prediction.pending(player), "Capacity revision invalidates pending permutation");
        inventory.shape = new InventoryShape(216, inventory.shape.hasOffhand(), revision);
        HotbarBanks.configure(player, 0);
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) copy[i] = ItemStack.copyItemStack(stacks[i]);
        return copy;
    }

    private static void same(ItemStack[] actual, ItemStack[] expected, String message) {
        for (int i = 0; i < actual.length; i++)
            check(ItemStack.areItemStacksEqual(actual[i], expected[i]), message + " raw=" + i);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
