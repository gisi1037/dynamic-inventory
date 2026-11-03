package dev.gilbe.dynamicinventory.compat.ae2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.NativePanelFrame;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

/** Client-only placement of the reviewed AE2 upgrade/toolbox inventories beside the main frame. */
public final class AppliedEnergisticsSidePanels extends Gui {

    private static final int GAP = 3;
    private static final int PADDING = 6;
    private static final ResourceLocation SLOTS = new ResourceLocation("textures/gui/container/inventory.png");
    private static final ResourceLocation SECURITY = new ResourceLocation(
        "appliedenergistics2",
        "textures/guis/security.png");
    private final List<Panel> panels;
    private final Map<Slot, int[]> origins = new IdentityHashMap<>();

    private AppliedEnergisticsSidePanels(List<Panel> panels) {
        this.panels = panels;
        panels.sort(Comparator.comparingInt(panel -> panel.top));
        for (Panel panel : panels) for (Slot slot : panel.slots) origins.put(
            slot,
            panel.linking ? new int[] { slot.xDisplayPosition - 195, slot.yDisplayPosition }
                : new int[] { slot.xDisplayPosition - panel.left, slot.yDisplayPosition - panel.top });
        for (Panel panel : panels) measure(panel);
    }

    public static AppliedEnergisticsSidePanels create(GuiContainer gui, ContainerInventoryRegion region) {
        if (!AppliedEnergisticsBackground.hasSidePanels(
            gui.getClass()
                .getName()))
            return null;
        Map<IInventory, Panel> groups = new IdentityHashMap<>();
        boolean linking = AppliedEnergisticsBackground.isSecurity(
            gui.getClass()
                .getName());
        int sideLeft = AppliedEnergisticsBackground.mainSheetWidth(
            gui.getClass()
                .getName(),
            177);
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.slotNumber >= region.nativeSlotCount || region.logical(slot.slotNumber) >= 0
                || slot.xDisplayPosition < sideLeft) continue;
            // AE2 creates one backing inventory for upgrades and another for the borrowed toolbox.
            // Group by that actual owner, never by slot IDs or the player's current inventory size.
            groups.computeIfAbsent(slot.inventory, ignored -> new Panel(linking))
                .add(slot);
        }
        return groups.isEmpty() ? null : new AppliedEnergisticsSidePanels(new ArrayList<>(groups.values()));
    }

    public boolean contains(Slot slot) {
        return origins.containsKey(slot);
    }

    public int width(int mainWidth) {
        int width = mainWidth;
        for (Panel panel : panels) {
            // Cell Workbench upgrade slots can become enabled without reinitializing the GUI.
            // Reserve their possible column now so revealing them cannot push the toolbox off screen.
            int contentWidth = 0;
            for (Slot slot : panel.slots) contentWidth = Math.max(contentWidth, origins.get(slot)[0] + 16);
            width += GAP + (panel.linking ? 47 : contentWidth + PADDING * 2);
        }
        return width;
    }

    public List<Rect> bounds(int left, int top) {
        List<Rect> result = new ArrayList<>();
        for (Panel panel : panels)
            if (panel.visible()) result.add(new Rect(left + panel.x, top + panel.y, panel.width(), panel.height()));
        return result;
    }

    public int position(int mainWidth, int headerTop) {
        int bottom = headerTop;
        int nextX = mainWidth + GAP;
        for (Panel panel : panels) {
            measure(panel);
            if (!panel.visible()) continue;
            // Keep every native side inventory beside the machine. A borrowed tool must not
            // occupy the player band's height or displace detached player panels to the right.
            panel.x = nextX;
            panel.y = headerTop;
            for (Slot slot : panel.slots) {
                int[] origin = origins.get(slot);
                slot.xDisplayPosition = panel.x + (panel.linking ? 0 : PADDING) + origin[0];
                slot.yDisplayPosition = panel.y + (panel.linking ? 0 : PADDING) + origin[1];
            }
            bottom = Math.max(bottom, panel.y + panel.height());
            nextX += panel.width() + GAP;
        }
        return bottom;
    }

    public void draw(int left, int top, int style, int borderWidth, int frameCrop, int cornerCrop) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (Panel panel : panels) {
                if (!panel.visible()) continue;
                NativePanelFrame.draw(
                    left + panel.x,
                    top + panel.y,
                    panel.width(),
                    panel.height(),
                    style == 0 ? 2 : style,
                    borderWidth,
                    frameCrop,
                    cornerCrop,
                    null);
                GL11.glColor4f(1, 1, 1, 1);
                if (panel.linking) {
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(SECURITY);
                    // Keep AE2's linking arrow and larger output-slot artwork, without its old frame.
                    drawTexturedModalRect(left + panel.x + 16, top + panel.y + 9, 211, 9, 18, 18);
                    drawTexturedModalRect(left + panel.x + 17, top + panel.y + 32, 212, 32, 18, 25);
                    drawTexturedModalRect(left + panel.x + 12, top + panel.y + 63, 207, 63, 26, 26);
                    continue;
                }
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(SLOTS);
                for (Slot slot : panel.slots) if (slot.func_111238_b()) drawTexturedModalRect(
                    left + slot.xDisplayPosition - 1,
                    top + slot.yDisplayPosition - 1,
                    7,
                    83,
                    18,
                    18);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** AE2 keeps disabled optional upgrade slots in the container so changing cells needs no new window. */
    private void measure(Panel panel) {
        panel.contentWidth = panel.contentHeight = 0;
        for (Slot slot : panel.slots) if (slot.func_111238_b()) {
            int[] origin = origins.get(slot);
            panel.contentWidth = Math.max(panel.contentWidth, origin[0] + 16);
            panel.contentHeight = Math.max(panel.contentHeight, origin[1] + 16);
        }
    }

    private static final class Panel {

        private final List<Slot> slots = new ArrayList<>();
        private final boolean linking;
        private int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, x, y;
        private int contentWidth, contentHeight;

        private Panel(boolean linking) {
            this.linking = linking;
        }

        private void add(Slot slot) {
            slots.add(slot);
            left = Math.min(left, slot.xDisplayPosition);
            top = Math.min(top, slot.yDisplayPosition);
        }

        private int width() {
            return linking ? 47 : visible() ? contentWidth + PADDING * 2 : 0;
        }

        private int height() {
            return linking ? 96 : visible() ? contentHeight + PADDING * 2 : 0;
        }

        private boolean visible() {
            return linking || contentWidth > 0;
        }
    }
}
