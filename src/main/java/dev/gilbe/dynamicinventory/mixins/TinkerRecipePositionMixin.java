package dev.gilbe.dynamicinventory.mixins;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.PositionedStack;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ContainerRecipeAlignment;

@Mixin(targets = "tconstruct.plugins.nei.CraftingStationStackPositioner", remap = false)
public abstract class TinkerRecipePositionMixin {

    @Inject(method = "positionStacks", at = @At("RETURN"))
    private void dynamicinventory$position(ArrayList<PositionedStack> stacks,
        CallbackInfoReturnable<ArrayList<PositionedStack>> cir) {
        NativeInventoryPanel panel = ContainerRecipeAlignment.currentPanel();
        if (panel != null) for (PositionedStack stack : cir.getReturnValue()) {
            stack.relx += panel.craftingOffsetX();
            stack.rely += panel.craftingOffsetY();
        }
    }
}
