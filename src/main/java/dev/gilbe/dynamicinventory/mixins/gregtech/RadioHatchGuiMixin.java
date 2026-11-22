package dev.gilbe.dynamicinventory.mixins.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.gilbe.dynamicinventory.compat.mui2.PlayerInventoryWidgetGroup;

@Pseudo
@Mixin(targets = "gregtech.common.gui.modularui.hatch.MTERadioHatchGui", remap = false)
public abstract class RadioHatchGuiMixin {

    @WrapOperation(
        method = "build",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/screen/ModularPanel;bindPlayerInventory()Lcom/cleanroommc/modularui/screen/ModularPanel;"),
        require = 0)
    private ModularPanel dynamicinventory$keepOnePlayerInventory(ModularPanel panel, Operation<ModularPanel> original) {
        // The GregTech template already binds the inventory before the hatch adds its controls.
        for (IWidget child : panel.getChildren()) if (child instanceof PlayerInventoryWidgetGroup) return panel;
        return original.call(panel);
    }
}
