package dev.gilbe.dynamicinventory.compat;

/**
 * HarvestCraft 1.3.15-GTNH's ordinary processing inventories use vanilla slot synchronization and
 * progress properties. Keep their recipe/bait checks and custom result-slot callbacks in native code.
 * Market, cutting board and pot have different interaction contracts and are deliberately separate.
 */
public final class HarvestCraftCompatibility {

    private HarvestCraftCompatibility() {}

    public static void registerInventoryAdapters() {
        register("churn", "Churn", 39);
        register("oven", "Oven", 39);
        register("quern", "Quern", 39);
        register("animal-trap", "PamAnimalTrap", 55);
        register("fish-trap", "PamFishTrap", 55);
        register("apiary", "PamApiary", 55);
        register("grinder", "PamGrinder", 39);
        register("presser", "PamPresser", 39);
    }

    private static void register(String id, String type, int nativeSlots) {
        NativeMachineAdapters.machine(
            "dynamicinventory:harvestcraft/" + id,
            "com.pam.harvestcraft.Container" + type,
            "com.pam.harvestcraft.Gui" + type,
            nativeSlots);
    }
}
