package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.widgets.TextWidget;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PanelAccess;

@Mixin(value = TextWidget.class, remap = false)
public abstract class Mui2TextLayoutMixin {

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
