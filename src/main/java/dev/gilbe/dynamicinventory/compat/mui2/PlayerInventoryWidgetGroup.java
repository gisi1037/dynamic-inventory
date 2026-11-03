package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/** Explicit layout roles keep responsive resizing independent of the group's child order. */
public final class PlayerInventoryWidgetGroup extends SlotGroupWidget {

    private final InventoryScrollWidget viewport;
    private final List<ItemSlot> hotbar = new ArrayList<>();
    private final int initialRows;
    private boolean detachedPresentation;

    public boolean isDetachedPresentation() {
        return detachedPresentation;
    }

    public void setDetachedPresentation(boolean detached) {
        detachedPresentation = detached;
        viewport.setDetachedPresentation(detached);
    }

    public PlayerInventoryWidgetGroup(InventoryScrollWidget viewport, int initialRows) {
        this.viewport = viewport;
        this.initialRows = initialRows;
    }

    public void addHotbarSlot(ItemSlot slot) {
        hotbar.add(slot);
        child(slot);
    }

    public InventoryScrollWidget getInventoryViewport() {
        return viewport;
    }

    public List<ItemSlot> getHotbarSlots() {
        return Collections.unmodifiableList(hotbar);
    }

    public int getInitialRows() {
        return initialRows;
    }
}
