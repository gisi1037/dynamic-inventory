package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

@Pseudo
@Mixin(targets = "thaumcraft.common.container.ContainerArcaneWorkbench", remap = false)
public abstract class WorkbenchTransferMixin extends Container {

    // Keep the real source and use the native storage branch, which checks for casting wands.
    @ModifyVariable(
        method = { "transferStackInSlot", "func_82846_b" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;func_77946_l()Lnet/minecraft/item/ItemStack;",
            ordinal = 0),
        argsOnly = true,
        index = 2)
    private int dynamicinventory$expandedSource(int id) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        return region != null && id >= region.getNativeSlotCount() && region.logical(id) >= 0 ? region.id(9) : id;
    }
}
