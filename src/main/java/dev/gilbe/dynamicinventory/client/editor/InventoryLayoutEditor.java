package dev.gilbe.dynamicinventory.client.editor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.client.NativePanelFrame;

/** A modal presentation editor over the real GUI. It never opens, closes or replaces the game container. */
public final class InventoryLayoutEditor {

    private static final KeyBinding KEY = new KeyBinding(
        "key.dynamicinventory.uiEditor",
        Keyboard.KEY_F10,
        "key.categories.dynamicinventory");
    private static Overlay active;
    private boolean wasDown;

    public InventoryLayoutEditor() {
        ClientRegistry.registerKeyBinding(KEY);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean down = GameSettings.isKeyDown(KEY);
        if (active != null && !active.isCurrent()) {
            NativeLayoutPatches.draft(active.host, null);
            active = null;
        }
        if (active == null && down && !wasDown && mc.currentScreen instanceof GuiContainer)
            open((GuiContainer) mc.currentScreen);
        wasDown = down;
    }

    /** Client entry point for the hotkey. An editor cannot opt an unknown screen into inventory support. */
    public static boolean open(GuiContainer host) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null || mc.currentScreen != host) return false;
        if (((ContainerPanelAccess) host).dynamicinventory$panel() == null) {
            message(
                "This editor supports registered native screens. MUI2 editing is not available yet; F9 captures an audit.");
            return false;
        }
        if (mc.thePlayer == null || mc.thePlayer.openContainer != host.inventorySlots
            || !InventoryUi.isOpeningReady(mc.thePlayer)) {
            message("Wait for the inventory opening to finish before editing.");
            return false;
        }
        if (mc.thePlayer.inventory.getItemStack() != null) {
            message("Put down the held item before editing the GUI.");
            return false;
        }
        active = new Overlay(host);
        active.setWorldAndResolution(mc, host.width, host.height);
        return true;
    }

    /** Called before GuiScreen dispatches any mouse/keyboard events to slots, NEI or the native GUI. */
    public static boolean handleInput(GuiScreen screen) {
        Overlay editor = active;
        if (editor == null || screen != editor.host) return false;
        if (!editor.isCurrent()) {
            NativeLayoutPatches.draft(editor.host, null);
            active = null;
        }
        // Do not call displayGuiScreen(editor): that closes the host's container and sends a close packet.
        // This unattached GuiScreen drains the complete input batch, even if Save/Cancel ends the session.
        editor.handleInput();
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (active == null || event.gui != active.host) return;
        if (active.width != event.gui.width || active.height != event.gui.height)
            active.setWorldAndResolution(Minecraft.getMinecraft(), event.gui.width, event.gui.height);
        active.drawScreen(event.mouseX, event.mouseY, event.renderPartialTicks);
    }

    private static void message(String text) {
        if (Minecraft.getMinecraft().thePlayer != null)
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Inventory editor: " + text));
    }

    private static final class Overlay extends GuiScreen {

        private static final int TOOL_WIDTH = 206, TOOL_HEIGHT = 210;
        private static final int[][] PAGES = { { 0, 1, 2, 3, 4, 11 }, { 7, 8, 9, 10, 13 }, { 12, 16 }, { 5, 6 },
            { 14, 15 } };
        private static final String[] PAGE_NAMES = { "Layout", "Sections", "Style", "Advanced", "Screen" };
        private static final String[] DRAG_NAMES = { "Panel", "Player", "Native" };
        private final GuiContainer host;
        private final Container container;
        private final List<Slot> slots;
        private NativeLayoutPatch patch;
        private boolean controls = true, dragging;
        private int lastX, lastY, toolX, toolY;
        private int page, dragTarget;
        private String status = "Drag the outlined panel to move it.";

        private Overlay(GuiContainer host) {
            this.host = host;
            container = host.inventorySlots;
            slots = new ArrayList<>(container.inventorySlots);
            patch = NativeLayoutPatches.resolve(host);
        }

        private boolean isCurrent() {
            Minecraft client = Minecraft.getMinecraft();
            if (client.currentScreen != host || client.thePlayer == null
                || client.thePlayer.openContainer != container
                || host.inventorySlots != container
                || container.inventorySlots.size() != slots.size()) return false;
            for (int i = 0; i < slots.size(); i++)
                if (container.inventorySlots.get(i) != slots.get(i) || slots.get(i).slotNumber != i) return false;
            return true;
        }

        private boolean canEdit() {
            return isCurrent() && InventoryUi.isOpeningReady(mc.thePlayer)
                && mc.thePlayer.inventory.getItemStack() == null;
        }

        @Override
        public void initGui() {
            toolX = Math.max(2, width - TOOL_WIDTH - 4);
            toolY = 4;
            buttonList.clear();
            if (!controls) {
                buttonList.add(new GuiButton(102, toolX, toolY, TOOL_WIDTH, 20, "Show editor controls (F10)"));
                return;
            }
            NativeInventoryPanel panel = ((ContainerPanelAccess) host).dynamicinventory$panel();
            GuiButton mode = new GuiButton(
                106,
                toolX + 113,
                toolY + 4,
                87,
                14,
                InventoryUi.findLayout(container, host.getClass())
                    .getMaximumVisibleRows() == 3 || !NativeLayoutPatches.expanded(host) ? "Mode: Scroll"
                        : "Mode: Vertical");
            mode.enabled = canEdit() && InventoryUi.findLayout(container, host.getClass())
                .followsMachineMode();
            buttonList.add(mode);
            int labelWidth = 0;
            for (String name : PAGE_NAMES) labelWidth += fontRendererObj.getStringWidth(name);
            int tabX = toolX + 6;
            int tabPadding = Math.max(2, (TOOL_WIDTH - 12 - labelWidth) / PAGE_NAMES.length);
            for (int tab = 0; tab < PAGE_NAMES.length; tab++) {
                int tabWidth = fontRendererObj.getStringWidth(PAGE_NAMES[tab]) + tabPadding;
                GuiButton button = new GuiButton(110 + tab, tabX, toolY + 32, tabWidth - 1, 18, PAGE_NAMES[tab]);
                button.enabled = tab != page;
                buttonList.add(button);
                tabX += tabWidth;
            }
            for (int row = 0; row < PAGES[page].length; row++) {
                int field = PAGES[page][row];
                GuiButton minus = new GuiButton(field * 2, toolX + 153, toolY + 54 + row * 18, 22, 18, "-");
                GuiButton plus = new GuiButton(field * 2 + 1, toolX + 178, toolY + 54 + row * 18, 22, 18, "+");
                minus.enabled = fieldEnabled(panel, field)
                    && (field != 5 || patch.value(5) > panel.nativeHeaderBoundary())
                    && (field < 14 || patch.value(field) > 0);
                plus.enabled = fieldEnabled(panel, field) && (field != 1
                    || InventoryUi.findLayout(container, host.getClass())
                        .getMaximumVisibleRows() == 0
                    || patch.value(1) < InventoryUi.findLayout(container, host.getClass())
                        .getMaximumVisibleRows())
                    && (field < 14 || patch.value(field) < (field == 16 ? 6 : 128));
                buttonList.add(minus);
                buttonList.add(plus);
            }
            buttonList.add(new GuiButton(105, toolX + 6, toolY + 164, 96, 18, "Drag: " + DRAG_NAMES[dragTarget]));
            buttonList.add(new GuiButton(102, toolX + 106, toolY + 164, 94, 18, "Preview (F10)"));
            buttonList.add(new GuiButton(100, toolX + 6, toolY + 186, 61, 18, "Save"));
            buttonList.add(new GuiButton(101, toolX + 72, toolY + 186, 61, 18, "Cancel"));
            buttonList.add(new GuiButton(103, toolX + 138, toolY + 186, 62, 18, "Reset"));
        }

        private boolean fieldEnabled(NativeInventoryPanel panel, int field) {
            if (panel == null) return false;
            if (field == 0) return InventoryUi.findLayout(container, host.getClass())
                .getFixedPlayerColumns() == 0;
            if (field == 1) return !InventoryUi.findLayout(container, host.getClass())
                .followsMachineMode();
            if (field == 16) return patch.value(12) != 0;
            if (field == 13) return panel.hasAttachedChest();
            if (panel.hasAttachedChest() && (field == 4 || field == 5 || field == 9 || field == 10)) return false;
            return field != 6 || panel.hasAttachedChest()
                || !InventoryViewSettings.compactNativePanels
                || patch.value(12) != 0;
        }

        private String hint(int field, NativeInventoryPanel panel) {
            if (field == 1 && !fieldEnabled(panel, field))
                return "Rows follow the mode above. Mode previews do not change the global machine setting.";
            if (field == 0 && !fieldEnabled(panel, field))
                return "This host keeps fixed columns; resize vertically or scroll to preserve item positions.";
            if (field == 16 && patch.value(12) == 0)
                return "Choose a unified style; Original uses the resource-pack border.";
            if (!fieldEnabled(panel, field)) return "This control is owned by the split/compact frame adapter.";
            if (field == 16) return "Border thickness in pixels; 0 keeps the fill without a border.";
            if (field == 5)
                return "Minimum only; measured header: " + panel.nativeHeaderBoundary() + " px. Use gap to tighten.";
            if (field == 6) return "Crop native frame edges, including custom-drawn chest textures.";
            if (field == 11) return "-1: automatic. 0: no extra spacing after the native section.";
            if (field == 13) return "Space between player and content sections; stacks vertically on small screens.";
            if (field == 14 || field == 15) return "Reserve viewport space; fewer visible rows may need scrolling.";
            if (field == 12)
                return "Original, unified square edges, or unified beveled corners. Uses the active resource-pack palette.";
            if (field >= 7 && field <= 10) return "Move this section independently; negative values move left/up.";
            return "Presentation preference; automatically fitted to this GUI scale.";
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ContainerPanelAccess access = (ContainerPanelAccess) host;
            NativeInventoryPanel current = access.dynamicinventory$panel();
            int[] section = current.editorSectionBounds(dragTarget);
            int x = section[0], y = section[1];
            int right = x + section[2], bottom = y + section[3];
            drawRect(x - 1, y - 1, right + 1, y, 0xFF55DDFF);
            drawRect(x - 1, bottom, right + 1, bottom + 1, 0xFF55DDFF);
            drawRect(x - 1, y, x, bottom, 0xFF55DDFF);
            drawRect(right, y, right + 1, bottom, 0xFF55DDFF);
            if (controls) {
                drawRect(toolX, toolY, toolX + TOOL_WIDTH, toolY + TOOL_HEIGHT, 0xF0222222);
                fontRendererObj.drawString("Layout editor", toolX + 6, toolY + 6, 0xFFFFFF);
                NativeInventoryPanel panel = access.dynamicinventory$panel();
                if (panel != null) fontRendererObj.drawString(
                    "Fitted: " + panel.visibleColumns() + " x " + panel.visibleRows() + " (screen limited)",
                    toolX + 6,
                    toolY + 20,
                    0xAAAAAA);
                for (int row = 0; row < PAGES[page].length; row++) {
                    int field = PAGES[page][row];
                    String value = field == 11 && patch.value(field) < 0 ? "Auto"
                        : Integer.toString(patch.value(field));
                    if (field == 5 && patch.value(field) == 0) value = "Auto";
                    if (field == 12) value = NativePanelFrame.NAMES[patch.value(field)];
                    if (field == 1 && !fieldEnabled(panel, field))
                        value = NativeLayoutPatches.expanded(host) ? "Auto" : "Up to 3";
                    fontRendererObj.drawString(
                        NativeLayoutPatch.LABELS[field] + ": " + value,
                        toolX + 6,
                        toolY + 59 + row * 18,
                        fieldEnabled(panel, field) ? 0xFFFFFF : 0x777777);
                }
            }
            String explanation = status;
            if (controls && mouseX >= toolX + 113 && mouseX < toolX + 200 && mouseY >= toolY + 4 && mouseY < toolY + 18)
                explanation = "Preview Vertical/Scroll; Save keeps layout edits, not this temporary mode.";
            if (controls && mouseX >= toolX && mouseX < toolX + TOOL_WIDTH && mouseY >= toolY + 54) {
                int row = (mouseY - toolY - 54) / 18;
                if (row < PAGES[page].length) explanation = hint(PAGES[page][row], current);
            }
            drawRect(0, height - 23, width, height, 0xD0222222);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(
                    canEdit() ? explanation : "Editing paused: wait for sync / put down held item.",
                    width - 8),
                4,
                height - 20,
                0xFFFFFF);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth("Esc: cancel | F10: controls | Shift +/-: 8 pixels", width - 8),
                4,
                height - 10,
                0xAAAAAA);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (active != this) return;
            if (button.id == 101) {
                finish(false);
                return;
            }
            if (button.id == 102) {
                controls = !controls;
                initGui();
                return;
            }
            if (button.id >= 110 && button.id < 110 + PAGE_NAMES.length) {
                page = button.id - 110;
                initGui();
                return;
            }
            if (button.id == 105) {
                dragTarget = (dragTarget + 1) % (((ContainerPanelAccess) host).dynamicinventory$panel()
                    .hasAttachedChest() ? 2 : 3);
                initGui();
                return;
            }
            if (!canEdit()) return;
            if (button.id == 106) {
                previewMode();
                return;
            }
            if (button.id == 100) finish(true);
            else if (button.id == 103) preview(NativeLayoutPatches.defaults(host));
            else {
                int field = button.id / 2;
                NativeInventoryPanel panel = ((ContainerPanelAccess) host).dynamicinventory$panel();
                if (!fieldEnabled(panel, field)) return;
                int step = field >= 2 && field != 6 && field != 12 && isShiftKeyDown() ? 8 : 1;
                int delta = button.id % 2 == 0 ? -step : step;
                // The old control incremented a floor of zero beneath a 190px native header, appearing broken.
                // Start its first increase at the measured floor so every enabled press has a visible effect.
                if (field == 5) {
                    int floor = panel.nativeHeaderBoundary();
                    delta = Math.max(floor, Math.max(floor, patch.value(field)) + delta) - patch.value(field);
                }
                if (field == 11) {
                    // Convert Auto to its measured value first: '-' must tighten the gap, never make it larger.
                    delta = Math.max(0, panel.sectionGap() + delta) - patch.value(field);
                }
                preview(patch.adjust(field, delta));
            }
        }

        @Override
        protected void keyTyped(char character, int keyCode) {
            if (active != this) return;
            if (keyCode == Keyboard.KEY_ESCAPE) finish(false);
            else if (keyCode == KEY.getKeyCode()) {
                controls = !controls;
                initGui();
            }
            // All other keys are intentionally consumed; hotbar shortcuts must not reach the host.
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (active != this) return;
            boolean toolbar = x >= toolX && x < toolX + TOOL_WIDTH
                && y >= toolY
                && y < toolY + (controls ? TOOL_HEIGHT : 20);
            if (toolbar) {
                super.mouseClicked(x, y, button);
                return;
            }
            int[] section = ((ContainerPanelAccess) host).dynamicinventory$panel()
                .editorSectionBounds(dragTarget);
            dragging = button == 0 && canEdit()
                && y < height - 23
                && x >= section[0]
                && x < section[0] + section[2]
                && y >= section[1]
                && y < section[1] + section[3];
            lastX = x;
            lastY = y;
        }

        @Override
        protected void mouseClickMove(int x, int y, int button, long elapsed) {
            if (active != this || !dragging || !canEdit()) return;
            int field = dragTarget == 1 ? 7 : dragTarget == 2 ? 9 : 2;
            preview(
                patch.adjust(field, x - lastX)
                    .adjust(field + 1, y - lastY));
            lastX = x;
            lastY = y;
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            dragging = false;
            super.mouseMovedOrUp(x, y, button);
        }

        private void preview(NativeLayoutPatch next) {
            NativeLayoutPatch previous = patch;
            NativeLayoutPatches.draft(host, next);
            try {
                rebuild();
                // Reflect host limits in the controls too, including a fallback's three-row cap.
                patch = NativeLayoutPatches.resolve(host);
                status = "Preview only. Save to keep this layout.";
                initGui();
            } catch (RuntimeException failure) {
                NativeLayoutPatches.draft(host, previous);
                rebuild();
                status = "Preview failed; see game log.";
                DynamicInventory.LOG.warn("Could not preview native inventory layout", failure);
            }
        }

        private void previewMode() {
            boolean previous = NativeLayoutPatches.expanded(host);
            NativeLayoutPatches.previewMode(host, !previous);
            try {
                rebuild();
                status = "Preview only; global machine mode is unchanged.";
                initGui();
            } catch (RuntimeException failure) {
                NativeLayoutPatches.previewMode(host, previous);
                rebuild();
                status = "Mode preview failed; see game log.";
                DynamicInventory.LOG.warn("Could not preview native inventory mode", failure);
            }
        }

        private void rebuild() {
            // Restore native anchors before constructing a new decorator. Never call host.initGui():
            // some native GUIs rebuild controls or mutate their slot lists there.
            if (!isCurrent()) throw new IllegalStateException("Container changed during editing");
            ContainerPanelAccess access = (ContainerPanelAccess) host;
            NativeInventoryPanel old = access.dynamicinventory$panel();
            if (old != null) old.restore();
            access.dynamicinventory$panel(null);
            access.dynamicinventory$panel(new NativeInventoryPanel(host));
        }

        private void finish(boolean save) {
            try {
                if (save) {
                    if (!canEdit()) return;
                    message(
                        "Saved " + NativeLayoutPatches.save(host, patch)
                            .getFileName());
                }
                NativeLayoutPatches.draft(host, null);
                // Both exit paths restore the real global mode after a temporary mode preview.
                if (isCurrent()) rebuild();
                active = null;
            } catch (IOException | RuntimeException failure) {
                status = "Could not save/restore layout; see game log.";
                DynamicInventory.LOG.warn("Could not finish native inventory layout editing", failure);
            }
        }
    }
}
