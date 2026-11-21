package dev.gilbe.dynamicinventory.mixins.backpack;

import java.util.ArrayList;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.eydamos.guiadvanced.misc.AbstractGuiPart;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

@Pseudo
@Mixin(targets = "de.eydamos.guiadvanced.ContainerWindow", remap = false)
public abstract class BackpackGuiMixin {

    @Shadow
    @Final
    protected ArrayList<AbstractGuiPart> subParts;

    @Unique
    private boolean dynamicinventory$positionedParts;

    @Inject(method = { "drawGuiContainerBackgroundLayer", "func_146976_a" }, at = @At("HEAD"))
    private void dynamicinventory$placeParts(float tick, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        boolean decorated = access.dynamicinventory$panel() != null;
        if (!decorated && !dynamicinventory$positionedParts) return;
        dynamicinventory$positionedParts = decorated;
        for (AbstractGuiPart part : subParts) {
            // The shared panel already moves GuiButtons, including their hitboxes.
            if (!(part instanceof GuiButton))
                part.setAbsolutePosition(access.dynamicinventory$left(), access.dynamicinventory$top());
        }
    }
}
