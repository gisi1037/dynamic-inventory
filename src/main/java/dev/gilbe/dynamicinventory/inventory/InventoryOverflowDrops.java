package dev.gilbe.dynamicinventory.inventory;

import java.util.function.BooleanSupplier;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Player-owned drops remain in the save until the world accepts them. */
public final class InventoryOverflowDrops {

    private static final String TAG = "pendingGroundDrops";

    private InventoryOverflowDrops() {}

    static void append(NBTTagList drops, ItemStack stack, int remaining) {
        if (remaining <= 0) return;
        int limit = Math.max(1, Math.min(Byte.MAX_VALUE, stack.getMaxStackSize()));
        ItemStack drop = stack.copy();
        while (remaining > 0) {
            drop.stackSize = Math.min(remaining, limit);
            drops.appendTag(drop.writeToNBT(new NBTTagCompound()));
            remaining -= drop.stackSize;
        }
    }

    static void enqueue(InventoryPlayer inventory, NBTTagList drops) {
        if (drops.tagCount() == 0) return;
        NBTTagCompound recovery = ((ExpandedInventoryAccess) inventory).dynamicinventory$getRecovery();
        NBTTagList pending = recovery.getTagList(TAG, 10);
        for (int i = 0; i < drops.tagCount(); i++) pending.appendTag(drops.getCompoundTagAt(i));
        recovery.setTag(TAG, pending);
    }

    public static void flush(EntityPlayerMP player) {
        NBTTagCompound recovery = ((ExpandedInventoryAccess) player.inventory).dynamicinventory$getRecovery();
        NBTTagList pending = recovery.getTagList(TAG, 10);
        if (pending.tagCount() == 0) return;
        for (int i = 0; i < pending.tagCount();) {
            NBTTagCompound entry = (NBTTagCompound) pending.getCompoundTagAt(i)
                .copy();
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack == null || stack.stackSize <= 0) {
                i++; // An unavailable item stays in recovery, even if other queued items can drop.
                continue;
            }
            EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY + 0.25, player.posZ, stack);
            drop.delayBeforeCanPickup = 40;
            if (!spawn(player.worldObj, drop, () -> player.worldObj.spawnEntityInWorld(drop))) break;
            pending.removeTag(i);
            player.inventory.markDirty();
        }
        if (pending.tagCount() == 0) recovery.removeTag(TAG);
    }

    public static boolean spawnDeathDrop(EntityPlayerMP player, EntityItem drop, BooleanSupplier spawn) {
        if (spawn(player.worldObj, drop, spawn)) return true;
        ItemStack stack = drop.getEntityItem();
        if (stack != null && stack.stackSize > 0) {
            NBTTagList pending = new NBTTagList();
            append(pending, stack, stack.stackSize);
            enqueue(player.inventory, pending);
        }
        return false;
    }

    private static boolean spawn(World world, EntityItem drop, BooleanSupplier spawn) {
        try {
            return spawn.getAsBoolean();
        } catch (RuntimeException failure) {
            // World adds the entity to the loaded list before calling world listeners.
            DynamicInventory.LOG.warn("Could not finish spawning an inventory overflow item", failure);
            return world.loadedEntityList.contains(drop);
        }
    }
}
