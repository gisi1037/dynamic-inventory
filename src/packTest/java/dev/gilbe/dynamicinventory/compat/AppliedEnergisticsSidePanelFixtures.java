package dev.gilbe.dynamicinventory.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

import appeng.container.slot.SlotPatternTerm;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsSidePanels;

/** Exercise real side-slot placement without a client, world, or OpenGL draw. */
public final class AppliedEnergisticsSidePanelFixtures {

    private AppliedEnergisticsSidePanelFixtures() {}

    public static void main(String[] args) throws Exception {
        for (int size : new int[] { 3, 5 }) checkToolbox(size);
        checkPatternResult();
        System.out
            .println("PASS AE2 toolbox columns: 3x3/5x5, live upgrades, repeated placement and native slot identity");
    }

    private static void checkPatternResult() throws Exception {
        boolean[] crafting = { false };
        InventoryBasic output = new InventoryBasic("pattern-result", false, 1);
        Slot result = new SlotPatternTerm(
            null,
            null,
            null,
            null,
            null,
            null,
            output,
            110,
            -58,
            group -> group == 2 && crafting[0],
            2,
            null);
        Method capture = NativeInventoryPanel.class.getDeclaredMethod("captureNativePosition", Slot.class);
        capture.setAccessible(true);
        Method place = NativeInventoryPanel.class
            .getDeclaredMethod("placeNativeSlot", Slot.class, int[].class, int.class, int.class);
        place.setAccessible(true);

        result.xDisplayPosition = -9000;
        result.yDisplayPosition = 237;
        int[] origin = (int[]) capture.invoke(null, result);
        place.invoke(null, result, origin, 12, 4);
        check(result.xDisplayPosition == -9000, "Processing mode keeps the result out of mouse hit testing");

        // The import enables the existing slot after the decorator has captured its origin.
        crafting[0] = true;
        place.invoke(null, result, origin, 12, 4);
        check(
            result.xDisplayPosition == 122 && result.yDisplayPosition == 241,
            "First import shows the result at the translated crafting output");
        place.invoke(null, result, origin, 0, 0);
        int[] reopened = (int[]) capture.invoke(null, result);
        check(
            reopened[0] == 110 && reopened[1] == 237,
            "Reopening for a second import retains the native result bounds");
        System.out.println("PASS AE2 pattern result placement across mode switch and recipe reopen");
    }

    private static void checkToolbox(int size) throws Exception {
        List<TestSlot> upgrades = slots(1, 4, 180, 12, 0);
        List<TestSlot> toolbox = slots(size, size, 180, 126, 100);
        Class<?> panelType = Class.forName(AppliedEnergisticsSidePanels.class.getName() + "$Panel");
        Constructor<?> createPanel = panelType.getDeclaredConstructor(boolean.class);
        createPanel.setAccessible(true);
        Method add = panelType.getDeclaredMethod("add", Slot.class);
        add.setAccessible(true);
        List<Object> panels = new ArrayList<>();
        // Deliberately reversed: placement must retain native top-to-bottom group order.
        for (List<TestSlot> group : java.util.Arrays.asList(toolbox, upgrades)) {
            Object panel = createPanel.newInstance(false);
            for (Slot slot : group) add.invoke(panel, slot);
            panels.add(panel);
        }
        Constructor<AppliedEnergisticsSidePanels> create = AppliedEnergisticsSidePanels.class
            .getDeclaredConstructor(List.class);
        create.setAccessible(true);
        AppliedEnergisticsSidePanels layout = create.newInstance(panels);
        int toolWidth = size * 18 + 10;
        for (int repeat = 0; repeat < 4; repeat++) {
            int bottom = layout.position(176, 10);
            List<Rect> bounds = layout.bounds(20, 30);
            check(bounds.size() == 2, "Both native inventories have frames");
            Rect cards = bounds.get(0), tool = bounds.get(1);
            check(cards.x == 199 && cards.y == 40 && cards.width == 28, "Upgrade column follows machine");
            check(
                tool.x == 230 && tool.y == cards.y && tool.width == toolWidth,
                "Toolbox follows upgrades at machine top");
            check(!cards.intersects(tool), "Side frames do not overlap");
            check(layout.width(176) == 176 + 3 + 28 + 3 + toolWidth, "Reserved width includes both columns");
            check(bottom == 10 + Math.max(82, toolWidth), "Height uses tallest column");
            for (int i = 0; i < toolbox.size(); i++) {
                TestSlot slot = toolbox.get(i);
                check(slot.slotNumber == 100 + i && slot.getSlotIndex() == i, "Native slot IDs stay unchanged");
                check(slot.xDisplayPosition == 216 + i % size * 18, "Tool slot x follows its native column");
                check(slot.yDisplayPosition == 16 + i / size * 18, "Tool slot y follows machine top");
            }
        }
        for (TestSlot slot : upgrades) slot.enabled = false;
        layout.position(176, 10);
        List<Rect> onlyTool = layout.bounds(0, 0);
        check(onlyTool.size() == 1 && onlyTool.get(0).x == 179, "Hidden upgrades leave no empty column");
        check(
            layout.width(176) == 176 + 3 + 28 + 3 + toolWidth,
            "Outer width reserves live upgrade columns before they become visible");
        upgrades.get(0).enabled = true;
        layout.position(176, 10);
        check(
            layout.bounds(0, 0)
                .get(1).x == 210,
            "Live upgrade visibility restores tool column");
    }

    private static List<TestSlot> slots(int columns, int rows, int x, int y, int firstId) {
        InventoryBasic inventory = new InventoryBasic("side-panel-fixture", false, columns * rows);
        List<TestSlot> slots = new ArrayList<>();
        for (int i = 0; i < columns * rows; i++) {
            TestSlot slot = new TestSlot(inventory, i, x + i % columns * 18, y + i / columns * 18);
            slot.slotNumber = firstId + i;
            slots.add(slot);
        }
        return slots;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class TestSlot extends Slot {

        private boolean enabled = true;

        private TestSlot(InventoryBasic inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean func_111238_b() {
            return enabled;
        }
    }
}
