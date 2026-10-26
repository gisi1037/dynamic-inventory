package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

/** Small construction seam: AE2 owns its player-slot type and locked-slot decisions. */
public interface AppliedEnergisticsContainerAccess {

    Slot dynamicinventory$playerSlot(InventoryPlayer inventory, int rawIndex);
}
