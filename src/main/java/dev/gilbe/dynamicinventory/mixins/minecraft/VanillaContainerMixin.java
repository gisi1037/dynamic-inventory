package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerBeacon;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerHorseInventory;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;

@Mixin({ ContainerChest.class, ContainerWorkbench.class, ContainerFurnace.class, ContainerDispenser.class,
    ContainerHopper.class, ContainerBrewingStand.class, ContainerEnchantment.class, ContainerBeacon.class,
    ContainerHorseInventory.class, ContainerRepair.class, ContainerMerchant.class })
public abstract class VanillaContainerMixin extends Container {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dynamicinventory$append(CallbackInfo ci) {
        Class<?> type = getClass();
        // A mod subclass may still be constructing its own slots. Attach only the exact vanilla owners here.
        if (type == ContainerChest.class || type == ContainerWorkbench.class
            || type == ContainerFurnace.class
            || type == ContainerDispenser.class
            || type == ContainerHopper.class
            || type == ContainerBrewingStand.class
            || type == ContainerEnchantment.class
            || type == ContainerBeacon.class
            || type == ContainerHorseInventory.class
            || type == ContainerRepair.class
            || type == ContainerMerchant.class) InventoryUi.attachRegistered(this);
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transferExtra(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        if (InventoryUi.handlesAdditionalTransfer(this, id))
            cir.setReturnValue(InventoryUi.transferAdditional(this, player, id));
    }
}
