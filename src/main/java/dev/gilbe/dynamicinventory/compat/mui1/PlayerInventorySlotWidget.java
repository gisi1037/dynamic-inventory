package dev.gilbe.dynamicinventory.compat.mui1;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/** Uses a separate visibility packet so scrolling cannot overwrite explicit slot restrictions. */
public final class PlayerInventorySlotWidget extends SlotWidget {

    private static final int VIEWPORT_VISIBILITY_PACKET = 6;
    private final InventoryViewport viewport;
    private final PlayerInventorySlot playerSlot;
    private boolean viewportVisible = true;

    public PlayerInventorySlotWidget(PlayerInventorySlot slot, InventoryViewport viewport) {
        super(slot);
        playerSlot = slot;
        this.viewport = viewport;
    }

    @Override
    public boolean isEnabled() {
        return viewportVisible && super.isEnabled();
    }

    public boolean isInteractionEnabled() {
        return super.isEnabled();
    }

    @Override
    public SlotWidget setEnabled(boolean enabled) {
        if (viewport.isUpdatingVisibility()) {
            setViewportVisible(enabled);
        } else {
            // SlotWidget compares against isEnabled() before updating its slot and sync state.
            // Exclude visibility during that comparison so explicit changes work while hidden.
            boolean previous = viewportVisible;
            viewportVisible = true;
            try {
                super.setEnabled(enabled);
            } finally {
                viewportVisible = previous;
            }
        }
        return this;
    }

    private void setViewportVisible(boolean visible) {
        if (viewportVisible == visible) return;
        viewportVisible = visible;
        playerSlot.setViewportVisible(visible);
        if (isInitialised() && isClient()) {
            syncToServer(VIEWPORT_VISIBILITY_PACKET, buffer -> buffer.writeBoolean(visible));
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id == VIEWPORT_VISIBILITY_PACKET) {
            setViewportVisible(buffer.readBoolean());
            markForUpdate();
        } else {
            super.readOnServer(id, buffer);
        }
    }
}
