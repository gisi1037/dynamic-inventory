package dev.gilbe.dynamicinventory.client;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Character-section placement is independent of the selected inventory-grid preset. */
public final class PlayerSectionLayout {

    private PlayerSectionLayout() {}

    public static int defaultTop(int screenWidth, int screenHeight, int tabs, int storage) {
        InventoryViewLayout compact = new InventoryViewLayout(
            screenWidth,
            screenHeight - tabs,
            InventoryViewSettings.DEFAULT_COLUMNS,
            InventoryViewSettings.DEFAULT_ROWS,
            storage);
        return Math.max(0, (screenHeight - tabs - compact.height) / 2);
    }

    public static Rect place(Rect viewport, int width, int height, int defaultLeft, int defaultTop, int anchorX,
        int anchorY) {
        int travelX = Math.max(0, viewport.width - width), travelY = Math.max(0, viewport.height - height);
        int x = anchorX < 0 ? defaultLeft : viewport.x + (int) Math.round(travelX * anchorX / 10000.0);
        int y = anchorY < 0 ? defaultTop : viewport.y + (int) Math.round(travelY * anchorY / 10000.0);
        return new Rect(
            Math.max(viewport.x, Math.min(viewport.x + travelX, x)),
            Math.max(viewport.y, Math.min(viewport.y + travelY, y)),
            width,
            height);
    }

    public static int anchor(int position, int origin, int travel) {
        return travel <= 0 ? 0 : Math.max(0, Math.min(10000, (int) Math.round((position - origin) * 10000.0 / travel)));
    }
}
