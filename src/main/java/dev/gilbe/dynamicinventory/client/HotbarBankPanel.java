package dev.gilbe.dynamicinventory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;

/** Revealable native-slot editor; the bank rows remain in the same server inventory and window. */
public final class HotbarBankPanel extends net.minecraft.client.gui.Gui {

    private static final int CELL = InventoryPanelLayout.CELL;
    private static final int FRAME_WIDTH = 32;
    private static final int FRAME_HEIGHT = 24;
    private static final Map<GuiContainer, HotbarBankPanel> PANELS = new WeakHashMap<>();
    private Rect bounds;
    private Rect dragStart, resizeStart;
    private int scroll, rows, columns = 9, rowStride = 1, startX, startY;
    private boolean releaseOwned, fitRefused;

    private static HotbarBankPanel panel(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!InventoryViewSettings.hotbarPanelVisible || mc.thePlayer == null
            || HotbarBanks.count(mc.thePlayer) == 0
            || mc.thePlayer.openContainer != gui.inventorySlots
            || PanelSettingsOverlay.isOpen(gui)
            || gui instanceof PlayerInventoryScreen screen && screen.layoutSettingsOpen()
            || !(gui instanceof PlayerInventoryScreen) && ((ContainerPanelAccess) gui).dynamicinventory$panel() == null
                && DetachedChestPanel.of(gui) == null)
            return null;
        return PANELS.computeIfAbsent(gui, ignored -> new HotbarBankPanel());
    }

    public static Rect bounds(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        return panel == null ? null : panel.bounds;
    }

    public static void draw(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        if (panel != null) {
            panel.layoutSlots(gui);
            panel.paint(gui, true);
        }
    }

    private List<Rect> obstacles(GuiContainer gui) {
        DetachedChestPanel floating = DetachedChestPanel.of(gui);
        if (floating != null) {
            List<Rect> occupied = new ArrayList<>(floating.occupiedBounds());
            if (!floating.detached()) occupied.add(floating.playerBounds());
            return occupied;
        }
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        return java.util.Collections.singletonList(
            new Rect(
                access.dynamicinventory$left(),
                access.dynamicinventory$top(),
                access.dynamicinventory$width(),
                access.dynamicinventory$height()));
    }

    public static void layout(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        if (panel != null) panel.layoutSlots(gui);
    }

    public static void drawFramework(GuiContainer gui) {
        drawFramework(gui, null, InventoryGuiTheme.get().label);
    }

    public static void drawFramework(GuiContainer gui, Consumer<Rect> frame, int labelColor) {
        HotbarBankPanel panel = panel(gui);
        if (panel != null && panel.bounds != null) panel.paint(gui, false, frame, labelColor);
    }

    private void layoutSlots(GuiContainer gui) {
        if (resizeStart != null) {
            placeSlots(gui);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int count = HotbarBanks.count(mc.thePlayer);
        if (dragStart != null) {
            bounds = new Rect(
                dragStart.x + Mouse.getX() * gui.width / mc.displayWidth - startX,
                dragStart.y + gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1 - startY,
                dragStart.width,
                dragStart.height);
        } else {
            Rect viewport = viewport(gui);
            ContainerPanelAccess access = (ContainerPanelAccess) gui;
            int preferredX = InventoryViewSettings.hotbarPanelX;
            int preferredY = InventoryViewSettings.hotbarPanelY;
            if (preferredX < 0 || preferredY < 0) {
                preferredX = Math.min(
                    gui.width - (9 * CELL + FRAME_WIDTH),
                    access.dynamicinventory$left() + access.dynamicinventory$width() + 6);
                preferredY = 16;
            }
            Rect fitted = null;
            List<Rect> occupied = obstacles(gui);
            int preferredColumns = InventoryViewSettings.hotbarPanelColumns;
            int preferredRows = Math.min(count, InventoryViewSettings.hotbarPanelRows);
            // Try the saved width first, then wrap or widen as space permits.
            for (int attempt = 0; attempt < 9 && fitted == null; attempt++) {
                int candidate = attempt < preferredColumns ? preferredColumns - attempt : attempt + 1;
                fitted = fit(viewport, occupied, preferredX, preferredY, candidate, preferredRows);
            }
            if (fitted == null) {
                bounds = null;
                if (!fitRefused) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "Move or resize an inventory panel to make room for the hotbar editor."));
                fitRefused = true;
                return;
            }
            applySize(fitted);
            fitRefused = false;
        }
        scroll = Math.max(0, Math.min(scroll, count - rows));
        placeSlots(gui);
    }

    private static Rect viewport(GuiContainer gui) {
        return new Rect(0, 8, gui.width, Math.max(0, gui.height - 16));
    }

    private static int stride(int columns) {
        return (9 + columns - 1) / columns;
    }

    private static Rect fit(Rect viewport, List<Rect> occupied, int x, int y, int columns, int preferredRows) {
        int bandHeight = stride(columns) * CELL;
        int maxRows = Math.min(preferredRows, (viewport.height - FRAME_HEIGHT) / bandHeight);
        for (int visible = maxRows; visible >= 1; visible--) {
            Rect fitted = InventoryPanelLayout.snapDrag(
                new Rect(x, y, columns * CELL + FRAME_WIDTH, visible * bandHeight + FRAME_HEIGHT),
                viewport,
                occupied);
            if (fitted != null) return fitted;
        }
        return null;
    }

    private void applySize(Rect fitted) {
        bounds = fitted;
        columns = (bounds.width - FRAME_WIDTH) / CELL;
        rowStride = stride(columns);
        rows = (bounds.height - FRAME_HEIGHT) / (rowStride * CELL);
    }

    private void finishDrag(GuiContainer gui, int x, int y) {
        Rect origin = dragStart;
        dragStart = null;
        Rect requested = new Rect(origin.x + x - startX, origin.y + y - startY, origin.width, origin.height);
        bounds = origin;
        if (requested.x != origin.x || requested.y != origin.y) {
            Rect fitted = InventoryPanelLayout.snapDrag(requested, viewport(gui), obstacles(gui));
            if (fitted != null) bounds = fitted;
        }
        if (bounds.x != origin.x || bounds.y != origin.y) {
            InventoryViewSettings.setHotbarPanelPosition(bounds.x, bounds.y);
        }
        placeSlots(gui);
    }

    private void resize(GuiContainer gui, int x, int y) {
        int columns = Math.max(1, Math.min(9, (resizeStart.width + x - startX - FRAME_WIDTH + CELL / 2) / CELL));
        int bandHeight = stride(columns) * CELL;
        int count = HotbarBanks.count(Minecraft.getMinecraft().thePlayer);
        if (count == 0) return;
        int visible = Math
            .max(1, Math.min(count, (resizeStart.height + y - startY - FRAME_HEIGHT + bandHeight / 2) / bandHeight));
        Rect candidate = new Rect(
            resizeStart.x,
            resizeStart.y,
            columns * CELL + FRAME_WIDTH,
            visible * bandHeight + FRAME_HEIGHT);
        Rect viewport = viewport(gui);
        if (!viewport.contains(candidate.x, candidate.y)
            || !viewport.contains(candidate.right() - 1, candidate.bottom() - 1)) return;
        for (Rect occupied : obstacles(gui)) if (occupied.intersects(candidate)) return;
        applySize(candidate);
        scroll = Math.min(scroll, count - rows);
        placeSlots(gui);
    }

    private List<Slot> placeSlots(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        DetachedChestPanel floating = DetachedChestPanel.of(gui);
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        List<Slot> visible = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            int raw = slot.inventory == mc.thePlayer.inventory ? slot.getSlotIndex() : -1;
            if (raw < 0 && floating != null) {
                int logical = floating.logicalSlot(slot.slotNumber);
                if (logical >= 0) raw = dev.gilbe.dynamicinventory.inventory.InventoryLayout.of(mc.thePlayer.inventory)
                    .rawIndex(logical);
            }
            if (raw < 0 || !HotbarBanks.isReserved(mc.thePlayer.inventory, raw)) continue;
            int index = HotbarBanks.bankIndex(mc.thePlayer.inventory, raw);
            int row = index / 9 - scroll;
            slot.xDisplayPosition = slot.yDisplayPosition = -10000;
            if (row < 0 || row >= rows) continue;
            slot.xDisplayPosition = bounds.x + 25 + index % 9 % columns * 18 - access.dynamicinventory$left();
            slot.yDisplayPosition = bounds.y + 18
                + (row * rowStride + index % 9 / columns) * 18
                - access.dynamicinventory$top();
            visible.add(slot);
        }
        return visible;
    }

    private void paint(GuiContainer gui, boolean drawSlotBackgrounds) {
        paint(gui, drawSlotBackgrounds, null, InventoryGuiTheme.get().label);
    }

    private void paint(GuiContainer gui, boolean drawSlotBackgrounds, Consumer<Rect> frame, int labelColor) {
        if (bounds == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (frame != null) frame.accept(bounds);
            else NativePanelFrame.draw(bounds.x, bounds.y, bounds.width, bounds.height, 2, 2, 0, 2, null);
            if (bounds.width >= 80) mc.fontRenderer.drawString("Hotbars", bounds.x + 7, bounds.y + 5, labelColor);
            mc.fontRenderer.drawString("X", bounds.right() - 13, bounds.y + 5, labelColor);
            int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
            int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
            PanelChrome.grip(bounds, 10, resizeStart != null || PanelChrome.corner(bounds, mouseX, mouseY) == 10);
            if (drawSlotBackgrounds) ResourceSlotGrid.draw(
                placeSlots(gui),
                ((ContainerPanelAccess) gui).dynamicinventory$left(),
                ((ContainerPanelAccess) gui).dynamicinventory$top());
            for (int row = 0; row < rows; row++) mc.fontRenderer.drawString(
                Integer.toString(row + scroll + 1),
                bounds.x + 6,
                bounds.y + 22 + row * rowStride * 18,
                labelColor);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static boolean pointer(GuiContainer gui, int x, int y, int button, boolean down) {
        HotbarBankPanel panel = PANELS.get(gui);
        if (panel != null && panel.releaseOwned) {
            if (panel.resizeStart != null && (button == -1 || button == 0 && !down)) panel.resize(gui, x, y);
            if (button == 0 && !down) {
                if (panel.resizeStart != null && (panel.bounds.width != panel.resizeStart.width
                    || panel.bounds.height != panel.resizeStart.height)) {
                    InventoryViewSettings.setHotbarPanelSize(panel.columns, panel.rows);
                }
                panel.resizeStart = null;
                if (panel.dragStart != null) panel.finishDrag(gui, x, y);
                panel.releaseOwned = false;
            }
            return true;
        }
        panel = panel(gui);
        if (panel == null || panel.bounds == null) return false;
        Rect rect = panel.bounds;
        if (!down || button != 0 || !rect.contains(x, y)) return false;
        if (PanelChrome.corner(rect, x, y) == 10) {
            panel.releaseOwned = true;
            if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
                panel.resizeStart = rect;
                panel.startX = x;
                panel.startY = y;
            }
            return true;
        }
        if (y < rect.y + 16) {
            panel.releaseOwned = true;
            if (x >= rect.right() - 18) InventoryViewSettings.setHotbarPanelVisible(false);
            else if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
                panel.dragStart = rect;
                panel.startX = x;
                panel.startY = y;
            }
            return true;
        }
        if (x < rect.x + 22 && y >= rect.y + 18 && y < rect.y + 18 + panel.rows * panel.rowStride * 18) {
            panel.releaseOwned = true;
            HotbarControls.swap(panel.scroll + (y - rect.y - 18) / (panel.rowStride * 18));
            return true;
        }
        return false;
    }

    public static boolean wheel(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        if (panel == null || panel.bounds == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        int delta = Mouse.getEventDWheel();
        if (delta == 0 || !panel.bounds.contains(x, y)) return false;
        panel.scroll = Math
            .max(0, Math.min(HotbarBanks.count(mc.thePlayer) - panel.rows, panel.scroll + (delta < 0 ? 1 : -1)));
        return true;
    }
}
