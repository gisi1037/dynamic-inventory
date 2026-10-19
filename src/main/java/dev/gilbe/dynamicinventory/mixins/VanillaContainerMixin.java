package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;

@Mixin({ ContainerChest.class, ContainerWorkbench.class, ContainerFurnace.class })
public abstract class VanillaContainerMixin extends Container {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dynamicinventory$append(CallbackInfo ci) {
        Class<?> type = getClass();
        if (type == ContainerChest.class || type == ContainerWorkbench.class || type == ContainerFurnace.class)
            InventoryUi.attachRegistered(this);
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transferExtra(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        if (InventoryUi.handlesAdditionalTransfer(this, id))
            cir.setReturnValue(InventoryUi.transferAdditional(this, player, id));
    }
}
