package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** NEI adds this dispatcher to GuiContainer; keep it intact and consume only panel scrolling. */
@Mixin(GuiContainer.class)
public abstract class NeiContainerWheelMixin extends GuiScreen {

    @Inject(method = { "handleMouseInput", "func_146274_d" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void dynamicinventory$wheel(CallbackInfo ci) {
        if (NativeInventoryPanel.handleWheel(this)) ci.cancel();
    }
}
