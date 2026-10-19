package dev.gilbe.dynamicinventory.compat.mui1;

import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;

/** Keeps viewport visibility separate from the slot's access restrictions. */
public final class PlayerInventorySlot extends BaseSlot {

    private boolean viewportVisible = true;

    public PlayerInventorySlot(Mui1PlayerInventory inventory, int index) {
        super(inventory, index);
    }

    void setViewportVisible(boolean visible) {
        viewportVisible = visible;
    }

    @Override
    public boolean isEnabled() {
        return viewportVisible && super.isEnabled();
    }

    /** Hidden storage still accepts shift transfers; explicitly disabled storage does not. */
    public boolean isTransferEnabled() {
        return super.isEnabled() && (!(getParentWidget() instanceof PlayerInventorySlotWidget)
            || ((PlayerInventorySlotWidget) getParentWidget()).isInteractionEnabled());
    }
}
