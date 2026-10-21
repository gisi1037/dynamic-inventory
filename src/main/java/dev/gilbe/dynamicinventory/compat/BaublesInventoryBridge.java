package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import baubles.api.expanded.BaubleExpandedSlots;
import baubles.common.BaublesConfig;
import baubles.common.container.InventoryBaubles;
import baubles.common.container.SlotBauble;
import baubles.common.lib.PlayerHandler;
import dev.gilbe.dynamicinventory.mixins.minecraft.SlotInventoryAccess;

/** Native Baubles storage and validation; no copied equipment inventory or new save format. */
public final class BaublesInventoryBridge {

    private BaublesInventoryBridge() {}

    public static List<Slot> slots(EntityPlayer player) {
        List<Slot> result = new ArrayList<>();
        String[] assignments = BaubleExpandedSlots.getCurrentSlotAssignments();
        for (int index = 0; index < assignments.length; index++)
            result.add(new BoundSlot(player, assignments[index], index));
        return result;
    }

    public static int slotCount() {
        return BaubleExpandedSlots.slotsCurrentlyUsed();
    }

    /** Resolve constructor placeholders before comparing backing-inventory aliases across peers. */
    public static void bind(Slot slot) {
        if (slot instanceof BoundSlot bound) bound.bind();
    }

    public static boolean visible(Slot slot) {
        return slot instanceof SlotBauble bauble
            && (BaublesConfig.showUnusedSlots || !BaubleExpandedSlots.unknownType.equals(bauble.getSlotType()));
    }

    public static int visibleSlotCount() {
        int count = 0;
        for (String type : BaubleExpandedSlots.getCurrentSlotAssignments())
            if (BaublesConfig.showUnusedSlots || !BaubleExpandedSlots.unknownType.equals(type)) count++;
        return count;
    }

    public static boolean blockClientEvents(EntityPlayer player, boolean blocked) {
        if (player == null || !player.worldObj.isRemote) return false;
        InventoryBaubles inventory = PlayerHandler.getPlayerBaubles(player);
        boolean previous = inventory.blockEvents;
        inventory.blockEvents = blocked;
        return previous;
    }

    /** Baubles replaces its canonical inventory on login, respawn and full equipment sync. */
    private static final class BoundSlot extends SlotBauble {

        private final EntityPlayer owner;

        private BoundSlot(EntityPlayer player, String type, int index) {
            // EntityPlayer creates its container before assigning its GameProfile.
            // The placeholder is never used for equipment after player construction.
            super(new InventoryBaubles(player), type, index, -10000, -10000);
            owner = player;
        }

        private void bind() {
            if (owner.getGameProfile() == null) return;
            ((SlotInventoryAccess) (Object) this).dynamicinventory$setInventory(PlayerHandler.getPlayerBaubles(owner));
        }

        @Override
        public ItemStack getStack() {
            bind();
            return super.getStack();
        }

        @Override
        public void putStack(ItemStack stack) {
            bind();
            super.putStack(stack);
        }

        @Override
        public ItemStack decrStackSize(int count) {
            bind();
            return super.decrStackSize(count);
        }

        @Override
        public void onSlotChanged() {
            bind();
            super.onSlotChanged();
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            bind();
            return super.isItemValid(stack);
        }
    }
}
