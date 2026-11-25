package dev.gilbe.dynamicinventory.mixins.modularui2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.utils.item.PlayerArmorInvWrapper;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

@Mixin(value = ModularSlot.class, remap = false)
public abstract class Mui2SlotLockMixin implements InventorySlotLocks.Backing {

    @Override
    public int dynamicinventory$lockIndex(InventoryPlayer inventory) {
        ModularSlot slot = (ModularSlot) (Object) this;
        if (slot.getItemHandler() instanceof PlayerArmorInvWrapper wrapper && wrapper.getInventoryPlayer() == inventory)
            return InventorySlotLocks.index(inventory, inventory.mainInventory.length + slot.getSlotIndex());
        return slot.getItemHandler() instanceof PlayerMainInvWrapper wrapper
            && wrapper.getInventoryPlayer() == inventory ? InventorySlotLocks.index(inventory, slot.getSlotIndex())
                : -1;
    }

    @Inject(method = "canTakeStack", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$denyTake(EntityPlayer player, CallbackInfoReturnable<Boolean> result) {
        if (InventorySlotLocks.locked((ModularSlot) (Object) this, player)) result.setReturnValue(false);
    }

    @Inject(method = "isItemValid", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$filterItem(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        ModularSlot slot = (ModularSlot) (Object) this;
        InventoryPlayer inventory;
        if (slot.getItemHandler() instanceof PlayerMainInvWrapper wrapper) inventory = wrapper.getInventoryPlayer();
        else if (slot.getItemHandler() instanceof PlayerArmorInvWrapper wrapper)
            inventory = wrapper.getInventoryPlayer();
        else return;
        int index = dynamicinventory$lockIndex(inventory);
        if (InventorySlotLocks.contains(inventory.player, index)
            || !InventorySlotLocks.permits(inventory.player, index, stack)) result.setReturnValue(false);
    }

}
