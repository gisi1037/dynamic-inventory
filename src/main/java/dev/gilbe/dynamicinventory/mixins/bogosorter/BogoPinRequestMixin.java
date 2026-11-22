package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.common.PinnedSlots;
import com.cleanroommc.bogosorter.common.network.CPlayerPins;
import com.cleanroommc.bogosorter.common.network.IPacket;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.BogoExpandedPins;

@Mixin(value = CPlayerPins.class, remap = false)
public abstract class BogoPinRequestMixin {

    @Shadow
    private int slotNumber;

    @Redirect(
        method = "executeServer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/PinnedSlots;toggle(Lnet/minecraft/entity/player/EntityPlayer;I)I"))
    private int dynamicinventory$toggle(EntityPlayer player, int raw) {
        // Bogo has already validated the window and slot number.
        int index = BogoExpandedPins.index(BogoSortAPI.getSlot(player.openContainer, slotNumber), player.inventory);
        if (index < 0) return PinnedSlots.toggle(player, raw);
        if (InventoryUi.isOpeningReady(player) && player.openContainer.canInteractWith(player))
            BogoExpandedPins.toggle(player, index);
        return PinnedSlots.getMask(player);
    }

    @Inject(method = "executeServer", at = @At("RETURN"))
    private void dynamicinventory$sync(NetHandlerPlayServer handler, CallbackInfoReturnable<IPacket> result) {
        if (result.getReturnValue() instanceof BogoExpandedPins.Sync response)
            response.dynamicinventory$setExpandedPins(BogoExpandedPins.mask(handler.playerEntity));
    }
}
