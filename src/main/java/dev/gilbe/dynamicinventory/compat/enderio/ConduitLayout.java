package dev.gilbe.dynamicinventory.compat.enderio;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Geometry shared by the conduit GUI and its inventory decorator. */
public interface ConduitLayout {

    // ExternalConnectionContainer starts its player slots at y=161.
    int PLAYER_BOUNDARY = 157;

    Rect dynamicinventory$tabBounds(int left, int top, int width);
}
