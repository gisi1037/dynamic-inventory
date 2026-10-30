package dev.gilbe.dynamicinventory.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** Preserve both joined-grid artwork and the outer edges supplied by the active resource pack. */
final class ResourceSlotGrid extends Gui {

    private static final ResourceSlotGrid DRAW = new ResourceSlotGrid();
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    static void draw(List<Slot> slots, int left, int top) {
        Set<Long> positions = new HashSet<>();
        for (Slot slot : slots)
            if (slot.xDisplayPosition != -10000) positions.add(key(slot.xDisplayPosition, slot.yDisplayPosition));
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(INVENTORY);
            for (Slot slot : slots) {
                int x = slot.xDisplayPosition, y = slot.yDisplayPosition;
                if (x == -10000) continue;
                boolean firstColumn = !positions.contains(key(x - 18, y));
                boolean lastColumn = !positions.contains(key(x + 18, y));
                boolean firstRow = !positions.contains(key(x, y - 18));
                boolean lastRow = !positions.contains(key(x, y + 18));
                int u = firstColumn ? 7 : lastColumn ? 151 : 25;
                int bottom = 136;
                int v = firstRow ? 83 : lastRow ? 119 : 101;
                int w = firstColumn && lastColumn ? 17 : 18;
                int h = firstRow && lastRow ? 17 : 18;
                int px = left + x - 1, py = top + y - 1;
                // Disjoint strips avoid double-blending transparent pack pixels in single rows/columns.
                DRAW.drawTexturedModalRect(px, py, u, v, w, h);
                if (w == 17) DRAW.drawTexturedModalRect(px + 17, py, 168, v, 1, h);
                if (h == 17) DRAW.drawTexturedModalRect(px, py + 17, u, bottom, w, 1);
                if (w == 17 && h == 17) DRAW.drawTexturedModalRect(px + 17, py + 17, 168, bottom, 1, 1);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static long key(int x, int y) {
        return (long) x << 32 | y & 0xffffffffL;
    }
}
