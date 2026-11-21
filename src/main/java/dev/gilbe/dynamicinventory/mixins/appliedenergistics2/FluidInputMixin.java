package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ClientInputGuard;

/** These native handlers act before calling AEBaseGui's input hooks. */
@Mixin(
    targets = { "com.glodblock.github.client.gui.GuiLevelTerminal",
        "com.glodblock.github.client.gui.GuiLevelMaintainer", "com.glodblock.github.client.gui.GuiMagnetFilter",
        "com.glodblock.github.client.gui.GuiIngredientBuffer",
        "com.glodblock.github.client.gui.GuiLargeIngredientBuffer",
        "com.glodblock.github.client.gui.GuiFluidPatternEncoder" },
    remap = false)
public abstract class FluidInputMixin {

    @Inject(method = "mouseClicked", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pendingClick(int x, int y, int button, CallbackInfo ci) {
        if (ClientInputGuard.isPending((GuiScreen) (Object) this)) ci.cancel();
    }

}
