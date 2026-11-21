package dev.gilbe.dynamicinventory.mixins.enderio;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "crazypants.enderio.conduit.gui.BaseSettingsPanel", remap = false)
public interface ConduitTabOriginMixin {

    @Accessor("left")
    void dynamicinventory$setTabLeft(int left);

    @Accessor("top")
    void dynamicinventory$setTabTop(int top);
}
