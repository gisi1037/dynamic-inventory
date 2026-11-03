package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;
import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.network.MainInventoryRoutingRequest;

/** Main-only automatic destinations, native transfer callbacks, persisted layouts and manual placement. */
public final class AutomaticInventoryRoutingFixtures {

    private AutomaticInventoryRoutingFixtures() {}

    public static final class Player extends EntityPlayerMP {

        private Player() {
            super(null, null, null, null);
        }
    }

    private static final class Inventory extends InventoryPlayer implements ExpandedInventoryAccess {

        private InventoryShape shape;

        Inventory(Player player, int capacity, boolean offhand) {
            super(player);
            shape = new InventoryShape(capacity, offhand, 3);
            mainInventory = new ItemStack[shape.rawMainSlots()];
        }

        @Override
        public InventoryShape dynamicinventory$getShape() {
            return shape;
        }

        @Override
        public void dynamicinventory$setShape(InventoryShape shape) {
            this.shape = shape;
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

        private ContainerInventoryRegion region;

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
            return region;
        }

        @Override
        public void dynamicinventory$region(ContainerInventoryRegion value) {
            region = value;
        }

        @Override
        public Slot dynamicinventory$append(Slot slot) {
            return addSlotToContainer(slot);
        }
    }

    public static void main(String[] args) throws Exception {
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new java.io.File(".setup"));
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Item item = new Item();
        int checks = 0;
        for (int capacity : new int[] { 27, 72, 216 }) for (boolean offhand : new boolean[] { false, true }) {
            Player player = (Player) unsafe.allocateInstance(Player.class);
            net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(player);
            watcher.addObject(6, Float.valueOf(20));
            Field watched = net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");
            watched.setAccessible(true);
            watched.set(player, watcher);
            Inventory inventory = new Inventory(player, capacity, offhand);
            player.inventory = inventory;
            Host own = new Host(), machine = new Host();
            player.inventoryContainer = own;
            player.openContainer = machine;
            machine.windowId = 7;
            InventoryBasic source = new InventoryBasic("machine", false, 1);
            machine.dynamicinventory$append(new Slot(source, 0, 0, 0));
            // Native player ordering: storage then hotbar. Binding must expand offscreen storage as usual.
            for (int logical = 9; logical < 36; logical++)
                machine.dynamicinventory$append(new Slot(inventory, inventory.shape.rawIndex(logical), 0, 0));
            for (int logical = 0; logical < 9; logical++)
                machine.dynamicinventory$append(new Slot(inventory, logical, 0, 0));
            ContainerInventoryRegion.attach(machine, inventory);
            int last = capacity - 1;
            check(
                AutomaticInventoryRouting.receive(player, 3, 7, new int[] { 0, last }, new int[] { 1, last }),
                "Accept canonical membership");
            inventory.mainInventory[9] = new ItemStack(item, 60); // Main in player layout; subpanel in machine layout.
            inventory.mainInventory[10] = new ItemStack(item, 61);
            for (int i = 0; i < 9; i++) inventory.mainInventory[i] = new ItemStack(item, 64);
            check(
                AutomaticInventoryRouting.mergeSlot(inventory, new ItemStack(item, 8)) == 10,
                "Pickup respects visible machine Main");
            int[] targets = machine.region.destinations(1, 37, false);
            check(targets.length == 11, "Only two Main storage cells and the hotbar are destinations");
            source.setInventorySlotContents(0, new ItemStack(item, 8));
            check(
                PlayerInventoryTransfers.transferTo(machine, player, 0, targets) != null,
                "Native source transfers into Main");
            check(inventory.mainInventory[9].stackSize == 60, "Matching subpanel stack is untouched");
            check(
                inventory.mainInventory[10].stackSize == 64
                    && inventory.mainInventory[inventory.shape.rawIndex(9 + last)].stackSize == 5,
                "Merge Main before using empty offscreen Main cell");
            inventory.mainInventory[inventory.shape.rawIndex(9 + last)].stackSize = 64;
            source.setInventorySlotContents(0, new ItemStack(item, 8));
            check(
                PlayerInventoryTransfers.transferTo(machine, player, 0, targets) == null
                    && source.getStackInSlot(0).stackSize == 8,
                "Full Main leaves source untouched even with free subpanel capacity");
            check(AutomaticInventoryRouting.firstEmpty(inventory) == -1, "Full Main refuses ground pickup");
            check(
                AutomaticInventoryRouting.mergeSlot(inventory, new ItemStack(item, 8)) == -1,
                "Full Main never merges into subpanel");
            if (offhand) check(!AutomaticInventoryRouting.pickup(inventory, 36), "Offhand remains outside Main");
            // Explicit placement through the ordinary merge primitive remains possible in a subpanel.
            ItemStack manual = new ItemStack(item, 4);
            check(
                PlayerInventoryTransfers.merge(machine, manual, new int[] { machine.region.id(11) })
                    && manual.stackSize == 0,
                "Explicit panel placement is not filtered");
            check(
                AutomaticInventoryRouting.nativeRange(machine, 0, 4, false) == null,
                "Mixed machine ranges stay native");
            int[] partial = AutomaticInventoryRouting.nativeRange(machine, 1, 4, true);
            check(
                Arrays.equals(partial, new int[] { machine.region.id(10) }),
                "Partial native ranges retain direction and Main membership");
            AutomaticInventoryRouting.persist(player);
            player.openContainer = own;
            check(
                AutomaticInventoryRouting.mergeSlot(inventory, new ItemStack(item, 8)) == 9,
                "Closing machine restores player-layout Main");
            Player restored = (Player) unsafe.allocateInstance(Player.class);
            Inventory restoredInventory = new Inventory(restored, capacity, offhand);
            restored.inventory = restoredInventory;
            restored.inventoryContainer = restored.openContainer = new Host();
            restored.getEntityData()
                .setTag(
                    net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG,
                    player.getEntityData()
                        .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG)
                        .copy());
            check(
                AutomaticInventoryRouting.pickup(restoredInventory, 9)
                    && !AutomaticInventoryRouting.pickup(restoredInventory, 10),
                "Persisted Main applies before first client synchronization after login");
            check(
                !AutomaticInventoryRouting.receive(player, 2, 0, new int[] { 0 }, new int[] { 0 }),
                "Stale capacity rejected");
            check(
                !AutomaticInventoryRouting.receive(player, 3, 0, new int[] { capacity }, new int[] { 0 }),
                "Out-of-capacity membership rejected");
            check(
                !AutomaticInventoryRouting.receive(player, 3, 0, new int[] { 0, 0 }, new int[] { 0 }),
                "Duplicate membership rejected");
            frameworks(inventory);
            attached(player, inventory, machine, source, item);
            HotbarBanks.configure(player, 1);
            check(
                !AutomaticInventoryRouting.pickup(inventory, 9),
                "Reserved hotbar row is skipped even when allocated to Main");
            HotbarBanks.configure(player, 0);
            check(
                AutomaticInventoryRouting.pickup(inventory, 9),
                "Releasing a hotbar returns its Main cells automatically");
            patternExtraction(player, inventory, machine, item);
            BogoHotbarFixtures.check(player, inventory);
            checks += 20;
        }
        wire();
        System.out.println(
            "PASS " + checks
                + " Main-only pickup/transfer, full-Main refusal, manual placement, persistence and offhand cases");
    }

    private static void patternExtraction(Player player, Inventory inventory, Host machine, Item item) {
        int capacity = inventory.shape.storageSlots();
        player.openContainer = machine;
        AutomaticInventoryRouting.receive(player, 3, 7, new int[] { capacity - 1 }, new int[] { capacity - 1 });
        for (int i = 0; i < inventory.mainInventory.length; i++) inventory.mainInventory[i] = null;
        for (int i = 0; i < 9; i++) inventory.mainInventory[i] = new ItemStack(item, 64);
        ItemStack pattern = new ItemStack(item, 4);
        NBTTagCompound identity = new NBTTagCompound();
        identity.setString("fixture", "encoded pattern identity");
        pattern.setTagCompound(identity);
        int last = inventory.shape.rawIndex(capacity + 8);
        check(
            dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers.insert(machine, pattern) == null,
            "Pattern extraction sees the last expanded Main cell with full hotbar");
        check(
            inventory.mainInventory[last].stackSize == 4 && pattern.stackSize == 4
                && ItemStack.areItemStackTagsEqual(pattern, inventory.mainInventory[last]),
            "Pattern extraction preserves native source ownership and encoded data");
        if (inventory.shape.hasOffhand()) check(inventory.mainInventory[36] == null, "Pattern never targets Backhand");
        inventory.mainInventory[last].stackSize = 63;
        ItemStack remainder = dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers
            .insert(machine, pattern);
        check(
            remainder != null && remainder.stackSize == 4 && inventory.mainInventory[last].stackSize == 63,
            "Insufficient pattern space inserts nothing, avoiding a partial-insertion duplicate");
        int[] full = java.util.stream.IntStream.range(0, capacity)
            .toArray();
        AutomaticInventoryRouting.receive(player, 3, 7, full, full);
        check(
            dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers.insert(machine, pattern) == null,
            "Attached pattern extraction can use all ordinary storage");
        player.openContainer = player.inventoryContainer;
    }

    private static void attached(Player player, Inventory inventory, Host machine, InventoryBasic source, Item item) {
        int capacity = inventory.shape.storageSlots();
        dev.gilbe.dynamicinventory.client.PlayerPanelSet panels = dev.gilbe.dynamicinventory.client.PlayerPanelSet.DEFAULT
            .add();
        String side = panels.entries.get(1).id;
        panels = panels.replace(
            panels.get(side)
                .withSlots(Arrays.asList(1, 2)));
        String saved = panels.toJson()
            .toString();
        int[] detached = panels.automaticMainSlots(capacity, true),
            attached = panels.automaticMainSlots(capacity, false);
        check(
            detached.length == capacity - 2 && Arrays.stream(detached)
                .noneMatch(i -> i == 1 || i == 2),
            "Detached Main excludes subpanel assignments");
        check(
            attached.length == capacity && attached[1] == 1 && attached[2] == 2,
            "Attached mode restores the complete ordinary storage route");
        check(
            saved.equals(
                panels.toJson()
                    .toString()),
            "Attaching never erases saved panel assignments");
        player.openContainer = machine;
        AutomaticInventoryRouting.receive(player, 3, 7, attached, attached);
        inventory.mainInventory[11] = null;
        check(
            AutomaticInventoryRouting.firstEmpty(inventory) == 11,
            "Attached pickup can use a previously assigned subpanel cell");
        source.setInventorySlotContents(0, new ItemStack(item, 8));
        int[] targets = machine.region.destinations(1, 37, false);
        check(targets.length == capacity + 9, "Attached native transfer can target all bound ordinary slots");
        check(
            PlayerInventoryTransfers.transferTo(machine, player, 0, targets) != null
                && source.getStackInSlot(0) == null,
            "Attached shift transfer has no Main-only refusal");
        check(
            inventory.mainInventory[11] != null && inventory.mainInventory[11].stackSize == 4,
            "Attached transfer fills previously reserved subpanel capacity");
        AutomaticInventoryRouting.receive(player, 3, 7, new int[] { 0, capacity - 1 }, new int[] { 1, capacity - 1 });
        check(
            !AutomaticInventoryRouting.transfer(inventory, 11) && inventory.mainInventory[11].stackSize == 4,
            "Detaching restores restrictions without relocating any items");
        player.openContainer = player.inventoryContainer;
    }

    private static void frameworks(Inventory inventory) throws Exception {
        dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot mui1 = new dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot(
            new dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory(inventory),
            11);
        check(
            !mui1.isTransferEnabled() && mui1.isEnabled(),
            "MUI1 subpanel rejects automatic insertion without disabling manual interaction");
        dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory player = new dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory(
            inventory);
        com.cleanroommc.modularui.widgets.slot.ModularSlot sub = new com.cleanroommc.modularui.widgets.slot.ModularSlot(
            player,
            inventory.shape.rawIndex(11));
        com.cleanroommc.modularui.widgets.slot.ModularSlot main = new com.cleanroommc.modularui.widgets.slot.ModularSlot(
            player,
            inventory.shape.rawIndex(9));
        com.cleanroommc.modularui.widgets.slot.ModularSlot machine = new com.cleanroommc.modularui.widgets.slot.ModularSlot(
            new com.cleanroommc.modularui.utils.item.ItemStackHandler(1),
            0);
        java.util.List<com.cleanroommc.modularui.widgets.slot.ModularSlot> original = Arrays.asList(sub, main, machine);
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.List<com.cleanroommc.modularui.widgets.slot.ModularSlot>> result = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<>(
            "fixture",
            true,
            original);
        dev.gilbe.dynamicinventory.mixins.modularui2.Mui2PlayerLookupMixin nativeHook = new dev.gilbe.dynamicinventory.mixins.modularui2.Mui2PlayerLookupMixin() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        };
        java.lang.reflect.Method filter = nativeHook.getClass()
            .getSuperclass()
            .getDeclaredMethod(
                "dynamicinventory$mainTargets",
                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);
        filter.setAccessible(true);
        filter.invoke(nativeHook, result);
        check(
            result.getReturnValue()
                .equals(Arrays.asList(main, machine)),
            "MUI2 native shift destinations retain Main and machine slots only");
        check(original.size() == 3 && sub.func_111238_b(), "MUI2 slot registry and manual input remain unchanged");
    }

    private static void wire() throws Exception {
        Field pickup = MainInventoryRoutingRequest.class.getDeclaredField("pickup");
        pickup.setAccessible(true);
        for (int count : new int[] { 0, 1, InventoryShape.MAX_STORAGE }) {
            io.netty.buffer.ByteBuf payload = io.netty.buffer.Unpooled.buffer()
                .writeInt(7)
                .writeLong(3)
                .writeInt(count);
            for (int i = 0; i < count; i++) payload.writeInt(i);
            payload.writeInt(count);
            for (int i = count - 1; i >= 0; i--) payload.writeInt(i);
            MainInventoryRoutingRequest request = new MainInventoryRoutingRequest();
            request.fromBytes(payload.copy());
            io.netty.buffer.ByteBuf roundTrip = io.netty.buffer.Unpooled.buffer();
            request.toBytes(roundTrip);
            check(payload.equals(roundTrip), "Main membership wire round trip, including empty/full Main");
            for (int length = 0; length < payload.readableBytes(); length++) {
                MainInventoryRoutingRequest truncated = new MainInventoryRoutingRequest();
                truncated.fromBytes(payload.copy(0, length));
                check(pickup.get(truncated) == null, "Truncated Main membership is rejected");
                check(
                    new MainInventoryRoutingRequest.Handler().onMessage(truncated, null) == null,
                    "Truncated membership never reaches player state");
            }
        }
        for (int bad : new int[] { -1, InventoryShape.MAX_STORAGE, 0 }) {
            MainInventoryRoutingRequest invalid = new MainInventoryRoutingRequest();
            invalid.fromBytes(
                io.netty.buffer.Unpooled.buffer()
                    .writeInt(7)
                    .writeLong(3)
                    .writeInt(2)
                    .writeInt(0)
                    .writeInt(bad)
                    .writeInt(0));
            check(pickup.get(invalid) == null, "Out-of-range and duplicate Main identities refused");
        }
        System.out.println(
            "PASS Main routing packet round trips, every truncated length, invalid identities and no item payload");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
