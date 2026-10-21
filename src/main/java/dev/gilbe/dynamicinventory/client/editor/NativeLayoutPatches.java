package dev.gilbe.dynamicinventory.client.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Local overrides for an already approved native presentation; never registers or expands a container. */
public final class NativeLayoutPatches {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final Map<String, NativeLayoutPatch> SAVED = new HashMap<>();
    private static final Map<GuiContainer, NativeLayoutPatch> DRAFTS = new WeakHashMap<>();
    private static final Map<GuiContainer, Boolean> PREVIEW_MODES = new WeakHashMap<>();
    private static Path directory;

    private NativeLayoutPatches() {}

    public static void initialize(File folder) {
        directory = folder.toPath();
        SAVED.clear();
        DRAFTS.clear();
        PREVIEW_MODES.clear();
    }

    public static NativeLayoutPatch defaults(GuiContainer gui) {
        NativeUiLayout layout = InventoryUi.findLayout(gui.inventorySlots, gui.getClass());
        if (layout == null) throw new IllegalArgumentException("No registered native presentation");
        // Personal Wide/Compact presets belong to the player menu. Hosts use a stable nine-wide baseline.
        return constrain(
            new NativeLayoutPatch(
                layout.getFixedPlayerColumns() > 0 ? layout.getFixedPlayerColumns()
                    : InventoryViewSettings.DEFAULT_COLUMNS,
                InventoryViewSettings.DEFAULT_ROWS,
                0,
                0,
                layout),
            layout);
    }

    public static NativeLayoutPatch resolve(GuiContainer gui) {
        return constrain(resolveStored(gui), InventoryUi.findLayout(gui.inventorySlots, gui.getClass()));
    }

    private static NativeLayoutPatch constrain(NativeLayoutPatch patch, NativeUiLayout hints) {
        int fixed = hints.getFixedPlayerColumns();
        // Apply the host constraint in memory; keep the user's saved file intact.
        if (fixed != 0) patch = patch.adjust(0, fixed - patch.value(0));
        int maximum = hints.getMaximumVisibleRows();
        if (maximum > 0 && patch.value(1) > maximum) patch = patch.adjust(1, maximum - patch.value(1));
        return patch;
    }

    public static boolean expanded(GuiContainer gui) {
        Boolean preview = PREVIEW_MODES.get(gui);
        return preview == null ? InventoryViewSettings.expandMachineInventory : preview;
    }

    /** Editor-only mode override. It is never written to the global settings or saved layout. */
    public static void previewMode(GuiContainer gui, boolean expanded) {
        PREVIEW_MODES.put(gui, expanded);
    }

    public static int requestedRows(GuiContainer gui, NativeLayoutPatch patch, int columns) {
        NativeUiLayout hints = InventoryUi.findLayout(gui.inventorySlots, gui.getClass());
        int requested = hints.followsMachineMode() ? InventoryViewportLayout.requestedRows(
            InventoryLayout.of(ContainerInventoryRegion.of(gui.inventorySlots).inventory)
                .storageSlots(),
            columns,
            expanded(gui)) : patch.value(1);
        return hints.getMaximumVisibleRows() > 0 ? Math.min(requested, hints.getMaximumVisibleRows()) : requested;
    }

    private static NativeLayoutPatch resolveStored(GuiContainer gui) {
        NativeLayoutPatch draft = DRAFTS.get(gui);
        if (draft != null) return draft;
        NativeLayoutPatch fallback = defaults(gui);
        JsonObject target = target(gui);
        NativeLayoutPatch saved = load(target);
        // Cache legacy migration too: opening/previewing must not repeatedly scan the layout directory.
        String legacyKey = key(target) + ":legacy:" + ContainerInventoryRegion.of(gui.inventorySlots).nativeSlotCount;
        if (saved == null) {
            if (!SAVED.containsKey(legacyKey)) SAVED
                .put(legacyKey, loadLegacy(target, ContainerInventoryRegion.of(gui.inventorySlots).nativeSlotCount));
            saved = SAVED.get(legacyKey);
        }
        return saved == null ? fallback : saved;
    }

