package dev.gilbe.dynamicinventory.client;

/** A bounded side panel in scaled GUI pixels; equipment IDs never depend on its geometry. */
public final class BaublesPanelLayout {

    public static final int COLUMNS = 3;
    public final int columns;
    public final int totalRows;
    public final int visibleRows;
    public final int width;
    public final int height;

    public BaublesPanelLayout(int screenHeight, int slots) {
        this(screenHeight, slots, screenHeight, Integer.MAX_VALUE);
    }

    public BaublesPanelLayout(int screenHeight, int slots, int maximumHeight, int maximumWidth) {
        int rowLimit = Math.max(1, (Math.min(screenHeight, maximumHeight) - 16) / 18);
        int desired = Math.max(COLUMNS, (slots + rowLimit - 1) / rowLimit);
        int available = Math.max(1, (maximumWidth - 16) / 18);
        int fitted = Math.min(desired, available);
        if ((slots + fitted - 1) / fitted > rowLimit && fitted * 18 + 26 > maximumWidth)
            fitted = Math.max(1, fitted - 1);
        columns = fitted;
        totalRows = (slots + columns - 1) / columns;
        visibleRows = Math.max(1, Math.min(totalRows, rowLimit));
        width = columns * 18 + 16 + (maxScroll() > 0 ? 10 : 0);
        height = visibleRows * 18 + 16;
    }

    public int maxScroll() {
        return Math.max(0, totalRows - visibleRows);
    }
}
