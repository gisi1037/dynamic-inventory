package dev.gilbe.dynamicinventory.client;

/** Two native bands: attached storage above, player inventory and crafting below. All values are scaled pixels. */
public final class AttachedChestLayout {

    public static final int GAP = 8;
    public static final int CRAFTING_WIDTH = 176;
    public static final int CRAFTING_HEIGHT = 80;
    public final boolean sideBySide;
    public final int columns, rows, playerWidth, playerHeight;
    public final int chestColumns, chestHeightBudget;
    public final int lowerY, lowerHeight;
    public final int width, height, playerX, playerY, craftingX, craftingY, chestX, descriptionX, descriptionY;

    public AttachedChestLayout(int screenWidth, int screenHeight, int storage, int preferredColumns, int preferredRows,
        boolean description, int nativeChestWidth, int nativeChestHeight) {
        this(
            screenWidth,
            screenHeight,
            storage,
            preferredColumns,
            preferredRows,
            description,
            nativeChestWidth,
            nativeChestHeight,
            0);
    }

    public AttachedChestLayout(int screenWidth, int screenHeight, int storage, int preferredColumns, int preferredRows,
        boolean description, int nativeChestWidth, int nativeChestHeight, int chestSlotCount) {
        int availableWidth = Math.max(176, screenWidth - 24);
        // This chest screen has room to repack the default 72 storage cells more efficiently.
        // Keep the saved Compact preference intact, and leave custom layouts/descriptions alone.
        boolean widenDefault = storage == 72 && preferredColumns == 9 && preferredRows == 8 && !description;
        int contentGap = widenDefault ? 4 : GAP;
        int playerPadding = widenDefault ? 16 : 26;
        int requestedColumns = widenDefault
            ? Math.min(12, Math.max(9, (availableWidth - CRAFTING_WIDTH - contentGap - playerPadding) / 18))
            : preferredColumns;
        boolean descriptionBesideStack = description && availableWidth < 360 && screenWidth >= 310;
        if (descriptionBesideStack) availableWidth = Math.max(availableWidth, 310);
        int preferredPlayerWidth = Math.max(176, Math.max(3, Math.min(18, preferredColumns)) * 18 + 26);
        boolean descriptionAbove = description && !descriptionBesideStack
            && availableWidth < preferredPlayerWidth + GAP + CRAFTING_WIDTH + 126;
        int craftingWidth = CRAFTING_WIDTH + (description && !descriptionAbove && !descriptionBesideStack ? 126 : 0);
        int craftingHeight = craftingWidth > CRAFTING_WIDTH ? 172 : CRAFTING_HEIGHT;
        int lowerAvailableWidth = availableWidth - (descriptionBesideStack ? 126 + GAP : 0);
        sideBySide = lowerAvailableWidth >= 176 + contentGap + craftingWidth;
        int inventoryWidth = lowerAvailableWidth - (sideBySide ? contentGap + craftingWidth : 0);
        columns = rectangularColumns(storage, requestedColumns, Math.min(18, (inventoryWidth - playerPadding) / 18));
        playerWidth = Math.max(176, columns * 18 + playerPadding);
        int minimumTopHeight = descriptionAbove ? 172 : 42;
        int rowBudget = (screenHeight - 16 - minimumTopHeight - GAP - 42 - (sideBySide ? 0 : craftingHeight + GAP))
            / 18;
        rows = Math.max(1, Math.min(Math.min(preferredRows, (storage + columns - 1) / columns), rowBudget));
        playerHeight = rows * 18 + 42;
        int lowerPanelHeight = sideBySide ? Math.max(playerHeight, craftingHeight)
            : playerHeight + GAP + craftingHeight;
        int lowerWidth = sideBySide ? playerWidth + contentGap + craftingWidth : Math.max(playerWidth, craftingWidth);
        if (descriptionBesideStack) {
            lowerWidth += GAP + 126;
            lowerPanelHeight = Math.max(172, lowerPanelHeight);
        }
        lowerHeight = lowerPanelHeight;
        // Match the lower band rather than filling the monitor. Otherwise a
        // Compact inventory on a large window sends crafting hundreds of pixels away.
        int topBandWidth = Math.min(availableWidth, lowerWidth);
        int desiredChestColumns = Math.max(5, (topBandWidth - (descriptionAbove ? GAP + 126 : 0) - 26) / 18);
        int maximumChestColumns = (availableWidth - (descriptionAbove ? GAP + 126 : 0) - 26) / 18;
        chestColumns = chestSlotCount > 0 ? rectangularColumns(chestSlotCount, desiredChestColumns, maximumChestColumns)
            : desiredChestColumns;
        chestHeightBudget = Math.max(minimumTopHeight, screenHeight - 16 - lowerHeight - GAP);
        int chestWidth = nativeChestWidth > 0 ? nativeChestWidth : chestColumns * 18 + 26;
        int chestHeight = Math.max(42, nativeChestHeight);
        width = Math.max(chestWidth + (descriptionAbove ? GAP + 126 : 0), lowerWidth);
        int topHeight = Math.max(chestHeight, minimumTopHeight);
        lowerY = topHeight + GAP;
        height = lowerY + lowerHeight;
        chestX = (width - chestWidth - (descriptionAbove ? GAP + 126 : 0)) / 2;
        craftingX = sideBySide ? width - craftingWidth : descriptionBesideStack ? 0 : (width - craftingWidth) / 2;
        // One lower panel contains both inventories. Center the shorter crafting section in that
        // panel without moving the player's first row away from the panel's own top edge.
        craftingY = lowerY + (sideBySide ? (lowerHeight - craftingHeight) / 2 : 0);
        playerX = sideBySide || descriptionBesideStack ? 0 : (width - playerWidth) / 2;
        playerY = lowerY + (sideBySide ? 0 : craftingHeight + GAP);
        descriptionX = descriptionAbove ? chestX + chestWidth + GAP
            : descriptionBesideStack ? Math.max(playerWidth, CRAFTING_WIDTH) + GAP : craftingX + CRAFTING_WIDTH;
        descriptionY = descriptionAbove ? 0 : craftingY;
    }

    /** Choose a real divisor: every row contains live slots, without padding cells or changing capacity. */
    private static int rectangularColumns(int slots, int preferred, int maximum) {
        int best = 1;
        for (int candidate = 1; candidate <= Math.min(slots, Math.max(1, maximum)); candidate++) {
            if (slots % candidate == 0 && Math.abs(candidate - preferred) < Math.abs(best - preferred))
                best = candidate;
        }
        return best;
    }
}
