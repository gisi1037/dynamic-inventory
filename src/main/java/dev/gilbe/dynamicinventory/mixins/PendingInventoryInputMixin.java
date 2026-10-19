package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ClientInputGuard;

/** Fallback for runtimes without ModularUI2's cancelable GUI input pre-events. */
@Mixin(GuiScreen.class)
public abstract class PendingInventoryInputMixin {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true, order = 900)
    private void dynamicinventory$freezePendingMouse(CallbackInfo ci) {
        if (ClientInputGuard.cancelFallbackMouse((GuiScreen) (Object) this)) ci.cancel();
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true, order = 900)
    private void dynamicinventory$freezePendingKeyboard(CallbackInfo ci) {
        if (ClientInputGuard.cancelFallbackKeyboard((GuiScreen) (Object) this)) ci.cancel();
    }
}
