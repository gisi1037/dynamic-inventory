package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** The Research Table overrides vanilla merge; only its player destination needs expanded routing. */
@Pseudo
@Mixin(targets = "thaumcraft.common.container.ContainerResearchTable", remap = false)
public abstract class ResearchTableTransferMixin extends Container {

    @Inject(method = { "func_75135_a", "mergeItemStack" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$playerMerge(ItemStack stack, int start, int end, boolean reverse,
        CallbackInfoReturnable<Boolean> result) {
        var region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        int[] destinations = region.destinations(start, end, reverse);
        if (destinations != null)
            result.setReturnValue(PlayerInventoryTransfers.mergeToPlayer(this, stack, destinations));
    }
}
