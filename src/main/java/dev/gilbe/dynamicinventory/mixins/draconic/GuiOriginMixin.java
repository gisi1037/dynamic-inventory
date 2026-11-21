package dev.gilbe.dynamicinventory.mixins.draconic;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Native components and tooltips also read the origin after vanilla has drawn the container. */
@Mixin(
    targets = { "com.brandon3055.draconicevolution.client.utils.guicomponents.GUIBase",
        "com.brandon3055.draconicevolution.client.gui.componentguis.GUIReactor",
        "com.brandon3055.draconicevolution.client.gui.GUIEnergyInfuser",
        "com.brandon3055.draconicevolution.client.gui.GUIDislocatorInhibitor",
        "com.brandon3055.draconicevolution.client.gui.GUIUpgradeModifier",
        "com.brandon3055.draconicevolution.client.gui.GUIPlayerDetector" },
    remap = false)
public abstract class GuiOriginMixin {

    @Group(name = "dynamicinventory$origin", min = 1)
    @Redirect(method = "*", at = @At(value = "FIELD", target = "guiLeft:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$leftMcp(@Coerce Object gui) {
        return dynamicinventory$left(gui);
    }

    @Group(name = "dynamicinventory$origin", min = 1)
    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_147003_i:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$leftSrg(@Coerce Object gui) {
        return dynamicinventory$left(gui);
    }

    private static int dynamicinventory$left(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$left() : panel.nativeHeaderLeft();
    }

    @Group(name = "dynamicinventory$origin", min = 1)
    @Redirect(method = "*", at = @At(value = "FIELD", target = "guiTop:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$topMcp(@Coerce Object gui) {
        return dynamicinventory$top(gui);
    }

    @Group(name = "dynamicinventory$origin", min = 1)
    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_147009_r:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$topSrg(@Coerce Object gui) {
        return dynamicinventory$top(gui);
    }

    private static int dynamicinventory$top(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$top() : panel.nativeHeaderTop();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "xSize:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$widthMcp(@Coerce Object gui) {
        return dynamicinventory$width(gui);
    }

    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_146999_f:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$widthSrg(@Coerce Object gui) {
        return dynamicinventory$width(gui);
    }

    private static int dynamicinventory$width(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$width() : panel.nativeUiWidth();
    }
}
