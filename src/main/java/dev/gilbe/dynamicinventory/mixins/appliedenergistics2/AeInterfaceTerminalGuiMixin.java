package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.core.AEConfig;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.NativeScreenMargins;
import dev.gilbe.dynamicinventory.client.TerminalViewportLayout;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

/** Keep the custom interface browser/search fields native while reserving the expanded player band. */
@Mixin(targets = "appeng.client.gui.implementations.GuiInterfaceTerminal", remap = false)
public abstract class AeInterfaceTerminalGuiMixin implements NativeUiAnchors {

    @Shadow
    @Final
    private MEGuiTextField searchFieldInputs;
    @Shadow
    @Final
    private MEGuiTextField searchFieldOutputs;
    @Shadow
    @Final
    private MEGuiTextField searchFieldNames;

    @Inject(method = "calculateViewHeight", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$viewport(CallbackInfoReturnable<Integer> cir) {
        GuiInterfaceTerminal gui = (GuiInterfaceTerminal) (Object) this;
        if (InventoryUi.findAdapter(gui.inventorySlots) == null) return;
        NativeLayoutPatch patch = NativeLayoutPatches.resolve(gui);
        int available = NativeScreenMargins
            .availableHeight(gui.height, Math.max(8, patch.value(14)), Math.max(28, patch.value(15))) - patch.value(4);
        cir.setReturnValue(
            18 * TerminalViewportLayout.terminalRows(
                available,
                117 + Math.max(0, patch.value(11) - 6),
                NativeLayoutPatches.requestedRows(gui, patch, 9),
                AEConfig.instance.getConfigManager()
                    .getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL,
                InventoryViewSettings.terminalSmallRows,
                InventoryViewSettings.terminalTallMinimumRows));
    }

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { searchFieldInputs.x, searchFieldInputs.y, searchFieldOutputs.x, searchFieldOutputs.y,
            searchFieldNames.x, searchFieldNames.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        searchFieldInputs.x = origins[0];
        searchFieldInputs.y = origins[1];
        searchFieldOutputs.x = origins[2];
        searchFieldOutputs.y = origins[3];
        searchFieldNames.x = origins[4];
        searchFieldNames.y = origins[5];
    }
}
