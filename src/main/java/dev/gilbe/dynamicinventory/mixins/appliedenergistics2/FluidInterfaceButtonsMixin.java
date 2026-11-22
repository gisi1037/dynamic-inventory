package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.util.List;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.widgets.GuiImgButton;

@Mixin(targets = "com.glodblock.github.client.FluidInterfaceButtons", remap = false)
public abstract class FluidInterfaceButtonsMixin {

    @Shadow
    private GuiImgButton sidelessMode;

    @Inject(method = "initCustomButtons", at = @At("RETURN"))
    private void dynamicinventory$groupSidelessButton(int left, int top, int width, int height, int xOffset,
        int yOffset, List<GuiButton> buttons, CallbackInfo ci) {
        if (sidelessMode == null) return;
        // The native outer column has Smart Blocking in the first row and an open second row.
        sidelessMode.xPosition = left - 36;
        sidelessMode.yPosition = top + 26;
    }
}
