package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.gui.Gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** A scoped background-sheet crop avoids baking a particular mod's texture coordinates into adapters. */
@Mixin(Gui.class)
public abstract class GuiBackgroundClipMixin {

    @Inject(method = "drawTexturedModalRect", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$clip(int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        int[] rectangle = NativeInventoryPanel.clipBackground(x, y, u, v, width, height);
        if (rectangle == null) return;
        // The cropped rectangle no longer matches the original sheet, ending the nested interception.
        if (rectangle[4] > 0 && rectangle[5] > 0) ((Gui) (Object) this)
            .drawTexturedModalRect(rectangle[0], rectangle[1], rectangle[2], rectangle[3], rectangle[4], rectangle[5]);
        ci.cancel();
    }
}
