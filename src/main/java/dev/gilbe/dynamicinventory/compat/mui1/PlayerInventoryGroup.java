package dev.gilbe.dynamicinventory.compat.mui1;

import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.SlotGroup;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/** Preserves SlotGroup's callback hook for storage slots nested inside the viewport. */
public final class PlayerInventoryGroup extends SlotGroup {

    private final InventoryViewport viewport;

    public PlayerInventoryGroup(InventoryViewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public SlotGroup setOnDragAndDropComplete(Consumer<Widget> callback) {
        super.setOnDragAndDropComplete(callback);
        for (Widget child : viewport.getChildren()) {
            if (child instanceof SlotWidget) ((SlotWidget) child).setOnDragAndDropComplete(callback);
        }
        return this;
    }
}
