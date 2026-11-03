package dev.gilbe.dynamicinventory.mixins.bogosorter;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.compat.PanelSortScope;

@Mixin(targets = "com.cleanroommc.bogosorter.common.sort.SortHandler", remap = false)
public abstract class BogoPanelSortMixin {

    @Inject(method = "getSortableSlots", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$panel(SlotGroup group, CallbackInfoReturnable<List<SlotAccessor>> cir) {
        cir.setReturnValue(PanelSortScope.filter(cir.getReturnValue()));
    }
}
