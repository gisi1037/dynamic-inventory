package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.compat.PanelItemSpawning;

@Mixin(targets = "codechicken.nei.NEIClientUtils", remap = false)
public abstract class NeiPanelSpawningMixin {

    @Inject(method = "giveStack(Lnet/minecraft/item/ItemStack;IZ)V", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$mainPanel(ItemStack stack, int count, boolean infinite, CallbackInfo ci) {
        if (PanelItemSpawning.give(stack, count, infinite)) ci.cancel();
    }
}
