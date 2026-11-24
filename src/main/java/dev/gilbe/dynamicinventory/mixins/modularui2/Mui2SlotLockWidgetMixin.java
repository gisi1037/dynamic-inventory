package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.widget.Interactable.Result;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import dev.gilbe.dynamicinventory.client.SlotLockControls;

@Mixin(value = ItemSlot.class, remap = false)
public abstract class Mui2SlotLockWidgetMixin {

    @Inject(method = "onMousePressed", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$toggleLock(int button, CallbackInfoReturnable<Result> result) {
        ItemSlot widget = (ItemSlot) (Object) this;
        if (widget.isSynced() && SlotLockControls.toggle(widget.getSlot(), button))
            result.setReturnValue(Result.SUCCESS);
    }

    @Inject(method = "drawOverlay", at = @At("TAIL"))
    private void dynamicinventory$lockMark(CallbackInfo ci) {
        ItemSlot widget = (ItemSlot) (Object) this;
        if (widget.isSynced()) SlotLockControls.draw(widget.getSlot(), 1, 1);
    }
}
