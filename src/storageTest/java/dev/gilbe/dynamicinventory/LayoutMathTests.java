package dev.gilbe.dynamicinventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import dev.gilbe.dynamicinventory.client.BaublesPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryViewLayout;
import dev.gilbe.dynamicinventory.client.PlayerHeaderLayout;

/** Viewport invariants independent of particular preset dimensions. */
public final class LayoutMathTests {

    private LayoutMathTests() {}

    public static void run() throws Exception {
        int cases = 0;
        int headerCases = 0;
        int sidePanelCases = 0;
        int tabCases = 0;
        for (int[] window : new int[][] { { 320, 240 }, { 427, 240 }, { 854, 480 }, { 960, 540 }, { 1920, 1080 } }) {
            for (int columns = 3; columns <= 18; columns++) {
                for (int rows = 1; rows <= 12; rows++) {
                    InventoryViewLayout layout = new InventoryViewLayout(window[0], window[1], columns, rows);
                    InventoryViewLayout withTabs = new InventoryViewLayout(window[0], window[1] - 28, columns, rows);
                    if (withTabs.height + 28 > window[1])
                        throw new AssertionError("Storage and inventory tabs exceed the scaled window");
                    for (int equipment : new int[] { 4, 18, 21, 32, 48 }) {
                        BaublesPanelLayout tabPanel = new BaublesPanelLayout(window[1] - 28, equipment);
                        InventoryViewLayout tabStorage = new InventoryViewLayout(
                            window[0] - tabPanel.width - 4,
                            window[1] - 28,
                            columns,
                            rows);
                        if (tabStorage.width + tabPanel.width + 4 > window[0]
                            || Math.max(tabStorage.height, tabPanel.height) + 28 > window[1])
                            throw new AssertionError("Inventory tabs and side panel exceed the scaled window");
                        tabCases++;
                    }
                    for (int equipment : new int[] { 0, 4, 16, 20, 32, 48 }) {
                        PlayerHeaderLayout header = new PlayerHeaderLayout(layout.width, equipment);
                        if (header.craftingX < 88 || header.resultX + 16 > layout.width
                            || header.layoutButtonX + 16 > layout.width)
                            throw new AssertionError("Header controls overlap the player or panel edge");
                        if (header.inlineBaublesFits
                            && (PlayerHeaderLayout.BAUBLES_X + header.baublesColumns * 18 > header.craftingX - 8
                                || header.baublesColumns * 4 < equipment))
                            throw new AssertionError("Inline equipment overlaps crafting or omits slots");
                        if (equipment == 20 && layout.width >= 288 && !header.inlineBaublesFits)
                            throw new AssertionError("A wide header must show every RC-2 equipment slot");
                        if (equipment > 0 && !header.inlineBaublesFits) {
                            BaublesPanelLayout panel = new BaublesPanelLayout(window[1], equipment);
                            InventoryViewLayout beside = new InventoryViewLayout(
                                window[0] - panel.width - 4,
                                window[1],
                                columns,
                                rows);
                            if (beside.width + panel.width + 4 > window[0] || panel.height > window[1])
                                throw new AssertionError("Baubles side panel exceeds the scaled window");
                            boolean[] equipmentSeen = new boolean[equipment];
                            for (int offset = 0; offset <= panel.maxScroll(); offset++) {
                                for (int slot = 0; slot < equipment; slot++) {
                                    int row = slot / BaublesPanelLayout.COLUMNS - offset;
                                    if (row < 0 || row >= panel.visibleRows) continue;
                                    int x = 8 + slot % BaublesPanelLayout.COLUMNS * 18;
                                    int y = 8 + row * 18;
                                    if (x + 16 > panel.width || y + 16 > panel.height)
                                        throw new AssertionError("Equipment lies outside its side panel");
                                    equipmentSeen[slot] = true;
                                }
                            }
                            for (boolean reached : equipmentSeen)
                                if (!reached) throw new AssertionError("Side panel cannot reach equipment slot");
                            sidePanelCases++;
                        }
                        headerCases++;
                    }
                    if (layout.width > window[0] || layout.height > window[1] || layout.visibleRows < 1)
                        throw new AssertionError("Viewport exceeds supported scaled window");
                    boolean[] seen = new boolean[dev.gilbe.dynamicinventory.inventory.InventoryLayout.storageSlots()];
                    for (int offset = 0; offset <= layout.maxScroll(); offset++) {
                        for (int slot = 0; slot
                            < dev.gilbe.dynamicinventory.inventory.InventoryLayout.storageSlots(); slot++) {
                            int row = slot / layout.columns - offset;
                            if (row < 0 || row >= layout.visibleRows) continue;
                            int x = layout.gridX + slot % layout.columns * 18;
                            int y = 84 + row * 18;
                            if (x < 0 || x + 16 > layout.width || y < 84 || y + 16 >= layout.hotbarY)
                                throw new AssertionError("Visible slot overlaps hotbar or panel edge");
                            seen[slot] = true;
                        }
                    }
                    for (boolean reached : seen)
                        if (!reached) throw new AssertionError("Storage slot cannot be reached by scrolling");
                    cases++;
                }
            }
        }
        Files.write(
            Paths.get("diagnostics/dynamicinventory/layout-math.json"),
            ("{\"success\":true,\"cases\":" + cases
                + ",\"headerCases\":"
                + headerCases
                + ",\"sidePanelCases\":"
                + sidePanelCases
                + ",\"tabCases\":"
                + tabCases
                + ",\"smallestScaledWindow\":\"320x240\"}").getBytes(StandardCharsets.UTF_8));
    }
}
