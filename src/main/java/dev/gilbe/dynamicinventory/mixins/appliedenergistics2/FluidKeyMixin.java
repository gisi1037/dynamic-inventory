package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ClientInputGuard;

@Mixin(
    targets = { "com.glodblock.github.client.gui.GuiLevelTerminal",
        "com.glodblock.github.client.gui.GuiLevelMaintainer", "com.glodblock.github.client.gui.GuiMagnetFilter" },
    remap = false)
public abstract class FluidKeyMixin {

    @Inject(method = "keyTyped", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pendingKey(char key, int code, CallbackInfo ci) {
        if (ClientInputGuard.cancelFallbackKeyboard((GuiScreen) (Object) this)) ci.cancel();
    }
}
