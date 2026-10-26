package dev.gilbe.dynamicinventory.mixins.forestry;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.ForestryCompatibility;

@Pseudo
@Mixin(targets = "forestry.core.network.PacketHandler", remap = false)
public abstract class ForestryOpeningActionsMixin {

    @Inject(
        method = "onPacket(Lcpw/mods/fml/common/network/FMLNetworkEvent$ServerCustomPacketEvent;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void dynamicinventory$pending(ServerCustomPacketEvent event, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
        if (ForestryCompatibility.handles(player.openContainer) && !InventoryUi.isOpeningReady(player)) ci.cancel();
    }
}
