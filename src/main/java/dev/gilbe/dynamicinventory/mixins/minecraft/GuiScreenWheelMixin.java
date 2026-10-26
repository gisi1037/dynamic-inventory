package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Standalone fallback when NEI hasn't supplied GuiContainer's input dispatcher. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenWheelMixin {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$wheel(CallbackInfo ci) {
        if (dev.gilbe.dynamicinventory.client.PanelSettingsOverlay.handleMouse((GuiScreen) (Object) this)
            || NativeInventoryPanel.handlePointer((GuiScreen) (Object) this)
            || NativeInventoryPanel.handleWheel((GuiScreen) (Object) this)) ci.cancel();
    }
}
