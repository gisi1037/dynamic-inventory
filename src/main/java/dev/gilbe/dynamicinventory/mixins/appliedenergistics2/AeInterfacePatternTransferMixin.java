package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.util.InventoryAdaptor;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

/** Single-pattern and region extraction share this native insertion seam and keep native source synchronization. */
@Mixin(targets = "appeng.container.implementations.ContainerInterfaceTerminal", remap = false)
public abstract class AeInterfacePatternTransferMixin extends Container {

    @Inject(method = "mergeToPlayerInventory", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$ordinary(InventoryAdaptor ignored, ItemStack stack,
        CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerInventoryRegion.of(this) != null)
            cir.setReturnValue(AppliedEnergisticsPatternTransfers.insert(this, stack));
    }
}
