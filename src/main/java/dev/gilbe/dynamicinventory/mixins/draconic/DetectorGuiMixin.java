package dev.gilbe.dynamicinventory.mixins.draconic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.gilbe.dynamicinventory.compat.draconic.DetectorInventoryView;

@Mixin(targets = "com.brandon3055.draconicevolution.client.gui.GUIPlayerDetector", remap = false)
public abstract class DetectorGuiMixin implements DetectorInventoryView {

    @Shadow
    private boolean isInEditMode;

    @Override
    public boolean dynamicinventory$editingNames() {
        return isInEditMode;
    }
}
