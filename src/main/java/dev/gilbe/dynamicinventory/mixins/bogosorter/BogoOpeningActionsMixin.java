package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.common.network.IPacket;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** These GUI packets write slots directly, bypassing vanilla windowClick and Container.slotClick. */
@Pseudo
@Mixin(
    targets = { "com.cleanroommc.bogosorter.common.network.CSort",
        "com.cleanroommc.bogosorter.common.network.CShortcut", "com.cleanroommc.bogosorter.common.network.CHotbarSwap",
        "com.cleanroommc.bogosorter.common.network.CSlotSync" },
    remap = false)
public abstract class BogoOpeningActionsMixin {

    @Inject(method = "executeServer", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pending(NetHandlerPlayServer handler, CallbackInfoReturnable<IPacket> cir) {
        // Bogo dispatches these GUI packets from the ordered play queue on the server game thread.
        // Its dispatcher still runs acknowledgement cleanup when this implementation returns null.
        if (!InventoryUi.isOpeningReady(handler.playerEntity)) cir.setReturnValue(null);
    }
}
