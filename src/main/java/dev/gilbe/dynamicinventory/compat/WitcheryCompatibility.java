package dev.gilbe.dynamicinventory.compat;

/** Reviewed block machines; held-item bags, rituals and custom interaction screens remain separate. */
public final class WitcheryCompatibility {

    private WitcheryCompatibility() {}

    public static void registerInventoryAdapters() {
        // Keep the mod's recipe/jar/fibre routing and SlotFurnace output callbacks.
        // These hosts directly extend Container and return null only when no items moved.
        register("distillery", "Distillery", 43);
        register("spinning-wheel", "SpinningWheel", 41);
        register("witches-oven", "WitchesOven", 41);
    }

    private static void register(String id, String type, int nativeSlots) {
        NativeMachineAdapters.machine(
            "dynamicinventory:witchery/" + id,
            "com.emoniph.witchery.blocks.Block" + type + "$Container" + type,
            "com.emoniph.witchery.blocks.Block" + type + "GUI",
            nativeSlots);
    }
}
