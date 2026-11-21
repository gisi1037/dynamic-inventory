package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.glodblock.github.client.gui.GuiLevelTerminal;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.core.AEConfig;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.NativeScreenMargins;
import dev.gilbe.dynamicinventory.client.TerminalPanelSpace;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

@Mixin(targets = "com.glodblock.github.client.gui.GuiLevelTerminal", remap = false)
public abstract class FluidLevelTerminalMixin implements NativeUiAnchors {

    @Shadow
    @Final
    private MEGuiTextField searchFieldOutputs;
    @Shadow
    @Final
    private MEGuiTextField searchFieldNames;

    @Inject(method = "calculateViewHeight", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$viewport(CallbackInfoReturnable<Integer> cir) {
        GuiLevelTerminal gui = (GuiLevelTerminal) (Object) this;
        if (InventoryUi.findAdapter(gui.inventorySlots) == null) return;
        NativeLayoutPatch patch = NativeLayoutPatches.resolve(gui);
        int available = NativeScreenMargins
            .availableHeight(gui.height, Math.max(8, patch.value(14)), Math.max(28, patch.value(15))) - patch.value(4);
        cir.setReturnValue(
            18 * TerminalPanelSpace.rows(
                gui,
                patch,
                available,
                114 + Math.max(0, patch.value(11) - 6),
                (TerminalStyle) AEConfig.instance.getConfigManager()
                    .getSetting(Settings.TERMINAL_STYLE)));
    }

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { searchFieldOutputs.x, searchFieldOutputs.y, searchFieldNames.x, searchFieldNames.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        searchFieldOutputs.x = origins[0];
        searchFieldOutputs.y = origins[1];
        searchFieldNames.x = origins[2];
        searchFieldNames.y = origins[3];
    }
}
