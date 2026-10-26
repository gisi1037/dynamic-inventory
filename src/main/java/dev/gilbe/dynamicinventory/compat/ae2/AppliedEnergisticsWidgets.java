package dev.gilbe.dynamicinventory.compat.ae2;

/** Reviewed native AE2 button groups. Names keep this client helper independent of optional AE2 classes. */
public final class AppliedEnergisticsWidgets {

    private AppliedEnergisticsWidgets() {}

    /** First editor offset field, or -1 for widgets whose positioning has not been reviewed. */
    public static int offsetField(String guiClass, String buttonClass) {
        if (!guiClass.startsWith("appeng.client.gui.implementations.Gui")) return -1;
        return switch (buttonClass) {
            case "appeng.client.gui.widgets.GuiTabButton" -> 20;
            case "appeng.client.gui.widgets.GuiImgButton", "appeng.client.gui.widgets.GuiToggleButton", "appeng.client.gui.widgets.GuiSimpleImgButton" -> 22;
            default -> -1;
        };
    }
}
