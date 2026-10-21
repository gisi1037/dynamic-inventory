package dev.gilbe.dynamicinventory.upgrades;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Server rules, read once at startup. Client preferences never authorize capacity changes. */
public final class UpgradeSettings {

    public static boolean modulesEnabled;
    public static boolean beltsEnabled;
    public static int moduleSlots;
    public static int moduleLimit;
    public static int beltSlots;

    private UpgradeSettings() {}

    public static void load(File directory) {
        Configuration config = new Configuration(new File(directory, "dynamicinventory-upgrades.cfg"));
        modulesEnabled = config.getBoolean("enabled", "modules", true, "Allow permanent capacity modules.");
        moduleSlots = config.getInt("slotsPerModule", "modules", 9, 1, 189, "Storage slots added per consumed module.");
        moduleLimit = config.getInt("modulesPerPlayer", "modules", 16, 0, 189, "Maximum modules consumed per player.");
        beltsEnabled = config.getBoolean("enabled", "belt", true, "Enable the optional Baubles inventory belt.");
        beltSlots = config.getInt(
            "bonusSlots",
            "belt",
            9,
            1,
            189,
            "Storage slots while wearing a belt. Multiple belts do not stack.");
        // Keep item IDs registered even when disabled, so existing saved stacks remain valid.
        // Recipes deliberately belong to the pack: these items do not bypass GTNH progression.
        for (String category : new String[] { "modules", "belt" }) config.getCategory(category)
            .setRequiresMcRestart(true);
        if (config.hasChanged()) config.save();
    }
}
