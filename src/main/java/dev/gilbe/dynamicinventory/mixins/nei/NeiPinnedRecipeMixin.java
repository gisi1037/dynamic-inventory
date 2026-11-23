package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.KeyManager;
import dev.gilbe.dynamicinventory.client.AePinnedPanel;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

@Mixin(targets = "codechicken.nei.api.ShortcutInputHandler", remap = false)
public abstract class NeiPinnedRecipeMixin {

    @ModifyVariable(method = "handleKeyEvent", at = @At("HEAD"), argsOnly = true, require = 0)
    private static ItemStack dynamicinventory$pinnedRecipe(ItemStack original) {
        if (!PackCompatibility.aePinnedPanel() || !KeyManager.isKeyDown("recipe.recipe")) return original;
        var mouse = GuiDraw.getMousePosition();
        ItemStack pinned = AePinnedPanel.recipeStack(Minecraft.getMinecraft().currentScreen, mouse.x, mouse.y);
        return pinned == null ? original : pinned;
    }
}
