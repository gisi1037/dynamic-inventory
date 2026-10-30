package dev.gilbe.dynamicinventory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;

/** Revealable native-slot editor; the bank rows remain in the same server inventory and window. */
public final class HotbarBankPanel extends net.minecraft.client.gui.Gui {

    private static final Map<GuiContainer, HotbarBankPanel> PANELS = new WeakHashMap<>();
    private Rect bounds;
    private int scroll, rows, columns = 9, rowStride = 1, startX, startY;
    private boolean dragging, releaseOwned, fitRefused;

    private static HotbarBankPanel panel(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!InventoryViewSettings.hotbarPanelVisible || mc.thePlayer == null
            || HotbarBanks.count(mc.thePlayer) == 0
            || mc.thePlayer.openContainer != gui.inventorySlots
            || PanelSettingsOverlay.isOpen(gui)
            || gui instanceof PlayerInventoryScreen screen && screen.layoutSettingsOpen()
            || !(gui instanceof PlayerInventoryScreen) && ((ContainerPanelAccess) gui).dynamicinventory$panel() == null)
            return null;
        return PANELS.computeIfAbsent(gui, ignored -> new HotbarBankPanel());
    }

    public static Rect bounds(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        return panel == null ? null : panel.bounds;
    }

    public static void draw(GuiContainer gui) {
        HotbarBankPanel panel = panel(gui);
        if (panel != null) panel.paint(gui);
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

    private void paint(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        int count = HotbarBanks.count(mc.thePlayer);
        Rect viewport = new Rect(0, 8, gui.width, Math.max(0, gui.height - 16));
        if (bounds == null) {
            ContainerPanelAccess access = (ContainerPanelAccess) gui;
            bounds = new Rect(
                Math.min(gui.width - 194, access.dynamicinventory$left() + access.dynamicinventory$width() + 6),
                16,
                194,
                42);
        }
        if (dragging && Mouse.isButtonDown(0)) {
            bounds = new Rect(
                Mouse.getX() * gui.width / mc.displayWidth - startX,
                gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1 - startY,
                bounds.width,
                bounds.height);
        } else {
            dragging = false;
            Rect fitted = null;
            List<Rect> occupied = obstacles(gui);
            // Keep a full hotbar row when possible; wrap its nine real cells on smaller screens.
            for (int candidate = 9; candidate >= 1; candidate--) {
                int stride = (9 + candidate - 1) / candidate;
                int visible = Math.min(count, (viewport.height - 24) / (stride * 18));
                if (visible < 1) continue;
                fitted = InventoryPanelLayout.snapDrag(
                    new Rect(bounds.x, bounds.y, candidate * 18 + 32, visible * stride * 18 + 24),
                    viewport,
                    occupied);
                if (fitted != null) {
                    columns = candidate;
                    rowStride = stride;
                    rows = visible;
                    break;
                }
            }
            if (fitted == null) {
                bounds = null;
                if (!fitRefused) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "Move or resize an inventory panel to make room for the hotbar editor."));
                fitRefused = true;
                return;
            }
            bounds = fitted;
            fitRefused = false;
        }
        scroll = Math.min(scroll, count - rows);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            NativePanelFrame.draw(bounds.x, bounds.y, bounds.width, bounds.height, 2, 2, 0, 2, null);
            if (bounds.width >= 80)
                mc.fontRenderer.drawString("Hotbars", bounds.x + 7, bounds.y + 5, InventoryGuiTheme.get().label);
            mc.fontRenderer.drawString("X", bounds.right() - 13, bounds.y + 5, InventoryGuiTheme.get().label);
            ContainerPanelAccess access = (ContainerPanelAccess) gui;
            List<Slot> visible = new ArrayList<>();
            for (Slot slot : gui.inventorySlots.inventorySlots) {
                if (slot.inventory != mc.thePlayer.inventory
                    || !HotbarBanks.isReserved(mc.thePlayer.inventory, slot.getSlotIndex())) continue;
                int index = HotbarBanks.bankIndex(mc.thePlayer.inventory, slot.getSlotIndex());
                int row = index / 9 - scroll;
                slot.xDisplayPosition = slot.yDisplayPosition = -10000;
                if (row < 0 || row >= rows) continue;
                slot.xDisplayPosition = bounds.x + 25 + index % 9 % columns * 18 - access.dynamicinventory$left();
                slot.yDisplayPosition = bounds.y + 18
                    + (row * rowStride + index % 9 / columns) * 18
                    - access.dynamicinventory$top();
                visible.add(slot);
            }
            ResourceSlotGrid.draw(visible, access.dynamicinventory$left(), access.dynamicinventory$top());
            for (int row = 0; row < rows; row++) mc.fontRenderer.drawString(
                Integer.toString(row + scroll + 1),
                bounds.x + 6,
                bounds.y + 22 + row * rowStride * 18,
                InventoryGuiTheme.get().label);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static boolean pointer(GuiContainer gui, int x, int y, int button, boolean down) {
        HotbarBankPanel panel = PANELS.get(gui);
        if (panel != null && panel.releaseOwned) {
            if (button == 0 && !down) {
                panel.dragging = false;
                panel.releaseOwned = false;
            }
            return true;
        }
        panel = panel(gui);
        if (panel == null || panel.bounds == null) return false;
        Rect rect = panel.bounds;
        if (!down || button != 0 || !rect.contains(x, y)) return false;
        if (y < rect.y + 16) {
            panel.releaseOwned = true;
            if (x >= rect.right() - 18) InventoryViewSettings.setHotbarPanelVisible(false);
            else if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
                panel.dragging = true;
                panel.releaseOwned = true;
                panel.startX = x - rect.x;
                panel.startY = y - rect.y;
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
