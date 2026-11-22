package dev.gilbe.dynamicinventory.compat;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.bogosorter.common.PinnedSlots;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.utils.item.SlotItemHandler;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;

import dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Pins belong to logical storage slots and follow them between panel layouts. */
public final class BogoExpandedPins {

    public static final int MAX_WORDS = (InventoryShape.MAX_STORAGE - InventoryShape.MIN_STORAGE + 31) / 32;
    private static final String TAG = "dynamicinventoryBogoPins";

    private BogoExpandedPins() {}

    public static int index(SlotAccessor slot, InventoryPlayer owner) {
        if (slot == null) return -1;
        int index = slot.callGetSlotIndex();
        if (slot instanceof BaseSlot legacy && legacy.getItemHandler() instanceof Mui1PlayerInventory wrapped) {
            InventoryPlayer inventory = wrapped.getInventoryPlayer();
            InventoryShape shape = InventoryLayout.of(inventory);
            if ((owner != null && owner != inventory) || !shape.isExtra(index)) return -1;
            return indexFromRaw(inventory, shape.rawIndex(index));
        }
        if (slot instanceof SlotItemHandler handler
            && handler.getItemHandler() instanceof PlayerMainInvWrapper wrapped) {
            InventoryPlayer inventory = wrapped.getInventoryPlayer();
            return owner == null || owner == inventory ? indexFromRaw(inventory, index) : -1;
        }
        if (slot.getInventory() instanceof InventoryPlayer inventory && (owner == null || owner == inventory))
            return indexFromRaw(inventory, index);
        return -1;
    }

    public static int indexFromRaw(InventoryPlayer inventory, int raw) {
        InventoryShape shape = InventoryLayout.of(inventory);
        int ordinary = shape.ordinaryIndex(raw);
        return shape.isExtra(ordinary) && !HotbarBanks.isReserved(inventory, raw)
            ? ordinary - InventoryShape.LEGACY_MAIN_SLOTS
            : -1;
    }

    public static boolean isPinned(EntityPlayer player, int index) {
        return index >= 0 && PinnedSlots.isSet(mask(player), index);
    }

    public static int[] mask(EntityPlayer player) {
        return player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getIntArray(TAG);
    }

    public static void toggle(EntityPlayer player, int index) {
        int[] saved = mask(player);
        int word = index / Integer.SIZE;
        int[] updated = Arrays.copyOf(saved, Math.min(MAX_WORDS, Math.max(saved.length, word + 1)));
        updated[word] ^= 1 << (index % Integer.SIZE);
        setMask(player, updated);
    }

    public static void setMask(EntityPlayer player, int[] mask) {
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setIntArray(TAG, mask);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    public interface Sync {

        void dynamicinventory$setExpandedPins(int[] mask);

        int[] dynamicinventory$getExpandedPins();
    }
}
