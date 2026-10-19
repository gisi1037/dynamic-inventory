package dev.gilbe.dynamicinventory.compat.mui1;

import com.gtnewhorizons.modularui.common.widget.Scrollable;

public final class InventoryViewport extends Scrollable {

    private boolean updatingVisibility;

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
