package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.AEBaseGui;
import dev.gilbe.dynamicinventory.client.AePinnedPanel;

@Mixin(value = AEBaseGui.class, remap = false)
public abstract class AeSpaceTransferGuiMixin {

    @Shadow
    protected abstract boolean enableSpaceClicking();

    @Inject(
        method = "handleMouseClick",
        remap = true,
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/AEBaseGui;enableSpaceClicking()Z", remap = false),
        cancellable = true)
    private void dynamicinventory$spacePanel(Slot slot, int index, int button, int type, CallbackInfo ci) {
        if (slot != null && enableSpaceClicking()
            && AePinnedPanel.spaceTransferNative((AEBaseGui) (Object) this, slot.slotNumber)) ci.cancel();
    }
}
