package dev.gilbe.dynamicinventory.qualification;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.Tags;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.IntegrationProfile;

/** Plain JVM probe: Forge's construction/status checks must not freeze registration or throw. */
public final class EarlyNetworkFixtures {

    public static void main(String[] args) {
        BackhandCompatibility.configure(true);
        DynamicInventory mod = new DynamicInventory();
        for (Side side : Side.values()) {
            if (mod.acceptsRemote(Collections.emptyMap(), side))
                throw new AssertionError("A peer without the mod must be rejected");
            Map<String, String> status = new HashMap<>();
            status.put(DynamicInventory.MODID, Tags.VERSION);
            if (BackhandCompatibility.enabled()) {
                status.put("backhand", BackhandCompatibility.supportedVersion());
                if (IntegrationProfile.selected()
                    .usesPackAdapters()) status.put("bogosorter", "present");
            }
            if (!mod.acceptsRemote(status, side)) throw new AssertionError("Matching status version was rejected");
            status.put("backhand", "unknown-version");
            if (mod.acceptsRemote(status, side)) throw new AssertionError("Unknown offhand mapping was accepted");
        }
        try {
            InventoryUi.getCompatibilityDigest();
            throw new AssertionError("A status probe must not close adapter registration");
        } catch (IllegalStateException expected) {}
        System.out.println("PASS: 7 early network-check assertions; adapter registration remains open");
    }
}
