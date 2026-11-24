package dev.gilbe.dynamicinventory.compat.mui2;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.NativePanelFrame;

/** Reuses the native panel artwork and live theme without moving or redrawing its widgets. */
public final class Mui2PanelFrame {

    private Mui2PanelFrame() {}

    public static void draw(ModularPanel owner, ModularGuiContext context, Rect bounds) {
        WidgetThemeEntry<?> entry = owner.getWidgetTheme(owner.getTheme());
        WidgetTheme theme = entry.getTheme(owner.isHovering());
        draw(owner.getShadow(), context, bounds, theme);
        IDrawable background = !owner.isDisableThemeBackground() || !owner.isDisableHoverThemeBackground()
            ? owner.getThemeBackground(entry, theme)
            : null;
        IDrawable overlay = owner.getCurrentBackground(entry);
        if (!IDrawable.isVisible(background) && !IDrawable.isVisible(overlay)) return;
        Runnable body = () -> {
            draw(background, context, bounds, theme);
            draw(overlay, context, bounds, theme);
        };
        if (context == null) body.run();
        else NativePanelFrame.drawInherited(bounds.x, bounds.y, bounds.width, bounds.height, body);
    }

    public static int textColor(ModularPanel owner) {
        return owner.getWidgetTheme(owner.getTheme())
            .getTheme(owner.isHovering())
            .getTextColor();
    }

    private static void draw(IDrawable drawable, ModularGuiContext context, Rect bounds, WidgetTheme theme) {
        if (drawable != null) drawable.draw(context, bounds.x, bounds.y, bounds.width, bounds.height, theme);
    }
}
