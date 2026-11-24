package dev.gilbe.dynamicinventory.client;

import java.util.Arrays;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

/** Shared, resource-palette frames for native and expanded sections. Styling never touches slots or input. */
public final class NativePanelFrame extends Gui {

    public static final int SEAMLESS = 3;
    public static final String[] NAMES = { "Original", "Unified flat", "Unified bevel", "Seamless" };

    private NativePanelFrame() {}

    /** Paint the reference corners at their original size on the player section and its tabs. */
    public static void drawPlayerFrame(int x, int y, int width, int height) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            PanelCorners corners = new PanelCorners(x, y, width, height, PanelCorners.SOURCE_WIDTH);
            int right = x + width, bottom = y + height;
            playerBorder(corners, x, y, width, height, theme.panelOuter);
            corners.fill(x + 1, y + 1, right - 1, y + 3, theme.panelHighlight);
            corners.fill(x + 1, y + 3, x + 3, bottom - 1, theme.panelHighlight);
            corners.fill(x + 3, bottom - 3, right - 1, bottom - 1, theme.panelShadow);
            corners.fill(right - 3, y + 3, right - 1, bottom - 3, theme.panelShadow);
            corners.fill(x + 3, y + 3, right - 3, bottom - 3, theme.panelFill);
            corners.drawSource(theme);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void playerBorder(PanelCorners corners, int x, int y, int width, int height, int color) {
        corners.fill(x, y, x + width, y + 1, color);
        corners.fill(x, y + height - 1, x + width, y + height, color);
        corners.fill(x, y + 1, x + 1, y + height - 1, color);
        corners.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawInherited(int x, int y, int width, int height, Runnable body) {
        PanelCorners corners = new PanelCorners(x, y, width, height, 4);
        corners.drawInheritedBody(body);
        drawCorners(corners, InventoryGuiTheme.get());
    }

    public static void drawSettingsFrame(int x, int y, int width, int height) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        PanelCorners corners = new PanelCorners(x, y, width, height, 4);
        corners.fill(x, y, x + width, y + 2, theme.settingsOuter);
        corners.fill(x, y + height - 2, x + width, y + height, theme.settingsOuter);
        corners.fill(x, y + 2, x + 2, y + height - 2, theme.settingsOuter);
        corners.fill(x + width - 2, y + 2, x + width, y + height - 2, theme.settingsOuter);
        corners.fill(x + 2, y + 2, x + width - 2, y + height - 2, theme.settingsFill);
        int[] edge = { theme.settingsOuter, theme.settingsOuter };
        corners.draw(theme.settingsFill, edge, edge);
    }

    static void drawCorners(PanelCorners corners, InventoryGuiTheme theme) {
        corners.draw(
            theme.panelFill,
            edge(3, 1, theme.panelOuter, theme.panelHighlight),
            edge(3, 1, theme.panelOuter, theme.panelShadow));
    }

    public static void draw(int x, int y, int width, int height, int style, int[] nativeArea) {
        draw(x, y, width, height, style, 2, nativeArea);
    }

    public static void draw(int x, int y, int width, int height, int style, int borderWidth, int[] nativeArea) {
        draw(x, y, width, height, style, borderWidth, 0, 2, nativeArea);
    }

    public static void draw(int x, int y, int width, int height, int style, int borderWidth, int crop, int cornerCrop,
        int[] nativeArea) {
        if (style == SEAMLESS) crop = 0;
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
            if (style == SEAMLESS) fillExcept(
                new PanelCorners(x, y, width, height, 0),
                x,
                y,
                x + width,
                y + height,
                nativeArea,
                InventoryGuiTheme.get().panelFill);
            else if (style == 0) ResourcePanelFrame.draw(x, y, width, height);
            else drawFrame(x, y, width, height, style, thickness, cornerCrop, nativeArea);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawFrame(int x, int y, int width, int height, int style, int thickness, int cornerCrop,
        int[] nativeArea) {
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int right = x + width, bottom = y + height;
        int size = style == 1 ? 4 : Math.max(thickness, Math.max(0, Math.min(8, cornerCrop)) + 2);
        PanelCorners corners = new PanelCorners(x, y, width, height, thickness == 0 ? 0 : size);
        // A transparent native sheet already supplies its own fill. Fill only the surrounding area,
        // avoiding a second translucent layer that would make the upper section darker than the lower.
        fillExcept(
            corners,
            x + thickness,
            y + thickness,
            right - thickness,
            bottom - thickness,
            nativeArea,
            theme.panelFill);
        if (thickness == 0) return;
        int outline = style == 1 ? 0 : 1;
        if (outline != 0) playerBorder(corners, x, y, width, height, theme.panelOuter);
        if (thickness > outline) {
            corners.fill(x + outline, y + outline, right - outline, y + thickness, theme.panelHighlight);
            corners.fill(x + outline, y + thickness, x + thickness, bottom - thickness, theme.panelHighlight);
            corners.fill(x + outline, bottom - thickness, right - outline, bottom - outline, theme.panelShadow);
            corners.fill(right - thickness, y + thickness, right - outline, bottom - thickness, theme.panelShadow);
        }
        corners.draw(
            theme.panelFill,
            edge(thickness, outline, theme.panelOuter, theme.panelHighlight),
            edge(thickness, outline, theme.panelOuter, theme.panelShadow));
    }

    private static int[] edge(int thickness, int outline, int outer, int bevel) {
        int[] colors = new int[thickness];
        Arrays.fill(colors, bevel);
        if (outline > 0) colors[0] = outer;
        return colors;
    }

    private static void fillExcept(PanelCorners corners, int left, int top, int right, int bottom, int[] hole,
        int color) {
        if (left >= right || top >= bottom) return;
        if (hole == null || hole[2] <= left || hole[0] >= right || hole[3] <= top || hole[1] >= bottom) {
            corners.fill(left, top, right, bottom, color);
            return;
        }
        int x1 = Math.max(left, hole[0]), x2 = Math.min(right, hole[2]);
        int y1 = Math.max(top, hole[1]), y2 = Math.min(bottom, hole[3]);
        if (top < y1) corners.fill(left, top, right, y1, color);
        if (y2 < bottom) corners.fill(left, y2, right, bottom, color);
        if (left < x1) corners.fill(left, y1, x1, y2, color);
        if (x2 < right) corners.fill(x2, y1, right, y2, color);
    }
}
