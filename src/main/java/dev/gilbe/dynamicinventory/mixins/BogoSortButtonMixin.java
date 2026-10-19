package dev.gilbe.dynamicinventory.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.cleanroommc.bogosorter.common.sort.SlotGroup;

import dev.gilbe.dynamicinventory.client.NativeSortButton;

@Pseudo
@Mixin(targets = "com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton", remap = false)
public abstract class BogoSortButtonMixin implements NativeSortButton {

    @Shadow
    @Final
    private SlotGroup slotGroup;

    @Override
    public SlotGroup dynamicinventory$sortGroup() {
        return slotGroup;
    }
}
