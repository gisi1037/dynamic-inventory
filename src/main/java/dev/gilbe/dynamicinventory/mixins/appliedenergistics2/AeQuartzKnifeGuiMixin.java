package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.client.gui.implementations.GuiQuartzKnife;
import appeng.client.gui.widgets.MEGuiTextField;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;

/** Keep the knife's name field and its hitbox aligned with the native header. */
@Mixin(value = GuiQuartzKnife.class, remap = false)
public abstract class AeQuartzKnifeGuiMixin implements NativeUiAnchors {

    @Shadow
    private MEGuiTextField textField;

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { textField.x, textField.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        textField.x = origins[0];
        textField.y = origins[1];
    }
}
