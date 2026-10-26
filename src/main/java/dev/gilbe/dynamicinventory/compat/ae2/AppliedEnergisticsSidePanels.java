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
        int widest = 0;
        for (Panel panel : panels) widest = Math.max(widest, panel.width());
        return mainWidth + GAP + widest;
    }

    public int position(int mainWidth, int headerTop, int playerTop) {
        int bottom = headerTop;
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            panel.x = mainWidth + GAP;
            // Cards align with the machine; the toolbox's first row aligns with the player grid.
            panel.y = i == 0 ? headerTop : Math.max(bottom + GAP, playerTop + 14 - PADDING);
            for (Slot slot : panel.slots) {
                int[] origin = origins.get(slot);
                slot.xDisplayPosition = panel.x + (panel.linking ? 0 : PADDING) + origin[0];
                slot.yDisplayPosition = panel.y + (panel.linking ? 0 : PADDING) + origin[1];
            }
            bottom = panel.y + panel.height();
        }
        return bottom;
    }

    public void draw(int left, int top, int style, int borderWidth, int frameCrop, int cornerCrop) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (Panel panel : panels) {
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
                for (Slot slot : panel.slots) drawTexturedModalRect(
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

    private static final class Panel {

        private final List<Slot> slots = new ArrayList<>();
        private final boolean linking;
        private int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right, bottom, x, y;

        private Panel(boolean linking) {
            this.linking = linking;
        }

        private void add(Slot slot) {
            slots.add(slot);
            left = Math.min(left, slot.xDisplayPosition);
            top = Math.min(top, slot.yDisplayPosition);
            right = Math.max(right, slot.xDisplayPosition + 16);
            bottom = Math.max(bottom, slot.yDisplayPosition + 16);
        }

        private int width() {
            return linking ? 47 : right - left + PADDING * 2;
        }

        private int height() {
            return linking ? 96 : bottom - top + PADDING * 2;
        }
    }
}
