package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;

/** AE2's native region transfer also runs with the inventory attached. Stored hotbars never join it. */
@Mixin(value = AEBaseContainer.class, remap = false)
public abstract class AeSpaceTransferMixin {

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$storedSource(EntityPlayerMP player, InventoryAction action, int slot, long id,
        CallbackInfo ci) {
        AEBaseContainer container = (AEBaseContainer) (Object) this;
        if (action == InventoryAction.MOVE_REGION && slot >= 0
            && slot < container.inventorySlots.size()
            && reserved(player, container.getSlot(slot))) ci.cancel();
    }

    @WrapOperation(
        method = "doAction",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/AEBaseContainer;transferStackInSlot(Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;",
            remap = true))
    private ItemStack dynamicinventory$storedHotbars(AEBaseContainer container, EntityPlayer player, int id,
        Operation<ItemStack> original) {
        return reserved(player, container.getSlot(id)) ? null : original.call(container, player, id);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean reserved(EntityPlayer player, Slot slot) {
        return slot.inventory == player.inventory && HotbarBanks.isReserved(player.inventory, slot.getSlotIndex());
    }
}
