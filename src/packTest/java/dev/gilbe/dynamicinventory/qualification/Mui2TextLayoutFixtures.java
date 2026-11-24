package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2TextSizing;

/** Exercise the native resize solver with the same row, column and scroll nesting as GregTech's status panel. */
public final class Mui2TextLayoutFixtures {

    private static final String MESSAGE = "Expects Superconducting Coil Block at (1092, 173, -351)";

    private Mui2TextLayoutFixtures() {}

    public static void main(String[] args) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        minecraft.fontRenderer = (TestFont) unsafe.allocateInstance(TestFont.class);
        minecraft.fontRenderer.FONT_HEIGHT = 9;
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        try {
            singleton.set(null, minecraft);
            for (int width : new int[] { 170, 195, 210 }) {
                checkOverlapReproduces(width);
                checkLayout(width);
            }
        } finally {
            singleton.set(null, previous);
        }
        System.out
            .println("PASS ModularUI2 sizes wrapped labels before positioning the next status line and scrollbar");
    }

    private static void checkOverlapReproduces(int width) {
        StatusLayout nativeLayout = new StatusLayout(width, false);
        check(
            nativeLayout.following.getArea().y < nativeLayout.text.getArea().y + drawnHeight(nativeLayout),
            "Unpatched solver must reproduce the overlap at width " + width);
    }

    private static void checkLayout(int width) {
        StatusLayout fixed = new StatusLayout(width, true);
        check(fits(fixed), "The next status line follows the wrapped text at width " + width);
        int longScrollHeight = fixed.list.getScrollData()
            .getScrollSize();
        fixed.message.set("Structure complete");
        WidgetTree.resizeInternal(fixed.text.resizer(), false);
        check(
            fits(fixed) && longScrollHeight > fixed.list.getScrollData()
                .getScrollSize(),
            "The row and scroll range shrink when the text shortens");
        fixed.message.set(MESSAGE);
        WidgetTree.resizeInternal(fixed.text.resizer(), false);
        check(
            fits(fixed) && longScrollHeight == fixed.list.getScrollData()
                .getScrollSize(),
            "The row and scroll range grow again when the warning returns");
    }

    private static boolean fits(StatusLayout layout) {
        int height = drawnHeight(layout);
        return layout.text.getArea().height == height
            && layout.following.getArea().y >= layout.text.getArea().y + height
            && layout.list.getScrollData()
                .getScrollSize()
                >= layout.following.getArea()
                    .ey() - layout.list.getArea().y;
    }

    private static int drawnHeight(StatusLayout layout) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        return font.FONT_HEIGHT * font.listFormattedStringToWidth(
            layout.message.get(),
            layout.text.getArea()
                .paddedWidth() + 1)
            .size();
    }

    private static final class StatusLayout {

        final AtomicReference<String> message = new AtomicReference<>(MESSAGE);
        final TextWidget<?> text;
        final TextWidget<?> following = new TextWidget<>("Missing Energy Hatch").fullWidth();
        final ListWidget<IWidget, ?> list;

        StatusLayout(int width, boolean corrected) {
            text = new StatusText(IKey.dynamic(message::get), corrected).expanded()
                .textAlign(Alignment.TopLeft);
            Flow row = Flow.row()
                .fullWidth()
                .coverChildrenHeight(0)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .child(
                    new Widget<>().size(12, 12)
                        .marginRight(4))
                .child(text);
            Flow column = Flow.column()
                .coverChildrenHeight(0)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .childPadding(2)
                .child(row)
                .child(following);
            list = new ListWidget<>().size(width, 30)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .child(
                    new DynamicSyncedWidget<>().initialChild(column)
                        .widthRel(0.85f)
                        .coverChildrenHeight(0));
            ModularPanel panel = new ModularPanel("wrapped_status_fixture").size(width + 10, 180)
                .child(list);
            ModularScreen screen = new ModularScreen("dynamicinventory", panel);
            screen.constructOverlay(new GuiScreen());
            screen.onResize(800, 700);
        }
    }

    private static final class StatusText extends TextWidget<StatusText> {

        private final boolean corrected;

        StatusText(IKey key, boolean corrected) {
            super(key);
            this.corrected = corrected;
        }

        @Override
        public int getDefaultHeight() {
            // Standalone fixtures use ModularUI2 directly, so apply the same guard as the mixin.
            return corrected && Mui2TextSizing.awaitingWidth(resizer()) ? 0 : super.getDefaultHeight();
        }
    }

    private static final class TestFont extends FontRenderer {

        private TestFont() {
            super(null, null, null, false);
        }

        @Override
        public int getCharWidth(char character) {
            return character == ' ' ? 4 : 6;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
