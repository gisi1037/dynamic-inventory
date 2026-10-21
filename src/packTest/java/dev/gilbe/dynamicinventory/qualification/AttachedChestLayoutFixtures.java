package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.AttachedChestLayout;
import dev.gilbe.dynamicinventory.client.NativeScreenMargins;

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
                        check(layout.chestColumns == budget.chestColumns, "stable full-width chest grid " + label);
                        check(layout.chestColumns * 18 + 14 <= layout.width, "chest grid fits its frame " + label);
                        check(layout.width <= screen[0] && layout.height <= screen[1], "window bounds " + label);
                        check(layout.columns <= columns && layout.rows <= rows, "chosen layout limits " + label);
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
            defaults.sideBySide && defaults.columns == 9 && defaults.rows <= 8,
            "external baseline stays nine-wide and fits its rows to the screen");
        AttachedChestLayout wide = new AttachedChestLayout(640, 360, 72, 18, 4, false, 590, 168);
        check(
            wide.sideBySide && wide.columns == 18 && wide.rows == 4,
            "explicit Wide layout exposes all 72 storage slots beside crafting");
        // A large framebuffer must not turn Compact into a monitor-wide chest.
        AttachedChestLayout compactBudget = new AttachedChestLayout(1920, 1080, 72, 9, 8, false, 0, 0);
        check(
            compactBudget.chestColumns == 19 && compactBudget.width == 372,
            "Compact chest width follows the inventory and crafting band");
        check(compactBudget.craftingX == 196, "crafting follows the explicit lower-group gap");
        AttachedChestLayout narrow = new AttachedChestLayout(400, 360, 72, 9, 8, false, 0, 0);
        check(narrow.columns == 9, "a narrower chest screen retains a complete nine-column rectangle");
        AttachedChestLayout small = new AttachedChestLayout(382, 360, 72, 9, 8, false, 0, 0);
        check(small.columns == 9, "the smallest side-by-side chest screen retains nine columns");
        AttachedChestLayout wideBudget = new AttachedChestLayout(1920, 1080, 72, 18, 4, false, 0, 0);
        check(
            wideBudget.chestColumns == 28 && wideBudget.width == 534,
            "Wide chest width follows the inventory and crafting band");
        AttachedChestLayout rectangular = new AttachedChestLayout(960, 540, 72, 9, 8, false, 0, 0, 243);
        check(rectangular.columns == 9 && 72 % rectangular.columns == 0, "player grid has no partial row");
        check(
            rectangular.chestColumns == 19,
            "243-slot chest uses the available band width instead of a narrow capacity divisor");
        AttachedChestLayout doubleChest = new AttachedChestLayout(960, 540, 72, 9, 8, false, 0, 0, 486);
        check(doubleChest.chestColumns == rectangular.chestColumns, "chest capacity does not change the grid width");
        for (int capacity : new int[] { 27, 54, 243, 486 }) {
            AttachedChestLayout budget = new AttachedChestLayout(960, 540, 72, 9, 8, false, 0, 0, capacity);
            check(
                budget.width == 372 && budget.chestColumns * 18 + 26 <= budget.width,
                "chest capacity cannot widen either frame: " + capacity);
        }
        for (int gap : new int[] { 0, 4, 32, 128 }) {
            AttachedChestLayout spacing = new AttachedChestLayout(1920, 1080, 72, 9, 8, false, 700, 150, 243, 8, gap);
            check(
                spacing.craftingX - spacing.playerX - spacing.playerWidth == gap,
                "content gap remains exact even when native chest is wider: " + gap);
        }
        AttachedChestLayout stacked = new AttachedChestLayout(320, 400, 72, 9, 8, false, 0, 0, 27, 8, 20);
        check(
            !stacked.sideBySide && stacked.playerY - stacked.craftingY - 80 == 20,
            "content gap also separates stacked sections");
        for (int[] screen : new int[][] { { 427, 240 }, { 960, 540 } })
            for (int[] margins : new int[][] { { 8, 28 }, { 16, 48 } }) {
                AttachedChestLayout budget = new AttachedChestLayout(
                    screen[0],
                    screen[1],
                    72,
                    9,
                    8,
                    false,
                    0,
                    0,
                    486,
                    8,
                    8,
                    margins[0],
                    margins[1]);
                int totalRows = (486 + budget.chestColumns - 1) / budget.chestColumns;
                int visibleRows = Math.max(1, Math.min(totalRows, (budget.chestHeightBudget - 24) / 18));
                int chestHeight = visibleRows * 18 + 24;
                AttachedChestLayout fitted = new AttachedChestLayout(
                    screen[0],
                    screen[1],
                    72,
                    9,
                    8,
                    false,
                    budget.chestColumns * 18 + 26,
                    chestHeight,
                    486,
                    8,
                    8,
                    margins[0],
                    margins[1]);
                int available = NativeScreenMargins.availableHeight(screen[1], margins[0], margins[1]);
                check(fitted.height <= available, "explicit margins reduce native/player row budgets");
                check(visibleRows < totalRows, "large chest remains reachable through native scrolling");
                check(fitted.playerY + fitted.playerHeight <= fitted.height, "player rows remain inside fitted bounds");
                for (int offset : new int[] { -4096, 0, 4096 }) {
                    int top = NativeScreenMargins.top(screen[1], fitted.height, offset, margins[0], margins[1]);
                    check(top >= margins[0], "panel Y offset respects top margin");
                    check(top + fitted.height <= screen[1] - margins[1], "panel Y offset respects bottom margin");
                    cases++;
                }
            }
        AttachedChestLayout descriptionMinimum = new AttachedChestLayout(
            320,
            240,
            72,
            9,
            8,
            true,
            0,
            42,
            486,
            8,
            8,
            8,
            28);
        check(descriptionMinimum.height == 222, "small description keeps its minimum content height");
        check(
            descriptionMinimum.descriptionY + 172 <= descriptionMinimum.height
                && descriptionMinimum.playerY + descriptionMinimum.playerHeight <= descriptionMinimum.height,
            "margin fallback never clips description or player content");
        check(
            NativeScreenMargins.top(240, descriptionMinimum.height, 4096, 8, 28) == 0,
            "insufficient free space preserves the bottom margin first");
        check(NativeScreenMargins.availableHeight(40, 128, 128) == 1, "height budget stays positive");
        check(NativeScreenMargins.top(240, 200, -4096, 8, 28) == 8, "extreme upward offset clamps");
        check(NativeScreenMargins.top(240, 200, 0, 8, 28) == 10, "center uses the reserved interval");
        check(NativeScreenMargins.top(240, 200, 4096, 8, 28) == 12, "extreme downward offset clamps");
        check(NativeScreenMargins.top(240, 220, 0, 8, 28) == 0, "bottom reservation wins a margin conflict");
        check(
            NativeScreenMargins.top(240, 260, -4096, 8, 28) == 0,
            "oversized native minimum anchors without resizing");
        cases += 8;
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
