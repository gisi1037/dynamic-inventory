package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Native tab changes reinitialize this GUI directly, outside Forge's usual init events. */
@Pseudo
@Mixin(targets = "crazypants.enderio.conduit.gui.GuiExternalConnection", remap = false)
public abstract class ConduitGuiMixin {

    @Unique
    private boolean dynamicinventory$rebuilding;

    @Inject(method = { "initGui", "func_73866_w_" }, at = @At("HEAD"))
    private void dynamicinventory$before(CallbackInfo ci) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        NativeInventoryPanel old = access.dynamicinventory$panel();
        dynamicinventory$rebuilding = old != null;
        if (old != null) {
            old.restore();
            access.dynamicinventory$panel(null);
        }
    }

    @Inject(method = { "initGui", "func_73866_w_" }, at = @At("RETURN"))
    private void dynamicinventory$after(CallbackInfo ci) {
        if (dynamicinventory$rebuilding) {
            dynamicinventory$rebuilding = false;
            ((ContainerPanelAccess) this)
                .dynamicinventory$panel(new NativeInventoryPanel((GuiContainer) (Object) this));
        }
    }
}
