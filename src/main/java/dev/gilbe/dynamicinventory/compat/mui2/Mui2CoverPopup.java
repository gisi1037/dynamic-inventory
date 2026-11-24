package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

final class Mui2CoverPopup {

    private static final int GAP = 3;

    private Mui2CoverPopup() {}

    static boolean isWirelessController(String name) {
        return name.startsWith("cover.wireless_controller.");
    }

    static Rect position(Rect popup, Rect viewport, Rect inventory, List<Rect> playerPanels, List<Rect> nativePanels,
        Rect pins) {
        Rect preferred = new Rect(
            inventory.x + (inventory.width - popup.width) / 2,
            inventory.y - popup.height - GAP,
            popup.width,
            popup.height);
        List<Rect> player = new ArrayList<>(playerPanels);
        if (pins != null) player.add(pins);
        List<Rect> all = new ArrayList<>(nativePanels);
        all.addAll(player);

        Rect position = besideOrAbove(preferred, viewport, inventory, all);
        if (position != null) return position;
        // When the viewport is crowded, allow the cover to overlay the machine before covering inventory slots.
        position = besideOrAbove(preferred, viewport, inventory, player);
        if (position != null) return position;
        position = fit(preferred, viewport, player);
        if (position != null) return position;
        // Pins render after the native panels, so keep that area clear even when a player panel has to be covered.
        if (pins != null) {
            position = fit(preferred, viewport, Collections.singletonList(pins));
            if (position != null) return position;
        }
        return new Rect(
            Math.max(viewport.x, Math.min(preferred.x, viewport.right() - popup.width)),
            Math.max(viewport.y, Math.min(preferred.y, viewport.bottom() - popup.height)),
            popup.width,
            popup.height);
    }

    private static Rect besideOrAbove(Rect preferred, Rect viewport, Rect inventory, List<Rect> obstacles) {
        int rightStart = Math.max(viewport.x, inventory.right() + GAP);
        int leftEnd = Math.min(viewport.right(), inventory.x - GAP);
        int aboveEnd = Math.min(viewport.bottom(), inventory.y - GAP);
        Rect right = fit(
            preferred,
            new Rect(rightStart, viewport.y, viewport.right() - rightStart, viewport.height),
            obstacles);
        if (right != null) return right;
        Rect left = fit(preferred, new Rect(viewport.x, viewport.y, leftEnd - viewport.x, viewport.height), obstacles);
        if (left != null) return left;
        return fit(preferred, new Rect(viewport.x, viewport.y, viewport.width, aboveEnd - viewport.y), obstacles);
    }

    private static Rect fit(Rect preferred, Rect area, List<Rect> obstacles) {
        if (area.width < preferred.width || area.height < preferred.height) return null;
        return InventoryPanelLayout.snapDrag(preferred, area, obstacles);
    }
}
