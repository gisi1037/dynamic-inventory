package dev.gilbe.dynamicinventory.compat.enderio;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Geometry shared by the conduit GUI and its inventory decorator. */
public interface ConduitLayout {

    Rect dynamicinventory$tabBounds(int left, int top, int width);
}
