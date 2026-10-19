package dev.gilbe.dynamicinventory.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Local presentation preferences, never a server inventory capacity setting. */
public final class InventoryViewSettings {

    public static final int DEFAULT_COLUMNS = 9;
    public static final int DEFAULT_ROWS = 8;
    public static int columns = DEFAULT_COLUMNS;
    public static int rows = DEFAULT_ROWS;
    public static boolean expandMachineInventory = true;
    public static boolean compactNativePanels = false;
    public static boolean mui2Presentation = false;
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
        expandMachineInventory = configuration
            .get(
                "layout",
                "expandMachineInventory",
                true,
                "Expand machine inventories vertically when possible; otherwise use their native scroll view.")
            .getBoolean(true);
        compactNativePanels = configuration
            .get(
                "layout",
                "compactNativePanels",
                false,
                "Keep native machine panels compact instead of joining them to the player inventory panel.")
            .getBoolean(false);
        mui2Presentation = configuration
            .get(
                "layout",
                "mui2Presentation",
                false,
                "Experimental MUI2 panel and slot-background widgets. Native containers and item input remain in use.")
            .getBoolean(false);
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

    public static void setMachineInventoryExpanded(boolean expanded) {
        expandMachineInventory = expanded;
        if (configuration != null) {
            configuration.get("layout", "expandMachineInventory", true)
                .set(expanded);
            configuration.save();
        }
    }

    public static void setCompactNativePanels(boolean compact) {
        compactNativePanels = compact;
        if (configuration != null) {
            configuration.get("layout", "compactNativePanels", false)
                .set(compact);
            configuration.save();
        }
    }

    public static void setMui2Presentation(boolean enabled) {
        mui2Presentation = enabled && InventoryPresentation.available();
        if (configuration != null) {
            configuration.get("layout", "mui2Presentation", false)
                .set(mui2Presentation);
            configuration.save();
        }
    }
}
