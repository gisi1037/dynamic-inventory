package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.recipe.RecipeInfo;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** NEI's invisible Recipes hotspot must move with the same native header as the crafting arrow. */
@Mixin(value = RecipeInfo.class, remap = false)
public abstract class NativeRecipeHotspotMixin {

    @Inject(method = "getGuiOffset", at = @At("RETURN"), cancellable = true)
    private static void dynamicinventory$headerOffset(GuiContainer gui, CallbackInfoReturnable<int[]> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) gui).dynamicinventory$panel();
        if (panel == null) return;
        int[] original = cir.getReturnValue();
        // RecipeInfo caches its arrays globally. Return a translated copy, never modify that shared entry.
        cir.setReturnValue(new int[] { original[0] + panel.headerOffsetX(), original[1] + panel.headerOffsetY() });
    }
}
