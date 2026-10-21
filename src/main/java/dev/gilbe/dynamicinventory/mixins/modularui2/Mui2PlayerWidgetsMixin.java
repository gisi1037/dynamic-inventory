package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.widgets.SlotGroupWidget;

import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;

@Mixin(value = SlotGroupWidget.class, remap = false)
public abstract class Mui2PlayerWidgetsMixin {

    @Inject(
        method = "playerInventory(Lcom/cleanroommc/modularui/widgets/SlotGroupWidget$SlotConsumer;)Lcom/cleanroommc/modularui/widgets/SlotGroupWidget;",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$widgets(SlotGroupWidget.SlotConsumer consumer,
        CallbackInfoReturnable<SlotGroupWidget> cir) {
        cir.setReturnValue(Mui2InventoryWidgets.playerInventory(consumer));
    }
}
