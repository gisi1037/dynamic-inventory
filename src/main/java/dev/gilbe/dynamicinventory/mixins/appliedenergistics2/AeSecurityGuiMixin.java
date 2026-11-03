package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.core.AEConfig;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.NativeScreenMargins;
import dev.gilbe.dynamicinventory.client.TerminalViewportLayout;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsBrowserAccess;

/** Fit the native browser around the expanded player band, preserving AE2's Small/Tall row cap. */
@Mixin(targets = "appeng.client.gui.implementations.GuiMEMonitorable", remap = false)
public abstract class AeSecurityGuiMixin implements NativeUiAnchors, AppliedEnergisticsBrowserAccess {

    @Shadow
    protected int rows;
    @Shadow
    private int reservedSpace;

    @Shadow
    private int perRow;
    @Shadow
    private appeng.client.gui.widgets.GuiImgButton pinsStateButton;

    @Shadow
    @Final
    protected MEGuiTextField searchField;

    private boolean dynamicinventory$registered() {
        return InventoryUi.findAdapter(((GuiMEMonitorable) (Object) this).inventorySlots) != null;
    }

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void dynamicinventory$sidebarPins(CallbackInfo ci) {
        if (!dynamicinventory$registered() || pinsStateButton == null) return;
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        int column = access.dynamicinventory$left() - 36;
        int next = access.dynamicinventory$top() + 8;
        for (net.minecraft.client.gui.GuiButton button : access.dynamicinventory$buttons()) {
            if (button != pinsStateButton && button.visible && button.xPosition == column)
                next = Math.max(next, button.yPosition + Math.max(20, button.height + 4));
        }
        // Capture this as the native origin before the shared decorator applies any saved Pins X/Y offsets.
        pinsStateButton.xPosition = column;
        pinsStateButton.yPosition = next;
    }

    @Inject(method = "calculateRowsCount", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$rows(CallbackInfoReturnable<Integer> cir) {
        if (!dynamicinventory$registered()) return;
        GuiMEMonitorable gui = (GuiMEMonitorable) (Object) this;
        NativeLayoutPatch patch = NativeLayoutPatches.resolve(gui);
        // Keep NEI's bottom input clear even if an older saved layout has smaller margins.
        int available = NativeScreenMargins
            .availableHeight(gui.height, Math.max(8, patch.value(14)), Math.max(28, patch.value(15))) - patch.value(4);
        // 22px native boundary + 42px player chrome + the panel fitter's 8px allowance.
        cir.setReturnValue(
            TerminalViewportLayout.terminalRows(
                available,
                72 + reservedSpace + Math.max(0, patch.value(11) - 6),
                NativeLayoutPatches.requestedRows(gui, patch, 9),
                AEConfig.instance.getConfigManager()
                    .getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL,
                InventoryViewSettings.terminalSmallRows,
                InventoryViewSettings.terminalTallMinimumRows));
    }

    @Override
    public int dynamicinventory$browserBottom() {
        return 16 + rows * 18;
    }

    @Override
    public boolean dynamicinventory$isPinWidget(net.minecraft.client.gui.GuiButton button) {
        return button == pinsStateButton;
    }

    @Override
    public int[] dynamicinventory$virtualCraftingOrigin() {
        return switch (((Object) this).getClass()
            .getName()) {
            case "appeng.client.gui.implementations.GuiPatternTerm" -> new int[] { 18, rows * 18 + 39 };
            case "appeng.client.gui.implementations.GuiPatternTermEx" -> new int[] { 15, rows * 18 + 32 };
            default -> null;
        };
    }

    @Redirect(
        method = "initGui",
        remap = true,
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiMEMonitorable;perRow:I",
            opcode = Opcodes.PUTFIELD,
            remap = false))
    private void dynamicinventory$columns(GuiMEMonitorable gui, int columns) {
        // FULL terminal style must not expand this browser into the space reserved for its encoder.
        perRow = dynamicinventory$registered() ? 9 : columns;
    }

    @Override
    public int[] captureHeaderOrigins() {
        return new int[] { searchField.x, searchField.y };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        searchField.x = origins[0];
        searchField.y = origins[1];
    }
}
