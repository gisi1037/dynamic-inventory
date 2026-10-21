package dev.gilbe.dynamicinventory.compat.mui1;

import com.gtnewhorizons.modularui.common.widget.Scrollable;

public final class InventoryViewport extends Scrollable {

    private final int storageSlots;
    private boolean updatingVisibility;

    public InventoryViewport() {
        this(dev.gilbe.dynamicinventory.inventory.InventoryLayout.storageSlots());
    }

    public InventoryViewport(int storageSlots) {
        this.storageSlots = storageSlots;
    }

    public int getStorageSlots() {
        return storageSlots;
    }

    boolean isUpdatingVisibility() {
        return updatingVisibility;
    }

    @Override
    public void setHorizontalScrollOffset(int offset) {
        updateVisibility(() -> super.setHorizontalScrollOffset(offset));
    }

    @Override
    public void setVerticalScrollOffset(int offset) {
        updateVisibility(() -> super.setVerticalScrollOffset(offset));
    }

    @Override
    public void onFirstRebuild() {
        updateVisibility(() -> super.onFirstRebuild());
    }

    @Override
    public void onRebuild() {
        super.onRebuild();
        // The native callback also synchronizes enabled slots to the server after a GUI-scale change.
        onFirstRebuild();
    }

    private void updateVisibility(Runnable update) {
        boolean previous = updatingVisibility;
        updatingVisibility = true;
        try {
            update.run();
        } finally {
            updatingVisibility = previous;
        }
    }
}
