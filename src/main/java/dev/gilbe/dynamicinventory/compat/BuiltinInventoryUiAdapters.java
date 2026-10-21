package dev.gilbe.dynamicinventory.compat;

import cpw.mods.fml.common.Loader;

/** Built-in integrations use the same registration contract available to other mods. */
public final class BuiltinInventoryUiAdapters {

    private BuiltinInventoryUiAdapters() {}

    public static void initialize() {
        VanillaInventoryCompatibility.registerInventoryAdapters();
        if (!PackCompatibility.enabled()) return;
        // Optional mods must not contribute adapters (or trigger API class loading)
        // when absent. Keep version checks and the opening handshake separate.
        if (Loader.isModLoaded("TConstruct")) TinkersConstructCompatibility.registerInventoryAdapters();
        if (Loader.isModLoaded("GalacticraftCore")) GalacticraftCompatibility.registerInventoryAdapters();
        if (Loader.isModLoaded("IronChest")) IronChestCompatibility.registerInventoryAdapters();
        if (Loader.isModLoaded("etfuturum")) EtFuturumCompatibility.registerInventoryAdapters();
        if (Loader.isModLoaded("avaritiaddons")) AvaritiaddonsCompatibility.registerInventoryAdapters();
        if (Loader.isModLoaded("dreamcraft")) DreamcraftCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("alchgrate"))
            AlchemicalGrateCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("Avaritia")) AvaritiaCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("FloodLights"))
            FloodLightsCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("TwilightForest"))
            TwilightForestCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("harvestcraft"))
            HarvestCraftCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("Thaumcraft"))
            ThaumcraftCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.reviewedVersion("witchery")) WitcheryCompatibility.registerInventoryAdapters();
        if (OptionalInventoryAdapters.profileVersion("SpiceOfLife", "SpiceOfLife"))
            SpiceOfLifeCompatibility.registerInventoryAdapters();
        registerGeneratedAdapters();
    }

    /** F9 suggestions stay separate until reviewed, then move into the owning mod's compatibility file. */
    private static void registerGeneratedAdapters() {
        // When enabling a suggestion, put it in its mod's file behind a version guard above.
        // F9-SCAFFOLDS: New native adapter suggestions are inserted above this marker, fully commented.
    }
}
