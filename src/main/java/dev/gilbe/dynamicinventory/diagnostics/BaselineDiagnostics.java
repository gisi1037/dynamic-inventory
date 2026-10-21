package dev.gilbe.dynamicinventory.diagnostics;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.Tags;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Read-only reports. Does not call inventory mutators, slotClick, or item update callbacks. */
public final class BaselineDiagnostics {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Set<String> SUPPORT_MODS = new HashSet<>(
        Arrays.asList(
            "mcp",
            "FML",
            "Forge",
            "dynamicinventory",
            "unimixins",
            "unimixins-mixin",
            "unimixins-compat",
            "mixingasm",
            "spongemixins",
            "mixinbooterlegacy",
            "gtnhmixins",
            "mixinextras",
            "gtnhlib",
            "GTNHLib Core",
            "gtnhextlib",
            "hodgepodge",
            "lwjgl3ify"));
    private static File reportDirectory;

    private BaselineDiagnostics() {}

    public static void initialize(File configDirectory) {
        reportDirectory = new File(
            configDirectory.getAbsoluteFile()
                .getParentFile(),
            "diagnostics/dynamicinventory");
    }

    public static void writeRuntimeManifest() {
        JsonObject manifest = header();
        JsonArray mods = new JsonArray();
        JsonArray unexpected = new JsonArray();
        List<ModContainer> containers = new ArrayList<>(
            Loader.instance()
                .getActiveModList());
        containers.sort(Comparator.comparing(ModContainer::getModId));
        for (ModContainer mod : containers) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mod.getModId());
            entry.addProperty("name", mod.getName());
            entry.addProperty("version", mod.getVersion());
            entry.addProperty(
                "source",
                mod.getSource() == null ? "unknown"
                    : mod.getSource()
                        .getName());
            mods.add(entry);
            if (!SUPPORT_MODS.contains(mod.getModId())) unexpected.add(new JsonPrimitive(mod.getModId()));
        }
        manifest.addProperty("javaVersion", System.getProperty("java.version"));
        manifest.addProperty("javaVendor", System.getProperty("java.vendor"));
        manifest.addProperty("supportLibrariesOnly", unexpected.size() == 0);
        manifest.add("mods", mods);
        manifest.add("modsRequiringProfileReview", unexpected);
        try {
            write("runtime.json", manifest);
            DynamicInventory.LOG.info(
                "Inventory baseline runtime: {} mods, {} outside the isolated support profile",
                containers.size(),
                unexpected.size());
        } catch (IOException e) {
            DynamicInventory.LOG.error("Could not write inventory baseline runtime manifest", e);
        }
    }

    public static JsonObject snapshot(EntityPlayer player, String screen, String reason) {
        JsonObject report = header();
        report.addProperty("reason", reason);
        report.addProperty("logicalSide", player.worldObj.isRemote ? "client" : "server");
        report.addProperty("player", player.getCommandSenderName());
        report.addProperty("dimension", player.dimension);
        report.addProperty("screen", screen);
        report.addProperty("mainSlots", player.inventory.mainInventory.length);
        report.addProperty(
            "ordinarySlots",
            InventoryLayout.of(player.inventory)
                .mainSlots());
        report.addProperty("offhandIndex", BackhandCompatibility.enabled() ? InventoryLayout.OFFHAND_INDEX : -1);
        report.addProperty("armorSlots", player.inventory.armorInventory.length);
        report.addProperty("inventoryApiSlots", player.inventory.getSizeInventory());
        report.addProperty("selectedIndex", player.inventory.currentItem);
        report.addProperty("cursorOccupied", player.inventory.getItemStack() != null);
        JsonArray main = new JsonArray();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("inventoryIndex", i);
            entry.addProperty(
                "ordinaryIndex",
                InventoryLayout.of(player.inventory)
                    .ordinaryIndex(i));
            describeItem(entry, player.inventory.mainInventory[i]);
            main.add(entry);
        }
        report.add("mainInventory", main);
        report.addProperty(
            "hasRecoveryData",
            !((ExpandedInventoryAccess) player.inventory).dynamicinventory$getRecovery()
                .hasNoTags());
        report.addProperty(
            "storagePrototypeLayoutMatches",
            matchesLayout(
                player,
                InventoryLayout.of(player.inventory)
                    .mainSlots()));
        report.addProperty("expandedPlayerLayoutMatches", matchesExpandedLayout(player));
        report.add("playerContainer", container(player, player.inventoryContainer));
        report.add("activeContainer", container(player, player.openContainer));
        report.addProperty("vanillaPlayerLayoutMatches", matchesVanillaLayout(player));
        return report;
    }

    public static void capture(EntityPlayer player, String screen, String reason, boolean notify) {
        JsonObject report = snapshot(player, screen, reason);
        String side = player.worldObj.isRemote ? "client" : "server";
        String name = side + "-" + timestamp("yyyyMMdd-HHmmss-SSS") + "-" + SEQUENCE.incrementAndGet() + ".json";
        try {
            File file = write(name, report);
            String summary = "Inventory baseline (" + side
                + "): "
                + player.inventory.mainInventory.length
                + " main, "
                + player.inventory.armorInventory.length
                + " armor; player container "
                + slotCount(player.inventoryContainer)
                + ", active "
                + slotCount(player.openContainer)
                + "; expanded layout="
                + matchesExpandedLayout(player);
            DynamicInventory.LOG.info("{}; report={}", summary, file.getAbsolutePath());
            if (notify) {
                player.addChatMessage(new ChatComponentText(summary));
                player.addChatMessage(new ChatComponentText("Saved diagnostics/dynamicinventory/" + name));
            }
        } catch (IOException e) {
            DynamicInventory.LOG.error("Could not write inventory baseline report", e);
            if (notify) player.addChatMessage(new ChatComponentText("Baseline report failed; see the game log."));
        }
    }

    private static JsonObject container(EntityPlayer player, Container container) {
        JsonObject result = new JsonObject();
        result.addProperty(
            "class",
            container == null ? "none"
                : container.getClass()
                    .getName());
        result.addProperty("slotCount", slotCount(container));
        JsonArray slots = new JsonArray();
        if (container != null) {
            result.addProperty("windowId", container.windowId);
            for (Object entry : container.inventorySlots) {
                Slot slot = (Slot) entry;
                JsonObject record = new JsonObject();
                record.addProperty("containerSlot", slot.slotNumber);
                record.addProperty("inventoryIndex", slot.getSlotIndex());
                record.addProperty(
                    "inventoryClass",
                    slot.inventory.getClass()
                        .getName());
                record.addProperty(
                    "slotClass",
                    slot.getClass()
                        .getName());
                record.addProperty("role", role(player, slot));
                record.addProperty("x", slot.xDisplayPosition);
                record.addProperty("y", slot.yDisplayPosition);
                record.addProperty("limit", slot.getSlotStackLimit());
                describeItem(record, slot.getStack());
                slots.add(record);
            }
        }
        result.add("slots", slots);
        return result;
    }

    private static String role(EntityPlayer player, Slot slot) {
        int index = slot.getSlotIndex();
        if (slot.inventory == player.inventory) {
            if (index >= 0 && index < 9) return "hotbar";
            if (index >= 9 && index < 36) return "storage";
            if (BackhandCompatibility.enabled() && index == InventoryLayout.OFFHAND_INDEX) return "offhand";
            if (InventoryLayout.of(player.inventory)
                .ordinaryIndex(index) >= 36) return "additional-storage";
            int armor = index - player.inventory.mainInventory.length;
            if (armor >= 0 && armor < player.inventory.armorInventory.length) return "armor-" + armor;
            return "player-unclassified";
        }
        if (slot.inventory instanceof InventoryCrafting) return "crafting-input";
        if (slot.inventory instanceof InventoryCraftResult) return "crafting-result";
        return "external";
    }

    private static boolean matchesVanillaLayout(EntityPlayer player) {
        return matchesLayout(player, InventoryLayout.LEGACY_MAIN_SLOTS);
    }

    private static boolean matchesExpandedLayout(EntityPlayer player) {
        Container container = player.inventoryContainer;
        if (player.inventory.mainInventory.length != InventoryLayout.of(player.inventory)
            .rawMainSlots() || player.inventory.armorInventory.length != InventoryLayout.ARMOR_SLOTS
            || slotCount(container) != PlayerContainerLayout.totalSlots(player.inventory)) return false;
        for (int id = 0; id < PlayerContainerLayout.slotCount(player.inventory); id++) {
            Slot slot = container.getSlot(id);
            if (slot.slotNumber != id) return false;
            if (id == 0 && !(slot.inventory instanceof InventoryCraftResult)) return false;
            if (id >= 1 && id < 5 && (!(slot.inventory instanceof InventoryCrafting) || slot.getSlotIndex() != id - 1))
                return false;
            if (id >= 5 && id < 9
                && (slot.inventory != player.inventory || slot.getSlotIndex() != InventoryLayout.of(player.inventory)
                    .rawMainSlots() + 8
                    - id || slot.getSlotStackLimit() != 1))
                return false;
            if (id >= 9) {
                if (BackhandCompatibility.enabled() && id == PlayerContainerLayout.OFFHAND) {
                    if (slot.inventory != player.inventory || slot.getSlotIndex() != InventoryLayout.OFFHAND_INDEX
                        || !slot.getClass()
                            .getName()
                            .equals("xonin.backhand.api.core.BackhandSlot"))
                        return false;
                    continue;
                }
                int main = PlayerContainerLayout.mainIndex(player.inventory, id);
                if (slot.inventory != player.inventory || slot.getSlotIndex() != InventoryLayout.of(player.inventory)
                    .rawIndex(main)) return false;
                int x = main < InventoryLayout.HOTBAR_SLOTS ? 8 + main * 18 : PlayerContainerLayout.storageX(main);
                int y = main < InventoryLayout.HOTBAR_SLOTS ? PlayerContainerLayout.hotbarY(player.inventory)
                    : PlayerContainerLayout.storageY(main);
                if (!player.worldObj.isRemote && (slot.xDisplayPosition != x || slot.yDisplayPosition != y))
                    return false;
            }
        }
        return true;
    }

    private static void describeItem(JsonObject record, ItemStack stack) {
        if (stack == null) return;
        record.addProperty("item", String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())));
        record.addProperty("count", stack.stackSize);
        record.addProperty("damage", stack.getItemDamage());
        record.addProperty("hasNbt", stack.hasTagCompound());
        if (stack.hasTagCompound()) record.addProperty(
            "nbt",
            stack.getTagCompound()
                .toString());
        if (stack.hasTagCompound()) record.addProperty("nbtCanonical", canonicalNbt(stack.getTagCompound()).toString());
    }

    private static JsonObject canonicalNbt(NBTBase tag) {
        JsonObject result = new JsonObject();
        result.addProperty("type", tag.getId());
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            JsonObject entries = new JsonObject();
            for (String key : new TreeSet<>(compound.func_150296_c()))
                entries.add(key, canonicalNbt(compound.getTag(key)));
            result.add("entries", entries);
        } else if (tag instanceof NBTTagList) {
            NBTTagList copy = (NBTTagList) tag.copy();
            result.addProperty("elementType", copy.func_150303_d());
            JsonArray values = new JsonArray();
            while (copy.tagCount() > 0) values.add(canonicalNbt(copy.removeTag(0)));
            result.add("values", values);
        } else if (tag instanceof NBTTagByteArray) {
            JsonArray values = new JsonArray();
            for (byte value : ((NBTTagByteArray) tag).func_150292_c()) values.add(new JsonPrimitive(value));
            result.add("values", values);
        } else if (tag instanceof NBTTagIntArray) {
            JsonArray values = new JsonArray();
            for (int value : ((NBTTagIntArray) tag).func_150302_c()) values.add(new JsonPrimitive(value));
            result.add("values", values);
        } else result.addProperty("value", tag.toString());
        return result;
    }

    private static boolean matchesLayout(EntityPlayer player, int mainSlots) {
        Container container = player.inventoryContainer;
        if (player.inventory.mainInventory.length != mainSlots || player.inventory.armorInventory.length != 4
            || slotCount(container) != 45) return false;
        for (int id = 0; id < 45; id++) {
            Slot slot = (Slot) container.inventorySlots.get(id);
            if (slot.slotNumber != id) return false;
            if (id == 0 && !(slot.inventory instanceof InventoryCraftResult)) return false;
            if (id >= 1 && id <= 4 && (!(slot.inventory instanceof InventoryCrafting) || slot.getSlotIndex() != id - 1))
                return false;
            if (id >= 5 && id <= 8
                && (slot.inventory != player.inventory || slot.getSlotIndex() != mainSlots + 8 - id
                    || slot.getSlotStackLimit() != 1))
                return false;
            if (id >= 9 && id <= 35 && (slot.inventory != player.inventory || slot.getSlotIndex() != id)) return false;
            if (id >= 36 && (slot.inventory != player.inventory || slot.getSlotIndex() != id - 36)) return false;
        }
        return true;
    }

    private static int slotCount(Container container) {
        return container == null ? 0 : container.inventorySlots.size();
    }

    private static JsonObject header() {
        JsonObject result = new JsonObject();
        result.addProperty("reportSchema", 4);
        result.addProperty("modVersion", Tags.VERSION);
        result.addProperty("capturedAtUtc", timestamp("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        return result;
    }

    private static String timestamp(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static File write(String name, JsonObject report) throws IOException {
        Files.createDirectories(reportDirectory.toPath());
        File file = new File(reportDirectory, name);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            JSON.toJson(report, writer);
        }
        return file;
    }
}
