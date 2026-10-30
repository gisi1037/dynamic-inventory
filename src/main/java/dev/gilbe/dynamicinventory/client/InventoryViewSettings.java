package dev.gilbe.dynamicinventory.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Local presentation preferences, never a server inventory capacity setting. */
public final class InventoryViewSettings {

    public static final int DEFAULT_COLUMNS = 9;
    public static final int DEFAULT_ROWS = 8;
    public static int columns = DEFAULT_COLUMNS;
    public static int rows = DEFAULT_ROWS;
    public static boolean automaticStorageScroll = true;
    public static boolean centerLastRow = true;
    public static boolean hotbarPanelVisible = false;
    public static int terminalSmallRows = 3;
    public static int terminalTallMinimumRows = 6;
    private static Configuration configuration;

    private InventoryViewSettings() {}

    public static void initialize(File file) {
        configuration = new Configuration(file);
        boolean legacyDefault = configuration.hasKey("layout", "visibleRows")
            && !configuration.hasKey("layout", "defaultsVersion");
        columns = configuration.getInt(
            "columns",
            "layout",
            DEFAULT_COLUMNS,
            3,
            18,
            "Preferred storage columns; automatically limited to fit the scaled window.");
        rows = configuration.getInt(
            "visibleRows",
            "layout",
            DEFAULT_ROWS,
            1,
            12,
            "Preferred visible storage rows; remaining rows scroll. Does not change capacity.");
        // Retire superseded preferences without carrying their old behavior into panel layouts.
        configuration.getCategory("layout")
            .remove("expandMachineInventory");
        configuration.getCategory("layout")
            .remove("compactNativePanels");
        configuration.getCategory("layout")
            .remove("mui2Presentation");
        automaticStorageScroll = configuration.get(
            "layout",
            "automaticStorageScroll",
            true,
            "Use a compact scroll panel for simple drawing-only GUIs backed by an already integrated vanilla storage container.")
            .getBoolean(true);
        hotbarPanelVisible = configuration
            .getBoolean("showPanel", "hotbars", false, "Show the reserved hotbar rows in their own panel.");
        centerLastRow = configuration
            .get(
                "layout",
                "centerLastRow",
                true,
                "Center the incomplete final row of the main player inventory grid; false keeps it left-aligned.")
            .getBoolean(true);
        terminalSmallRows = configuration.getInt(
            "smallRows",
            "ae2Terminal",
            3,
            3,
            24,
            "Fixed network-browser rows in AE2 Small mode; player rows scroll when necessary.");
        terminalTallMinimumRows = configuration.getInt(
            "tallMinimumRows",
            "ae2Terminal",
            6,
            3,
            24,
            "Reserve at least this many browser rows in Tall mode when space permits, then fit player rows and expand the browser into remaining space.");
        if (legacyDefault && columns == 18 && rows == 3) {
            // Only migrate the original, unversioned default. Saved modern/custom
            // preferences remain the user's choice when the factory default changes.
            columns = DEFAULT_COLUMNS;
            rows = DEFAULT_ROWS;
            configuration.get("layout", "columns", DEFAULT_COLUMNS)
                .set(columns);
            configuration.get("layout", "visibleRows", DEFAULT_ROWS)
                .set(rows);
        }
        configuration.get("layout", "defaultsVersion", 2)
            .set(2);
        if (configuration.hasChanged()) configuration.save();
    }

    public static void set(int preferredColumns, int preferredRows) {
        columns = Math.max(3, Math.min(18, preferredColumns));
        rows = Math.max(1, Math.min(12, preferredRows));
        if (configuration != null) {
            configuration.get("layout", "columns", DEFAULT_COLUMNS)
                .set(columns);
            configuration.get("layout", "visibleRows", DEFAULT_ROWS)
                .set(rows);
            configuration.save();
        }
    }

    public static void setCenterLastRow(boolean centered) {
        centerLastRow = centered;
        if (configuration != null) {
            configuration.get("layout", "centerLastRow", true)
                .set(centered);
            configuration.save();
        }
    }

    public static void setHotbarPanelVisible(boolean visible) {
        hotbarPanelVisible = visible;
        if (configuration != null) {
            configuration.get("hotbars", "showPanel", false)
                .set(visible);
            configuration.save();
        }
    }

    /** Global client preferences; a native terminal rebuilds its browser on its next opening. */
    public static void setTerminalRows(int small, int tallMinimum) {
        terminalSmallRows = Math.max(3, Math.min(24, small));
        terminalTallMinimumRows = Math.max(3, Math.min(24, tallMinimum));
        if (configuration != null) {
            configuration.get("ae2Terminal", "smallRows", 3)
                .set(terminalSmallRows);
            configuration.get("ae2Terminal", "tallMinimumRows", 6)
                .set(terminalTallMinimumRows);
            configuration.save();
        }
    }

}
