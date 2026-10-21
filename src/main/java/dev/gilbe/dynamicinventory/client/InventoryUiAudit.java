package dev.gilbe.dynamicinventory.client;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Read-only capture of a container's visible player-slot layout for compatibility audits. */
public final class InventoryUiAudit {

    private static final int MAX_SLOTS = 4096;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .create();

    private InventoryUiAudit() {}

    /** Captures a bounded snapshot and writes it below the game's diagnostics directory. */
    public static File capture(GuiContainer gui) throws Exception {
        if (gui == null || gui.inventorySlots == null) throw new IllegalArgumentException("gui/container is null");
        Container container = gui.inventorySlots;
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("capturedAtUtc", utcNow());
        root.addProperty("packTarget", "2.9.0-RC-2");
        root.addProperty(
            "containerClass",
            container.getClass()
                .getName());
        root.addProperty(
            "screenClass",
            gui.getClass()
                .getName());
        root.addProperty("framework", framework(gui, container));
        int total = container.inventorySlots.size();
        root.addProperty("slotCount", total);

        Integer nativeCount = null;
        if (container instanceof ExpandedContainerAccess) {
            ContainerInventoryRegion region = ((ExpandedContainerAccess) container).dynamicinventory$region();
            if (region != null) nativeCount = region.nativeSlotCount;
        }
        if (nativeCount == null) root.add("nativeSlotCount", null);
        else root.addProperty("nativeSlotCount", nativeCount);
        InventoryUiAdapter adapter = InventoryUi.findAdapter(container);
        if (adapter == null) root.add("adapterId", null);
        else root.addProperty("adapterId", adapter.getId());
        String refusal = InventoryUi.getRefusalReason(container);
        root.addProperty("refusalReason", refusal);

        JsonArray slots = new JsonArray();
        List<String> reasons = new ArrayList<String>();
        if (refusal != null) reasons.add("Automatic attachment was refused: " + refusal);
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        int external = 0;
        boolean truncated = total > MAX_SLOTS;
        int inspected = Math.min(total, MAX_SLOTS);
        InventoryPlayer canonical = player == null ? null : player.inventory;
        for (int i = 0; i < inspected; i++) {
            Slot slot = (Slot) container.inventorySlots.get(i);
            SlotMapping mapping = mapPlayerSlot(slot, canonical);
            if (mapping == null) {
                external++;
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("containerSlot", slot.slotNumber);
            entry.addProperty("rawIndex", mapping.rawIndex);
            entry.addProperty("ordinaryIndex", mapping.ordinaryIndex);
            entry.addProperty("x", slot.xDisplayPosition);
            entry.addProperty("y", slot.yDisplayPosition);
            entry.addProperty(
                "slotClass",
                slot.getClass()
                    .getName());
            slots.add(entry);
            if (mapping.ordinaryIndex >= 0) counts.put(
                mapping.ordinaryIndex,
                counts.containsKey(mapping.ordinaryIndex) ? counts.get(mapping.ordinaryIndex) + 1 : 1);
        }
        root.add("playerSlots", slots);
        root.addProperty("externalSlotCount", external);

        JsonArray missing = new JsonArray();
        JsonArray duplicates = new JsonArray();
        for (int index = 0; index < 36; index++) {
            Integer count = counts.get(index);
            if (count == null || count == 0) missing.add(new JsonPrimitive(index));
        }
        for (int index = 0; index < (canonical == null ? 36
            : InventoryLayout.of(canonical)
                .mainSlots()); index++)
            if (counts.containsKey(index) && counts.get(index) > 1) duplicates.add(new JsonPrimitive(index));
        root.add("missingLegacyIndices", missing);
        root.add("duplicateOrdinaryIndices", duplicates);
        if (player == null) reasons.add("No local player was available to identify canonical inventory slots.");
        if (truncated) reasons.add("Container exceeded the 4096-slot audit limit; remaining slots were not inspected.");
        if (missing.size() > 0) reasons.add("One or more legacy player indices were missing.");
        if (duplicates.size() > 0) reasons.add("One or more ordinary player indices appeared more than once.");
        boolean candidate = player != null && refusal == null
            && !truncated
            && missing.size() == 0
            && duplicates.size() == 0;
        if (candidate) reasons.add("Layout is structurally complete; screen behavior still requires manual review.");
        root.addProperty("candidate", candidate);
        JsonArray reasonArray = new JsonArray();
        for (String reason : reasons) reasonArray.add(new JsonPrimitive(reason));
        root.add("reasons", reasonArray);

        File directory = new File(minecraft.mcDataDir, "diagnostics/dynamicinventory");
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory())
            throw new java.io.IOException("Could not create audit directory");
        File output = new File(directory, "ui-audit-" + fileTimestamp() + ".json");
        try (Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(output), StandardCharsets.UTF_8)) {
            JSON.toJson(root, writer);
        }
        return output;
    }

    private static SlotMapping mapPlayerSlot(Slot slot, InventoryPlayer canonical) {
        if (canonical != null && slot.inventory == canonical) {
            int raw = slot.getSlotIndex();
            int ordinary = InventoryLayout.of(canonical)
                .ordinaryIndex(raw);
            return new SlotMapping(raw, ordinary);
        }
        Object handler = invokeNoArg(slot, "getItemHandler");
        if (handler == null) return null;
        String name = handler.getClass()
            .getName();
        if (!name.equals("dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory")
            && !name.equals("dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory")) return null;
        Object inv = invokeNoArg(handler, "getInventoryPlayer");
        if (canonical == null || inv != canonical) return null;
        Object index = invokeNoArg(slot, "getSlotIndex");
        if (!(index instanceof Number)) return null;
        if (name.equals("dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory")) {
            int raw = ((Number) index).intValue();
            return new SlotMapping(
                raw,
                InventoryLayout.of(canonical)
                    .ordinaryIndex(raw));
        }
        int ordinary = ((Number) index).intValue();
        if (ordinary < 0 || ordinary >= (canonical == null ? 36
            : InventoryLayout.of(canonical)
                .mainSlots()))
            return null;
        int raw = InventoryLayout.of(canonical)
            .rawIndex(ordinary);
        return new SlotMapping(raw, ordinary);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass()
                .getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static String framework(GuiContainer gui, Container container) {
        String name = gui.getClass()
            .getName()
            .toLowerCase(java.util.Locale.ROOT);
        if (name.contains("com.cleanroommc.modularui")) return "MUI2";
        if (name.contains("com.gtnewhorizons.modularui")) return "MUI1";
        int limit = Math.min(container.inventorySlots.size(), MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Object handler = invokeNoArg(container.inventorySlots.get(i), "getItemHandler");
            if (handler == null) continue;
            String handlerName = handler.getClass()
                .getName();
            if (handlerName.equals("dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory")) return "MUI2";
            if (handlerName.equals("dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory")) return "MUI1";
        }
        return "NATIVE";
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String fileTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class SlotMapping {

        final int rawIndex;
        final int ordinaryIndex;

        SlotMapping(int rawIndex, int ordinaryIndex) {
            this.rawIndex = rawIndex;
            this.ordinaryIndex = ordinaryIndex;
        }
    }
}
