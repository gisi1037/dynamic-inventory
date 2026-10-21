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
        this(
            screenWidth,
            screenHeight,
            storage,
            preferredColumns,
            preferredRows,
            description,
            nativeChestWidth,
            nativeChestHeight,
            chestSlotCount,
            GAP);
    }

    public AttachedChestLayout(int screenWidth, int screenHeight, int storage, int preferredColumns, int preferredRows,
        boolean description, int nativeChestWidth, int nativeChestHeight, int chestSlotCount, int sectionGap) {
        this(
            screenWidth,
            screenHeight,
            storage,
            preferredColumns,
            preferredRows,
            description,
            nativeChestWidth,
            nativeChestHeight,
            chestSlotCount,
            sectionGap,
            GAP);
    }

    public AttachedChestLayout(int screenWidth, int screenHeight, int storage, int preferredColumns, int preferredRows,
        boolean description, int nativeChestWidth, int nativeChestHeight, int chestSlotCount, int sectionGap,
        int contentGap) {
        this(
            screenWidth,
            screenHeight,
            storage,
            preferredColumns,
            preferredRows,
            description,
            nativeChestWidth,
            nativeChestHeight,
            chestSlotCount,
            sectionGap,
            contentGap,
            8,
            8);
    }

    public AttachedChestLayout(int screenWidth, int screenHeight, int storage, int preferredColumns, int preferredRows,
        boolean description, int nativeChestWidth, int nativeChestHeight, int chestSlotCount, int sectionGap,
        int contentGap, int topMargin, int bottomMargin) {
        if (sectionGap < 0 || sectionGap > 128) throw new IllegalArgumentException("Section gap out of range");
        if (contentGap < 0 || contentGap > 128) throw new IllegalArgumentException("Content gap out of range");
        int availableWidth = Math.max(176, screenWidth - 24);
        // External screens use one explicit preference; never silently widen a Compact player layout.
        int playerPadding = 26;
        int requestedColumns = preferredColumns;
        boolean descriptionBesideStack = description && availableWidth < 360 && screenWidth >= 310;
        if (descriptionBesideStack) availableWidth = Math.max(availableWidth, 310);
        int preferredPlayerWidth = Math.max(176, Math.max(3, Math.min(18, preferredColumns)) * 18 + 26);
        boolean descriptionAbove = description && !descriptionBesideStack
            && availableWidth < preferredPlayerWidth + contentGap + CRAFTING_WIDTH + 126;
        int craftingWidth = CRAFTING_WIDTH + (description && !descriptionAbove && !descriptionBesideStack ? 126 : 0);
        int craftingHeight = craftingWidth > CRAFTING_WIDTH ? 172 : CRAFTING_HEIGHT;
        int lowerAvailableWidth = availableWidth - (descriptionBesideStack ? 126 + GAP : 0);
        sideBySide = lowerAvailableWidth >= 176 + contentGap + craftingWidth;
        int inventoryWidth = lowerAvailableWidth - (sideBySide ? contentGap + craftingWidth : 0);
        columns = rectangularColumns(storage, requestedColumns, Math.min(18, (inventoryWidth - playerPadding) / 18));
        playerWidth = Math.max(176, columns * 18 + playerPadding);
        // The first pass budgets the native viewport; the final pass receives its measured height.
        // Reserve that real extent so the lower panel cannot grow underneath the chest.
        int minimumTopHeight = Math.max(descriptionAbove ? 172 : 42, nativeChestHeight);
        int minimumLowerHeight = sideBySide ? Math.max(60, craftingHeight) : 60 + contentGap + craftingHeight;
        if (descriptionBesideStack) minimumLowerHeight = Math.max(172, minimumLowerHeight);
        // A tiny screen may not fit the description and one live row. Reduce empty margins, never clip slots.
        int availableHeight = Math.max(
            minimumTopHeight + sectionGap + minimumLowerHeight,
            NativeScreenMargins.availableHeight(screenHeight, topMargin, bottomMargin));
        int rowBudget = (availableHeight - minimumTopHeight
            - sectionGap
            - 42
            - (sideBySide ? 0 : craftingHeight + contentGap)) / 18;
        rows = Math.max(1, Math.min(Math.min(preferredRows, (storage + columns - 1) / columns), rowBudget));
        playerHeight = rows * 18 + 42;
        int lowerPanelHeight = sideBySide ? Math.max(playerHeight, craftingHeight)
            : playerHeight + contentGap + craftingHeight;
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
        // The lower player/content layout owns the width. Never choose a larger divisor merely
        // because the monitor can fit it: more chest slots add rows and eventually a scrollbar.
        // Fill the shared panel instead of restricting the chest to divisors of its capacity.
        // For example, 243 slots forced nine columns and left huge margins. Tinkers already
        // draws inactive padding in a final partial row; those cells never become real slots.
        chestColumns = desiredChestColumns;
        chestHeightBudget = Math.max(minimumTopHeight, availableHeight - lowerHeight - sectionGap);
        int chestWidth = nativeChestWidth > 0 ? nativeChestWidth : chestColumns * 18 + 26;
        int chestHeight = Math.max(42, nativeChestHeight);
        width = Math.max(chestWidth + (descriptionAbove ? GAP + 126 : 0), lowerWidth);
        int topHeight = Math.max(chestHeight, minimumTopHeight);
        lowerY = topHeight + sectionGap;
        height = lowerY + lowerHeight;
        chestX = (width - chestWidth - (descriptionAbove ? GAP + 126 : 0)) / 2;
        // Center the lower group as a unit. A wider native chest must not turn Content gap into a no-op.
        int lowerLeft = (width - lowerWidth) / 2;
        craftingX = sideBySide ? lowerLeft + playerWidth + contentGap
            : descriptionBesideStack ? lowerLeft : (width - craftingWidth) / 2;
        // One lower panel contains both inventories. Center the shorter crafting section in that
        // panel without moving the player's first row away from the panel's own top edge.
        craftingY = lowerY + (sideBySide ? (lowerHeight - craftingHeight) / 2 : 0);
        playerX = sideBySide || descriptionBesideStack ? lowerLeft : (width - playerWidth) / 2;
        playerY = lowerY + (sideBySide ? 0 : craftingHeight + contentGap);
        descriptionX = descriptionAbove ? chestX + chestWidth + GAP
            : descriptionBesideStack ? lowerLeft + Math.max(playerWidth, CRAFTING_WIDTH) + GAP
                : craftingX + CRAFTING_WIDTH;
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
