package dev.gilbe.dynamicinventory.container;

import java.util.Arrays;

/** Panel membership chooses ordinary player destinations; machine slots never enter these lists. */
public final class PanelTransferTargets {

    private PanelTransferTargets() {}

    public static int[] quickMove(int source, int[] hotbar, int[] mainStorage) {
        return quickMove(source, hotbar, mainStorage, 9);
    }

    public static int[] quickMove(int source, int[] hotbar, int[] mainStorage, int columns) {
        for (int id : hotbar) if (id == source) return mainStorage.clone();
        for (int i = 0; i < mainStorage.length; i++) if (mainStorage[i] == source) {
            // Divide at a row boundary, including offscreen rows and a partial last row.
            int rows = (mainStorage.length + columns - 1) / columns;
            int split = Math.min(mainStorage.length, (rows + 1) / 2 * columns);
            boolean sourceAbove = i < split;
            int[] result = Arrays.copyOf(hotbar, hotbar.length + (sourceAbove ? mainStorage.length - split : split));
            if (sourceAbove) {
                // Start with the bottom row, preserving left-to-right placement within each row.
                int destination = hotbar.length;
                for (int end = mainStorage.length; end > split;) {
                    int start = (end - 1) / columns * columns;
                    System.arraycopy(mainStorage, start, result, destination, end - start);
                    destination += end - start;
                    end = start;
                }
            } else System.arraycopy(mainStorage, 0, result, hotbar.length, split);
            return result;
        }
        int[] result = Arrays.copyOf(hotbar, hotbar.length + mainStorage.length);
        System.arraycopy(mainStorage, 0, result, hotbar.length, mainStorage.length);
        return result;
    }
}
