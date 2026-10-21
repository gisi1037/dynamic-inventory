package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** A scoped caption translation lets native sections move without hardcoding every GUI's text coordinates. */
@Mixin(FontRenderer.class)
public abstract class NativeInventoryLabelMixin {

    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$playerCaption(String text, int x, int y, int color,
        CallbackInfoReturnable<Integer> ci) {
        int[] position = NativeInventoryPanel.playerLabel(text, x, y);
        if (position != null)
            ci.setReturnValue(((FontRenderer) (Object) this).drawString(text, position[0], position[1], color));
    }
}
