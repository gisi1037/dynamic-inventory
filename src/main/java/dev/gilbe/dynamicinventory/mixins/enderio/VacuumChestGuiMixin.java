package dev.gilbe.dynamicinventory.mixins.enderio;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "crazypants.enderio.machine.vacuum.GuiVacuumChest", remap = false)
public abstract class VacuumChestGuiMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 104))
    private int dynamicinventory$raiseBottomButtons(int nativeY) {
        return nativeY - 4;
    }
}
