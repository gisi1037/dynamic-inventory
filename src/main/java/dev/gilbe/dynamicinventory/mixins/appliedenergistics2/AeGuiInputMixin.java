package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiUpgradeable;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsUpgradeTooltip;

/** Native virtual-slot dragging and its scrollbar use the machine origin, not the expanded outer frame. */
@Mixin(targets = "appeng.client.gui.AEBaseGui", remap = false)
public abstract class AeGuiInputMixin {

    @Inject(method = "drawScreen", remap = true, at = @At("HEAD"))
    private void dynamicinventory$upgradeTooltip(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // GuiUpgradeable already queues this tooltip before calling super. Fill only the missing
        // base-GUI path used by Inscribers/Crystal Growth Chamber, and only for registered hosts.
        if (((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !((Object) this instanceof GuiUpgradeable))
            AppliedEnergisticsUpgradeTooltip.draw((AEBaseGui) (Object) this, mouseX, mouseY);
    }

    @Redirect(
        method = { "mouseClicked", "mouseClickMove", "handleMouseInput" },
        remap = true,
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/AEBaseGui;guiLeft:I",
            opcode = Opcodes.GETFIELD,
            remap = true))
    private int dynamicinventory$left(AEBaseGui gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$left() : panel.nativeHeaderLeft();
    }

    @Redirect(
        method = { "mouseClicked", "mouseClickMove", "handleMouseInput" },
        remap = true,
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/AEBaseGui;guiTop:I",
            opcode = Opcodes.GETFIELD,
            remap = true))
    private int dynamicinventory$top(AEBaseGui gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$top() : panel.nativeHeaderTop();
    }
}
