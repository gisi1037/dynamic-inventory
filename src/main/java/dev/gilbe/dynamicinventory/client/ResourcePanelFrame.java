package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** Nine-slice the active inventory sheet, retaining pack artwork, transparency and HD UV scaling. */
final class ResourcePanelFrame {

    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    private ResourcePanelFrame() {}

    static void draw(int x, int y, int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(INVENTORY);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1);
        int edge = Math.min(4, Math.min(width, height) / 2);
        // Use blank frame inset pixels; (8,8) belongs to the native armor slot.
        patch(x + edge, y + edge, width - edge * 2, height - edge * 2, 4, 4, 2, 2);
        patch(x + edge, y, width - edge * 2, edge, 8, 0, 4, 4);
        patch(x + edge, y + height - edge, width - edge * 2, edge, 8, 162, 4, 4);
        patch(x, y + edge, edge, height - edge * 2, 0, 8, 4, 4);
        patch(x + width - edge, y + edge, edge, height - edge * 2, 172, 8, 4, 4);
        patch(x, y, edge, edge, 0, 0, 4, 4);
        patch(x + width - edge, y, edge, edge, 172, 0, 4, 4);
        patch(x, y + height - edge, edge, edge, 0, 162, 4, 4);
        patch(x + width - edge, y + height - edge, edge, edge, 172, 162, 4, 4);
    }

    private static void patch(int x, int y, int width, int height, int u, int v, int sw, int sh) {
        if (width <= 0 || height <= 0) return;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0, u / 256.0, (v + sh) / 256.0);
        tessellator.addVertexWithUV(x + width, y + height, 0, (u + sw) / 256.0, (v + sh) / 256.0);
        tessellator.addVertexWithUV(x + width, y, 0, (u + sw) / 256.0, v / 256.0);
        tessellator.addVertexWithUV(x, y, 0, u / 256.0, v / 256.0);
        tessellator.draw();
    }
}
