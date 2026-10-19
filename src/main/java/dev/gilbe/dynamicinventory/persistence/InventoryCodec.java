package dev.gilbe.dynamicinventory.persistence;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Startup-capacity schema. No live inventory array is temporarily swapped during serialization. */
public final class InventoryCodec {

    public static final String ROOT_TAG = "dynamicinventory";
    public static final int SCHEMA_VERSION = 1;

    private InventoryCodec() {}

    /**
     * Inventory-only users (for example backup mods) receive the same extra/recovery schema as player saves.
     * The last compound is metadata, not an ItemStack: byte slot 255 is outside vanilla's main/armor slots,
     * and there is deliberately no item id. Ordinary entries retain their original byte slot identities.
     */
    public static NBTTagList writeInventory(InventoryPlayer inventory, NBTTagList list) {
        writeLegacy(inventory, list);
        NBTTagCompound root = new NBTTagCompound();
        writeExtra(inventory, root);
        NBTTagCompound extension = new NBTTagCompound();
        extension.setByte("Slot", (byte) 255);
        extension.setTag(ROOT_TAG, root.getTag(ROOT_TAG));
        list.appendTag(extension);
        return list;
    }

    public static void readInventory(InventoryPlayer inventory, NBTTagList list) {
        readLegacy(inventory, list);
        readInventoryData(inventory, list, new NBTTagCompound());
    }

    /** Read the legacy array and exactly one extra schema, even if a foreign writer saved both forms. */
    public static void readPlayerInventory(InventoryPlayer inventory, NBTTagList list, NBTTagCompound playerTag) {
        readLegacy(inventory, list);
        readInventoryData(inventory, list, playerTag);
    }

