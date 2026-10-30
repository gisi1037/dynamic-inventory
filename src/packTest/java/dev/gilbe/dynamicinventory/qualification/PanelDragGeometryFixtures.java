package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Alignment;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Runs without Minecraft so drag placement can also be checked without restarting a live instance. */
public final class PanelDragGeometryFixtures {

    private PanelDragGeometryFixtures() {}

    public static void main(String[] args) {
        run();
        System.out.println("PASS nearest-edge dragging, weak alignment, and exhaustive multi-obstacle placement");
    }

    public static void run() {
        Rect viewport = new Rect(0, 0, 600, 400);
        List<Rect> machine = Collections.singletonList(new Rect(250, 150, 100, 100));
        at(InventoryPanelLayout.snapDrag(new Rect(340, 170, 100, 60), viewport, machine), 351, 170);
        at(InventoryPanelLayout.snapDrag(new Rect(160, 170, 100, 60), viewport, machine), 149, 170);
        at(InventoryPanelLayout.snapDrag(new Rect(260, 100, 100, 60), viewport, machine), 260, 89);
        at(InventoryPanelLayout.snapDrag(new Rect(260, 240, 100, 60), viewport, machine), 260, 251);
        at(InventoryPanelLayout.snapDrag(new Rect(450, 170, 100, 60), viewport, machine), 450, 170);
        check(
            InventoryPanelLayout.snapDrag(new Rect(0, 0, 700, 60), viewport, machine) == null,
            "dragging must not silently shrink a panel");
        at(InventoryPanelLayout.alignDrag(new Rect(270, 170, 100, 60), viewport, machine, 3).bounds, 270, 170);
        for (int margin : new int[] { 4, 8, 18 }) for (int columns = 1; columns <= 18; columns++) {
            InventoryPanelLayout narrow = InventoryPanelLayout.fit(
                new Rect(0, 0, columns * 18 + margin * 2, 64),
                viewport,
                Collections.emptyList(),
                72,
                margin,
                true);
            check(narrow != null && narrow.columns == columns, "Main can use every grid width, including one column");
            java.util.Set<String> positions = new java.util.HashSet<>();
            for (int slot = 0; slot < 9; slot++) {
                int x = narrow.hotbarSlotX(slot), y = narrow.hotbarSlotY(slot);
                check(x > narrow.bounds.x && x + 17 <= narrow.bounds.right(), "wrapped hotbar stays within width");
                check(y > narrow.bounds.y && y + 17 <= narrow.bounds.bottom(), "wrapped hotbar stays within height");
                check(positions.add(x + "," + y), "all nine hotbar slots retain separate positions");
            }
        }
        Rect touching = new Rect(0, 10, 100, 60), neighbor = new Rect(100, 0, 100, 200);
        Rect shrunk = InventoryPanelLayout
            .constrainGesture(touching, new Rect(0, 10, 80, 60), viewport, Collections.singletonList(neighbor), 26, 40);
        check(shrunk.width == 80, "touching panel can shrink away from its neighbor");
        Rect parallel = InventoryPanelLayout.constrainGesture(
            touching,
            new Rect(0, 10, 100, 80),
            viewport,
            Collections.singletonList(neighbor),
            26,
            40);
        check(parallel.height == 80, "touching panel can resize parallel to the shared edge");
        Rect blocked = InventoryPanelLayout.constrainGesture(
            touching,
            new Rect(0, 10, 120, 60),
            viewport,
            Collections.singletonList(neighbor),
            26,
            40);
        check(blocked.width == 100, "touching panel cannot grow through its neighbor");
        Rect resizeStart = new Rect(50, 50, 124, 116);
        Rect snapped = InventoryPanelLayout
            .snapResize(resizeStart, new Rect(50, 50, 132, 104), 10, viewport, Collections.emptyList(), 8, false, 72);
        check(snapped.width == 124 && snapped.height == 98, "Shift picks the nearest complete columns and rows");
        at(snapped, 50, 50);
        Rect topLeft = InventoryPanelLayout
            .snapResize(resizeStart, new Rect(42, 38, 132, 128), 5, viewport, Collections.emptyList(), 8, false, 72);
        check(
            topLeft.right() == resizeStart.right() && topLeft.bottom() == resizeStart.bottom(),
            "Shift resizing fixes the opposite corner");
        Rect mainSnap = InventoryPanelLayout
            .snapResize(resizeStart, new Rect(50, 50, 132, 125), 10, viewport, Collections.emptyList(), 8, true, 72);
        check(mainSnap.width == 124 && mainSnap.height == 118, "Shift includes the wrapped hotbar overhead");
        Rect constrainedSnap = InventoryPanelLayout.snapResize(
            resizeStart,
            new Rect(50, 50, 150, 116),
            10,
            viewport,
            Collections.singletonList(new Rect(180, 0, 40, 300)),
            8,
            false,
            72);
        check(
            constrainedSnap.width == 124 && constrainedSnap.right() < 180,
            "neighbor collision chooses a smaller whole grid, not a clipped partial column");
        for (int corner : new int[] { 5, 6, 9, 10 }) for (boolean hotbar : new boolean[] { false, true }) {
            Rect result = InventoryPanelLayout.snapResize(
                resizeStart,
                new Rect(40, 40, 140, 140),
                corner,
                viewport,
                Collections.emptyList(),
                8,
                hotbar,
                72);
            check(result != null && (result.width - 16) % 18 == 0, "every corner snaps full columns");
            check(
                (result.height - InventoryPanelLayout.minimumHeight(result.width, 8, hotbar)) % 18 == 0,
                "every corner snaps full rows with the correct hotbar space");
        }

        List<Rect> reference = Collections.singletonList(new Rect(200, 50, 100, 100));
        Alignment left = InventoryPanelLayout.alignDrag(new Rect(202, 200, 60, 40), viewport, reference, 3);
        at(left.bounds, 200, 200);
        check(left.guideX == 200 && left.guideY == Integer.MIN_VALUE, "only the aligned axis has a guide");
        at(InventoryPanelLayout.alignDrag(new Rect(238, 200, 60, 40), viewport, reference, 3).bounds, 240, 200);
        at(InventoryPanelLayout.alignDrag(new Rect(222, 200, 60, 40), viewport, reference, 3).bounds, 220, 200);
        at(InventoryPanelLayout.alignDrag(new Rect(420, 52, 60, 40), viewport, reference, 3).bounds, 420, 50);
        at(InventoryPanelLayout.alignDrag(new Rect(420, 82, 60, 40), viewport, reference, 3).bounds, 420, 80);
        at(InventoryPanelLayout.alignDrag(new Rect(205, 205, 60, 40), viewport, reference, 3).bounds, 205, 205);
        at(InventoryPanelLayout.alignDrag(new Rect(2, 205, 60, 40), viewport, reference, 3).bounds, 0, 205);

        Rect small = new Rect(0, 0, 80, 70);
        List<Rect> obstacles = Arrays.asList(new Rect(25, 20, 20, 30), new Rect(50, 5, 15, 20));
        for (int x = -10; x <= 90; x += 5) for (int y = -10; y <= 80; y += 5) {
            Rect request = new Rect(x, y, 20, 14);
            Rect actual = InventoryPanelLayout.snapDrag(request, small, obstacles);
            long best = Long.MAX_VALUE;
            for (int px = 0; px <= 60; px++) for (int py = 0; py <= 56; py++) {
                Rect candidate = new Rect(px, py, 20, 14);
                if (clear(candidate, obstacles)) best = Math.min(best, distance(candidate, request));
            }
            check(actual != null && actual.width == 20 && actual.height == 14, "panel dimensions preserved");
            check(clear(actual, obstacles), "one-pixel clearance around every obstacle");
            check(distance(actual, request) == best, "globally nearest valid position, regardless of approach side");
        }
    }

    private static boolean clear(Rect panel, List<Rect> obstacles) {
        for (Rect obstacle : obstacles)
            if (panel.intersects(new Rect(obstacle.x - 1, obstacle.y - 1, obstacle.width + 2, obstacle.height + 2)))
                return false;
        return true;
    }

    private static long distance(Rect a, Rect b) {
        return (long) (a.x - b.x) * (a.x - b.x) + (long) (a.y - b.y) * (a.y - b.y);
    }

    private static void at(Rect rect, int x, int y) {
        check(rect != null && rect.x == x && rect.y == y, "expected position " + x + ", " + y);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
