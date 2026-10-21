package dev.gilbe.dynamicinventory.compat.mui2;

import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** A transparent viewport must enter MUI2's hit-test stack before its children can receive input. */
public final class InventoryScrollWidget extends ScrollWidget<InventoryScrollWidget> {

    private final boolean adaptive;
    private final int storageSlots;

    public InventoryScrollWidget() {
        this(9, true, InventoryLayout.storageSlots());
    }

    /** Creates a fixed-column viewport for a custom public inventory layout. */
    public InventoryScrollWidget(int columns) {
        this(columns, false, InventoryLayout.storageSlots());
    }

    public InventoryScrollWidget(int columns, boolean adaptive, int storageSlots) {
        super(new VerticalScrollData());
        if (columns <= 0 || storageSlots <= 0)
            throw new IllegalArgumentException("Invalid inventory scroll dimensions");
        this.adaptive = adaptive;
        this.storageSlots = storageSlots;
        getScrollArea().getScrollY()
            .setScrollSize(((storageSlots + columns - 1) / columns) * 18);
        getScrollArea().getScrollY()
            .setScrollSpeed(18);
    }

    public boolean isAdaptive() {
        return adaptive;
    }

    public int getStorageSlots() {
        return storageSlots;
    }

    @Override
    public void getWidgetsAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y) {
        if (widgets.peek() != this) return;
        // MUI2 2.3.92 passes absolute mouse coordinates here, but its scrollbar test expects local coordinates.
        // The stack already includes our child scroll translation; remove it for the stationary scrollbar.
        int localX = stack.unTransformX(x, y) - getScrollX();
        int localY = stack.unTransformY(x, y) - getScrollY();
        if (!getScrollArea().isInsideScrollbarArea(localX, localY)) IViewport.getChildrenAt(this, stack, widgets, x, y);
    }

    @Override
    public boolean canHover() {
        return true;
    }
}
