package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;
import java.util.Collections;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.ResizeNode;
import com.cleanroommc.modularui.widget.sizer.StaticResizer;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import dev.gilbe.dynamicinventory.compat.mui2.InventoryScrollWidget;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2CompactMachineHosts;
import dev.gilbe.dynamicinventory.compat.mui2.PlayerInventoryWidgetGroup;
import dev.gilbe.dynamicinventory.compat.mui2.ResponsiveInventoryPanel;

/** Geometry-only optional-host checks use native MUI2 resizing, without SFM classes or live slot interactions. */
final class Mui2CompactMachineHostFixtures {

    private static int checks;

    private Mui2CompactMachineHostFixtures() {}

    static int run() {
        checks = 0;
        check(
            Mui2CompactMachineHosts.keepsCompactViewport("superfactorymanager", "sfm_manager"),
            "exact optional manager host uses a compact player viewport");
        check(
            !Mui2CompactMachineHosts.keepsCompactViewport("another_mod", "sfm_manager"),
            "a same-named foreign panel retains its native expansion policy");
        check(
            !Mui2CompactMachineHosts.keepsCompactViewport("superfactorymanager", "another_panel"),
            "other optional-mod panels are not intercepted");
        check(
            !Mui2CompactMachineHosts.keepsCompactViewport(null, "sfm_manager")
                && !Mui2CompactMachineHosts.keepsCompactViewport("superfactorymanager", null),
            "missing host identifiers do not select compact presentation");
        check(
            SlotGroupWidget.playerInventory((index, slot) -> slot) instanceof PlayerInventoryWidgetGroup,
            "the stock framework factory reaches the inventory-owned player widget group");

        for (int[] screen : new int[][] { { 320, 240 }, { 640, 360 }, { 960, 540 }, { 1920, 1080 } }) {
            int nativeHeight = Math.min(288, screen[1] - 8);
            PlayerInventoryWidgetGroup inventory = expandedInventory();
            inventory.leftRel(0.5f)
                .bottom(6);
            ModularPanel panel = new ModularPanel("sfm_manager").size(312, nativeHeight)
                .child(inventory);
            layout(panel, screen[0], screen[1]);
            ResponsiveInventoryPanel responsive = new ResponsiveInventoryPanel();
            responsive.resizePanels(Collections.singletonList(panel), screen[1], "superfactorymanager");
            layout(panel, screen[0], screen[1]);
            check(
                panel.getArea().width == 312 && panel.getArea().height == nativeHeight,
                "compact optional host preserves its machine and recipe-viewer rectangle");
            check(
                inventory.getArea().width == 168 && inventory.getArea().height == 76,
                "nine columns, scrollbar gutter and hotbar retain the compact factory footprint");
            check(
                inventory.getArea().rx == 72 && inventory.getArea().ry == nativeHeight - 82,
                "centered player inventory retains the host's six-pixel bottom margin");
            check(
                inventory.getInventoryViewport()
                    .getArea().height == 54,
                "extra storage uses three visible rows on small and large displays");
            check(
                inventory.getInventoryViewport()
                    .getChildren()
                    .size() == 216
                    && inventory.getInventoryViewport()
                        .getScrollArea()
                        .getScrollY()
                        .getScrollSize() == 432,
                "all maximum-capacity storage rows remain present and scrollable");
            for (int logical = 0; logical < 9; logical++) {
                ItemSlot hotbar = inventory.getHotbarSlots()
                    .get(logical);
                check(
                    hotbar.getArea().rx == logical * 18 && hotbar.getArea().ry == 58,
                    "compact presentation leaves hotbar positions and roles intact");
            }
            for (int offset = 0; offset <= 21; offset++) {
                for (int row = offset; row < offset + 3; row++) {
                    for (int column = 0; column < 9; column++) {
                        int logical = 9 + row * 9 + column;
                        check(
                            inventory.getInventoryViewport()
                                .getChildren()
                                .get(logical - 9)
                                .getName()
                                .equals("slot_" + logical),
                            "scroll rows retain every canonical logical slot name");
                    }
                }
            }
            check(
                !responsive.resizePanels(Collections.singletonList(panel), screen[1], "superfactorymanager"),
                "unchanged compact geometry does not trigger repeated resize loops");
        }

        PlayerInventoryWidgetGroup compact = expandedInventory(), secondary = expandedInventory();
        ModularPanel manager = new ModularPanel("sfm_manager").size(312, 288)
            .child(compact);
        ModularPanel other = new ModularPanel("other_panel").size(312, 288)
            .child(secondary);
        layout(manager, 1000, 700);
        layout(other, 1000, 700);
        ResponsiveInventoryPanel responsive = new ResponsiveInventoryPanel();
        responsive.resizePanels(Arrays.asList(manager, other), 700, "superfactorymanager");
        layout(manager, 1000, 700);
        layout(other, 1000, 700);
        check(
            manager.getArea().height == 288 && compact.getInventoryViewport()
                .getArea().height == 54,
            "the declared compact panel stays fixed alongside another inventory branch");
        check(
            other.getArea().height > 288 && secondary.getInventoryViewport()
                .getArea().height > 54,
            "a different panel in the same optional screen keeps normal adaptive expansion");

        // Also cover a panel previously expanded under the ordinary policy: its baseline must be restored exactly.
        responsive.resizePanels(Collections.singletonList(manager), 700, "another_mod");
        layout(manager, 1000, 700);
        check(manager.getArea().height > 288, "same panel name under another owner still expands normally");
        responsive.resizePanels(Collections.singletonList(manager), 700, "superfactorymanager");
        layout(manager, 1000, 700);
        check(
            manager.getArea().height == 288 && compact.getInventoryViewport()
                .getArea().height == 54,
            "compact admission restores prior responsive height without accumulating an ancestor delta");
        return checks;
    }

    private static PlayerInventoryWidgetGroup expandedInventory() {
        InventoryScrollWidget viewport = new InventoryScrollWidget(9, true, 216);
        viewport.size(168, 54);
        for (int logical = 9; logical < 225; logical++) viewport.child(
            new ItemSlot().name("slot_" + logical)
                .syncHandler("player", logical)
                .pos((logical - 9) % 9 * 18, (logical - 9) / 9 * 18));
        PlayerInventoryWidgetGroup group = new PlayerInventoryWidgetGroup(viewport, 3);
        group.size(168, 76);
        for (int logical = 0; logical < 9; logical++) group.addHotbarSlot(
            new ItemSlot().name("slot_" + logical)
                .syncHandler("player", logical)
                .pos(logical * 18, 58));
        group.child(viewport);
        return group;
    }

    private static void layout(ModularPanel panel, int width, int height) {
        StaticResizer root = new StaticResizer() {

            private final Area area = new Area(0, 0, width, height);

            @Override
            public Area getArea() {
                return area;
            }

            @Override
            public String getDebugDisplayName() {
                return "compact optional-host fixture screen";
            }

            @Override
            public String toString() {
                return getDebugDisplayName();
            }
        };
        layout(panel, root, root);
    }

    private static void layout(IWidget widget, ResizeNode parent, ResizeNode root) {
        widget.resizer()
            .initialize(parent, root);
        widget.resizer()
            .setResized(false);
        widget.resizer()
            .resize(false);
        for (IWidget child : widget.getChildren()) layout(child, widget.resizer(), root);
    }

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
}
