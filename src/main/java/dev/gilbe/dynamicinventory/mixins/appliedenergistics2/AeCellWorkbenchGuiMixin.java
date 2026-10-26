package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.client.gui.implementations.GuiCellWorkbench;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Bookmark drops use the same native phantom-grid origin as drawing and ordinary virtual-slot clicks. */
@Mixin(targets = "appeng.client.gui.implementations.GuiCellWorkbench", remap = false)
public abstract class AeCellWorkbenchGuiMixin {

    @Redirect(
        method = "handleBookmarkGroupDrop",
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiCellWorkbench;guiLeft:I",
            opcode = Opcodes.GETFIELD,
            remap = true))
    private int dynamicinventory$left(GuiCellWorkbench gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$left() : panel.nativeHeaderLeft();
    }

    @Redirect(
        method = "handleBookmarkGroupDrop",
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiCellWorkbench;guiTop:I",
            opcode = Opcodes.GETFIELD,
            remap = true))
    private int dynamicinventory$top(GuiCellWorkbench gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$top() : panel.nativeHeaderTop();
    }
}
