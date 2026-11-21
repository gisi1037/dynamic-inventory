package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.client.gui.widgets.MEGuiTextField;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;

@Mixin(targets = "com.glodblock.github.client.gui.GuiMagnetFilter", remap = false)
public abstract class FluidMagnetFilterMixin implements NativeUiAnchors {

    @Shadow
    protected MEGuiTextField oreDict;

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { oreDict.x, oreDict.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        oreDict.x = origins[0];
        oreDict.y = origins[1];
    }
}
