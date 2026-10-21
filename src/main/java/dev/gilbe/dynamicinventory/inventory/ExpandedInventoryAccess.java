package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.nbt.NBTTagCompound;

/** Internal inventory-owned state. Setters are lifecycle hooks, not an unsynchronized resize API. */
public interface ExpandedInventoryAccess {

    InventoryShape dynamicinventory$getShape();

    void dynamicinventory$setShape(InventoryShape shape);

    boolean dynamicinventory$hasCapacityOverride();

    void dynamicinventory$setCapacityOverride(boolean overridden);

    NBTTagCompound dynamicinventory$getRecovery();

    void dynamicinventory$setRecovery(NBTTagCompound recovery);
}