    private static void readInventoryData(InventoryPlayer inventory, NBTTagList list, NBTTagCompound playerTag) {
        // Established player saves use the top-level compound. Inventory-only lists use the inline copy.
        // Prefer the established player root if both exist, but retain a differing inline payload for repair.
        NBTBase selected = playerTag.hasKey(ROOT_TAG) ? playerTag.getTag(ROOT_TAG) : null;
        NBTTagList conflicts = new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!isInventoryData(entry)) continue;
            NBTBase inline = entry.getTag(ROOT_TAG);
            if (selected == null) selected = inline;
            else if (!playerTag.hasKey(ROOT_TAG) || !selected.equals(inline)) conflicts.appendTag(entry);
        }
        if (selected != null) {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag(ROOT_TAG, selected);
            readExtra(inventory, root);
        }
        // Append after reading saved recovery: an opaque or malformed schema must not hide these records.
        for (int i = 0; i < conflicts.tagCount(); i++)
            quarantine(inventory, conflicts.getCompoundTagAt(i), "inventory");
    }

    private static boolean isInventoryData(NBTTagCompound entry) {
        return entry.hasKey("Slot", 1) && (entry.getByte("Slot") & 255) == 255
            && entry.hasKey(ROOT_TAG)
            && !entry.hasKey("id");
    }

    public static NBTTagList writeLegacy(InventoryPlayer inventory, NBTTagList list) {
        for (int i = 0; i < InventoryLayout.LEGACY_MAIN_SLOTS; i++) {
            appendLegacy(list, inventory.mainInventory[i], i);
        }
        if (BackhandCompatibility.enabled()) {
            appendLegacy(list, inventory.mainInventory[InventoryLayout.OFFHAND_INDEX], InventoryLayout.OFFHAND_INDEX);
        }
        for (int i = 0; i < inventory.armorInventory.length; i++) {
            appendLegacy(list, inventory.armorInventory[i], 100 + i);
        }
        return list;
    }

    private static void appendLegacy(NBTTagList list, ItemStack stack, int index) {
        if (stack == null) return;
        NBTTagCompound entry = new NBTTagCompound();
        stack.writeToNBT(entry);
        entry.setByte("Slot", (byte) index);
        // Vanilla ItemStack.writeToNBT attaches its live custom tag by reference. Saved snapshots must own it.
        list.appendTag(entry.copy());
    }

    public static void readLegacy(InventoryPlayer inventory, NBTTagList list) {
        allocate(inventory);
        inventory.armorInventory = new ItemStack[InventoryLayout.ARMOR_SLOTS];
        access(inventory).dynamicinventory$setRecovery(new NBTTagCompound());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            // Metadata is decoded once by the caller, never as an invalid legacy item.
            if (isInventoryData(entry)) continue;
            int index = entry.getByte("Slot") & 255;
            ItemStack stack = decode(entry);
            ItemStack[] target = inventory.mainInventory;
            int targetIndex = index;
            boolean valid = entry.hasKey("Slot", 1) && index < InventoryLayout.LEGACY_MAIN_SLOTS;
            if (BackhandCompatibility.enabled() && entry.hasKey("Slot", 1)
                && index == InventoryLayout.LEGACY_MAIN_SLOTS) {
                targetIndex = InventoryLayout.OFFHAND_INDEX;
                valid = true;
            }
            if (entry.hasKey("Slot", 1) && index >= 100 && index < 100 + InventoryLayout.ARMOR_SLOTS) {
                target = inventory.armorInventory;
                targetIndex = index - 100;
                valid = true;
            }
            if (valid && stack != null && target[targetIndex] == null) target[targetIndex] = stack;
            else quarantine(inventory, entry, "legacy");
        }
    }

    public static void writeExtra(InventoryPlayer inventory, NBTTagCompound playerTag) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schemaVersion", SCHEMA_VERSION);
        data.setInteger("mainSlots", InventoryLayout.mainSlots());
        NBTTagList items = new NBTTagList();
        for (int i = InventoryLayout.LEGACY_MAIN_SLOTS; i < InventoryLayout.mainSlots(); i++) {
            ItemStack stack = inventory.mainInventory[InventoryLayout.rawIndex(i)];
            if (stack == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            stack.writeToNBT(entry);
            entry.setInteger("Slot", i);
            items.appendTag(entry.copy());
        }
        data.setTag("extraItems", items);
        NBTTagCompound recovery = access(inventory).dynamicinventory$getRecovery();
        if (!recovery.hasNoTags()) data.setTag("recovery", recovery.copy());
        playerTag.setTag(ROOT_TAG, data);
        // EntityPlayer calls InventoryPlayer.writeToNBT first. Keep its established on-disk layout:
        // extras/recovery live once at the player root, while Inventory contains only legacy item entries.
        // Replace the list instead of deleting from it, so callers retaining the standalone list keep it intact.
        if (playerTag.hasKey("Inventory", 9)) {
            NBTTagList inventoryList = playerTag.getTagList("Inventory", 10);
            NBTTagList legacy = new NBTTagList();
            boolean found = false;
            for (int i = 0; i < inventoryList.tagCount(); i++) {
                NBTTagCompound entry = inventoryList.getCompoundTagAt(i);
                if (isInventoryData(entry)) found = true;
                else legacy.appendTag(entry.copy());
            }
            if (found) playerTag.setTag("Inventory", legacy);
        }
    }

    /** Call immediately after legacy loading, which allocates and clears all ordinary slots. */
    public static void readExtra(InventoryPlayer inventory, NBTTagCompound playerTag) {
        if (!playerTag.hasKey(ROOT_TAG)) return;
        NBTBase raw = playerTag.getTag(ROOT_TAG);
        if (!(raw instanceof NBTTagCompound)) {
            preserveUnread(inventory, raw);
            return;
        }
        NBTTagCompound data = (NBTTagCompound) raw;
        if (!isSupportedData(data)) {
            preserveUnread(inventory, data);
            return;
        }
        mergeSavedRecovery(inventory, data);
        NBTTagList items = data.getTagList("extraItems", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int index = entry.getInteger("Slot");
            ItemStack stack = decode(entry);
            if (entry.hasKey("Slot", 3) && InventoryLayout.isExtra(index)
                && index < data.getInteger("mainSlots")
                && stack != null
                && inventory.mainInventory[InventoryLayout.rawIndex(index)] == null) {
                inventory.mainInventory[InventoryLayout.rawIndex(index)] = stack;
            } else if (entry.hasKey("Slot", 3) && index >= InventoryLayout.mainSlots()
                && index >= InventoryLayout.LEGACY_MAIN_SLOTS
                && index < data.getInteger("mainSlots")
                && stack != null) {
                    quarantine(inventory, entry, "capacity");
                } else quarantine(inventory, entry, "extra");
        }
        restoreCapacityOverflow(inventory);
    }

    private static boolean isSupportedData(NBTTagCompound data) {
        return data.hasKey("schemaVersion", 3) && data.getInteger("schemaVersion") == SCHEMA_VERSION
            && data.hasKey("mainSlots", 3)
            && data.getInteger("mainSlots") >= InventoryLayout.LEGACY_MAIN_SLOTS
            && data.hasKey("extraItems", 9)
            && isCompoundList(data.getTag("extraItems"));
    }

    /** Retain saved opaque data and any legacy entries rejected earlier in this same load. */
    private static void mergeSavedRecovery(InventoryPlayer inventory, NBTTagCompound data) {
        if (!data.hasKey("recovery")) return;
        NBTTagCompound current = access(inventory).dynamicinventory$getRecovery();
        if (data.hasKey("recovery", 10) && (!data.getCompoundTag("recovery")
            .hasKey("items") || isCompoundList(
                data.getCompoundTag("recovery")
                    .getTag("items")))) {
            NBTTagCompound saved = (NBTTagCompound) data.getCompoundTag("recovery")
                .copy();
            NBTTagList rejected = current.getTagList("items", 10);
            if (rejected.tagCount() > 0) {
                NBTTagList combined = saved.getTagList("items", 10);
                for (int i = 0; i < rejected.tagCount(); i++) combined.appendTag(
                    rejected.getCompoundTagAt(i)
                        .copy());
                saved.setTag("items", combined);
            }
            access(inventory).dynamicinventory$setRecovery(saved);
        } else current.setTag(
            "unreadRecovery",
            data.getTag("recovery")
                .copy());
    }

    private static void restoreCapacityOverflow(InventoryPlayer inventory) {
        NBTTagCompound recovery = access(inventory).dynamicinventory$getRecovery();
        NBTTagList records = recovery.getTagList("items", 10);
        NBTTagList retained = new NBTTagList();
        for (int i = 0; i < records.tagCount(); i++) {
            NBTTagCompound record = records.getCompoundTagAt(i);
            NBTTagCompound entry = record.getCompoundTag("entry");
            int index = entry.getInteger("Slot");
            ItemStack stack = "capacity".equals(record.getString("origin")) && entry.hasKey("Slot", 3)
                && InventoryLayout.isExtra(index) ? decode(entry) : null;
            if (stack != null && inventory.mainInventory[InventoryLayout.rawIndex(index)] == null)
                inventory.mainInventory[InventoryLayout.rawIndex(index)] = stack;
            else retained.appendTag(record.copy());
        }
        if (retained.tagCount() == 0) recovery.removeTag("items");
        else recovery.setTag("items", retained);
    }

    private static boolean isCompoundList(NBTBase tag) {
        if (!(tag instanceof NBTTagList)) return false;
        NBTTagList list = (NBTTagList) tag;
        return list.tagCount() == 0 || list.func_150303_d() == 10;
    }

    public static void copy(InventoryPlayer destination, InventoryPlayer source) {
        if (destination == source) return;
        int offhand = BackhandCompatibility.enabled() ? InventoryLayout.OFFHAND_INDEX : -1;
        allocate(destination);
        destination.armorInventory = new ItemStack[InventoryLayout.ARMOR_SLOTS];
        access(destination).dynamicinventory$setRecovery(
            (NBTTagCompound) access(source).dynamicinventory$getRecovery()
                .copy());
        for (int i = 0; i < source.mainInventory.length; i++) {
            ItemStack stack = source.mainInventory[i];
            int ordinary = BackhandCompatibility.enabled() ? (i < offhand ? i : i - 1) : i;
            if (i == offhand) destination.mainInventory[InventoryLayout.OFFHAND_INDEX] = ItemStack.copyItemStack(stack);
            else if (ordinary >= 0 && ordinary < InventoryLayout.mainSlots())
                destination.mainInventory[InventoryLayout.rawIndex(ordinary)] = ItemStack.copyItemStack(stack);
            else if (stack != null) {
                NBTTagCompound entry = new NBTTagCompound();
                stack.writeToNBT(entry);
                entry.setInteger("Slot", ordinary);
                quarantine(destination, entry, "capacity");
            }
        }
        for (int i = 0; i < source.armorInventory.length; i++) {
            if (i >= destination.armorInventory.length) throw new IllegalArgumentException("Unsupported armor layout");
            destination.armorInventory[i] = ItemStack.copyItemStack(source.armorInventory[i]);
        }
        destination.currentItem = source.currentItem >= 0 && source.currentItem < InventoryLayout.HOTBAR_SLOTS
            ? source.currentItem
            : 0;
    }

    private static void allocate(InventoryPlayer inventory) {
        inventory.mainInventory = new ItemStack[InventoryLayout.rawMainSlots()];
    }

    private static ItemStack decode(NBTTagCompound entry) {
        try {
            // Vanilla loading can retain the supplied custom-tag reference; never share it with saved/recovery NBT.
            ItemStack stack = ItemStack.loadItemStackFromNBT((NBTTagCompound) entry.copy());
            return stack != null && stack.stackSize > 0 && stack.stackSize <= stack.getMaxStackSize() ? stack : null;
        } catch (RuntimeException e) {
            DynamicInventory.LOG.warn("Could not decode an inventory entry; preserving raw NBT for recovery", e);
            return null;
        }
    }

    private static void quarantine(InventoryPlayer inventory, NBTTagCompound entry, String origin) {
        NBTTagCompound recovery = access(inventory).dynamicinventory$getRecovery();
        NBTTagList items = recovery.getTagList("items", 10);
        NBTTagCompound record = new NBTTagCompound();
        record.setString("origin", origin);
        record.setTag("entry", entry.copy());
        items.appendTag(record);
        recovery.setTag("items", items);
        DynamicInventory.LOG.warn("Preserved rejected {} inventory entry in {}.recovery: {}", origin, ROOT_TAG, entry);
    }

    private static void preserveUnread(InventoryPlayer inventory, NBTBase raw) {
        access(inventory).dynamicinventory$getRecovery()
            .setTag("unreadData", raw.copy());
        DynamicInventory.LOG
            .warn("Unsupported inventory save schema; preserved original data in {}.recovery", ROOT_TAG);
    }

    private static ExpandedInventoryAccess access(InventoryPlayer inventory) {
        return (ExpandedInventoryAccess) inventory;
    }
}
