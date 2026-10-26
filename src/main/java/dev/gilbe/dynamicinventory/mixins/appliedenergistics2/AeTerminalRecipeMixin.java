package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.PositionedStack;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ContainerRecipeAlignment;

/** AE2's own recipe positioner keeps its native grid offset; add only the editor's header translation. */
@Mixin(targets = "appeng.integration.modules.NEIHelpers.TerminalCraftingSlotFinder", remap = false)
public abstract class AeTerminalRecipeMixin {

    @Inject(method = "positionStacks", at = @At("RETURN"))
    private void dynamicinventory$align(ArrayList<PositionedStack> stacks,
        CallbackInfoReturnable<ArrayList<PositionedStack>> cir) {
        NativeInventoryPanel panel = ContainerRecipeAlignment.currentPanel();
        if (panel != null) for (PositionedStack stack : cir.getReturnValue()) if (stack != null) {
            stack.relx += panel.headerOffsetX();
            stack.rely += panel.headerOffsetY();
        }
    }
}
