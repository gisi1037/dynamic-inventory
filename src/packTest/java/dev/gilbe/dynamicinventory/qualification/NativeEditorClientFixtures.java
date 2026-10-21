package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.recipe.RecipeInfo;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

/** Client-thread editor checks against a real open window in a disposable world. Never shipped in the mod. */
public final class NativeEditorClientFixtures {

    private NativeEditorClientFixtures() {}

    public static void run() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getIntegratedServer() == null
            || !mc.getIntegratedServer()
                .getFolderName()
                .startsWith("DI-Baseline-"))
            throw new IllegalStateException("Disposable integrated test world required");
        GuiContainer host = (GuiContainer) mc.currentScreen;
        ContainerPanelAccess access = (ContainerPanelAccess) host;
        Container container = host.inventorySlots;
        int window = container.windowId;
        List<Slot> slots = new ArrayList<>(container.inventorySlots);
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : slots) stacks.add(ItemStack.copyItemStack(slot.getStack()));
        check(mc.thePlayer.inventory.getItemStack() == null, "empty cursor");
        Field directoryField = NativeLayoutPatches.class.getDeclaredField("directory");
        directoryField.setAccessible(true);
        Path directory = (Path) directoryField.get(null);
        Path testDirectory = Files.createTempDirectory(mc.mcDataDir.toPath(), "editor-regression-");
        Field activeField = InventoryLayoutEditor.class.getDeclaredField("active");
        activeField.setAccessible(true);
        try {
            NativeLayoutPatches.initialize(testDirectory.toFile());
            rebuild(host);
            NativeLayoutPatch defaults = NativeLayoutPatches.resolve(host);
            int personalColumns = InventoryViewSettings.columns, personalRows = InventoryViewSettings.rows;
            try {
                InventoryViewSettings.columns = 18;
                InventoryViewSettings.rows = 4;
                check(
                    NativeLayoutPatches.defaults(host)
                        .value(0) == 9
                        && NativeLayoutPatches.defaults(host)
                            .value(1) == 8,
                    "external baseline ignores personal Wide/Compact preferences");
            } finally {
                InventoryViewSettings.columns = personalColumns;
                InventoryViewSettings.rows = personalRows;
            }
            if (host.getClass() == GuiChest.class) {
                int[] hotspot = RecipeInfo.getGuiOffset(host);
                check(
                    hotspot[0] == 5 + access.dynamicinventory$panel()
                        .headerOffsetX() && hotspot[1] == 11
                            + access.dynamicinventory$panel()
                                .headerOffsetY(),
                    "NEI hotspot follows native header translation");
                check(RecipeInfo.getGuiOffset(host)[0] == hotspot[0], "NEI shared offsets do not accumulate");
            }
            check(InventoryLayoutEditor.open(host), "open registered screen");
            Object editor = activeField.get(null);
            check(mc.currentScreen == host && mc.thePlayer.openContainer == container, "host/container retained");
            check(
                !InventoryLayoutEditor.handleInput((GuiScreen) editor),
                "overlay cannot recursively intercept itself");
            host.handleInput(); // Exercise the transformed GuiScreen.handleInput interception with a real modal
                                // overlay.
            int delta = defaults.value(0) == 18 ? -1 : 1;
            action(editor, delta > 0 ? 1 : 0); // Move away from either allowed boundary.
            check(
                NativeLayoutPatches.resolve(host)
                    .value(0) == defaults.value(0) + delta,
                "live preference preview");
            action(editor, 102); // Hide controls so an occupied slot can be used as a drag handle.
            Slot visible = null;
            for (Slot slot : slots) if (slot.getHasStack() && slot.xDisplayPosition >= 0
                && slot.yDisplayPosition > 24
                && slot.yDisplayPosition < access.dynamicinventory$height() - 24) {
                    visible = slot;
                    break;
                }
            check(visible != null, "occupied visible slot for drag/input regression");
            int x = access.dynamicinventory$left() + visible.xDisplayPosition + 8;
            int y = access.dynamicinventory$top() + visible.yDisplayPosition + 8;
            invoke(editor, "mouseClicked", new Class<?>[] { int.class, int.class, int.class }, x, y, 0);
            invoke(
                editor,
                "mouseClickMove",
                new Class<?>[] { int.class, int.class, int.class, long.class },
                x + 13,
                y + 7,
                0,
                100L);
            invoke(editor, "mouseMovedOrUp", new Class<?>[] { int.class, int.class, int.class }, x + 13, y + 7, 0);
            check(
                NativeLayoutPatches.resolve(host)
                    .value(2) == 13,
                "drag updates panel offset");
            ((GuiScreen) editor).drawScreen(0, 0, 0);
            action(editor, 101);
            check(activeField.get(null) == null, "cancel exits modal editor");
            check(
                NativeLayoutPatches.resolve(host)
                    .toJson()
                    .equals(defaults.toJson()),
                "cancel restores saved/default values");
            check(InventoryLayoutEditor.open(host), "reopen after cancel");
            editor = activeField.get(null);
            int originalBoundary = access.dynamicinventory$panel()
                .nativeHeaderBoundary();
            for (int step = 0; step < 20; step++) action(editor, 22); // Tighten Auto to zero extra spacing.
            check(
                access.dynamicinventory$panel()
                    .nativeHeaderBoundary() <= originalBoundary,
                "zero gap removes native excess spacing");
            int[] nativeBefore = access.dynamicinventory$panel()
                .editorSectionBounds(2);
            int[] playerBefore = access.dynamicinventory$panel()
                .editorSectionBounds(1);
            int originalTop = access.dynamicinventory$top();
            action(editor, 16); // Player Y - 1, leaving native content in place.
            check(
                access.dynamicinventory$panel()
                    .editorSectionBounds(2)[1] - access.dynamicinventory$top() == nativeBefore[1] - originalTop,
                "player move leaves native content at its local origin");
            check(
                access.dynamicinventory$panel()
                    .editorSectionBounds(1)[1] - access.dynamicinventory$top() == playerBefore[1] - originalTop - 1,
                "player section moves upward independently");
            playerBefore = access.dynamicinventory$panel()
                .editorSectionBounds(1);
            originalTop = access.dynamicinventory$top();
            action(editor, 21); // Native Y + 1, leaving the player band in place.
            check(
                access.dynamicinventory$panel()
                    .editorSectionBounds(1)[1] - access.dynamicinventory$top() == playerBefore[1] - originalTop,
                "native move leaves player content at its local origin");
            NativeInventoryPanel previewPanel = access.dynamicinventory$panel();
            int nativeLabelY = originalBoundary - 9;
            previewPanel.foreground(() -> {
                int[] caption = NativeInventoryPanel
                    .playerLabel(net.minecraft.client.resources.I18n.format("container.inventory"), 0, nativeLabelY);
                check(
                    caption != null && caption[1] + previewPanel.headerOffsetY()
                        == previewPanel.editorSectionBounds(1)[1] - access.dynamicinventory$top() + 4,
                    "inventory caption follows the independently moved player band");
            });
            action(editor, 5); // Panel X + 1.
            NativeLayoutPatch expected = NativeLayoutPatches.resolve(host);
            action(editor, 100);
            check(activeField.get(null) == null, "save exits modal editor");
            NativeLayoutPatches.initialize(testDirectory.toFile()); // Clear memory cache, read the actual saved file.
            check(
                NativeLayoutPatches.resolve(host)
                    .toJson()
                    .equals(expected.toJson()),
                "saved layout survives cache reload");
            if (host.getClass() == GuiChest.class) {
                for (int chestSize : new int[] { 27, 54, 243 }) {
                    GuiChest variant = new GuiChest(
                        mc.thePlayer.inventory,
                        new InventoryBasic("fixture", false, chestSize));
                    InventoryUi.attachRegistered(variant.inventorySlots, mc.thePlayer.inventory);
                    check(
                        NativeLayoutPatches.resolve(variant)
                            .toJson()
                            .equals(expected.toJson()),
                        "same adapter layout shared with native chest capacity " + chestSize);
                }
            }
            check(InventoryLayoutEditor.open(host), "reopen saved layout");
            editor = activeField.get(null);
            action(editor, 103);
            check(
                NativeLayoutPatches.resolve(host)
                    .toJson()
                    .equals(defaults.toJson()),
                "reset is a defaults preview");
            action(editor, 101);
            check(
                NativeLayoutPatches.resolve(host)
                    .toJson()
                    .equals(expected.toJson()),
                "cancel reset retains saved layout");
            check(
                container.windowId == window && mc.currentScreen == host && mc.thePlayer.openContainer == container,
                "no replacement window or close packet");
            check(container.inventorySlots.size() == slots.size(), "slot count retained");
            for (int i = 0; i < slots.size(); i++) {
                check(
                    container.inventorySlots.get(i) == slots.get(i) && slots.get(i).slotNumber == i,
                    "slot identity " + i);
                check(
                    ItemStack.areItemStacksEqual(
                        stacks.get(i),
                        slots.get(i)
                            .getStack()),
                    "stack contents " + i);
            }
            check(mc.thePlayer.inventory.getItemStack() == null, "click/drag did not pick up an item");
            System.out.println(
                "PASS native editor: live drag, modal input, cancel, save/reload, reset/cancel; " + slots.size()
                    + " slot identities and stacks preserved");
        } finally {
            activeField.set(null, null);
            NativeLayoutPatches.draft(host, null);
            NativeLayoutPatches.initialize(directory.toFile());
            rebuild(host);
        }
    }

    private static void rebuild(GuiContainer host) {
        ContainerPanelAccess access = (ContainerPanelAccess) host;
        access.dynamicinventory$panel()
            .restore();
        access.dynamicinventory$panel(new NativeInventoryPanel(host));
    }

    private static void action(Object editor, int id) throws Exception {
        invoke(editor, "actionPerformed", new Class<?>[] { GuiButton.class }, new GuiButton(id, 0, 0, "test"));
    }

    private static void invoke(Object editor, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = editor.getClass()
            .getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(editor, arguments);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Native editor: " + message);
    }
}
