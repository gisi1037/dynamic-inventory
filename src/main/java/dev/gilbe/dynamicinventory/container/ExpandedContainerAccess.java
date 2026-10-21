package dev.gilbe.dynamicinventory.container;

import net.minecraft.inventory.Slot;

/** Container-local protocol state; absent for interfaces without an explicit adapter. */
public interface ExpandedContainerAccess {

    /** Server-safe counterpart to vanilla's client-only listener removal method. */
    void dynamicinventory$removeListener(net.minecraft.inventory.ICrafting listener);

    ContainerInventoryRegion dynamicinventory$region();

    void dynamicinventory$region(ContainerInventoryRegion region);

    Slot dynamicinventory$append(Slot slot);
}
