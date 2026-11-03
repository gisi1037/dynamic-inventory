package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;

import dev.gilbe.dynamicinventory.container.PanelTransferTargets;

public final class PanelTransferTargetsFixtures {

    private PanelTransferTargetsFixtures() {}

    public static void main(String[] args) {
        // Real container IDs need not be contiguous, ordered, or start at zero.
        int[] hotbar = { 270, 271, 272, 273, 274, 275, 276, 277, 278 };
        int[] main = { 252, 253, 254, 279, 280, 281 };
        check(PanelTransferTargets.quickMove(270, hotbar, main), main);
        check(PanelTransferTargets.quickMove(280, hotbar, main), hotbar);
        check(
            PanelTransferTargets.quickMove(243, hotbar, main),
            new int[] { 270, 271, 272, 273, 274, 275, 276, 277, 278, 252, 253, 254, 279, 280, 281 });
        check(PanelTransferTargets.quickMove(270, hotbar, new int[0]), new int[0]);
        int[] result = PanelTransferTargets.quickMove(280, hotbar, main);
        result[0] = -1;
        if (hotbar[0] != 270) throw new AssertionError("Routing must not mutate canonical IDs");
        int[] rows = java.util.stream.IntStream.range(400, 427)
            .toArray();
        check(
            PanelTransferTargets.quickMove(400, hotbar, rows, 9),
            concat(hotbar, java.util.Arrays.copyOfRange(rows, 18, 27)));
        check(
            PanelTransferTargets.quickMove(426, hotbar, rows, 9),
            concat(hotbar, java.util.Arrays.copyOfRange(rows, 0, 18)));
        check(
            PanelTransferTargets.quickMove(400, hotbar, rows, 5),
            concat(hotbar, java.util.Arrays.copyOfRange(rows, 15, 27)));
        check(
            PanelTransferTargets.quickMove(426, hotbar, rows, 5),
            concat(hotbar, java.util.Arrays.copyOfRange(rows, 0, 15)));
        System.out.println("PASS Main/hotbar-only transfers, satellite returns, empty Main and noncontiguous IDs");
    }

    private static int[] concat(int[] first, int[] last) {
        int[] result = Arrays.copyOf(first, first.length + last.length);
        System.arraycopy(last, 0, result, first.length, last.length);
        return result;
    }

    private static void check(int[] actual, int[] expected) {
        if (!Arrays.equals(actual, expected)) throw new AssertionError(Arrays.toString(actual));
    }
}
