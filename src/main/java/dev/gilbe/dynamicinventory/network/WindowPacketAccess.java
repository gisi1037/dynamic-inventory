package dev.gilbe.dynamicinventory.network;

import net.minecraft.item.ItemStack;

/** Packet getters in Minecraft are client-only; these accessors also exist on a dedicated server. */
public interface WindowPacketAccess {

    int dynamicinventory$windowId();

    default ItemStack[] dynamicinventory$contents() {
        return null;
    }
}
