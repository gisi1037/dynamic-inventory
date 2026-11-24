package dev.gilbe.dynamicinventory.mixins.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.ModularScreen;

@Pseudo
@Mixin(targets = "gregtech.common.gui.modularui.cover.base.CoverBaseGui$1", remap = false)
public abstract class StandaloneCoverPanelMixin {

    @Inject(
        method = "onOpen",
        at = @At(value = "FIELD", target = "Lgregtech/api/metatileentity/CoverableTileEntity;isCoverOpen:[Z"),
        cancellable = true,
        require = 0)
    private void dynamicinventory$rememberOnlyMachinePopups(ModularScreen screen, CallbackInfo ci) {
        // GregTech reopens remembered cover tabs when the machine screen initializes. A cover opened
        // from the world is the main panel, and has no machine tab to remember.
        if (screen.getMainPanel() == (Object) this) ci.cancel();
    }
}
