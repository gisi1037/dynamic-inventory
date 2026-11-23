package dev.gilbe.dynamicinventory.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import dev.gilbe.dynamicinventory.inventory.HotbarBanks;

/** Local presentation preferences, never a server inventory capacity setting. */
public final class InventoryViewSettings {

    public static final int DEFAULT_COLUMNS = 9;
    public static final int DEFAULT_ROWS = 8;
    public static int columns = DEFAULT_COLUMNS;
    public static int rows = DEFAULT_ROWS;
    public static boolean automaticStorageScroll = true;
    public static boolean centerLastRow = true;
    public static boolean hotbarPanelVisible = false;
    static int hotbarPanelColumns = 9;
    static int hotbarPanelRows = HotbarBanks.MAX_BANKS;
    static int hotbarPanelX = -1, hotbarPanelY = -1;
    private static final String TERMINAL_CATEGORY = "ae2terminal";
    private static final String[] TERMINAL_ROW_KEYS = { "smallRows", "tallRows", "fullRows" };
    private static final int[] terminalRows = { 3, 6, 6 };
    private static final int[] craftingTerminalRows = { 3, 6, 6 };
    public static int playerSectionAnchorX = -1, playerSectionAnchorY = -1;
    static PinsPanelGeometry legacyPinsGeometry = PinsPanelGeometry.DEFAULT;
    static AePinsGrid pinsGrid;
    static boolean pinsWirelessOnly, pinsReturnMatching;
    private static Configuration configuration;

    private InventoryViewSettings() {}

    public static void initialize(File file) {
        configuration = new Configuration(file);
        legacyPinsGeometry = new PinsPanelGeometry(
            new InventoryPanelLayout.Rect(
                configuration.getInt("x", "ae2Pins", -1, -1, 10000, "Legacy pinned panel horizontal position."),
                configuration.getInt("y", "ae2Pins", -1, -1, 10000, "Legacy pinned panel vertical position."),
                configuration.getInt("width", "ae2Pins", 178, 72, 450, "Legacy pinned panel width."),
                configuration.getInt("height", "ae2Pins", 101, 43, 450, "Legacy pinned panel height.")),
            null);
        pinsGrid = AePinsGrid.load(
            configuration
                .getInt("gridColumns", "ae2Pins", 0, 0, AePinsGrid.MAX_COLUMNS, "Columns in the custom pins grid."),
            configuration.get("ae2Pins", "gridSlots", new int[0])
                .getIntList());
        pinsReturnMatching = configuration.getBoolean(
            "returnMatching",
            "ae2Pins",
            false,
            "Shift-click matching inventory items into the network displayed in the Pins panel.");
        pinsWirelessOnly = configuration.getBoolean(
            "wirelessOnly",
            "ae2Pins",
            false,
            "Use the linked wireless terminal for the Pins panel even when another terminal is open.");
        playerSectionAnchorX = configuration.getInt(
            "anchorX",
            "playerSection",
            -1,
            -1,
            10000,
            "Detached character/crafting section position; -1 uses its default.");
        playerSectionAnchorY = configuration.getInt(
            "anchorY",
            "playerSection",
            -1,
            -1,
            10000,
            "Detached character/crafting section position; -1 uses its default.");
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
        hotbarPanelX = configuration.getInt("panelX", "hotbars", -1, -1, 10000, "Hotbar panel horizontal position.");
        hotbarPanelY = configuration.getInt("panelY", "hotbars", -1, -1, 10000, "Hotbar panel vertical position.");
        hotbarPanelColumns = configuration
            .getInt("panelColumns", "hotbars", 9, 1, 9, "Preferred number of columns in the visible hotbar panel.");
        hotbarPanelRows = configuration.getInt(
            "panelRows",
            "hotbars",
            HotbarBanks.MAX_BANKS,
            1,
            HotbarBanks.MAX_BANKS,
            "Preferred number of complete hotbars visible before scrolling.");
        centerLastRow = configuration
            .get(
                "layout",
                "centerLastRow",
                true,
                "Center the incomplete final row of the main player inventory grid; false keeps it left-aligned.")
            .getBoolean(true);
        var terminal = configuration.getCategory(TERMINAL_CATEGORY);
        var legacyTall = terminal.remove("tallMinimumRows");
        int tallDefault = legacyTall == null ? 6 : Math.max(3, Math.min(24, legacyTall.getInt(6)));
        for (int style = 0; style < TERMINAL_ROW_KEYS.length; style++) {
            terminalRows[style] = configuration.getInt(
                terminalRowKey(false, style),
                TERMINAL_CATEGORY,
                style == 0 ? 3 : tallDefault,
                3,
                24,
                "Fixed network-browser row count for normal AE2 terminals.");
            craftingTerminalRows[style] = configuration.getInt(
                terminalRowKey(true, style),
                TERMINAL_CATEGORY,
                terminalRows[style],
                3,
                24,
                "Fixed network-browser row count for AE2 crafting terminals.");
        }
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

    static void setPinsGrid(AePinsGrid grid) {
        pinsGrid = grid;
        configuration.get("ae2Pins", "gridColumns", 0)
            .set(grid.columns);
        configuration.get("ae2Pins", "gridSlots", new int[0])
            .set(grid.slots);
        configuration.save();
    }

    static void setPinsWirelessOnly(boolean wirelessOnly) {
        pinsWirelessOnly = wirelessOnly;
        configuration.get("ae2Pins", "wirelessOnly", false)
            .set(wirelessOnly);
        configuration.save();
    }

    static void togglePinsReturnMatching() {
        pinsReturnMatching = !pinsReturnMatching;
        configuration.get("ae2Pins", "returnMatching", false)
            .set(pinsReturnMatching);
        configuration.save();
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

    public static void setPlayerSectionPosition(int x, int y) {
        playerSectionAnchorX = Math.max(-1, Math.min(10000, x));
        playerSectionAnchorY = Math.max(-1, Math.min(10000, y));
        if (configuration != null) {
            configuration.get("playerSection", "anchorX", -1)
                .set(playerSectionAnchorX);
            configuration.get("playerSection", "anchorY", -1)
                .set(playerSectionAnchorY);
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

    static void setHotbarPanelSize(int columns, int rows) {
        hotbarPanelColumns = columns;
        hotbarPanelRows = rows;
        if (configuration != null) {
            configuration.get("hotbars", "panelColumns", 9)
                .set(columns);
            configuration.get("hotbars", "panelRows", HotbarBanks.MAX_BANKS)
                .set(rows);
            configuration.save();
        }
    }

    static void setHotbarPanelPosition(int x, int y) {
        hotbarPanelX = x;
        hotbarPanelY = y;
        if (configuration != null) {
            configuration.get("hotbars", "panelX", -1)
                .set(x);
            configuration.get("hotbars", "panelY", -1)
                .set(y);
            configuration.save();
        }
    }

    public static int terminalRows(boolean crafting, int style) {
        return (crafting ? craftingTerminalRows : terminalRows)[style];
    }

    public static void setTerminalRows(boolean crafting, int style, int count) {
        int value = Math.max(3, Math.min(24, count));
        (crafting ? craftingTerminalRows : terminalRows)[style] = value;
        if (configuration != null) {
            configuration.get(TERMINAL_CATEGORY, terminalRowKey(crafting, style), value)
                .set(value);
            configuration.save();
        }
    }

    private static String terminalRowKey(boolean crafting, int style) {
        String key = TERMINAL_ROW_KEYS[style];
        return crafting ? "crafting" + Character.toUpperCase(key.charAt(0)) + key.substring(1) : key;
    }

}
