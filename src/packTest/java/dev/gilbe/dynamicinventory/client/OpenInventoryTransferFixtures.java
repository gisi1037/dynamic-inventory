package dev.gilbe.dynamicinventory.client;

import java.lang.reflect.Field;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

public final class OpenInventoryTransferFixtures {

    private OpenInventoryTransferFixtures() {}

    public static void main(String[] args) throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        var allocator = (sun.misc.Unsafe) unsafe.get(null);
        Loader loader = (Loader) allocator.allocateInstance(Loader.class);
        Field mods = Loader.class.getDeclaredField("namedMods");
        mods.setAccessible(true);
        mods.set(loader, Collections.emptyMap());
        Field instance = Loader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, loader);
        EntityPlayer player = (EntityPlayer) allocator.allocateInstance(EntityPlayerMP.class);
        player.inventory = new PlayerSlots(player);
        Host chest = new Host();
        Slot source = chest.add(new Slot(player.inventory, 9, 8, 90));
        Slot satellite = chest.add(new Slot(player.inventory, 10, 8, 90));
        Item item = new Item();
        Item other = new Item();
        ItemStack stack = new ItemStack(item, 16);
        source.putStack(stack);
        satellite.putStack(new ItemStack(other, 16));
        check(!canDeposit(player, chest, source), "Player-only slots do not take priority over Pins");

        InventoryBasic machine = new InventoryBasic("chest", false, 1);
        Slot target = chest.add(new Slot(machine, 0, 8, 30) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() == item;
            }
        });
        check(canDeposit(player, chest, source), "Empty compatible chest slot gets Shift-click first");
        target.putStack(new ItemStack(item, 63));
        check(canDeposit(player, chest, source), "A partial merge still gives the chest the gesture");
        check(OpenInventoryTransfers.canScroll(player, chest, source, stack, true, true), "Scroll pushes to the chest");
        check(
            OpenInventoryTransfers.canScroll(player, chest, source, stack, false, false),
            "Scroll pulls from the chest");
        check(
            OpenInventoryTransfers.canScroll(player, chest, source, stack, false, true),
            "Directional scroll uses the chest above the source");
        target.getStack().stackSize = 64;
        check(!canDeposit(player, chest, source), "Full chest permits Pins fallback");
        check(
            !OpenInventoryTransfers.canScroll(player, chest, source, stack, true, false),
            "Full chest cannot receive scroll push");
        target.putStack(new ItemStack(other, 1));
        check(!canDeposit(player, chest, source), "A different stack does not count as available space");
        check(
            !OpenInventoryTransfers.canScroll(player, chest, source, stack, false, false),
            "Scroll pull requires matching items");
        target.putStack(null);

        int[] panel = { satellite.slotNumber, source.slotNumber };
        check(
            OpenInventoryTransfers.canDeposit(
                player,
                chest,
                satellite,
                satellite.getStack(),
                false,
                panel,
                id -> chest.getSlot(id)
                    .getStack()),
            "Space checks the other stacks in the originating panel");
        InventorySlotLocks.toggle(player, 9);
        check(
            !OpenInventoryTransfers.canDeposit(
                player,
                chest,
                satellite,
                satellite.getStack(),
                false,
                panel,
                id -> chest.getSlot(id)
                    .getStack()),
            "Space does not use a locked stack to choose the destination");
        check(target.getStack() == null && source.getStack().stackSize == 16, "The priority check never moves items");
        System.out.println("PASS native inventory gets priority for shift, bulk and directional scroll transfers");
    }

    private static boolean canDeposit(EntityPlayer player, Container container, Slot slot) {
        return OpenInventoryTransfers.canDeposit(player, container, slot, slot.getStack(), true, null, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Host extends Container {

        Slot add(Slot slot) {
            return addSlotToContainer(slot);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class PlayerSlots extends InventoryPlayer implements ExpandedInventoryAccess {

        PlayerSlots(EntityPlayer player) {
            super(player);
        }

        @Override
        public InventoryShape dynamicinventory$getShape() {
            return new InventoryShape(27, false, 0);
        }

        @Override
        public void dynamicinventory$setShape(InventoryShape shape) {}

        @Override
        public boolean dynamicinventory$hasCapacityOverride() {
            return false;
        }

        @Override
        public void dynamicinventory$setCapacityOverride(boolean overridden) {}

        @Override
        public NBTTagCompound dynamicinventory$getRecovery() {
            return null;
        }

        @Override
        public void dynamicinventory$setRecovery(NBTTagCompound recovery) {}
    }
}
