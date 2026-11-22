package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.client.gui.widgets.MEGuiTextField;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;

@Mixin(targets = "appeng.client.gui.implementations.GuiAdvancedLevelEmitter", remap = false)
public abstract class AeAdvancedLevelEmitterGuiMixin implements NativeUiAnchors {

    @Shadow
    @Final
    private MEGuiTextField[] amountFields;

    @Override
    public int[] captureHeaderOrigins() {
        int[] origins = new int[amountFields.length * 2];
        for (int i = 0; i < amountFields.length; i++) {
            origins[i * 2] = amountFields[i].x;
            origins[i * 2 + 1] = amountFields[i].y;
        }
        return origins;
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        for (int i = 0; i < amountFields.length; i++) {
            amountFields[i].x = origins[i * 2];
            amountFields[i].y = origins[i * 2 + 1];
        }
    }
}
