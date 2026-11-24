package dev.gilbe.dynamicinventory.compat.mui2;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.widget.sizer.StandardResizer;
import com.cleanroommc.modularui.widgets.layout.IExpander;

/** A wrapped label's natural height depends on the width its layout actually gives it. */
public final class Mui2TextSizing {

    private Mui2TextSizing() {}

    public static boolean awaitingWidth(StandardResizer resizer) {
        return !resizer.isWidthCalculated()
            && (resizer.widthDependsOnParent() || resizer.hasStartPos(GuiAxis.X) && resizer.hasEndPos(GuiAxis.X)
                || resizer.isExpanded() && resizer.getParent() instanceof IExpander expander
                    && expander.getExpandAxis()
                        .isHorizontal());
    }
}
