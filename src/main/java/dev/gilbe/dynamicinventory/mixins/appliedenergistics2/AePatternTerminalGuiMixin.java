package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsMovingButtons;

/** Keep AE2's live processing-layout changes; apply editor offsets after those calculations. */
@Mixin(targets = "appeng.client.gui.implementations.GuiPatternTermEx", remap = false)
public abstract class AePatternTerminalGuiMixin extends GuiPatternTerm implements AppliedEnergisticsMovingButtons {

    @Shadow
    private GuiImgButton invertBtn;

    protected AePatternTerminalGuiMixin(InventoryPlayer inventory, ITerminalHost host) {
        super(inventory, host);
    }

    @Override
    public GuiButton[] dynamicinventory$movingButtons() {
        return new GuiButton[] { clearBtn, doubleBtn, substitutionsEnabledBtn, substitutionsDisabledBtn,
            beSubstitutionsEnabledBtn, beSubstitutionsDisabledBtn, invertBtn };
    }

    @Inject(
        method = "drawScreen",
        remap = true,
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/implementations/GuiPatternTerm;drawScreen(IIF)V",
            remap = true))
    private void dynamicinventory$position(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) panel.positionMovingButtons(dynamicinventory$movingButtons());
    }
}
