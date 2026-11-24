package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

public final class Mui2CoverPopupFixtures {

    private Mui2CoverPopupFixtures() {}

    public static void main(String[] args) {
        Rect popup = new Rect(272, 132, 212, 138);
        Rect inventory = new Rect(275, 190, 188, 224);
        Rect machine = new Rect(271, 14, 196, 172);
        Rect sidePanel = new Rect(200, 190, 72, 210);
        Rect pins = new Rect(467, 191, 182, 194);
        Rect viewport = new Rect(2, 2, 720, 426);
        Rect right = Mui2CoverPopup
            .position(popup, viewport, inventory, Arrays.asList(inventory, sidePanel), Arrays.asList(machine), pins);
        check(right.x > inventory.right() && right.bottom() <= pins.y, "Use the open side above AE2 Pins");
        checkFits(popup, viewport, right, Arrays.asList(inventory, sidePanel, machine, pins));

        Rect rightPanel = new Rect(467, 5, 252, 412);
        Rect left = Mui2CoverPopup.position(
            popup,
            viewport,
            inventory,
            Arrays.asList(inventory, sidePanel),
            Arrays.asList(machine, rightPanel),
            pins);
        check(left.right() < inventory.x, "Use the left side when the right is occupied");
        checkFits(popup, viewport, left, Arrays.asList(inventory, sidePanel, machine, rightPanel, pins));

        Rect compact = new Rect(2, 2, 300, 426);
        Rect compactInventory = new Rect(56, 190, 188, 224);
        Rect compactMachine = new Rect(52, 14, 196, 172);
        Rect above = Mui2CoverPopup.position(
            popup,
            compact,
            compactInventory,
            Arrays.asList(compactInventory),
            Arrays.asList(compactMachine),
            null);
        check(
            above.bottom() < compactInventory.y && above.intersects(compactMachine),
            "On a narrow screen prefer covering the machine instead of the inventory");
        checkFits(popup, compact, above, Arrays.asList(compactInventory));

        Rect tiny = new Rect(2, 2, 180, 110);
        Rect clamped = Mui2CoverPopup
            .position(popup, tiny, compactInventory, Collections.emptyList(), Collections.emptyList(), null);
        check(
            clamped.x == tiny.x && clamped.y == tiny.y
                && clamped.width == popup.width
                && clamped.height == popup.height,
            "An oversized popup keeps its native size and puts its header in view");
        check(Mui2CoverPopup.isWirelessController("cover.wireless_controller.NORTH"), "Match the GregTech panel ID");
        check(
            !Mui2CoverPopup.isWirelessController("cover.wireless_fluid_detector.NORTH"),
            "Other covers keep their layout");
        System.out.println("PASS wireless cover popup placement and narrow-screen fallback");
    }

    private static void checkFits(Rect original, Rect viewport, Rect position, List<Rect> obstacles) {
        check(position.width == original.width && position.height == original.height, "Keep the popup's native size");
        check(
            viewport.contains(position.x, position.y) && viewport.contains(position.right() - 1, position.bottom() - 1),
            "Keep the popup inside the viewport when it fits");
        check(
            obstacles.stream()
                .noneMatch(position::intersects),
            "Do not cover panels when a clear spot exists");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
