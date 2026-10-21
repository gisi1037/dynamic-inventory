package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Proxy;
import java.util.Map;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import dev.gilbe.dynamicinventory.compat.IntegrationProfile;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

/** Plain JVM probe using the test script's small, mutable Forge Loader double. */
public final class PackCompatibilityFixtures {

    public static void main(String[] args) {
        Map<String, ModContainer> mods = Loader.instance()
            .getIndexedModList();
        add(mods, "backhand", "Backhand");
        add(mods, "Baubles", "Baubles-Expanded");
        add(mods, "NotEnoughItems", "NotEnoughItems");
        add(mods, "bogosorter", "InventoryBogoSorter");
        // All optional mods absent: a pack profile still starts.
        PackCompatibility.verifyVersions();
        checkOptional(mods, "avaritiaddons", "Avaritiaddons", args[0]);
        checkOptional(mods, "dreamcraft", "NewHorizonsCoreMod", args[1]);
        add(mods, "OpenMods", "OpenModsLib");
        checkOptional(mods, "OpenBlocks", "OpenBlocks", args[2]);
        checkOptional(mods, "IronChest", "IronChest", args[3]);
        checkOptional(mods, "etfuturum", "EtFuturum", args[4]);
        add(mods, "avaritiaddons", "Avaritiaddons");
        add(mods, "dreamcraft", "NewHorizonsCoreMod");
        add(mods, "OpenBlocks", "OpenBlocks");
        add(mods, "IronChest", "IronChest");
        PackCompatibility.verifyVersions();
        mods.remove("OpenMods");
        expectRejected(
            "OpenMods",
            IntegrationProfile.selected()
                .version("OpenModsLib"));
        mods.put("OpenMods", container("unsupported-version"));
        expectRejected(
            "OpenMods",
            IntegrationProfile.selected()
                .version("OpenModsLib"));
        add(mods, "OpenMods", "OpenModsLib");
        // Optional guards must not weaken the original required dependency checks.
        mods.remove("backhand");
        expectRejected(
            "backhand",
            IntegrationProfile.selected()
                .version("Backhand"));
        System.out.println(
            "PASS: 25 optional pack-version checks for " + IntegrationProfile.selected()
                .profileName());
    }

    private static void checkOptional(Map<String, ModContainer> mods, String id, String project, String expected) {
        if (!expected.equals(
            IntegrationProfile.selected()
                .version(project)))
            throw new AssertionError("Wrong fixture pin for " + id);
        add(mods, id, project);
        PackCompatibility.verifyVersions();
        mods.put(id, container("unsupported-version"));
        expectRejected(id, expected);
        mods.remove(id);
        PackCompatibility.verifyVersions();
    }

    private static void expectRejected(String id, String version) {
        try {
            PackCompatibility.verifyVersions();
            throw new AssertionError("Unsupported or missing " + id + " was accepted");
        } catch (IllegalStateException expected) {
            String message = IntegrationProfile.selected()
                .profileName() + " requires "
                + id
                + " "
                + version;
            if (!message.equals(expected.getMessage())) throw new AssertionError("Wrong version diagnostic", expected);
        }
    }

    private static void add(Map<String, ModContainer> mods, String id, String project) {
        mods.put(
            id,
            container(
                IntegrationProfile.selected()
                    .version(project)));
    }

    private static ModContainer container(String version) {
        return (ModContainer) Proxy.newProxyInstance(
            ModContainer.class.getClassLoader(),
            new Class<?>[] { ModContainer.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getVersion")) return version;
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
