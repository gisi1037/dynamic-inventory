package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.gui.Gui;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Pixel-sized controls share their hit geometry with pointer handling. */
public final class PanelChrome extends Gui {

    private PanelChrome() {}

    public static Rect control(Rect panel) {
        return new Rect(panel.x + 10, panel.y + 1, 12, 12);
    }

    public static int corner(Rect panel, int x, int y) {
        if (!panel.contains(x, y)) return 0;
        int horizontal = x < panel.x + 8 ? 1 : x >= panel.right() - 8 ? 2 : 0;
        int vertical = y < panel.y + 8 ? 4 : y >= panel.bottom() - 8 ? 8 : 0;
        return horizontal != 0 && vertical != 0 ? horizontal | vertical : 0;
    }

    public static void icon(Rect target, boolean hovered) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int x = target.x + 2, y = target.y + 2;
        if (hovered) drawRect(target.x, target.y, target.right(), target.bottom(), theme.panelHighlight);
        drawRect(x, y, x + 5, y + 1, theme.label);
        drawRect(x, y, x + 1, y + 5, theme.label);
        drawRect(x + 4, y, x + 5, y + 2, theme.label);
        drawRect(x + 2, y + 2, x + 7, y + 3, theme.label);
        drawRect(x + 2, y + 2, x + 3, y + 7, theme.label);
        drawRect(x + 6, y + 2, x + 7, y + 7, theme.label);
        drawRect(x + 2, y + 6, x + 7, y + 7, theme.label);
        drawRect(x + 3, y + 3, x + 6, y + 4, theme.panelHighlight);
    }

    public static void grips(Rect bounds, int hovered) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        for (int corner : new int[] { 5, 6, 9, 10 }) {
            boolean right = (corner & 2) != 0, bottom = (corner & 8) != 0;
            int x = right ? bounds.right() - 3 : bounds.x + 2;
            int y = bottom ? bounds.bottom() - 3 : bounds.y + 2;
            for (int step = 0; step < 3; step++) {
                int px = x + (right ? -step : step), py = y + (bottom ? step - 2 : 2 - step);
                drawRect(px, py, px + 1, py + 1, hovered == corner ? theme.panelHighlight : theme.panelShadow);
                drawRect(px + (right ? -2 : 2), py, px + (right ? -2 : 2) + 1, py + 1, theme.panelShadow);
            }
        }
    }
}