    /** Old files used transfer revisions in their names. Never guess between conflicting saved edits. */
    private static NativeLayoutPatch loadLegacy(JsonObject target, int nativeSlots) {
        if (!Files.isDirectory(directory) || target.get("presentationRevision")
            .getAsInt() != 1) return null;
        NativeLayoutPatch chosen = null;
        int chosenPriority = 0;
        boolean conflict = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                try {
                    if (Files.size(file) > 16384) continue;
                    JsonObject root = new JsonParser()
                        .parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                    NativeLayoutPatch candidate = decodeLegacy(root, target, nativeSlots);
                    // A shared schema-4 family edit wins over the older per-chest-size copy.
                    int priority = root.getAsJsonObject("target")
                        .has("nativeSlotCount") ? 1 : 2;
                    if (priority > chosenPriority) {
                        chosen = candidate;
                        chosenPriority = priority;
                        conflict = false;
                    } else if (priority == chosenPriority && !chosen.toJson()
                        .equals(candidate.toJson())) conflict = true;
                } catch (IOException | RuntimeException unrelated) {
                    // This file may belong to another GUI, capacity, or schema. Leave it intact.
                }
            }
        } catch (IOException failure) {
            DynamicInventory.LOG.warn("Could not inspect legacy inventory layouts", failure);
            return null; // An incomplete directory scan cannot rule out a conflicting saved edit.
        }
        if (conflict)
            DynamicInventory.LOG.warn("Conflicting legacy layouts for {}; keeping files for manual selection", target);
        return conflict ? null : chosen;
    }

    /** Pure migration check used by the headless editor fixture. Revision 1 retains the old geometry contract. */
    public static NativeLayoutPatch decodeLegacy(JsonObject root, JsonObject target, int nativeSlots) {
        JsonObject old = root.getAsJsonObject("target");
        if (target.get("presentationRevision")
            .getAsInt() != 1 || !old.has("adapterRevision")
            || !old.get("adapterRevision")
                .isJsonPrimitive()
            || !old.get("adapterRevision")
                .getAsJsonPrimitive()
                .isNumber()
            || !old.get("adapterRevision")
                .getAsString()
                .matches("[1-9][0-9]*")
            || !root.get("schemaVersion")
                .getAsString()
                .matches("[1-4]"))
            throw new IllegalArgumentException("Not a compatible legacy layout");
        JsonObject expected = new JsonObject();
        for (Map.Entry<String, JsonElement> field : target.entrySet()) if (!field.getKey()
            .equals("presentationRevision")) expected.add(field.getKey(), field.getValue());
        expected.add("adapterRevision", old.get("adapterRevision"));
        if (old.has("nativeSlotCount")) expected.addProperty("nativeSlotCount", nativeSlots);
        return decode(root, expected);
    }

    private static NativeLayoutPatch load(JsonObject target) {
        String key = key(target);
        if (!SAVED.containsKey(key)) {
            NativeLayoutPatch loaded = null;
            Path file = file(target);
            try {
                if (Files.exists(file)) {
                    if (Files.size(file) > 16384) throw new IOException("Layout patch exceeds 16 KiB");
                    JsonObject root = new JsonParser()
                        .parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                    loaded = decode(root, target);
                }
            } catch (IOException | RuntimeException invalid) {
                DynamicInventory.LOG.warn("Ignoring invalid local inventory layout {}", file, invalid);
            }
            // Cache misses too: a corrupt file must not be parsed/logged on every preview frame.
            SAVED.put(key, loaded);
        }
        return SAVED.get(key);
    }

    public static void draft(GuiContainer gui, NativeLayoutPatch patch) {
        if (patch == null) {
            DRAFTS.remove(gui);
            PREVIEW_MODES.remove(gui);
        } else DRAFTS.put(gui, patch);
    }

    public static Path save(GuiContainer gui, NativeLayoutPatch patch) throws IOException {
        JsonObject target = target(gui);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 5);
        root.add("target", target);
        root.add("layout", patch.toJson());
        Path destination = file(target);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "layout-", ".tmp");
        try {
            Files.write(
                temporary,
                JSON.toJson(root)
                    .getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        // Do not update the active preference until the file was successfully replaced.
        SAVED.put(key(target), patch);
        return destination;
    }

    /** Strict target matching protects GUI, presentation revision and capacity; transfer revisions are separate. */
    public static NativeLayoutPatch decode(JsonObject root, JsonObject expectedTarget) {
        if (root.entrySet()
            .size() != 3 || !root.has("schemaVersion")
            || !root.get("schemaVersion")
                .isJsonPrimitive()
            || !root.get("schemaVersion")
                .getAsJsonPrimitive()
                .isNumber()
            || !("1".equals(
                root.get("schemaVersion")
                    .getAsString())
                || "2".equals(
                    root.get("schemaVersion")
                        .getAsString())
                || "3".equals(
                    root.get("schemaVersion")
                        .getAsString())
                || "4".equals(
                    root.get("schemaVersion")
                        .getAsString())
                || "5".equals(
                    root.get("schemaVersion")
                        .getAsString()))
            || !expectedTarget.equals(root.get("target")))
            throw new IllegalArgumentException("Unsupported schema or mismatched inventory layout target");
        return NativeLayoutPatch.fromJson(root.getAsJsonObject("layout"));
    }

    private static JsonObject target(GuiContainer gui) {
        InventoryUiAdapter adapter = InventoryUi.findAdapter(gui.inventorySlots);
        ContainerInventoryRegion region = ContainerInventoryRegion.of(gui.inventorySlots);
        if (adapter == null || region == null || InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) == null)
            throw new IllegalArgumentException("Only attached, registered native screens can have local layouts");
        JsonObject target = new JsonObject();
        target.addProperty("adapterId", adapter.getId());
        target.addProperty(
            "presentationRevision",
            InventoryUi.findLayout(gui.inventorySlots, gui.getClass())
                .getPresentationRevision());
        target.addProperty(
            "containerClass",
            gui.inventorySlots.getClass()
                .getName());
        target.addProperty(
            "guiClass",
            gui.getClass()
                .getName());
        // Presentation only: chest capacity may vary within this exact adapter/GUI family.
        // The server opening contract still validates the native slot map independently.
        target.addProperty("ordinarySlotCount", region.getOrdinarySlotCount());
        return target;
    }

    private static Path file(JsonObject target) {
        if (directory == null) throw new IllegalStateException("Local inventory layout directory not initialized");
        return directory.resolve(key(target) + ".json");
    }

    private static String key(JsonObject target) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(
                    target.toString()
                        .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
