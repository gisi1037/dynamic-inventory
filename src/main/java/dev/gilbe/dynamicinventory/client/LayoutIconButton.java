package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** A small inventory-grid icon that remains legible at every GUI scale. */
public final class LayoutIconButton extends GuiButton {

    public LayoutIconButton(int x, int y) {
        super(100, x, y, 16, 16, "");
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        super.drawButton(minecraft, mouseX, mouseY);
        if (!visible) return;
        // Vanilla crops a 20-pixel button texture to this 16-pixel height, losing
        // its bottom border. Copy the real lower edge before painting the icon;
        // the active resource pack still supplies the entire button appearance.
        minecraft.getTextureManager()
            .bindTexture(buttonTextures);
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        int bottomV = 46 + getHoverState(field_146123_n) * 20 + 16;
        drawTexturedModalRect(xPosition, yPosition + height - 4, 0, bottomV, width / 2, 4);
        drawTexturedModalRect(xPosition + width / 2, yPosition + height - 4, 200 - width / 2, bottomV, width / 2, 4);
        int color = enabled ? 0xffeeeeee : 0xff888888;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int x = xPosition + 4 + column * 3;
                int y = yPosition + 4 + row * 3;
                drawRect(x, y, x + 2, y + 2, color);
            }
        }
    }
}
