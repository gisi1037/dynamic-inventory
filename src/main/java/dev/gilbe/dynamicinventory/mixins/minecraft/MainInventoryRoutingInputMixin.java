package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.MainInventoryRoutingClient;

/** Framework click packets bypass PlayerControllerMP; publish preferences before their input dispatch too. */
@Mixin(GuiScreen.class)
public abstract class MainInventoryRoutingInputMixin {

    @Inject(method = "handleInput", at = @At("HEAD"))
    private void dynamicinventory$routing(CallbackInfo ci) {
        MainInventoryRoutingClient.sync();
    }
}
