package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.InventoryViewLayout;
import dev.gilbe.dynamicinventory.client.PanelLayoutReference;
import dev.gilbe.dynamicinventory.client.PlayerHeaderLayout;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;
import dev.gilbe.dynamicinventory.client.PlayerSectionLayout;

/** Stable preset anchors and saved character-section positions without a Minecraft runtime. */
public final class PlayerSectionLayoutFixtures {

    private PlayerSectionLayoutFixtures() {}

    public static void main(String[] args) {
        reopenInventory();
        for (int[] screen : new int[][] { { 426, 240 }, { 640, 360 }, { 854, 480 }, { 1280, 720 } })
            for (int storage : new int[] { 27, 72, 144 }) {
                int tabs = 27, top = PlayerSectionLayout.defaultTop(screen[0], screen[1], tabs, storage);
                Rect viewport = new Rect(0, 8, screen[0], screen[1] - 36);
                Rect compact = null;
                for (int columns : new int[] { 9, 18 }) {
                    InventoryViewLayout view = new InventoryViewLayout(
                        screen[0],
                        screen[1] - tabs,
                        columns,
                        (storage + columns - 1) / columns,
                        storage);
                    Rect placed = PlayerSectionLayout.place(
                        viewport,
                        view.width,
                        tabs + PlayerHeaderLayout.DETACHED_HEIGHT,
                        (screen[0] - view.width) / 2,
                        top,
                        -1,
                        -1);
                    if (compact == null) compact = placed;
                    else check(placed.y == compact.y, "Wide/Compact must share their vertical anchor");
                    Rect moved = PlayerSectionLayout
                        .place(viewport, view.width, placed.height, placed.x, top, 2300, 6800);
                    int x = PlayerSectionLayout.anchor(moved.x, viewport.x, viewport.width - moved.width);
                    int y = PlayerSectionLayout.anchor(moved.y, viewport.y, viewport.height - moved.height);
                    Rect restored = PlayerSectionLayout.place(viewport, moved.width, moved.height, 0, 0, x, y);
                    check(moved.x == restored.x && moved.y == restored.y, "Saved position must round-trip exactly");
                    check(
                        moved.x >= viewport.x && moved.y >= viewport.y
                            && moved.right() <= viewport.right()
                            && moved.bottom() <= viewport.bottom(),
                        "Section stays visible");
                }
            }
        Rect obstacle = new Rect(160, 100, 90, 80), viewport = new Rect(0, 8, 640, 324);
        Rect safe = InventoryPanelLayout
            .snapDrag(new Rect(210, 110, 180, 117), viewport, java.util.Collections.singletonList(obstacle));
        check(
            safe != null && !safe.intersects(obstacle),
            "Dropping a character section cannot overlap inventory panels");
        System.out.println(
            "PASS shared Wide/Compact vertical anchor, saved section positions, viewport limits and drop clearance");
    }

    private static void reopenInventory() {
        // A fresh default was learned at a smaller GUI size before the global detach toggle.
        PanelLayoutReference origin = new PanelLayoutReference(new Rect(119, 42, 188, 90), new Rect(0, 8, 427, 204));
        Rect viewport = new Rect(0, 8, 851, 410), section = new Rect(336, 110, 178, 90);
        Rect requested = origin.adapt(PlayerPanelProfile.DEFAULT, section, viewport);
        java.util.List<Rect> obstacles = java.util.Arrays.asList(section, new Rect(336, 82, 55, 28));
        for (int reopen = 0; reopen < 3; reopen++) {
            InventoryPanelLayout fitted = InventoryPanelLayout.fit(requested, viewport, obstacles, 72, 8, true);
            // The subsequent clearance pass retains fitted bounds; the first fit must include tabs.
            fitted = InventoryPanelLayout.fit(fitted.bounds, viewport, obstacles, 72, 8, true);
            check(
                fitted != null && fitted.bounds.y >= section.bottom() + 1,
                "Reopening a detached default must keep Main below the character section");
            check(
                fitted.bounds.height == PlayerPanelProfile.DEFAULT.height,
                "Reopening cannot choose a short strip above the character when the full panel fits below");
        }
        Rect above = new Rect(section.x, 8, 178, 64), beside = new Rect(20, section.y, 178, 190);
        InventoryPanelLayout aboveFit = InventoryPanelLayout.fit(above, viewport, obstacles, 72, 8, true);
        InventoryPanelLayout besideFit = InventoryPanelLayout.fit(beside, viewport, obstacles, 72, 8, true);
        check(
            aboveFit.bounds.x == above.x && aboveFit.bounds.y == above.y,
            "An intentionally saved panel above the character stays above");
        check(
            besideFit.bounds.x == beside.x && besideFit.bounds.y == beside.y,
            "A saved panel beside the character keeps its position");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
