package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.client.gui.widgets.MEGuiTextField;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;

@Mixin(targets = "appeng.client.gui.implementations.GuiLevelEmitter", remap = false)
public abstract class AeLevelEmitterGuiMixin implements NativeUiAnchors {

    @Shadow
    private MEGuiTextField amountTextField;

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { amountTextField.x, amountTextField.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        amountTextField.x = origins[0];
        amountTextField.y = origins[1];
    }
}
