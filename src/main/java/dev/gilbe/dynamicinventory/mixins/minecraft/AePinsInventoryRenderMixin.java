package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.gilbe.dynamicinventory.client.AePinnedPanel;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

@Mixin(InventoryPlayer.class)
public abstract class AePinsInventoryRenderMixin {

    @WrapMethod(method = "getStackInSlot")
    private ItemStack dynamicinventory$predictedSlot(int raw, Operation<ItemStack> original) {
        // Vanilla reuses its index local for armor after subtracting the main inventory size.
        ItemStack stack = original.call(raw);
        return PackCompatibility.aePinnedPanel()
            ? AePinnedPanel.renderedStack((InventoryPlayer) (Object) this, raw, stack)
            : stack;
    }

    @Inject(method = "getItemStack", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$predictedCursor(CallbackInfoReturnable<ItemStack> cir) {
        dynamicinventory$renderStack(-1, cir);
    }

    @org.spongepowered.asm.mixin.Unique
    private void dynamicinventory$renderStack(int raw, CallbackInfoReturnable<ItemStack> cir) {
        if (!PackCompatibility.aePinnedPanel()) return;
        ItemStack original = cir.getReturnValue();
        ItemStack displayed = AePinnedPanel.renderedStack((InventoryPlayer) (Object) this, raw, original);
        if (displayed != original) cir.setReturnValue(displayed);
    }
}
