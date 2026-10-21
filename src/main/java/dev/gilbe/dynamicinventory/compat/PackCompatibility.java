package dev.gilbe.dynamicinventory.compat;

import cpw.mods.fml.common.Loader;

/** Exact named fixtures are checked independently; selecting a daily does not qualify it. */
public final class PackCompatibility {

    private PackCompatibility() {}

    public static boolean enabled() {
        return IntegrationProfile.selected()
            .usesPackAdapters();
    }

    public static void verifyVersions() {
        if (!enabled()) return;
        require("backhand", "Backhand");
        require("Baubles", "Baubles-Expanded");
        require("NotEnoughItems", "NotEnoughItems");
        require("bogosorter", "InventoryBogoSorter");
        if (Loader.isModLoaded("craftingtweaks")) require("craftingtweaks", "GregoryTweaksForCrafting");
        if (Loader.isModLoaded("TConstruct")) require("TConstruct", "TinkersConstruct");
        if (Loader.isModLoaded("modularui")) require("modularui", "ModularUI");
        if (Loader.isModLoaded("modularui2")) require("modularui2", "ModularUI2");
        if (Loader.isModLoaded("GalacticraftCore")) require("GalacticraftCore", "Galacticraft");
        if (Loader.isModLoaded("IronChest")) require("IronChest", "IronChest");
        if (Loader.isModLoaded("etfuturum")) require("etfuturum", "EtFuturum");
        // These integrations are optional, but their loaded versions must match the fixture.
        if (Loader.isModLoaded("avaritiaddons")) require("avaritiaddons", "Avaritiaddons");
        if (Loader.isModLoaded("dreamcraft")) require("dreamcraft", "NewHorizonsCoreMod");
        if (Loader.isModLoaded("OpenBlocks")) {
            require("OpenBlocks", "OpenBlocks");
            require("OpenMods", "OpenModsLib");
        }
    }

    // The late loader also checks a required bridge before applying its optional GUI mixin.
    static void require(String id, String project) {
        IntegrationProfile profile = IntegrationProfile.selected();
        String version = profile.version(project);
        if (!Loader.isModLoaded(id) || !version.equals(
            Loader.instance()
                .getIndexedModList()
                .get(id)
                .getVersion())) {
            throw new IllegalStateException(profile.profileName() + " requires " + id + " " + version);
        }
    }
}
