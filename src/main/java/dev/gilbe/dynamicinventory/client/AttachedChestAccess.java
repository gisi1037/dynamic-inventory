package dev.gilbe.dynamicinventory.client;

import net.minecraft.inventory.Slot;

/** Native attached-chest roles supplied by the crafting station, without replacing its GUI or slots. */
public interface AttachedChestAccess extends NativeHeaderAccess {

    boolean dynamicinventory$splitChest();

    boolean dynamicinventory$hasDescription();

    int dynamicinventory$chestWidth();

    int dynamicinventory$chestHeight();

    /** Refit the native viewport during an editor preview, preserving all container slot identities. */
    void dynamicinventory$refreshChestLayout();

    boolean dynamicinventory$isChestSlot(Slot slot);

    void dynamicinventory$positionText(int craftingOriginX);

    /** Draw both panels with the native chest's resource-pack border, without changing its slot geometry. */
    void dynamicinventory$drawFrame(int left, int top, int width, int height);
}
