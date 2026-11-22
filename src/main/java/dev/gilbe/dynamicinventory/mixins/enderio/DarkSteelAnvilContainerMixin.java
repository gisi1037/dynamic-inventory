package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.inventory.ContainerRepair;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "crazypants.enderio.block.ContainerDarkSteelAnvil", remap = false)
public abstract class DarkSteelAnvilContainerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$numberOutputSlot(CallbackInfo ci) {
        // Ender IO replaces the result slot directly, bypassing Container's slot numbering.
        ((ContainerRepair) (Object) this).getSlot(2).slotNumber = 2;
    }
}
