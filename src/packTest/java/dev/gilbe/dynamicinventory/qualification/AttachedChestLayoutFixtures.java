package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.AttachedChestLayout;

/** Pure geometry cases for supported scaled windows, capacity, user columns, and optional descriptions. */
public final class AttachedChestLayoutFixtures {

    private AttachedChestLayoutFixtures() {}

    public static int run() {
        int cases = 0;
        for (int[] screen : new int[][] { { 320, 240 }, { 480, 270 }, { 640, 360 }, { 960, 540 }, { 1920, 1080 } })
            for (int storage : new int[] { 27, 72, 216 }) for (int columns : new int[] { 3, 6, 9, 18 })
                for (int rows : new int[] { 1, 4, 8, 12 }) for (boolean description : new boolean[] { false, true })
                    for (int chestSlots : new int[] { 1, 9, 27, 54, 60, 243, 486 }) {
                        AttachedChestLayout budget = new AttachedChestLayout(
                            screen[0],
                            screen[1],
                            storage,
                            columns,
                            rows,
                            description,
                            0,
                            0,
                            chestSlots);
                        // Ordinary and compressed chests use the same rectangular scrolling budget.
                        int totalRows = (chestSlots + budget.chestColumns - 1) / budget.chestColumns;
                        int visibleRows = Math.max(1, Math.min(totalRows, (budget.chestHeightBudget - 24) / 18));
                        int chestWidth = budget.chestColumns * 18 + 14 + (visibleRows < totalRows ? 12 : 0);
                        int chestHeight = visibleRows * 18 + 24;
                        AttachedChestLayout layout = new AttachedChestLayout(
                            screen[0],
                            screen[1],
                            storage,
                            columns,
                            rows,
                            description,
                            chestWidth,
                            chestHeight,
                            chestSlots);
                        String label = screen[0] + "x"
                            + screen[1]
                            + " storage="
                            + storage
                            + " columns="
                            + columns
                            + " rows="
                            + rows
                            + " description="
                            + description
                            + " chestSlots="
                            + chestSlots;
                        check(chestSlots % layout.chestColumns == 0, "complete rectangular chest rows " + label);
                        check(layout.width <= screen[0] && layout.height <= screen[1], "window bounds " + label);
                        int columnLimit = storage == 72 && columns == 9 && rows == 8 && !description ? 12 : columns;
                        check(layout.columns <= columnLimit && layout.rows <= rows, "chosen layout limits " + label);
                        check(
                            layout.playerY >= chestHeight + 8 && layout.craftingY >= chestHeight + 8,
                            "lower band follows the chest " + label);
                        check(
                            !overlap(
                                layout.playerX,
                                layout.playerY,
                                layout.playerWidth,
                                layout.playerHeight,
                                layout.craftingX,
                                layout.craftingY,
                                176,
                                80),
                            "player/crafting separation " + label);
                        if (description) {
                            check(
                                layout.descriptionX >= 0 && layout.descriptionX + 126 <= layout.width
                                    && layout.descriptionY + 172 <= layout.height,
                                "description bounds " + label);
                            check(
                                !overlap(
                                    layout.playerX,
                                    layout.playerY,
                                    layout.playerWidth,
                                    layout.playerHeight,
                                    layout.descriptionX,
                                    layout.descriptionY,
                                    126,
                                    172),
                                "player/description separation " + label);
                            check(
                                !overlap(
                                    layout.craftingX,
                                    layout.craftingY,
                                    176,
                                    80,
                                    layout.descriptionX,
                                    layout.descriptionY,
                                    126,
                                    172),
                                "craft/description separation " + label);
                        }
                        cases++;
                    }
        AttachedChestLayout defaults = new AttachedChestLayout(640, 360, 72, 9, 8, false, 590, 150);
        check(
            defaults.sideBySide && defaults.columns == 12 && defaults.rows == 6,
            "Compact chest repacks all 72 storage slots into twelve columns beside crafting");
        AttachedChestLayout wide = new AttachedChestLayout(640, 360, 72, 18, 4, false, 590, 168);
        check(
            wide.sideBySide && wide.columns == 18 && wide.rows == 4,
            "explicit Wide layout exposes all 72 storage slots beside crafting");
        // A large framebuffer must not turn Compact into a monitor-wide chest.
        AttachedChestLayout compactBudget = new AttachedChestLayout(1920, 1080, 72, 9, 8, false, 0, 0);
        check(
            compactBudget.chestColumns == 21 && compactBudget.width == 412,
            "Compact chest width follows the inventory and crafting band");
        check(compactBudget.craftingX == 236, "crafting shifts right while player padding and section gap shrink");
        AttachedChestLayout narrow = new AttachedChestLayout(400, 360, 72, 9, 8, false, 0, 0);
        check(narrow.columns == 9, "a narrower chest screen retains a complete nine-column rectangle");
        AttachedChestLayout small = new AttachedChestLayout(382, 360, 72, 9, 8, false, 0, 0);
        check(small.columns == 9, "the smallest side-by-side chest screen retains nine columns");
        AttachedChestLayout wideBudget = new AttachedChestLayout(1920, 1080, 72, 18, 4, false, 0, 0);
        check(
            wideBudget.chestColumns == 28 && wideBudget.width == 534,
            "Wide chest width follows the inventory and crafting band");
        AttachedChestLayout rectangular = new AttachedChestLayout(960, 540, 72, 9, 8, false, 0, 0, 243);
        check(rectangular.columns == 12 && 72 % rectangular.columns == 0, "player grid has no partial row");
        check(
            rectangular.chestColumns == 27 && 243 % rectangular.chestColumns == 0,
            "243-slot chest has no partial row");
        AttachedChestLayout doubleChest = new AttachedChestLayout(960, 540, 72, 9, 8, false, 0, 0, 486);
        check(486 % doubleChest.chestColumns == 0, "486-slot chest also has no partial row");
        return cases;
    }

    private static boolean overlap(int x, int y, int width, int height, int otherX, int otherY, int otherWidth,
        int otherHeight) {
        return x < otherX + otherWidth && otherX < x + width && y < otherY + otherHeight && otherY < y + height;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Attached chest: " + message);
    }
}
