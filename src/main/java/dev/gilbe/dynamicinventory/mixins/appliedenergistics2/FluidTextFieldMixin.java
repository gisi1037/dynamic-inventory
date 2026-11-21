package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import dev.gilbe.dynamicinventory.compat.ae2.FluidTextFieldPosition;

@Mixin(targets = "com.glodblock.github.client.gui.FCGuiTextField", remap = false)
public abstract class FluidTextFieldMixin extends GuiTextField implements FluidTextFieldPosition {

    @Shadow
    @Final
    @Mutable
    private int _xPos;
    @Shadow
    @Final
    @Mutable
    private int _yPos;

    protected FluidTextFieldMixin(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override
    public int[] dynamicinventory$origin() {
        return new int[] { _xPos, _yPos };
    }

    @Override
    public void dynamicinventory$position(int x, int y) {
        xPosition += x - _xPos;
        yPosition += y - _yPos;
        _xPos = x;
        _yPos = y;
    }
}
