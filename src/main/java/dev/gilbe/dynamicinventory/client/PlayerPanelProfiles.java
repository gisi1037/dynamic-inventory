package dev.gilbe.dynamicinventory.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Client-only global preference and complete per-interface overrides. No item stacks or server capacity changes. */
public final class PlayerPanelProfiles {

    private static PlayerPanelProfile global = PlayerPanelProfile.DEFAULT;
    private static Map<String, PlayerPanelProfile> overrides = new TreeMap<>();
    private static Path file;
    private static int legacy;
    private static PlayerPanelSet panels = PlayerPanelSet.DEFAULT;

    private PlayerPanelProfiles() {}

    public static void initialize(File location) {
        file = location.toPath();
        global = PlayerPanelProfile.DEFAULT;
        overrides.clear();
        panels = PlayerPanelSet.DEFAULT;
        legacy = 0;
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > 1048576) throw new IllegalArgumentException("Panel profiles too large");
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
            boolean collection = "3".equals(
                root.get("schemaVersion")
                    .getAsString());
            if (root.has("panels") != collection) throw new IllegalArgumentException("Invalid panel collection schema");
            if (root.entrySet()
                .size() != (root.has("panels") ? 4 : 3)
                || !root.get("schemaVersion")
                    .getAsString()
                    .matches("[123]"))
                throw new IllegalArgumentException("Unknown panel profile schema");
            PlayerPanelProfile loadedGlobal = PlayerPanelProfile.fromJson(root.getAsJsonObject("global"));
            Map<String, PlayerPanelProfile> loaded = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("interfaces")
                .entrySet()) {
                validateKey(entry.getKey());
                loaded.put(
                    entry.getKey(),
                    PlayerPanelProfile.fromJson(
                        entry.getValue()
                            .getAsJsonObject()));
            }
            PlayerPanelSet loadedPanels = "3".equals(
                root.get("schemaVersion")
                    .getAsString()) ? PlayerPanelSet.fromJson(root.getAsJsonArray("panels")) : PlayerPanelSet.DEFAULT;
            panels = loadedPanels;
            global = loadedGlobal;
            overrides = loaded;
            legacy = root.get("schemaVersion")
                .getAsInt() < 3 ? root.get("schemaVersion")
                    .getAsInt() : 0;
        } catch (IOException | RuntimeException invalid) {
            DynamicInventory.LOG.warn("Ignoring invalid player panel profiles {}", file, invalid);
        }
    }

    public static String key(String adapter, String container, String gui) {
        String key = adapter + "|" + container + "|" + gui;
        validateKey(key);
        return key;
    }

    static void validateKey(String key) {
        String type = "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+";
        if (key.length() > 1024 || !key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|" + type + "\\|" + type))
            throw new IllegalArgumentException("Invalid panel profile key");
    }

    public static PlayerPanelProfile resolve(String key) {
        return overrides.containsKey(key) ? overrides.get(key) : global;
    }

    public static PlayerPanelProfile global() {
        return global;
    }

    public static void save(String key, PlayerPanelProfile profile) throws IOException {
        validateKey(key);
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        next.put(key, profile);
        commit(global, next);
    }

    public static void setGlobal(PlayerPanelProfile profile) throws IOException {
        commit(profile, overrides);
    }

    public static void resetGlobal() throws IOException {
        setGlobal(PlayerPanelProfile.DEFAULT);
    }

    public static void inherit(String key) throws IOException {
        validateKey(key);
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        next.remove(key);
        commit(global, next);
    }

    public static void reset(String group) throws IOException {
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        next.keySet()
            .removeIf(key -> group == null || group.equals(group(key.split("\\|")[1])));
        java.util.List<PlayerPanelSet.Entry> entries = new java.util.ArrayList<>();
        for (PlayerPanelSet.Entry entry : panels.entries) entries.add(entry.reset(group));
        commit(global, next, new PlayerPanelSet(entries));
    }

    public static PlayerPanelSet panels() {
        return panels;
    }

    /** One atomic write for allocations, all panel geometries and the selected Main scope. */
    public static void applyPanels(PlayerPanelSet nextPanels, String key, PlayerPanelProfile main) throws IOException {
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        if (key != null) {
            validateKey(key);
            next.put(key, main);
        }
        commit(key == null ? main : global, next, nextPanels);
    }

    public static Map<String, Integer> groups() {
        Map<String, Integer> groups = new TreeMap<>();
        for (String key : overrides.keySet()) groups.merge(group(key.split("\\|")[1]), 1, Integer::sum);
        for (PlayerPanelSet.Entry entry : panels.entries)
            for (String key : entry.overrides.keySet()) groups.merge(group(key.split("\\|")[1]), 1, Integer::sum);
        return groups;
    }

    /** Stable class-package groups also identify saved layouts when the owning mod is no longer installed. */
    public static String group(String container) {
        if (container.startsWith("net.minecraft.")) return "Vanilla";
        if (container.startsWith("appeng.")) return "Applied Energistics 2";
        if (container.startsWith("cpw.mods.ironchest.")) return "Iron Chests";
        if (container.startsWith("micdoodle8.")) return "Galacticraft";
        if (container.startsWith("com.dreammaster.")) return "GTNH";
        if (container.startsWith("com.technicianlp.chestgrate.")) return "Alchemical Grate";
        if (container.startsWith("fox.spiteful.avaritia.")) return "Avaritia";
        if (container.startsWith("ganymedes01.etfuturum.")) return "Et Futurum";
        if (container.startsWith("tconstruct.")) return "Tinkers Construct";
        if (container.startsWith("squeek.spiceoflife.")) return "Spice of Life";
        if (container.startsWith("wanion.avaritiaddons.")) return "Avaritiaddons";
        if (container.startsWith("twilightforest.")) return "Twilight Forest";
        if (container.startsWith("forestry.")) return "Forestry";
        if (container.startsWith("com.pam.")) return "Pam's HarvestCraft";
        if (container.startsWith("squeek.applecore.")) return "AppleCore";
        if (container.startsWith("com.rwtema.extrautils.")) return "Extra Utilities";
        if (container.startsWith("crazypants.enderio.")) return "Ender IO";
        if (container.startsWith("openblocks.")) return "OpenBlocks";
        if (container.startsWith("thaumcraft.")) return "Thaumcraft";
        // Unknown owners stay separate; never combine unrelated mods just because both use "com".
        int dot = container.lastIndexOf('.');
        return dot > 0 ? container.substring(0, dot) : container;
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles)
        throws IOException {
        commit(preference, profiles, panels);
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles,
        PlayerPanelSet nextPanels) throws IOException {
        if (file == null) throw new IllegalStateException("Player panel preferences not initialized");
        JsonObject root = new JsonObject(), entries = new JsonObject();
        root.addProperty("schemaVersion", 3);
        root.add("panels", nextPanels.toJson());
        root.add("global", preference.toJson());
        for (Map.Entry<String, PlayerPanelProfile> entry : profiles.entrySet()) entries.add(
            entry.getKey(),
            entry.getValue()
                .toJson());
        root.add("interfaces", entries);
        byte[] encoded = new GsonBuilder().setPrettyPrinting()
            .create()
            .toJson(root)
            .getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 1048576) throw new IOException("Panel preferences exceed the supported file size");
        Files.createDirectories(file.getParent());
        if (legacy != 0 && Files.isRegularFile(file)) {
            Path backup = file.resolveSibling("player-panels.schema" + legacy + "-backup.json");
            if (!Files.exists(backup)) Files.copy(file, backup);
        }
        Path temporary = Files.createTempFile(file.getParent(), "player-panels-", ".tmp");
        try {
            Files.write(temporary, encoded);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        panels = nextPanels;
        global = preference;
        overrides = new TreeMap<>(profiles);
        legacy = 0;
    }
}
