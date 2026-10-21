package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.client.AutomaticScrollPresentation;

/** Focused rejection tests for the fallback contract. No world, GL context or game input is needed. */
public final class AutomaticScrollFixtures {

    private AutomaticScrollFixtures() {}

    public static void main(String[] args) {
        check(AutomaticScrollPresentation.isDrawingOnly(Drawing.class, Root.class), "drawing-only GUI accepted");
        check(AutomaticScrollPresentation.isDrawingOnly(SrgDrawing.class, Root.class), "SRG drawing names accepted");
        check(AutomaticScrollPresentation.isDrawingOnly(Leaf.class, Root.class), "safe drawing superclass accepted");
        check(!AutomaticScrollPresentation.isDrawingOnly(Input.class, Root.class), "custom mouse input rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(InheritedInput.class, Root.class), "inherited input rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(Init.class, Root.class), "custom controls/init rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(Helper.class, Root.class), "unknown helper rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(Overlay.class, Root.class), "overlay interface rejected");
        check(
            !AutomaticScrollPresentation.isDrawingOnly(WrongSignature.class, Root.class),
            "wrong draw signature rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(Root.class, Root.class), "bare framework rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(String.class, Root.class), "unrelated class rejected");
        check(!AutomaticScrollPresentation.isDrawingOnly(null, Root.class), "missing class rejected");
        for (int logical = 0; logical < 36; logical++) {
            int index = logical < 9 ? logical : logical - 9;
            int x = 8 + index % 9 * 18, y = 84 + (logical < 9 ? 58 : index / 9 * 18);
            check(AutomaticScrollPresentation.standardPlayerPosition(logical, x, y, 8, 84), "native grid accepted");
            check(
                !AutomaticScrollPresentation.standardPlayerPosition(logical, x + 1, y, 8, 84),
                "shifted column rejected");
            check(
                !AutomaticScrollPresentation.standardPlayerPosition(logical, x, y + 1, 8, 84),
                "shifted row rejected");
        }
        check(!AutomaticScrollPresentation.standardPlayerPosition(36, 8, 84, 8, 84), "added slot is not a legacy cell");
        NativeUiLayout capped = NativeUiLayout.builder()
            .maximumVisibleRows(3)
            .build();
        check(capped.getMaximumVisibleRows() == 3, "row cap retained");
        check(NativeUiLayout.DEFAULT.getMaximumVisibleRows() == 0, "existing adapters remain uncapped");
        try {
            NativeUiLayout.builder()
                .maximumVisibleRows(13);
            throw new AssertionError("invalid cap accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("PASS automatic scroll class/geometry rejection guards and layout caps");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static class Root {
    }

    private static class Drawing extends Root {

        protected void drawGuiContainerBackgroundLayer(float tick, int x, int y) {}

        protected void drawGuiContainerForegroundLayer(int x, int y) {}
    }

    private static final class SrgDrawing extends Root {

        protected void func_146976_a(float tick, int x, int y) {}

        protected void func_146979_b(int x, int y) {}
    }

    private static final class Leaf extends Drawing {
    }

    private static class Input extends Drawing {

        protected void mouseClicked(int x, int y, int button) {}
    }

    private static final class InheritedInput extends Input {
    }

    private static final class Init extends Drawing {

        public void initGui() {}
    }

    private static final class Helper extends Drawing {

        private void customBehavior() {}
    }

    private interface CustomOverlay {
    }

    private static final class Overlay extends Drawing implements CustomOverlay {
    }

    private static final class WrongSignature extends Root {

        protected int drawGuiContainerForegroundLayer(int x, int y) {
            return 0;
        }
    }
}
