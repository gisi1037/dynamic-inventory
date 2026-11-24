package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PanelAccess;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2TextSizing;

@Mixin(value = TextWidget.class, remap = false)
public abstract class Mui2TextLayoutMixin {

    @Inject(method = "getDefaultHeight", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForTextWidth(CallbackInfoReturnable<Integer> cir) {
        TextWidget<?> widget = (TextWidget<?>) (Object) this;
        // A nonpositive default tells the native resizer to retry after the width is assigned.
        if (Mui2TextSizing.awaitingWidth(widget.resizer())) cir.setReturnValue(0);
    }

    @ModifyExpressionValue(
        method = "getDefaultHeight",
        at = @At(value = "FIELD", target = "Lcom/cleanroommc/modularui/widget/sizer/Area;width:I", ordinal = 0))
    private int dynamicinventory$measureInsidePadding(int width) {
        Area area = ((TextWidget<?>) (Object) this).getArea();
        return area.paddedWidth();
    }

    @Inject(method = "onTextChanged", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$resizeBeforeProjection(String text, CallbackInfo ci) {
        TextWidget<?> widget = (TextWidget<?>) (Object) this;
        // A resize during drawing would be discarded when the saved native areas are restored.
        if (widget.isValid() && widget.getScreen() instanceof Mui2PanelAccess access
            && access.dynamicinventory$detachedPanels()
                .deferTextLayout(widget))
            ci.cancel();
    }
}
