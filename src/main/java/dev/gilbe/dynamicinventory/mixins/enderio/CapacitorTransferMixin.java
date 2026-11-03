package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/**
 * Native numeric ranges predate its armor/Baubles slots; preserve native armor action with bound player destinations.
 */
@Pseudo
@Mixin(targets = "crazypants.enderio.machine.capbank.ContainerCapBank", remap = false)
public abstract class CapacitorTransferMixin extends Container {

    @Shadow
    private boolean mergeItemStackIntoArmor(EntityPlayer player, ItemStack stack, int id) {
        throw new AssertionError();
    }

    @Inject(method = { "transferStackInSlot", "func_82846_b" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transfer(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null || id < 0 || id >= inventorySlots.size()) return;
        cir.setReturnValue(null);
        Slot source = getSlot(id);
        if (!source.getHasStack() || !source.canTakeStack(player)) return;
        ItemStack stack = source.getStack(), before = stack.copy();
        int logical = region.logical(id);
        boolean moved;
        if (logical >= 0) {
            moved = PlayerInventoryTransfers.merge(this, stack, new int[] { 0, 1, 2, 3 });
            if (stack.stackSize > 0) moved = PlayerInventoryTransfers
                .mergeAutomatic(this, stack, logical < 9 ? region.getStorageSlotIds() : region.getHotbarSlotIds())
                || moved;
        } else {
            moved = id < 4 && mergeItemStackIntoArmor(player, stack, id);
            if (stack.stackSize > 0)
                moved = PlayerInventoryTransfers.mergeAutomatic(this, stack, region.getStorageSlotIds()) || moved;
            if (stack.stackSize > 0)
                moved = PlayerInventoryTransfers.merge(this, stack, region.getHotbarSlotIds()) || moved;
        }
        if (!moved) return;
        if (stack.stackSize == 0) source.putStack(null);
        else source.onSlotChanged();
        source.onPickupFromSlot(player, stack);
        cir.setReturnValue(before);
    }
}
