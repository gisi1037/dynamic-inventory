package dev.gilbe.dynamicinventory.compat;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Optional integrations may be absent or newer than our review; neither should prevent pack startup. */
final class OptionalInventoryAdapters {

    private OptionalInventoryAdapters() {}

    static boolean reviewedVersion(String modId) {
        return matches(modId, IntegrationProfile.reviewedVersion(modId));
    }

    /** Preserve a user-enabled adapter on the named fixture versions, skipping unknown builds. */
    static boolean profileVersion(String modId, String project) {
        return matches(
            modId,
            IntegrationProfile.selected()
                .version(project));
    }

    private static boolean matches(String modId, String version) {
        if (!Loader.isModLoaded(modId)) return false;
        String loaded = Loader.instance()
            .getIndexedModList()
            .get(modId)
            .getVersion();
        if (version.equals(loaded)) return true;
        // Skip only this integration. Do not guess that the same slot/transfer contract survived an update.
        DynamicInventory.LOG
            .warn("Skipping inventory adapters for {} {}: reviewed version is {}", modId, loaded, version);
        return false;
    }
}
