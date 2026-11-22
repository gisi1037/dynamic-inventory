package dev.gilbe.dynamicinventory.client;

import java.util.Arrays;
import java.util.function.IntPredicate;

import appeng.items.contents.PinList;

/** Maps stable AE2 pin IDs to cells. Resizing adds or removes only the right or bottom edge. */
final class AePinsGrid {

    static final int MAX_COLUMNS = 24;
    private static final int MAX_CELLS = PinList.TOTAL_SLOTS + MAX_COLUMNS;
    final int columns;
    final int[] slots;
    private final int[] inlineOrder;

    private AePinsGrid(int columns, int[] slots) {
        this.columns = columns;
        this.slots = slots;
        inlineOrder = new int[PinList.TOTAL_SLOTS];
        boolean[] used = used();
        int crafting = 0, player = PinList.PLAYER_OFFSET;
        for (int id : slots) if (id >= 0) inlineOrder[id < PinList.PLAYER_OFFSET ? crafting++ : player++] = id;
        for (int id = 0; id < used.length; id++)
            if (!used[id]) inlineOrder[id < PinList.PLAYER_OFFSET ? crafting++ : player++] = id;
    }

    int inlineId(int index) {
        return index < inlineOrder.length ? inlineOrder[index] : index;
    }

    static AePinsGrid load(int columns, int[] slots) {
        if (columns < 1 || columns > MAX_COLUMNS
            || slots.length < columns
            || slots.length > MAX_CELLS
            || slots.length % columns != 0) return null;
        boolean[] seen = new boolean[PinList.TOTAL_SLOTS];
        for (int slot : slots) {
            if (slot == -1) continue;
            if (slot < 0 || slot >= seen.length || seen[slot]) return null;
            seen[slot] = true;
        }
        return new AePinsGrid(columns, slots);
    }

    static AePinsGrid of(int columns, int[] slots) {
        return new AePinsGrid(columns, padded(slots, columns));
    }

    private static int[] padded(int[] slots, int columns) {
        int length = (slots.length + columns - 1) / columns * columns;
        int[] result = Arrays.copyOf(slots, length);
        Arrays.fill(result, slots.length, length, -1);
        return result;
    }

    /** A different terminal can already have pins outside the saved grid. Append their cells. */
    AePinsGrid includeOccupied(IntPredicate occupied) {
        boolean[] used = used();
        int[] result = Arrays.copyOf(slots, slots.length + PinList.TOTAL_SLOTS);
        int length = slots.length;
        int gap = 0;
        for (int id = 0; id < used.length; id++) {
            if (used[id] || !occupied.test(id)) continue;
            while (gap < length && result[gap] != -1) gap++;
            if (gap == length) length++;
            result[gap++] = id;
        }
        return length == slots.length && gap == 0 ? this : of(columns, Arrays.copyOf(result, length));
    }

    AePinsGrid resize(boolean column, boolean add, IntPredicate occupied) {
        int rows = slots.length / columns;
        int delta = add ? 1 : -1;
        int width = column ? columns + delta : columns;
        int height = column ? rows : rows + delta;
        if (width < 1 || width > MAX_COLUMNS || height < 1 || width * height > MAX_CELLS) return null;
        if (!add) for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
            int id = slots[row * columns + col];
            if ((row >= height || col >= width) && id >= 0 && occupied.test(id)) return null;
        }
        boolean[] used = used();
        int next = PinList.PLAYER_OFFSET;
        boolean added = false;
        int[] resized = new int[width * height];
        Arrays.fill(resized, -1);
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) {
            int cell = row * width + col;
            if (row < rows && col < columns) resized[cell] = slots[row * columns + col];
            else {
                while (next < used.length && used[next]) next++;
                if (next < used.length) {
                    resized[cell] = next++;
                    added = true;
                }
            }
        }
        return add && !added ? null : new AePinsGrid(width, resized);
    }

    private boolean[] used() {
        boolean[] used = new boolean[PinList.TOTAL_SLOTS];
        for (int id : slots) if (id >= 0) used[id] = true;
        return used;
    }
}
