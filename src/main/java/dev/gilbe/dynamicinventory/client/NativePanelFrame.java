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
        draw(x, y, width, height, style, borderWidth, 0, 2, nativeArea);
    }

    public static void draw(int x, int y, int width, int height, int style, int borderWidth, int crop, int cornerCrop,
        int[] nativeArea) {
        // Inset only the painted frame. Slot coordinates and the native texture crop are independent.
        x += crop;
        y += crop;
        width -= crop * 2;
        height -= crop * 2;
        if (width <= 0 || height <= 0) return;
        int thickness = Math.min(Math.max(0, Math.min(6, borderWidth)), Math.min(width, height) / 2);
        // Gui.drawRect leaves its tint behind. Native chest textures are drawn immediately
        // after this frame, so restore the caller's color and blend/texture enable state.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            // A preceding item/widget can leave item lighting enabled. Flat UI colors must
            // stay unlit, or the first tab's white bevel becomes gray until its icon resets lighting.
            GL11.glDisable(GL11.GL_LIGHTING);
            if (style == 0) ResourcePanelFrame.draw(x, y, width, height);
            else drawFrame(x, y, width, height, style, thickness, cornerCrop, nativeArea);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawFrame(int x, int y, int width, int height, int style, int thickness, int cornerCrop,
        int[] nativeArea) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int right = x + width, bottom = y + height;
        // A transparent native sheet already supplies its own fill. Fill only the surrounding area,
        // avoiding a second translucent layer that would make the upper section darker than the lower.
        if (thickness == 0 || style == 1) {
            fillExcept(
                x + thickness,
                y + thickness,
                right - thickness,
                bottom - thickness,
                nativeArea,
                theme.panelFill);
        }
        if (thickness == 0) return;
        if (style == 1) {
            drawRect(x, y, right, y + thickness, theme.panelHighlight);
            drawRect(x, y + thickness, x + thickness, bottom - thickness, theme.panelHighlight);
            drawRect(x, bottom - thickness, right, bottom, theme.panelShadow);
            drawRect(right - thickness, y + thickness, right, bottom - thickness, theme.panelShadow);
            return;
        }
        // Corner cuts are independent of border thickness. Draw disjoint bands so translucent
        // resource-pack colors are never blended twice. Work is bounded by the eight-pixel corner.
        int radius = Math.min(Math.max(0, Math.min(8, cornerCrop)), Math.min(width, height) / 2);
        int band = Math.max(radius, thickness);
        for (int row = 0; row < band; row++) {
            int cut = Math.max(0, radius - row);
            frameRow(x + cut, right - cut, y + row, row, thickness, false, nativeArea, theme);
            frameRow(x + cut, right - cut, bottom - row - 1, row, thickness, true, nativeArea, theme);
        }
        fillExcept(x + thickness, y + band, right - thickness, bottom - band, nativeArea, theme.panelFill);
        drawRect(x, y + band, x + 1, bottom - band, theme.panelOuter);
        drawRect(right - 1, y + band, right, bottom - band, theme.panelOuter);
        if (thickness > 1) {
            drawRect(x + 1, y + band, x + thickness, bottom - band, theme.panelHighlight);
            drawRect(right - thickness, y + band, right - 1, bottom - band, theme.panelShadow);
        }
    }

    private static void frameRow(int left, int right, int y, int row, int thickness, boolean bottom, int[] hole,
        InventoryGuiTheme theme) {
        if (left >= right) return;
        if (row == 0) {
            drawRect(left, y, right, y + 1, theme.panelOuter);
            return;
        }
        int edge = Math.min(thickness, (right - left) / 2);
        drawRect(left, y, left + 1, y + 1, theme.panelOuter);
        drawRect(right - 1, y, right, y + 1, theme.panelOuter);
        if (edge > 1) {
            drawRect(left + 1, y, left + edge, y + 1, theme.panelHighlight);
            drawRect(right - edge, y, right - 1, y + 1, theme.panelShadow);
        }
        if (row < thickness)
            drawRect(left + edge, y, right - edge, y + 1, bottom ? theme.panelShadow : theme.panelHighlight);
        else fillExcept(left + edge, y, right - edge, y + 1, hole, theme.panelFill);
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
