package dev.gilbe.dynamicinventory.mixins.extrautils;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** These hosts override vanilla's merge method; expand only an entirely canonical player range. */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils.dynamicgui.DynamicContainer", remap = false)
public abstract class NativeMergeMixin extends Container {

    @Inject(method = { "mergeItemStack", "func_75135_a" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$merge(ItemStack stack, int start, int end, boolean reverse,
        CallbackInfoReturnable<Boolean> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        int[] ids = region.destinations(start, end, reverse);
        if (ids == null)
            ids = dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting.nativeRange(this, start, end, reverse);
        if (ids != null) cir.setReturnValue(PlayerInventoryTransfers.merge(this, stack, ids));
    }
}
