package dev.gilbe.dynamicinventory.client;

import java.nio.file.Files;
import java.util.Collections;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Resize directions share the panel's hit geometry and preserve the opposite corner through clipping. */
public final class PinsPanelResizeFixtures {

    private PinsPanelResizeFixtures() {}

    public static void main(String[] args) throws Exception {
        Rect start = new Rect(120, 100, 120, 90);
        Rect viewport = new Rect(2, 18, 400, 300);
        for (int corner : new int[] { 5, 6, 9, 10 }) {
            boolean left = (corner & 1) != 0, top = (corner & 4) != 0;
            check(
                PanelChrome.corner(start, left ? start.x : start.right() - 1, top ? start.y : start.bottom() - 1)
                    == corner,
                "Every visible corner is a resize target");
            Rect larger = PinsPanelGeometry.resizeFromCorner(start, viewport, corner, left ? -17 : 17, top ? -22 : 22);
            check(larger.width == 137 && larger.height == 112, "Each corner can grow both dimensions");
            anchor(start, larger, left, top);
            Rect smaller = PinsPanelGeometry
                .resizeFromCorner(start, viewport, corner, left ? 900 : -900, top ? 900 : -900);
            check(smaller.width == 72 && smaller.height == 43, "Shrinking stops at the existing limits");
            anchor(start, smaller, left, top);
            Rect edge = PinsPanelGeometry
                .resizeFromCorner(start, viewport, corner, left ? -900 : 900, top ? -900 : 900);
            check(left ? edge.x == viewport.x : edge.right() == viewport.right(), "Resize stops at the viewport sides");
            check(
                top ? edge.y == viewport.y : edge.bottom() == viewport.bottom(),
                "Resize stops at the viewport top and bottom");
            anchor(start, edge, left, top);
        }
        Rect requested = PinsPanelGeometry.resizeFromCorner(start, viewport, 5, -70, -60);
        Rect obstacle = new Rect(50, 19, 60, 235);
        Rect fitted = InventoryPanelLayout.constrainGesture(
            start,
            requested,
            viewport,
            Collections.singletonList(obstacle),
            requested.width,
            requested.height);
        check(fitted.x == obstacle.right() + 1, "Top-left drag stops at the neighboring panel");
        anchor(start, fitted, true, true);
        checkSizeMatching(start, viewport);
        checkWideMain();
        checkTallPanels();
        checkSeamlessDocking(start, viewport);
        checkPinsAppearance();
        System.out
            .println("PASS corner resizing, size snapping, wider and taller panels, and seamless docking and styles");
    }

    private static void checkSizeMatching(Rect start, Rect viewport) {
        Rect peer = new Rect(8, 211, 122, 94);
        for (int corner : new int[] { 5, 6, 9, 10 }) {
            boolean left = (corner & 1) != 0, top = (corner & 4) != 0;
            Rect near = PinsPanelGeometry.resizeFromCorner(start, viewport, corner, left ? -1 : 1, top ? -2 : 2);
            Rect match = InventoryPanelLayout
                .alignResize(start, near, corner, viewport, Collections.singletonList(peer), 72, 43, 450, 450);
            check(match.width == peer.width && match.height == peer.height, "Both nearby dimensions snap");
            anchor(start, match, left, top);
        }
        Rect requested = new Rect(start.x, start.y, 121, 92);
        Rect blocked = new Rect(242, 100, 122, 95);
        Rect match = InventoryPanelLayout
            .alignResize(start, requested, 10, viewport, Collections.singletonList(blocked), 72, 43, 450, 450);
        check(match.width == 121 && match.height == 95, "A blocked width does not prevent a legal height match");
        Rect far = new Rect(start.x, start.y, 130, 102);
        check(
            InventoryPanelLayout
                .alignResize(start, far, 10, viewport, Collections.singletonList(peer), 72, 43, 450, 450) == far,
            "Moving beyond three pixels releases the size snap");
    }

    private static void checkWideMain() {
        Rect viewport = new Rect(0, 0, 900, 360);
        Rect wide = new Rect(5, 25, 664, 110);
        InventoryPanelLayout main = InventoryPanelLayout.fit(wide, viewport, Collections.emptyList(), 144, 8, true);
        InventoryPanelLayout sub = InventoryPanelLayout.fit(wide, viewport, Collections.emptyList(), 144, 8, false);
        check(
            main != null && main.columns == 36 && main.hotbarColumns == 9,
            "Main uses all 36 columns and nine hotbar slots");
        check(sub != null && sub.columns == 18, "Subpanel storage keeps its existing column cap");
        Rect snapped = InventoryPanelLayout.snapResize(wide, wide, 10, viewport, Collections.emptyList(), 8, true, 144);
        check(snapped != null && snapped.width == wide.width, "Alt grid snap supports the wider Main");
        check(
            new InventoryViewLayout(900, 360, 36, 4, 144).columns == 36,
            "Attached player inventory supports wide layouts");
        check(new InventoryViewLayout(420, 360, 36, 4, 144).columns < 36, "Physical screen fit still limits columns");
    }

