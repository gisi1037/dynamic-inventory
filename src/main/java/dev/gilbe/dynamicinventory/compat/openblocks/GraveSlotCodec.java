package dev.gilbe.dynamicinventory.compat.openblocks;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Grave slot identities only. OpenMods still owns decoding the actual item stacks. */
public final class GraveSlotCodec {

    private GraveSlotCodec() {}

    /** New graves use ints; old byte tags are unsigned, just like vanilla inventory slots. */
    public static int readSlot(NBTTagCompound entry) {
        if (entry.hasKey("Slot", 3)) return entry.getInteger("Slot");
        if (entry.hasKey("Slot", 1)) return entry.getByte("Slot") & 255;
        return -1;
    }

    /**
     * A legacy byte cannot identify a slot uniquely in a grave larger than 256 slots.
     * Preserve every surviving item record, but discard the uncertain placement hints:
     * OpenBlocks can then reclaim normally without writing an item to the wrong origin.
     * The same rule avoids overwriting records with duplicate or invalid slot numbers.
     * Never change the caller's NBT, or infer identities from the order of a sparse list.
     */
    public static NBTTagCompound prepareLoad(NBTTagCompound source) {
        int size = source.getInteger("size");
        NBTTagList items = source.getTagList("Items", 10);
        Set<Integer> seen = new HashSet<>();
        boolean repack = size < 0;
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = readSlot(entry);
            if (slot < 0 || slot >= size || !seen.add(slot) || (size > 256 && entry.hasKey("Slot", 1))) {
                repack = true;
            }
        }
        if (!repack) return source;

        NBTTagCompound result = (NBTTagCompound) source.copy();
        NBTTagList copiedItems = result.getTagList("Items", 10);
        for (int i = 0; i < copiedItems.tagCount(); i++) copiedItems.getCompoundTagAt(i)
            .setInteger("Slot", i);
        result.setInteger("size", copiedItems.tagCount());
        // These hints refer to the old grave indices, which are now intentionally unknown.
        // Owner, death message, item NBT and every other tile field remain untouched.
        result.removeTag("SlotOrigins");
        return result;
    }
}
