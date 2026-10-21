package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

/** Shared, resource-palette frames for native and expanded sections. Styling never touches slots or input. */
public final class NativePanelFrame extends Gui {

    public static final String[] NAMES = { "Original", "Unified flat", "Unified bevel" };

    private NativePanelFrame() {}

    public static void draw(int x, int y, int width, int height, int style, int[] nativeArea) {
        draw(x, y, width, height, style, 2, nativeArea);
    }

    public static void draw(int x, int y, int width, int height, int style, int borderWidth, int[] nativeArea) {
        if (width <= 0 || height <= 0) return;
        int thickness = Math.min(Math.max(0, Math.min(6, borderWidth)), Math.min(width, height) / 2);
        // Gui.drawRect leaves its tint behind. Native chest textures are drawn immediately
        // after this frame, so restore the caller's color and blend/texture enable state.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            drawFrame(x, y, width, height, style, thickness, nativeArea);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawFrame(int x, int y, int width, int height, int style, int thickness, int[] nativeArea) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int right = x + width, bottom = y + height;
        // A transparent native sheet already supplies its own fill. Fill only the surrounding area,
        // avoiding a second translucent layer that would make the upper section darker than the lower.
        fillExcept(x + thickness, y + thickness, right - thickness, bottom - thickness, nativeArea, theme.panelFill);
        if (thickness == 0) return;
        if (style == 1) {
            drawRect(x, y, right, y + thickness, theme.panelHighlight);
            drawRect(x, y + thickness, x + thickness, bottom - thickness, theme.panelHighlight);
            drawRect(x, bottom - thickness, right, bottom, theme.panelShadow);
            drawRect(right - thickness, y + thickness, right, bottom - thickness, theme.panelShadow);
            return;
        }
        // Keep the one-pixel outline and stepped corners; thickness two matches the original frame.
        drawRect(x + thickness, y, right - thickness, y + 1, theme.panelOuter);
        drawRect(x + thickness, bottom - 1, right - thickness, bottom, theme.panelOuter);
        drawRect(x, y + thickness, x + 1, bottom - thickness, theme.panelOuter);
        drawRect(right - 1, y + thickness, right, bottom - thickness, theme.panelOuter);
        for (int step = 1; step < thickness; step++) {
            int left = x + thickness - step, edgeRight = right - thickness + step - 1;
            for (int edgeY : new int[] { y + step, bottom - step - 1 }) {
                drawRect(left, edgeY, left + 1, edgeY + 1, theme.panelOuter);
                drawRect(edgeRight, edgeY, edgeRight + 1, edgeY + 1, theme.panelOuter);
            }
            if (step > 1) {
                drawRect(left + 1, y + step, x + thickness, y + step + 1, theme.panelHighlight);
                drawRect(right - thickness, y + step, edgeRight, y + step + 1, theme.panelHighlight);
                drawRect(left + 1, bottom - step - 1, x + thickness, bottom - step, theme.panelShadow);
                drawRect(right - thickness, bottom - step - 1, edgeRight, bottom - step, theme.panelShadow);
            }
        }
        if (thickness > 1) {
            drawRect(x + thickness, y + 1, right - thickness, y + thickness, theme.panelHighlight);
            drawRect(x + 1, y + thickness, x + thickness, bottom - thickness, theme.panelHighlight);
            drawRect(x + thickness, bottom - thickness, right - thickness, bottom - 1, theme.panelShadow);
            drawRect(right - thickness, y + thickness, right - 1, bottom - thickness, theme.panelShadow);
        }
    }

    private static void fillExcept(int left, int top, int right, int bottom, int[] hole, int color) {
        if (left >= right || top >= bottom) return;
        if (hole == null || hole[2] <= left || hole[0] >= right || hole[3] <= top || hole[1] >= bottom) {
            drawRect(left, top, right, bottom, color);
            return;
        }
        int x1 = Math.max(left, hole[0]), x2 = Math.min(right, hole[2]);
        int y1 = Math.max(top, hole[1]), y2 = Math.min(bottom, hole[3]);
        if (top < y1) drawRect(left, top, right, y1, color);
        if (y2 < bottom) drawRect(left, y2, right, bottom, color);
        if (left < x1) drawRect(left, y1, x1, y2, color);
        if (x2 < right) drawRect(x2, y1, right, y2, color);
    }
}
