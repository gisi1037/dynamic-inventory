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
    private static boolean unreadable;
    private static PlayerPanelSet panels = PlayerPanelSet.DEFAULT;
    private static Map<String, PlayerPanelSet> interfacePanels = new TreeMap<>();
    private static PanelLayoutReference reference;
    private static Map<String, PanelLayoutReference> interfaceReferences = new TreeMap<>();
    private static PinsPanelGeometry globalPins = PinsPanelGeometry.DEFAULT;
    private static Map<String, PinsPanelGeometry> interfacePins = new TreeMap<>();

    private PlayerPanelProfiles() {}

    public static void initialize(File location) {
        file = location.toPath();
        PanelLayoutPresets.initialize(file.resolveSibling("panel-layout-presets.json"));
        global = PlayerPanelProfile.DEFAULT;
        overrides.clear();
        panels = PlayerPanelSet.DEFAULT;
        interfacePanels.clear();
        legacy = 0;
        unreadable = false;
        reference = null;
        interfaceReferences.clear();
        globalPins = InventoryViewSettings.legacyPinsGeometry;
        interfacePins.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > 1048576) throw new IllegalArgumentException("Panel profiles too large");
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
            String version = root.get("schemaVersion")
                .getAsString();
            boolean hasPins = "8".equals(version);
            boolean anchored = hasPins || "7".equals(version);
            boolean modern = anchored || "6".equals(version);
            boolean collection = version.matches("[345678]");
            if (root.has("panels") != collection) throw new IllegalArgumentException("Invalid panel collection schema");
            if (root.entrySet()
                .size()
                != (root.has("panelLayouts") ? 5 : root.has("panels") ? 4 : 3) + (modern ? 1 : 0)
                    + (anchored ? 1 : 0)
                    + (hasPins ? 1 : 0)
                || !version.matches("[12345678]")
                || root.has("pinsLayout") != hasPins
                || root.has("globalReference") != modern
                || root.has("interfaceReferences") != anchored
                || root.has("panelLayouts") != (modern || "5".equals(version)))
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
            PanelLayoutReference loadedReference = modern && !root.get("globalReference")
                .isJsonNull() ? PanelLayoutReference.fromJson(root.getAsJsonObject("globalReference")) : null;
            PlayerPanelSet loadedPanels = collection ? PlayerPanelSet.fromJson(root.getAsJsonArray("panels"))
                : PlayerPanelSet.DEFAULT;
            Map<String, PlayerPanelSet> loadedSets = new TreeMap<>();
            if (root.has("panelLayouts")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("panelLayouts")
                    .entrySet()) {
                    validateKey(entry.getKey());
                    loadedSets.put(
                        entry.getKey(),
                        PlayerPanelSet.fromJson(
                            entry.getValue()
                                .getAsJsonArray()));
                }
            } else if (collection) {
                // Older files shared membership between every interface. Preserve each known
                // interface's complete layout; an attached global default has only Main.
                java.util.Set<String> keys = new java.util.TreeSet<>(loaded.keySet());
                for (PlayerPanelSet.Entry entry : loadedPanels.entries) keys.addAll(entry.overrides.keySet());
                for (String key : keys) loadedSets.put(key, scoped(loadedPanels, key));
                loadedPanels = loadedGlobal.detached || keys.isEmpty() ? scoped(loadedPanels, null)
                    : PlayerPanelSet.DEFAULT;
            }
            Map<String, PanelLayoutReference> loadedReferences = new TreeMap<>();
            if (anchored) for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("interfaceReferences")
                .entrySet()) {
                    validateKey(entry.getKey());
                    if (!loaded.containsKey(entry.getKey()) && !loadedSets.containsKey(entry.getKey()))
                        throw new IllegalArgumentException("Orphan panel reference");
                    loadedReferences.put(
                        entry.getKey(),
                        PanelLayoutReference.fromJson(
                            entry.getValue()
                                .getAsJsonObject()));
                }
            PinsPanelGeometry loadedPins = globalPins;
            Map<String, PinsPanelGeometry> loadedInterfacePins = new TreeMap<>();
            if (hasPins) {
                JsonObject pinsLayout = root.getAsJsonObject("pinsLayout");
                if (pinsLayout.entrySet()
                    .size() != 2 || !pinsLayout.has("interfaces"))
                    throw new IllegalArgumentException("Invalid AE2 Pins layouts");
                loadedPins = PinsPanelGeometry.fromJson(pinsLayout.getAsJsonObject("global"));
                if (!loadedPins.hasBounds()) throw new IllegalArgumentException("Missing global Pins bounds");
                for (Map.Entry<String, JsonElement> entry : pinsLayout.getAsJsonObject("interfaces")
                    .entrySet()) {
                    validateKey(entry.getKey());
                    loadedInterfacePins.put(
                        entry.getKey(),
                        PinsPanelGeometry.fromJson(
                            entry.getValue()
                                .getAsJsonObject()));
                }
            }
            panels = loadedPanels;
            interfacePanels = loadedSets;
            global = loadedGlobal;
            overrides = loaded;
            reference = loadedReference;
            interfaceReferences = loadedReferences;
            globalPins = loadedPins;
            interfacePins = loadedInterfacePins;
            legacy = hasPins ? 0 : Integer.parseInt(version);
        } catch (IOException | RuntimeException invalid) {
            unreadable = true;
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

    public static boolean hasOverride(String key) {
        return overrides.containsKey(key) || interfacePanels.containsKey(key);
    }

    public static PinsPanelGeometry pins(String key) {
        return key == null ? globalPins : interfacePins.getOrDefault(key, globalPins);
    }

    public static InventoryPanelLayout.Rect requestedPinsBounds(String key, InventoryPanelLayout.Rect machine) {
        PinsPanelGeometry local = key == null ? null : interfacePins.get(key);
        return local != null && local.hasBounds() ? local.requested(machine, true)
            : globalPins.requested(machine, false);
    }

    public static PlayerPanelProfile pinsAppearance(String key) {
        PlayerPanelProfile selected = pins(key).appearance();
        if (selected == null) selected = globalPins.appearance();
        return selected == null ? PinsPanelGeometry.DEFAULT_APPEARANCE : selected;
    }

    static PinsPanelGeometry pinsSnapshot(String key) {
        PinsPanelGeometry selected = pins(key);
        return (selected.hasBounds() ? selected : globalPins).withAppearance(pinsAppearance(key));
    }

    static void savePinsBounds(String key, InventoryPanelLayout.Rect bounds, PanelLayoutReference reference)
        throws IOException {
        PinsPanelGeometry local = interfacePins.get(key);
        savePins(
            key,
            new PinsPanelGeometry(bounds, reference).withAppearance(local == null ? null : local.appearance()));
    }

    static void savePinsAppearance(String key, PlayerPanelProfile appearance, boolean promote) throws IOException {
        validateKey(key);
        Map<String, PinsPanelGeometry> next = new TreeMap<>(interfacePins);
        PinsPanelGeometry local = next.get(key);
        PlayerPanelProfile override = promote ? null : appearance;
        if (local != null && local.hasBounds()) next.put(key, local.withAppearance(override));
        else if (override == null) next.remove(key);
        else next.put(key, PinsPanelGeometry.appearanceOnly(override));
        commit(
            global,
            overrides,
            panels,
            interfacePanels,
            reference,
            interfaceReferences,
            promote ? globalPins.withAppearance(appearance) : globalPins,
            next);
    }

    public static void savePins(String key, PinsPanelGeometry geometry) throws IOException {
        validateKey(key);
        Map<String, PinsPanelGeometry> next = new TreeMap<>(interfacePins);
        next.put(key, geometry);
        commit(global, overrides, panels, interfacePanels, reference, interfaceReferences, globalPins, next);
    }

    public static PanelLayoutReference globalReference() {
        return reference;
    }

    public static InventoryPanelLayout.Rect inheritedBounds(InventoryPanelLayout.Rect requested,
        InventoryPanelLayout.Rect machine, InventoryPanelLayout.Rect viewport) {
        return reference == null ? requested : reference.adapt(requested, machine, viewport);
    }

    public static InventoryPanelLayout.Rect requestedBounds(String key, PlayerPanelProfile profile,
        InventoryPanelLayout.Rect machine, InventoryPanelLayout.Rect viewport) {
        boolean custom = hasOverride(key);
        PanelLayoutReference origin = custom ? interfaceReferences.get(key) : reference;
        return origin == null ? profile.requested(viewport)
            : custom ? origin.adaptCustom(profile, machine) : origin.adapt(profile, machine, viewport);
    }

    static int savedVerticalOffset(String key, InventoryPanelLayout.Rect baseMachine,
        InventoryPanelLayout.Rect viewport) {
        PanelLayoutReference origin = interfaceReferences.get(key);
        return origin == null ? -1 : origin.verticalOffset(baseMachine, viewport);
    }

    /** Automatic fitting keeps inventory panels out of the space above their machine. */
    public static InventoryPanelLayout.Rect fittingViewport(String key, PlayerPanelProfile profile,
        InventoryPanelLayout.Rect machine, InventoryPanelLayout.Rect viewport) {
        if (hasOverride(key)) return viewport;
        PanelLayoutReference origin = reference;
        if (origin != null && origin.placedAbove(profile)) return viewport;
        int top = Math.max(viewport.y, Math.min(viewport.bottom(), machine.y));
        return new InventoryPanelLayout.Rect(viewport.x, top, viewport.width, viewport.bottom() - top);
    }

    /** Older layouts learn their current authored viewport once, without changing allocations or appearance. */
    public static void establishReference(String key, PanelLayoutReference origin) throws IOException {
        // Opening an inventory must leave an unsupported or damaged preferences file untouched.
        if (unreadable) return;
        if (hasOverride(key)) {
            if (interfaceReferences.containsKey(key)) return;
            Map<String, PanelLayoutReference> next = new TreeMap<>(interfaceReferences);
            next.put(key, origin);
            commit(global, overrides, panels, interfacePanels, reference, next);
        } else if (reference == null) commit(global, overrides, panels, interfacePanels, origin);
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
        commit(
            PlayerPanelProfile.DEFAULT,
            overrides,
            PlayerPanelSet.DEFAULT,
            interfacePanels,
            null,
            interfaceReferences,
            PinsPanelGeometry.DEFAULT,
            interfacePins);
    }

    public static void inherit(String key) throws IOException {
        validateKey(key);
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        next.remove(key);
        Map<String, PlayerPanelSet> sets = new TreeMap<>(interfacePanels);
        sets.remove(key);
        Map<String, PinsPanelGeometry> pins = new TreeMap<>(interfacePins);
        pins.remove(key);
        commit(global, next, panels, sets, reference, interfaceReferences, globalPins, pins);
    }

    public static void reset(String group) throws IOException {
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        next.keySet()
            .removeIf(key -> group == null || group.equals(group(key.split("\\|")[1])));
        Map<String, PlayerPanelSet> sets = new TreeMap<>(interfacePanels);
        sets.keySet()
            .removeIf(key -> group == null || group.equals(group(key.split("\\|")[1])));
        Map<String, PinsPanelGeometry> pins = new TreeMap<>(interfacePins);
        pins.keySet()
            .removeIf(key -> group == null || group.equals(group(key.split("\\|")[1])));
        commit(global, next, panels, sets, reference, interfaceReferences, globalPins, pins);
    }

    public static PlayerPanelSet panels() {
        return panels;
    }

    public static PlayerPanelSet panels(String key) {
        return interfacePanels.containsKey(key) ? interfacePanels.get(key) : panels;
    }

    private static PlayerPanelSet scoped(PlayerPanelSet set, String key) {
        java.util.List<PlayerPanelSet.Entry> entries = new java.util.ArrayList<>();
        for (PlayerPanelSet.Entry entry : set.entries) entries.add(
            new PlayerPanelSet.Entry(
                entry.id,
                entry.name,
                entry.slots,
                key == null ? entry.global : entry.resolve(key),
                java.util.Collections.emptyMap()));
        return new PlayerPanelSet(entries);
    }

    /** One atomic write for allocations, all panel geometries and the selected Main scope. */
    public static void applyPanels(PlayerPanelSet nextPanels, String key, PlayerPanelProfile main) throws IOException {
        applyPanels(nextPanels, key, main, null);
    }

    public static void applyPanels(PlayerPanelSet nextPanels, String key, PlayerPanelProfile main,
        PanelLayoutReference origin) throws IOException {
        applyPanels(nextPanels, key, main, origin, false);
    }

    static void savePanelGeometry(PlayerPanelSet panels, String key, PlayerPanelProfile main,
        PanelLayoutReference origin) throws IOException {
        applyPanels(panels, key, main, origin, true);
    }

    private static void applyPanels(PlayerPanelSet nextPanels, String key, PlayerPanelProfile main,
        PanelLayoutReference origin, boolean explicitGeometry) throws IOException {
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        Map<String, PlayerPanelSet> sets = new TreeMap<>(interfacePanels);
        if (key != null) {
            validateKey(key);
            PlayerPanelSet local = scoped(nextPanels, key);
            if (!explicitGeometry && main.toJson()
                .equals(global.toJson())
                && local.toJson()
                    .equals(panels.toJson())) {
                next.remove(key);
                sets.remove(key);
            } else {
                next.put(key, main);
                sets.put(key, local);
            }
        }
        Map<String, PanelLayoutReference> origins = new TreeMap<>(interfaceReferences);
        if (key != null && origin != null && (next.containsKey(key) || sets.containsKey(key))) origins.put(key, origin);
        commit(
            key == null ? main : global,
            next,
            key == null ? scoped(nextPanels, null) : panels,
            sets,
            reference,
            origins);
    }

    /** Saving a global layout also makes the current interface inherit it, preserving unrelated interfaces. */
    public static void applyGlobalPanels(PlayerPanelSet set, String key, PlayerPanelProfile main) throws IOException {
        applyGlobalPanels(set, key, main, null);
    }

    public static void applyGlobalPanels(PlayerPanelSet set, String key, PlayerPanelProfile main,
        PanelLayoutReference origin) throws IOException {
        applyGlobalPanels(set, key, main, origin, pinsSnapshot(key));
    }

    static void applyGlobalPanels(PlayerPanelSet set, String key, PlayerPanelProfile main, PanelLayoutReference origin,
        PinsPanelGeometry newPins) throws IOException {
        if (key != null) validateKey(key);
        Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
        if (key != null) next.remove(key);
        Map<String, PlayerPanelSet> sets = new TreeMap<>(interfacePanels);
        if (key != null) sets.remove(key);
        Map<String, PinsPanelGeometry> pins = new TreeMap<>(interfacePins);
        if (key != null && newPins != null) pins.remove(key);
        commit(
            main,
            next,
            scoped(set, null),
            sets,
            origin,
            interfaceReferences,
            newPins == null || !newPins.hasBounds() ? globalPins : newPins,
            pins);
    }

    public static Map<String, Integer> groups() {
        Map<String, Integer> groups = new TreeMap<>();
        for (String key : overrides.keySet()) groups.merge(group(key.split("\\|")[1]), 1, Integer::sum);
        for (String key : interfacePanels.keySet())
            if (!overrides.containsKey(key)) groups.merge(group(key.split("\\|")[1]), 1, Integer::sum);
        for (String key : interfacePins.keySet())
            if (!hasOverride(key)) groups.merge(group(key.split("\\|")[1]), 1, Integer::sum);
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
        commit(preference, profiles, nextPanels, interfacePanels);
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles,
        PlayerPanelSet nextPanels, Map<String, PlayerPanelSet> nextLayouts) throws IOException {
        commit(preference, profiles, nextPanels, nextLayouts, reference);
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles,
        PlayerPanelSet nextPanels, Map<String, PlayerPanelSet> nextLayouts, PanelLayoutReference nextReference)
        throws IOException {
        commit(preference, profiles, nextPanels, nextLayouts, nextReference, interfaceReferences);
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles,
        PlayerPanelSet nextPanels, Map<String, PlayerPanelSet> nextLayouts, PanelLayoutReference nextReference,
        Map<String, PanelLayoutReference> origins) throws IOException {
        commit(preference, profiles, nextPanels, nextLayouts, nextReference, origins, globalPins, interfacePins);
    }

    private static void commit(PlayerPanelProfile preference, Map<String, PlayerPanelProfile> profiles,
        PlayerPanelSet nextPanels, Map<String, PlayerPanelSet> nextLayouts, PanelLayoutReference nextReference,
        Map<String, PanelLayoutReference> origins, PinsPanelGeometry nextPins, Map<String, PinsPanelGeometry> pins)
        throws IOException {
        if (file == null) throw new IllegalStateException("Player panel preferences not initialized");
        JsonObject root = new JsonObject(), entries = new JsonObject();
        root.addProperty("schemaVersion", 8);
        root.add("globalReference", nextReference == null ? null : nextReference.toJson());
        root.add("panels", nextPanels.toJson());
        root.add("global", preference.toJson());
        for (Map.Entry<String, PlayerPanelProfile> entry : profiles.entrySet()) entries.add(
            entry.getKey(),
            entry.getValue()
                .toJson());
        root.add("interfaces", entries);
        JsonObject layouts = new JsonObject();
        for (Map.Entry<String, PlayerPanelSet> entry : nextLayouts.entrySet()) layouts.add(
            entry.getKey(),
            entry.getValue()
                .toJson());
        root.add("panelLayouts", layouts);
        Map<String, PanelLayoutReference> nextOrigins = new TreeMap<>(origins);
        nextOrigins.keySet()
            .removeIf(key -> !profiles.containsKey(key) && !nextLayouts.containsKey(key));
        JsonObject references = new JsonObject();
        for (Map.Entry<String, PanelLayoutReference> entry : nextOrigins.entrySet()) references.add(
            entry.getKey(),
            entry.getValue()
                .toJson());
        root.add("interfaceReferences", references);
        JsonObject pinsLayout = new JsonObject(), pinsOverrides = new JsonObject();
        pinsLayout.add("global", nextPins.toJson());
        for (Map.Entry<String, PinsPanelGeometry> entry : pins.entrySet()) pinsOverrides.add(
            entry.getKey(),
            entry.getValue()
                .toJson());
        pinsLayout.add("interfaces", pinsOverrides);
        root.add("pinsLayout", pinsLayout);
        byte[] encoded = new GsonBuilder().setPrettyPrinting()
            .serializeNulls()
            .create()
            .toJson(root)
            .getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 1048576) throw new IOException("Panel preferences exceed the supported file size");
        Files.createDirectories(file.getParent());
        if (unreadable && Files.isRegularFile(file)) {
            Files.copy(file, file.resolveSibling("player-panels.unreadable-" + java.util.UUID.randomUUID() + ".json"));
        }
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
        interfacePanels = new TreeMap<>(nextLayouts);
        global = preference;
        overrides = new TreeMap<>(profiles);
        reference = nextReference;
        interfaceReferences = nextOrigins;
        globalPins = nextPins;
        interfacePins = new TreeMap<>(pins);
        legacy = 0;
        unreadable = false;
    }
}
