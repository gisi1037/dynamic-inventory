package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.PlayerInventoryScreen;

/** The owned player screen places its own header; NEI's potion-offset recenter would undo it every frame. */
@Pseudo
@Mixin(targets = "codechicken.nei.LayoutManager", remap = false)
public abstract class NeiPlayerSectionOriginMixin {

    @Inject(method = "onPreDraw", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$preservePlayerOrigin(GuiContainer gui, CallbackInfo ci) {
        if (gui instanceof PlayerInventoryScreen) ci.cancel();
    }
}
