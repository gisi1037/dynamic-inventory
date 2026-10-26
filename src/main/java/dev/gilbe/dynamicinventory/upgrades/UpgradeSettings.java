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
    public static final String[] BELT_TIERS = { "LV", "HV", "IV", "ZPM", "UHV" };
    public static final int[] tierSlots = new int[5];
    public static boolean placeholderRecipes;

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
        int[] defaults = { beltSlots, 27, 54, 108, 189 };
        tierSlots[0] = beltSlots;
        for (int tier = 1; tier < tierSlots.length; tier++) tierSlots[tier] = config.getInt(
            "bonusSlots" + BELT_TIERS[tier],
            "belt",
            defaults[tier],
            1,
            189,
            "Temporary storage bonus for this belt tier; only one belt applies.");
        placeholderRecipes = config.getBoolean(
            "placeholderRecipes",
            "belt",
            true,
            "Register provisional tiered GTNH crafting recipes when GregTech and Baubles are installed.");
        // Keep item IDs registered even when disabled, so existing saved stacks remain valid.
        // Legacy inventory_belt remains the LV item so existing saved equipment is preserved.
        for (String category : new String[] { "modules", "belt" }) config.getCategory(category)
            .setRequiresMcRestart(true);
        if (config.hasChanged()) config.save();
    }
}
