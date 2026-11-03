package dev.gilbe.dynamicinventory.qualification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2PanelFrame;

/** Native drawable/theme dispatch without OpenGL or a running game. */
public final class Mui2PanelFrameFixtures {

    private Mui2PanelFrameFixtures() {}

    public static void main(String[] args) {
        List<String> layers = new ArrayList<>();
        Rect bounds = new Rect(338, 183, 178, 190);
        Panel panel = new Panel();
        panel.getArea().x = 10;
        panel.getArea().y = 20;
        panel.getArea().width = 176;
        panel.getArea().height = 256;
        IDrawable themed = (context, x, y, width, height, theme) -> {
            check(
                x == bounds.x && y == bounds.y && width == bounds.width && height == bounds.height,
                "Native artwork receives detached bounds");
            layers.add("theme:" + theme.getColor());
        };
        panel.shadow((context, x, y, width, height, theme) -> layers.add("shadow"));
        panel.background((context, x, y, width, height, theme) -> layers.add("native:" + theme.getColor()));
        panel.disableThemeBackground(false)
            .disableHoverThemeBackground(false);
        for (int color : new int[] { 0xffa2abc7, 0xffff7800, 0x804080c0 }) {
            panel.entry = new WidgetThemeEntry<>(
                null,
                theme(themed, color, 0xff123456),
                theme(themed, color + 1, 0xffabcdef));
            layers.clear();
            panel.hovering = false;
            Mui2PanelFrame.draw(panel, null, bounds);
            check(
                layers.equals(Arrays.asList("shadow", "theme:" + color, "native:" + color)),
                "Native shadow, theme and explicit artwork keep native order and tint/alpha");
            check(Mui2PanelFrame.textColor(panel) == 0xff123456, "Native label color follows panel theme");
            panel.hovering = true;
            layers.clear();
            Mui2PanelFrame.draw(panel, null, bounds);
            check(
                layers.equals(Arrays.asList("shadow", "theme:" + (color + 1), "native:" + (color + 1))),
                "Theme changes are read live, including native hover state");
            check(Mui2PanelFrame.textColor(panel) == 0xffabcdef, "Native hover label color follows theme");
        }
        panel.disableThemeBackground(true)
            .disableHoverThemeBackground(true);
        layers.clear();
        Mui2PanelFrame.draw(panel, null, bounds);
        check(
            layers.size() == 2 && layers.get(0)
                .equals("shadow")
                && layers.get(1)
                    .startsWith("native:"),
            "A native explicit background does not gain a disabled theme layer");
        panel.shadow((IDrawable) null)
            .background((IDrawable) null);
        layers.clear();
        Mui2PanelFrame.draw(panel, null, bounds);
        check(layers.isEmpty(), "An intentionally transparent native frame stays transparent");
        check(
            panel.getArea().x == 10 && panel.getArea().y == 20
                && panel.getArea().width == 176
                && panel.getArea().height == 256
                && panel.getChildren()
                    .isEmpty(),
            "Native geometry/children are never changed");
        System.out.println(
            "PASS native MUI2 backgrounds, tint/alpha, layer order, live theme/hover text, transparency and geometry preservation");
    }

    private static WidgetTheme theme(IDrawable background, int tint, int text) {
        return new WidgetTheme(176, 166, background, tint, text, false, 0xffffffff);
    }

    private static final class Panel extends ModularPanel {

        WidgetThemeEntry<WidgetTheme> entry;
        boolean hovering;

        Panel() {
            super("native_frame_fixture");
        }

        @Override
        public ITheme getTheme() {
            return null;
        }

        @Override
        public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
            return entry;
        }

        @Override
        public boolean isHovering() {
            return hovering;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
