package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.nbt.NBTTagCompound;

/** Opaque rejected save data, retained for recovery rather than inserted as playable items. */
public interface ExpandedInventoryAccess {

    NBTTagCompound dynamicinventory$getRecovery();

    void dynamicinventory$setRecovery(NBTTagCompound recovery);
}
