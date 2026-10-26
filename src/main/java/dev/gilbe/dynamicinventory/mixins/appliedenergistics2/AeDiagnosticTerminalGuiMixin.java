package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.client.gui.widgets.MEGuiTextField;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

/** The native five-row diagnostic list scrolls independently of player storage. */
@Mixin(targets = "appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal", remap = false)
public abstract class AeDiagnosticTerminalGuiMixin implements NativeUiAnchors {

    @Shadow
    private MEGuiTextField searchField;
    @Shadow
    private int visibleRows;

    @Inject(method = "recalculateScreenSize", at = @At("RETURN"))
    private void dynamicinventory$viewport(CallbackInfo ci) {
        if (InventoryUi.findAdapter(((GuiCraftingDiagnosticTerminal) (Object) this).inventorySlots) == null) return;
        visibleRows = 5;
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        access.dynamicinventory$bounds(
            access.dynamicinventory$width(),
            256,
            access.dynamicinventory$left(),
            access.dynamicinventory$top());
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
