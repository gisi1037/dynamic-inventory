package dev.gilbe.dynamicinventory.mixins;

import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import dev.gilbe.dynamicinventory.compat.ContainerRecipeAlignment;

@Mixin(value = DefaultOverlayHandler.class, remap = false)
public abstract class NativeRecipeTransferMixin {

    @Shadow
    protected int offsetx;
    @Shadow
    protected int offsety;

    @Inject(method = "mapIngredSlots", at = @At("HEAD"))
    private void dynamicinventory$ingredients(GuiContainer gui, List<PositionedStack> ingredients,
        CallbackInfoReturnable<Slot[][]> cir) {
        int[] origin = ContainerRecipeAlignment.craftingOrigin(gui);
        if (origin != null) {
            offsetx = origin[0];
            offsety = origin[1];
        }
    }

    @Inject(method = "getCraftMatrixSlots", at = @At("HEAD"))
    private void dynamicinventory$matrix(GuiContainer gui, IRecipeHandler recipe,
        CallbackInfoReturnable<Set<Slot>> cir) {
        int[] origin = ContainerRecipeAlignment.craftingOrigin(gui);
        if (origin != null) {
            offsetx = origin[0];
            offsety = origin[1];
        }
    }
}
