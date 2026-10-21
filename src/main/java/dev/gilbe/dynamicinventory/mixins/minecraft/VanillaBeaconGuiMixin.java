package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Beacon power buttons are added late, and choosing a power performs an internal GUI initialization. */
@Mixin(GuiBeacon.class)
public abstract class VanillaBeaconGuiMixin {

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(GuiButton button, CallbackInfo ci) {
        // Cancel remains available while the opening is pending; power selection/confirmation must wait.
        if (button.id != -2 && ((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !InventoryUi.isOpeningReady(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    }

    @WrapMethod(method = "initGui")
    private void dynamicinventory$nativeReinitialize(Operation<Void> original) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        NativeInventoryPanel previous = access.dynamicinventory$panel();
        // Normal Forge initialization already cleared the decorator in its Pre event and recreates it in Post.
        // Only the beacon's direct internal initGui call needs this local restore/rebuild pair.
        if (previous != null) {
            previous.restore();
            access.dynamicinventory$panel(null);
        }
        original.call();
        if (previous != null) access.dynamicinventory$panel(new NativeInventoryPanel((GuiContainer) (Object) this));
    }

    @WrapMethod(method = "updateScreen")
    private void dynamicinventory$latePowerButtons(Operation<Void> original) {
        original.call();
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) panel.captureNewNativeButtons();
    }
}
