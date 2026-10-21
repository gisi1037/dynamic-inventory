package dev.gilbe.dynamicinventory.compat.openblocks;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import openmods.inventory.GenericInventory;
import openmods.utils.ItemUtils;

/** Used only by OpenBlocks graves; ordinary OpenMods inventories and backup dumps keep their format. */
public final class GraveInventory extends GenericInventory {

    public GraveInventory(String title, boolean localized, int size) {
        super(title, localized, size);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("size", slotsCount);
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < inventoryContents.length; slot++) {
            if (inventoryContents[slot] == null) continue;
            NBTTagCompound entry = ItemUtils.writeStack(inventoryContents[slot]);
            // The native byte writer wraps after 255; keep the complete grave index instead.
            entry.setInteger("Slot", slot);
            items.appendTag(entry.copy());
        }
        tag.setTag("Items", items);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        // The grave mixin validates/repackages indices before native origin hints are read.
        // Assign arrays directly, as the native loader does: no per-slot callbacks or clamping.
        int size = tag.getInteger("size");
        ItemStack[] loaded = new ItemStack[size];
        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = GraveSlotCodec.readSlot(entry);
            loaded[slot] = ItemUtils.readStack((NBTTagCompound) entry.copy());
        }
        slotsCount = size;
        inventoryContents = loaded;
    }
}
