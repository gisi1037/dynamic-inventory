package dev.gilbe.dynamicinventory.mixins.backpack;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

/** The old player caption is a background subpart. The relocated panel supplies its own label. */
@Pseudo
@Mixin(targets = "de.eydamos.guiadvanced.form.Label", remap = false)
public abstract class BackpackLabelMixin {

    @Shadow
    protected String text;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$hideOldInventoryCaption(Minecraft client, int x, int y, float tick, CallbackInfo ci) {
        if ("container.inventory".equals(text) && client.currentScreen instanceof ContainerPanelAccess access
            && access.dynamicinventory$panel() != null) ci.cancel();
    }
}
