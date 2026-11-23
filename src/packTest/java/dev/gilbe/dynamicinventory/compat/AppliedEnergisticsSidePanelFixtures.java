package dev.gilbe.dynamicinventory.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraftforge.common.config.Configuration;

import appeng.container.slot.SlotPatternTerm;
import cpw.mods.fml.relauncher.FMLInjectionData;
import dev.gilbe.dynamicinventory.client.AeNativePanelControls.Frame;
import dev.gilbe.dynamicinventory.client.AeNativePanelControls.Placement;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsSidePanels;

/** Exercise real side-slot placement without a client, world, or OpenGL draw. */
public final class AppliedEnergisticsSidePanelFixtures {

    private AppliedEnergisticsSidePanelFixtures() {}

    public static void main(String[] args) throws Exception {
        checkSavedPlacement();
        for (int size : new int[] { 3, 5 }) {
            checkToolbox(size, false);
            checkToolbox(size, true);
        }
        checkPatternResult();
        System.out.println("PASS AE2 toolbox: native margins, 3x3/5x5, resizing, live upgrades and unchanged slot IDs");
    }

    private static void checkSavedPlacement() throws Exception {
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        Object originalHome = home.get(null);
        Path file = Files.createTempFile("dynamicinventory-ae2-layout-", ".cfg");
        try {
            home.set(
                null,
                file.getParent()
                    .toFile());
            String key = "appeng.client.gui.implementations.GuiInterfaceTerminal.main";
            int[] old = { 42, 33, 0, 0 };
            Configuration legacy = new Configuration(file.toFile());
            legacy.get("ae2NativePanels", key, new int[0])
                .set(old);
            legacy.save();
            InventoryViewSettings.initialize(file.toFile());
            check(Arrays.equals(old, InventoryViewSettings.nativeAe2Panel(key)), "Reads existing Forge layout");

            int[] moved = { 150, 85, 0, 0 };
            InventoryViewSettings.setNativeAe2Panel(key, moved);
            InventoryViewSettings.initialize(file.toFile());
            check(Arrays.equals(moved, InventoryViewSettings.nativeAe2Panel(key)), "Position survives reload");
            System.out.println("PASS Interface Terminal placement reload and existing configuration");
        } finally {
            home.set(null, originalHome);
            Files.deleteIfExists(file);
        }
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

    private static void checkToolbox(int size, boolean stacked) throws Exception {
        List<TestSlot> upgrades = slots(1, 4, 180, 12, 0);
        List<TestSlot> toolbox = slots(size, size, 180, 126, 100);
        Class<?> panelType = Class.forName(AppliedEnergisticsSidePanels.class.getName() + "$Panel");
        Constructor<?> createPanel = panelType.getDeclaredConstructor(boolean.class);
        createPanel.setAccessible(true);
        Method add = panelType.getDeclaredMethod("add", Slot.class);
        add.setAccessible(true);
        List<Object> panels = new ArrayList<>();
        // Deliberately reversed: placement must retain native top-to-bottom group order.
        for (List<TestSlot> group : Arrays.asList(toolbox, upgrades)) {
            Object panel = createPanel.newInstance(false);
            for (Slot slot : group) add.invoke(panel, slot);
            panels.add(panel);
        }
        Constructor<AppliedEnergisticsSidePanels> create = AppliedEnergisticsSidePanels.class
            .getDeclaredConstructor(List.class, boolean.class);
        create.setAccessible(true);
        AppliedEnergisticsSidePanels layout = create.newInstance(panels, stacked);
        int toolWidth = size * 18 + 10;
        int toolX = stacked ? 179 : 210;
        int toolY = stacked ? 95 : 10;
        int reservedWidth = stacked ? 179 + Math.max(28, toolWidth) : 210 + toolWidth;
        for (int repeat = 0; repeat < 4; repeat++) {
            int bottom = layout.position(176, 10);
            List<Rect> bounds = layout.bounds(20, 30);
            check(bounds.size() == 2, "Both native inventories have frames");
            Rect cards = bounds.get(0), tool = bounds.get(1);
            check(cards.x == 199 && cards.y == 40 && cards.width == 28, "Upgrade column follows machine");
            check(
                tool.x == 20 + toolX && tool.y == 30 + toolY && tool.width == toolWidth,
                "Toolbox follows upgrades in its native group order");
            check(!cards.intersects(tool), "Side frames do not overlap");
            check(layout.width(176) == reservedWidth, "Reserved width accounts for both side panels");
            check(bottom == Math.max(92, toolY + toolWidth), "Height includes both side panels");
            for (int i = 0; i < toolbox.size(); i++) {
                TestSlot slot = toolbox.get(i);
                check(slot.slotNumber == 100 + i && slot.getSlotIndex() == i, "Native slot IDs stay unchanged");
                check(slot.xDisplayPosition == toolX + 6 + i % size * 18, "Tool slot x follows its native column");
                check(slot.yDisplayPosition == toolY + 6 + i / size * 18, "Tool slot y follows its side panel");
            }
        }
        for (TestSlot slot : upgrades) slot.enabled = false;
        layout.position(176, 10);
        List<Rect> onlyTool = layout.bounds(0, 0);
        check(onlyTool.size() == 1 && onlyTool.get(0).x == 179, "Hidden upgrades leave no empty column");
        check(
            layout.width(176) == reservedWidth,
            "Outer width reserves live upgrade columns before they become visible");
        upgrades.get(0).enabled = true;
        layout.position(176, 10);
        check(
            layout.bounds(0, 0)
                .get(1).x == toolX,
            "Live upgrade visibility restores tool placement");

        Object toolPanel = panels.get(1);
        Field isToolbox = panelType.getDeclaredField("toolbox");
        isToolbox.setAccessible(true);
        isToolbox.setBoolean(toolPanel, true);
        Field detached = panelType.getDeclaredField("detached");
        detached.setAccessible(true);
        Constructor<Placement> place = Placement.class
            .getDeclaredConstructor(Rect.class, int.class, int.class, int.class);
        place.setAccessible(true);
        Method frameSize = Frame.class.getDeclaredMethod("size", int.class, int.class, int.class, int.class);
        frameSize.setAccessible(true);
        Frame frame = new Frame(
            "toolbox",
            layout.bounds(0, 0)
                .get(1),
            toolbox.size(),
            size,
            size);
        Rect full = (Rect) frameSize.invoke(frame, 230, 20, size, size);
        check(full.width == toolWidth && full.height == toolWidth, "Detached toolbox uses attached margins");
        Rect moved = (Rect) frameSize.invoke(frame, 230, 20, 2, 2);
        check(moved.width == 46 && moved.height == 46, "No extra header or scrollbar margin when resized");
        for (int row = 0; row < (toolbox.size() + 1) / 2; row++) {
            detached.set(toolPanel, place.newInstance(moved, 2, 2, row));
            layout.positionSlots(17, 8);
            check(
                layout.bounds(17, 8)
                    .get(1) == moved,
                "Tool frame follows an independent drag");
            for (int i = 0; i < toolbox.size(); i++) {
                TestSlot slot = toolbox.get(i);
                boolean visible = i / 2 >= row && i / 2 < row + 2;
                check(slot.xDisplayPosition == (visible ? 219 + i % 2 * 18 : -10000), "Resized tool x");
                check(slot.yDisplayPosition == (visible ? 18 + (i / 2 - row) * 18 : -10000), "Scrolled tool y");
                check(slot.slotNumber == 100 + i && slot.getSlotIndex() == i, "Resizing preserves native slot IDs");
            }
        }
        layout.position(176, 10);
        check(toolbox.get(0).xDisplayPosition == toolX + 6, "Returning to attached restores native columns");
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
