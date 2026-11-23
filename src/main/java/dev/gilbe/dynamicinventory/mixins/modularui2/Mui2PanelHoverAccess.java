package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.cleanroommc.modularui.screen.ModularPanel;

@Mixin(value = ModularPanel.class, remap = false)
public interface Mui2PanelHoverAccess {

    @Invoker("findHoveredWidgets")
    void dynamicinventory$findHoveredWidgets();
}
