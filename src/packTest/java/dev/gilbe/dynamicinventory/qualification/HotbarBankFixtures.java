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
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

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
                + " direct/cycle identity checks plus reservation, full-range offhand safety, cursor refusal and merge exclusion");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