    private static void checkTallPanels() {
        Rect viewport = new Rect(0, 0, 500, 900);
        Rect tall = new Rect(10, 24, 232, 694);
        var noObstacles = Collections.<Rect>emptyList();
        InventoryPanelLayout main = InventoryPanelLayout.fit(tall, viewport, noObstacles, 432, 8, true);
        InventoryPanelLayout sub = InventoryPanelLayout.fit(tall, viewport, noObstacles, 432, 8, false);
        check(
            main != null && main.rows == 36 && sub != null && sub.rows == 36,
            "Main and subpanels can show 36 storage rows when they fit");
        Rect snapped = InventoryPanelLayout.snapResize(tall, tall, 10, viewport, noObstacles, 8, true, 432);
        check(snapped != null && snapped.height == tall.height, "Alt resize supports taller whole-cell grids");
        Rect shortScreen = new Rect(0, 0, 500, 260);
        InventoryPanelLayout fitted = InventoryPanelLayout.fit(tall, shortScreen, noObstacles, 432, 8, true);
        check(
            fitted != null && fitted.bounds.bottom() <= shortScreen.bottom() && fitted.maxScroll(432) > 0,
            "Taller settings fit and scroll on smaller screens");
        Rect pins = PinsPanelGeometry.resizeFromCorner(tall, viewport, 10, 0, 500);
        check(pins.height == InventoryPanelLayout.MAX_HEIGHT, "Pins stops at the new maximum height");
        check(
            PinsPanelGeometry.fromJson(new PinsPanelGeometry(pins, null).toJson())
                .requested(tall, false).height == pins.height,
            "Tall Pins geometry survives save and reload");
        check(
            new InventoryViewLayout(500, 900, 12, 36, 432).visibleRows == 36,
            "The attached player inventory can show the same number of rows");
    }

    private static void checkSeamlessDocking(Rect neighbor, Rect viewport) {
        var occupied = Collections.singletonList(neighbor);
        Rect near = new Rect(neighbor.right() - 2, neighbor.y, 100, 80);
        Rect touching = InventoryPanelLayout.alignDrag(near, viewport, occupied, 3, 0).bounds;
        check(touching.x == neighbor.right(), "Seamless drag aligns to the exact shared edge");
        check(
            InventoryPanelLayout.snapDrag(near, viewport, occupied, 0).x == neighbor.right(),
            "Seamless placement keeps touching panels separate for hit testing");
        check(
            InventoryPanelLayout.snapDrag(near, viewport, occupied, 1).x == neighbor.right() + 1,
            "Standard placement retains one pixel of spacing");
        Rect before = new Rect(neighbor.right() + 10, neighbor.y, 100, 80);
        Rect resize = new Rect(neighbor.right() - 5, neighbor.y, 115, 80);
        Rect constrained = InventoryPanelLayout.constrainGesture(before, resize, viewport, occupied, 72, 43, 0);
        check(constrained.x == neighbor.right(), "Corner resizing can also reach the shared edge");
        check(constrained.right() == before.right(), "Docking preserves the opposite resize edge");
    }

    private static void checkPinsAppearance() throws Exception {
        var location = Files.createTempDirectory("dynamic-inventory-pins")
            .resolve("profiles.json")
            .toFile();
        String key = PlayerPanelProfiles.key("fixture:one", "test.Container", "test.Gui");
        String other = PlayerPanelProfiles.key("fixture:two", "test.Container", "test.Gui");
        Rect machine = new Rect(10, 20, 100, 100);
        PlayerPanelProfiles.initialize(location);
        Rect original = PlayerPanelProfiles.requestedPinsBounds(key, machine);
        var seamless = PinsPanelGeometry.DEFAULT_APPEARANCE.withAppearance(NativePanelFrame.SEAMLESS, 3, 5, 6);
        PlayerPanelProfiles.savePinsAppearance(key, seamless, false);
        Rect inherited = PlayerPanelProfiles.requestedPinsBounds(key, machine);
        check(
            !PlayerPanelProfiles.pins(key)
                .hasBounds(),
            "Setting a local style alone keeps global position and size");
        check(
            original.x == inherited.x && original.y == inherited.y
                && original.width == inherited.width
                && original.height == inherited.height,
            "A style-only override retains global geometry");
        check(
            PlayerPanelProfiles.pinsAppearance(other).frameStyle != NativePanelFrame.SEAMLESS,
            "Local styles do not change other screens");
        Rect moved = new Rect(31, 42, 120, 101);
        PlayerPanelProfiles.savePinsBounds(key, moved, null);
        PlayerPanelProfiles.initialize(location);
        check(
            PlayerPanelProfiles.pinsAppearance(key).frameStyle == NativePanelFrame.SEAMLESS,
            "Local style survives moving the panel and reloading profiles");
        PlayerPanelProfiles.savePinsAppearance(key, seamless, true);
        check(
            PlayerPanelProfiles.pinsAppearance(other).frameStyle == NativePanelFrame.SEAMLESS,
            "Make global applies to screens without a style override");
        PlayerPanelProfiles.savePinsAppearance(key, null, false);
        Rect selected = PlayerPanelProfiles.requestedPinsBounds(key, machine);
        check(
            selected.x == moved.x && selected.y == moved.y
                && selected.width == moved.width
                && selected.height == moved.height,
            "Resetting a style keeps the local placement");
    }

    private static void anchor(Rect start, Rect resized, boolean left, boolean top) {
        check(left ? resized.right() == start.right() : resized.x == start.x, "Opposite horizontal edge stays fixed");
        check(top ? resized.bottom() == start.bottom() : resized.y == start.y, "Opposite vertical edge stays fixed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
