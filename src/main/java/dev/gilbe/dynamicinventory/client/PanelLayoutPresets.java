package dev.gilbe.dynamicinventory.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Named client layout snapshots; never stores stacks, capacity or unrelated player settings. */
public final class PanelLayoutPresets {

    private static Path file;
    private static Map<String, Preset> presets = new TreeMap<>();
    private static boolean invalid;

    private PanelLayoutPresets() {}

    private static final class Preset {

        final String name;
        final PlayerPanelSet panels;
        final PlayerPanelProfile main;
        final PanelLayoutReference reference;

        Preset(String name, PlayerPanelSet panels, PlayerPanelProfile main, PanelLayoutReference reference) {
            this.name = name;
            this.panels = panels;
            this.main = main;
            this.reference = reference;
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("name", name);
            object.add("main", main.toJson());
            object.add("panels", panels.toJson());
            object.add("reference", reference == null ? null : reference.toJson());
            return object;
        }
    }

    static void initialize(Path location) {
        file = location;
        presets = new TreeMap<>();
        invalid = false;
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > 1048576) throw new IllegalArgumentException("Layout profiles too large");
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (root.entrySet()
                .size() != 2
                || !"1".equals(
                    root.get("schemaVersion")
                        .getAsString()))
                throw new IllegalArgumentException("Unknown layout profile schema");
            JsonArray array = root.getAsJsonArray("profiles");
            if (array.size() > 32) throw new IllegalArgumentException("Too many layout profiles");
            Map<String, Preset> loaded = new TreeMap<>();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                if (object.entrySet()
                    .size() != 4 || !object.has("reference"))
                    throw new IllegalArgumentException("Invalid layout profile");
                String name = name(
                    object.get("name")
                        .getAsString());
                Preset value = new Preset(
                    name,
                    PlayerPanelSet.fromJson(object.getAsJsonArray("panels")),
                    PlayerPanelProfile.fromJson(object.getAsJsonObject("main")),
                    object.get("reference")
                        .isJsonNull() ? null : PanelLayoutReference.fromJson(object.getAsJsonObject("reference")));
                if (loaded.put(name, value) != null) throw new IllegalArgumentException("Duplicate layout profile");
            }
            presets = loaded;
        } catch (IOException | RuntimeException failure) {
            invalid = true;
            DynamicInventory.LOG.warn("Could not read named panel layouts {}. The file is retained.", file, failure);
        }
    }

    public static List<String> names() {
        return new ArrayList<>(presets.keySet());
    }

    public static boolean contains(String name) {
        return presets.containsKey(name.trim());
    }

    public static void save(String label, PlayerPanelSet panels, PlayerPanelProfile main, String key,
        PanelLayoutReference reference) throws IOException {
        String name = name(label);
        if (!presets.containsKey(name) && presets.size() >= 32)
            throw new IllegalArgumentException("Maximum 32 layout profiles");
        List<PlayerPanelSet.Entry> entries = new ArrayList<>();
        for (PlayerPanelSet.Entry entry : panels.entries) entries.add(
            new PlayerPanelSet.Entry(
                entry.id,
                entry.name,
                entry.slots,
                key == null ? entry.global : entry.resolve(key),
                Collections.emptyMap()));
        Map<String, Preset> next = new TreeMap<>(presets);
        next.put(name, new Preset(name, new PlayerPanelSet(entries), main, reference));
        commit(next);
    }

    public static void load(String name, String key) throws IOException {
        Preset preset = presets.get(name);
        if (preset == null) throw new IllegalArgumentException("Unknown layout profile");
        PlayerPanelProfiles.applyGlobalPanels(preset.panels, key, preset.main, preset.reference);
    }

    public static void delete(String name) throws IOException {
        Map<String, Preset> next = new TreeMap<>(presets);
        if (next.remove(name) == null) throw new IllegalArgumentException("Unknown layout profile");
        commit(next);
    }

    private static String name(String value) {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 32
            || name.chars()
                .anyMatch(c -> c < 32 || c == 127 || c == 167))
            throw new IllegalArgumentException("Use a profile name with 1-32 printable characters");
        return name;
    }

    private static void commit(Map<String, Preset> next) throws IOException {
        if (file == null) throw new IllegalStateException("Layout profiles not initialized");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonArray array = new JsonArray();
        for (Preset preset : next.values()) array.add(preset.toJson());
        root.add("profiles", array);
        byte[] data = new GsonBuilder().setPrettyPrinting()
            .serializeNulls()
            .create()
            .toJson(root)
            .getBytes(StandardCharsets.UTF_8);
        if (data.length > 1048576) throw new IOException("Saved layout profiles exceed 1 MiB");
        Files.createDirectories(file.getParent());
        if (invalid && Files.isRegularFile(file)) {
            Files.copy(
                file,
                file.resolveSibling("panel-layout-presets.invalid-" + System.currentTimeMillis() + ".json"));
            invalid = false;
        }
        Path temporary = Files.createTempFile(file.getParent(), "panel-layout-presets-", ".tmp");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        presets = next;
    }
}
