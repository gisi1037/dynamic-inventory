package dev.gilbe.dynamicinventory.client;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** The four silhouettes from the reference cube, shaded with the adjoining frame's edge colors. */
final class PanelCorners {

    // Source art uses an outline, highlight, shadow and fill. Other frame styles
    // use the same silhouettes with the bevel colors and width selected by the player.
    private static final String[][] SLICES = { { "..OO", ".OHH", "OHHH", "OHHH" }, { "O...", "HO..", "HFO.", "FSSO" },
        { "OHHF", "OHHF", ".OFS", "..OS", "...O" }, { "FSSO", "SSSO", "SSSO", "SSO.", "OO.." } };
    static final int SOURCE_WIDTH = 4;
    private static final IntBuffer SCISSOR = BufferUtils.createIntBuffer(16);

    private final int x, y, right, bottom;
    final int width, topHeight, bottomHeight;

    PanelCorners(int x, int y, int width, int height, int size) {
        this.x = x;
        this.y = y;
        right = x + width;
        bottom = y + height;
        this.width = Math.min(size, Math.min(width, height) / 2);
        topHeight = this.width;
        bottomHeight = this.width == 0 ? 0 : Math.min(this.width + 1, height / 2);
    }

    /** Paint the three disjoint bands outside the four corner slices. */
    void fill(int left, int top, int right, int bottom, int color) {
        rect(left, Math.max(top, y + topHeight), right, Math.min(bottom, this.bottom - bottomHeight), color);
        left = Math.max(left, x + width);
        right = Math.min(right, this.right - width);
        rect(left, top, right, Math.min(bottom, y + topHeight), color);
        rect(left, Math.max(top, this.bottom - bottomHeight), right, bottom, color);
    }

    private static void rect(int left, int top, int right, int bottom, int color) {
        if (left < right && top < bottom) Gui.drawRect(left, top, right, bottom, color);
    }

    void draw(int fill, int[] topLeft, int[] bottomRight) {
        draw(fill, topLeft, bottomRight, null);
    }

    void drawSource(InventoryGuiTheme theme) {
        draw(theme.panelFill, null, null, theme);
    }

    private void draw(int fill, int[] topLeft, int[] bottomRight, InventoryGuiTheme source) {
        if (width == 0) return;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (int corner = 0; corner < SLICES.length; corner++) {
                String[] slice = SLICES[corner];
                boolean lower = corner >= 2;
                boolean rightSide = (corner & 1) != 0;
                int left = rightSide ? right - width : x;
                int top = lower ? bottom - bottomHeight : y;
                int height = lower ? bottomHeight : topHeight;
                int[] horizontal = lower ? bottomRight : topLeft;
                int[] vertical = rightSide ? bottomRight : topLeft;
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        char pixel = pixelAt(slice, col, row, height);
                        if (pixel == '.') continue;
                        int edgeX = rightSide ? width - col - 1 : col;
                        int edgeY = lower ? height - row - 1 : row;
                        int color;
                        // At each seam, use the same stroke and depth as the straight edge.
                        if (source != null) color = switch (pixel) {
                            case 'O' -> source.panelOuter;
                            case 'H' -> source.panelHighlight;
                            case 'S' -> source.panelShadow;
                            default -> fill;
                        };
                        else if (edgeX == width - 1) color = shade(horizontal, edgeY, fill);
                        else if (edgeY == height - 1) color = shade(vertical, edgeX, fill);
                        else {
                            int dx = 0, sx = rightSide ? 1 : -1;
                            int dy = 0, sy = lower ? 1 : -1;
                            while (dx < edgeX && covered(slice, col + (dx + 1) * sx, row, height)) dx++;
                            while (dy < edgeY && covered(slice, col, row + (dy + 1) * sy, height)) dy++;
                            // Keep the lighting boundary straight when a larger cut repeats pixels.
                            int[] face = edgeY * width <= edgeX * height ? horizontal : vertical;
                            color = shade(face, Math.min(dx, dy), fill);
                        }
                        tessellator.setColorRGBA_I(color, color >>> 24);
                        tessellator.addVertex(left + col, top + row + 1, 0);
                        tessellator.addVertex(left + col + 1, top + row + 1, 0);
                        tessellator.addVertex(left + col + 1, top + row, 0);
                        tessellator.addVertex(left + col, top + row, 0);
                    }
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private boolean covered(String[] slice, int column, int row, int height) {
        return pixelAt(slice, column, row, height) != '.';
    }

    private char pixelAt(String[] slice, int column, int row, int height) {
        return slice[row * slice.length / height].charAt(column * SOURCE_WIDTH / width);
    }

    private static int shade(int[] edge, int distance, int fill) {
        return distance < edge.length ? edge[distance] : fill;
    }

    /** Limit an inherited drawable to the three bands while retaining its original bounds and texture UVs. */
    void drawInheritedBody(Runnable body) {
        if (width == 0) {
            body.run();
            return;
        }
        Minecraft client = Minecraft.getMinecraft();
        int scale = new ScaledResolution(client, client.displayWidth, client.displayHeight).getScaleFactor();
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (clipped) {
            SCISSOR.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR);
        }
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            drawBand(body, scale, client.displayHeight, clipped, x, y + topHeight, right, bottom - bottomHeight);
            drawBand(body, scale, client.displayHeight, clipped, x + width, y, right - width, y + topHeight);
            drawBand(
                body,
                scale,
                client.displayHeight,
                clipped,
                x + width,
                bottom - bottomHeight,
                right - width,
                bottom);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawBand(Runnable body, int scale, int screenHeight, boolean clipped, int left, int top,
        int right, int bottom) {
        int x = left * scale, y = screenHeight - bottom * scale;
        int x2 = right * scale, y2 = screenHeight - top * scale;
        if (clipped) {
            x = Math.max(x, SCISSOR.get(0));
            y = Math.max(y, SCISSOR.get(1));
            x2 = Math.min(x2, SCISSOR.get(0) + SCISSOR.get(2));
            y2 = Math.min(y2, SCISSOR.get(1) + SCISSOR.get(3));
        }
        if (x >= x2 || y >= y2) return;
        GL11.glScissor(x, y, x2 - x, y2 - y);
        body.run();
    }
}
