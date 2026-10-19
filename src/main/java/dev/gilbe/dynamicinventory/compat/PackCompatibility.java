package dev.gilbe.dynamicinventory.compat;

import cpw.mods.fml.common.Loader;

/** Exact RC-2 adapters are qualified independently of the full pack. */
public final class PackCompatibility {

    private PackCompatibility() {}

    public static boolean enabled() {
        return "rc2-integration".equals(System.getProperty("dynamicinventory.profile"));
    }

    public static void verifyVersions() {
        if (!enabled()) return;
        require("backhand", BackhandCompatibility.SUPPORTED_VERSION);
        require("Baubles", "2.2.25-GTNH");
        require("NotEnoughItems", "2.8.155-GTNH");
        require("bogosorter", "1.3.54-GTNH");
        if (Loader.isModLoaded("craftingtweaks")) require("craftingtweaks", "1.0.3");
        if (Loader.isModLoaded("TConstruct")) require("TConstruct", "1.14.118-GTNH");
        if (Loader.isModLoaded("modularui")) require("modularui", "1.3.4");
        if (Loader.isModLoaded("modularui2")) require("modularui2", "2.3.92-1.7.10");
        if (Loader.isModLoaded("GalacticraftCore")) require("GalacticraftCore", "3.4.34-GTNH");
    }

    private static void require(String id, String version) {
        if (!Loader.isModLoaded(id) || !version.equals(
            Loader.instance()
                .getIndexedModList()
                .get(id)
                .getVersion())) {
            throw new IllegalStateException("The RC-2 integration fixture requires " + id + " " + version);
        }
    }
}
