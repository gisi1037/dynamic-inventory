package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.widgets.GuiImgButton;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsMovingButtons;

/** Preserve the native row change that makes room for Ore Dictionary and Fuzzy card controls. */
@Mixin(targets = "appeng.client.gui.implementations.GuiStorageBus", remap = false)
public abstract class AeStorageBusGuiMixin implements AppliedEnergisticsMovingButtons {

    @Shadow
    private GuiImgButton reshuffleAccess;

    @Override
    public GuiButton[] dynamicinventory$movingButtons() {
        return new GuiButton[] { reshuffleAccess };
    }

    @Inject(method = "handleButtonVisibility", at = @At("RETURN"))
    private void dynamicinventory$positionReshuffle(CallbackInfo ci) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel == null || reshuffleAccess == null) return;
        // AE2 just recalculated Y at the native origin. Reset X before applying the editor offsets.
        reshuffleAccess.xPosition = panel.nativeHeaderLeft() - 18;
        panel.positionMovingButtons(dynamicinventory$movingButtons());
    }
}
