package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Pseudo
@Mixin(targets = "crazypants.enderio.conduit.gui.ExternalConnectionContainer", remap = false)
public abstract class ConduitSlotVisibilityMixin {

    @Inject(method = "setSlotsVisible", at = @At("RETURN"))
    private void dynamicinventory$machine(boolean visible, int start, int end, CallbackInfo ci) {
        if (start < 36 || !(Minecraft.getMinecraft().currentScreen instanceof GuiContainer gui)
            || gui.inventorySlots != (Object) this) return;
        NativeInventoryPanel panel = ((ContainerPanelAccess) gui).dynamicinventory$panel();
        if (panel != null) panel.refreshNativeMachinePositions(start, end);
    }
}
