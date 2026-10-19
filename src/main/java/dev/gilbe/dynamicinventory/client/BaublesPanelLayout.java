package dev.gilbe.dynamicinventory.client;

/** A bounded side panel in scaled GUI pixels; equipment IDs never depend on its geometry. */
public final class BaublesPanelLayout {

    public static final int COLUMNS = 3;
    public final int totalRows;
    public final int visibleRows;
    public final int width;
    public final int height;

    public BaublesPanelLayout(int screenHeight, int slots) {
        totalRows = (slots + COLUMNS - 1) / COLUMNS;
        visibleRows = Math.max(1, Math.min(totalRows, (screenHeight - 16) / 18));
        width = COLUMNS * 18 + 16 + (maxScroll() > 0 ? 10 : 0);
        height = visibleRows * 18 + 16;
    }

    public int maxScroll() {
        return Math.max(0, totalRows - visibleRows);
    }
}
