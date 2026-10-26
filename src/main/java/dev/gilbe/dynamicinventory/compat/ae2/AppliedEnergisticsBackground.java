package dev.gilbe.dynamicinventory.compat.ae2;

/** AE2 draws its main sheet, upgrade strip and toolbox separately, unlike a single vanilla sheet. */
public final class AppliedEnergisticsBackground {

    private AppliedEnergisticsBackground() {}

    public static boolean hasSidePanels(String guiClass) {
        if (isMonitorable(guiClass)) return true;
        return switch (guiClass) {
            case "appeng.client.gui.implementations.GuiInscriber", "appeng.client.gui.implementations.GuiAdvancedInscriber", "appeng.client.gui.implementations.GuiCrystalGrowthChamber", "appeng.client.gui.implementations.GuiMAC", "appeng.client.gui.implementations.GuiIOPort", "appeng.client.gui.implementations.GuiInterface", "appeng.client.gui.implementations.GuiCellWorkbench", "appeng.client.gui.implementations.GuiSecurity" -> true;
            default -> false;
        };
    }

    public static int mainSheetWidth(String guiClass, int nativeWidth) {
        if (isMonitorable(guiClass)) return 195;
        if (guiClass.equals("appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal")) return 195;
        return hasSidePanels(guiClass) ? 177 : nativeWidth;
    }

    /** End of real header artwork, excluding the obsolete stepped native player frame. */
    public static int headerBottom(String guiClass, int playerTop) {
        return switch (guiClass) {
            case "appeng.client.gui.implementations.GuiInterfaceTerminal" -> playerTop - 7;
            // Five native rows: scrollbar ends at 122, followed by four pixels of lower cap.
            case "appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal" -> 126;
            default -> playerTop;
        };
    }

    public static boolean isSecurity(String guiClass) {
        return guiClass.equals("appeng.client.gui.implementations.GuiSecurity");
    }

    public static boolean isMonitorable(String guiClass) {
        return switch (guiClass) {
            case "appeng.client.gui.implementations.GuiMEMonitorable", "appeng.client.gui.implementations.GuiCraftingTerm", "appeng.client.gui.implementations.GuiPatternTerm", "appeng.client.gui.implementations.GuiPatternTermEx", "appeng.client.gui.implementations.GuiMEPortableCell", "appeng.client.gui.implementations.GuiSecurity" -> true;
            default -> false;
        };
    }

    public static boolean isTerminal(String guiClass) {
        return isMonitorable(guiClass) || guiClass.equals("appeng.client.gui.implementations.GuiInterfaceTerminal")
            || guiClass.equals("appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal");
    }

    public static int mainContentRight(String guiClass, int inset) {
        return isMonitorable(guiClass) ? 195 - inset : mainContentRight(inset);
    }

    /** Share the texture edge with the frame's unfilled area, avoiding a transparent seam. */
    public static int mainContentRight(int inset) {
        return Math.min(170, 176 - inset);
    }

    /** Virtual cells and currently disabled pattern rows still need space when editor gaps shrink. */
    public static int minimumContentBottom(String guiClass) {
        return switch (guiClass) {
            case "appeng.client.gui.implementations.GuiCellWorkbench" -> 154;
            case "appeng.client.gui.implementations.GuiInterface" -> 124;
            default -> 0;
        };
    }

    public static int[] clip(String guiClass, int left, int top, int nativeWidth, int nativeHeight, int playerTop,
        int inset, int x, int y, int u, int v, int width, int height) {
        if (!hasSidePanels(guiClass)) return null;
        if (isMonitorable(guiClass) && x == left && u == 0 && width == 195) {
            int offsetY = y - top;
            int cropTop = offsetY == 0 ? inset : 0;
            // Security draws a wide browser above a narrower card editor. Remove both native
            // outer borders; the unified frame supplies the old lower-right cutout's background.
            int right = v == 70 ? 170 : mainContentRight(guiClass, inset);
            if (inset == 0 && right == width && playerTop - offsetY >= height) return null;
            return new int[] { x + inset, y + cropTop, u + inset, v + cropTop, right - inset,
                Math.min(height, playerTop - offsetY) - cropTop };
        }
        if (x == left && y == top && u == 0 && v == 0 && (width == 176 || width == 177) && height == nativeHeight) {
            // A borrowed toolbox widens the GUI to 246/290px, but the old inventory is still drawn
            // in a 176/177px sheet. Cropping only full-GUI-width rectangles leaves that old grid visible.
            // The sheet's right separator starts before its outermost bevel. It belongs to the
            // old attached upgrade strip, not our main frame; suppress it even with crop zero.
            int contentRight = mainContentRight(inset);
            return new int[] { x + inset, y + inset, u + inset, v + inset, contentRight - inset,
                Math.min(height, playerTop) - inset };
        }
        if (x > left && (width == 32 || width == 35 || width == 68 || width == 104) && inset > 0) {
            // These reviewed draw calls are the upgrade and 3x3/5x5 toolbox frames. Crop their bevels
            // with the same editor setting while retaining their native slot backgrounds and coordinates.
            // Changed widths also end the nested interception in GuiBackgroundClipMixin.
            return new int[] { x + inset, y + inset, u + inset, v + inset, width - inset * 2, height - inset * 2 };
        }
        return null;
    }
}
