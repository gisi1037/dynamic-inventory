package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Pixel-sized controls share their hit geometry with pointer handling. */
public final class PanelChrome extends Gui {

    private static final PanelChrome DRAW = new PanelChrome();
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    private PanelChrome() {}

    public static void raisedButton(Rect bounds, boolean enabled, boolean hovered) {
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(WIDGETS);
            int v = 46 + (!enabled ? 0 : hovered ? 2 : 1) * 20;
            int half = bounds.width / 2, upper = bounds.height / 2;
            DRAW.drawTexturedModalRect(bounds.x, bounds.y, 0, v, half, upper);
            DRAW.drawTexturedModalRect(
                bounds.x + half,
                bounds.y,
                200 - bounds.width + half,
                v,
                bounds.width - half,
                upper);
            DRAW.drawTexturedModalRect(
                bounds.x,
                bounds.y + upper,
                0,
                v + 20 - bounds.height + upper,
                half,
                bounds.height - upper);
            DRAW.drawTexturedModalRect(
                bounds.x + half,
                bounds.y + upper,
                200 - bounds.width + half,
                v + 20 - bounds.height + upper,
                bounds.width - half,
                bounds.height - upper);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static Rect control(Rect panel) {
        return new Rect(panel.x + 10, panel.y + 1, 12, 12);
    }

    public static void move(Rect target, boolean hovered, boolean enabled) {
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            raisedButton(target, enabled, hovered);
            int x = target.x + 5, y = target.y + 5, color = InventoryGuiTheme.get().label;
            drawRect(x, y - 3, x + 1, y + 5, color);
            drawRect(x - 3, y, x + 5, y + 1, color);
            for (int sign : new int[] { -1, 1 }) {
                drawRect(x - 1, y + sign * 2, x + 2, y + sign * 2 + 1, color);
                drawRect(x + sign * 2, y - 1, x + sign * 2 + 1, y + 2, color);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static int corner(Rect panel, int x, int y) {
        if (!panel.contains(x, y)) return 0;
        int horizontal = x < panel.x + 8 ? 1 : x >= panel.right() - 8 ? 2 : 0;
        int vertical = y < panel.y + 8 ? 4 : y >= panel.bottom() - 8 ? 8 : 0;
        return horizontal != 0 && vertical != 0 ? horizontal | vertical : 0;
    }

    public static void icon(Rect target, boolean hovered, boolean global) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int x = target.x + 2, y = target.y + 2;
        if (hovered) drawRect(target.x, target.y, target.right(), target.bottom(), theme.panelHighlight);
        drawRect(x, y, x + 5, y + 1, theme.label);
        drawRect(x, y, x + 1, y + 5, theme.label);
        drawRect(x + 4, y, x + 5, y + 2, theme.label);
        drawRect(x + 2, y + 2, x + 7, y + 3, theme.label);
        drawRect(x + 2, y + 2, x + 3, y + 7, theme.label);
        drawRect(x + 6, y + 2, x + 7, y + 7, theme.label);
        drawRect(x + 2, y + 6, x + 7, y + 7, theme.label);
        drawRect(x + 3, y + 3, x + 6, y + 4, theme.panelHighlight);
        int outline = global ? 0xb078a7d6 : 0xb0d79b61;
        drawRect(target.x, target.y, target.right(), target.y + 1, outline);
        drawRect(target.x, target.bottom() - 1, target.right(), target.bottom(), outline);
        drawRect(target.x, target.y + 1, target.x + 1, target.bottom() - 1, outline);
        drawRect(target.right() - 1, target.y + 1, target.right(), target.bottom() - 1, outline);
    }

    public static void grips(Rect bounds, int hovered) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        for (int corner : new int[] { 5, 6, 9, 10 }) {
            boolean right = (corner & 2) != 0, bottom = (corner & 8) != 0;
            int x = right ? bounds.right() - 3 : bounds.x + 2;
            int y = bottom ? bounds.bottom() - 3 : bounds.y + 2;
            for (int step = 0; step < 3; step++) {
                int px = x + (right ? -step : step), py = y + (bottom ? step - 2 : 2 - step);
                drawRect(px, py, px + 1, py + 1, hovered == corner ? theme.panelHighlight : theme.panelShadow);
                drawRect(px + (right ? -2 : 2), py, px + (right ? -2 : 2) + 1, py + 1, theme.panelShadow);
            }
        }
    }
}
