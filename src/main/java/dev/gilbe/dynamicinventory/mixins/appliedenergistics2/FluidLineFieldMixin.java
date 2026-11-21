package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.glodblock.github.client.gui.FCGuiLineField;

import dev.gilbe.dynamicinventory.compat.ae2.FluidTextFieldPosition;

@Mixin(targets = "com.glodblock.github.client.gui.FCGuiLineField", remap = false)
public abstract class FluidLineFieldMixin {

    @Redirect(
        method = "drawTextBox",
        remap = true,
        at = @At(value = "FIELD", target = "Lcom/glodblock/github/client/gui/FCGuiLineField;_xPos:I", remap = false))
    private int dynamicinventory$left(FCGuiLineField field) {
        return ((FluidTextFieldPosition) field).dynamicinventory$origin()[0];
    }

    @Redirect(
        method = "drawTextBox",
        remap = true,
        at = @At(value = "FIELD", target = "Lcom/glodblock/github/client/gui/FCGuiLineField;_yPos:I", remap = false))
    private int dynamicinventory$top(FCGuiLineField field) {
        return ((FluidTextFieldPosition) field).dynamicinventory$origin()[1];
    }
}
