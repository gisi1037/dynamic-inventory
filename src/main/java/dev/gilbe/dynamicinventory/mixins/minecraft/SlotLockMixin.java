package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

@Mixin(Slot.class)
public abstract class SlotLockMixin {

    @Inject(method = "canTakeStack", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$denyTake(EntityPlayer player, CallbackInfoReturnable<Boolean> result) {
        if (InventorySlotLocks.locked((Slot) (Object) this)) result.setReturnValue(false);
    }

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$denyInsert(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (InventorySlotLocks.locked((Slot) (Object) this)) result.setReturnValue(false);
    }
}
