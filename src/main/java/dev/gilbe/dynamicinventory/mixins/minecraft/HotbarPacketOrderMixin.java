package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import dev.gilbe.dynamicinventory.network.HotbarRequest;

@Mixin(NetworkManager.class)
public abstract class HotbarPacketOrderMixin {

    /** Forge puts normal play-channel messages on this game-thread queue alongside vanilla use/drop actions. */
    @WrapOperation(
        method = "processReceivedPackets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/Packet;processPacket(Lnet/minecraft/network/INetHandler;)V"),
        require = 1)
    private void dynamicinventory$applyHotbarBeforeNextPacket(Packet packet, INetHandler handler,
        Operation<Void> original) {
        original.call(packet, handler);
        if (handler instanceof NetHandlerPlayServer server && packet instanceof FMLProxyPacket custom
            && HotbarRequest.CHANNEL.equals(custom.channel())) HotbarRequest.applyPending(server.playerEntity);
    }
}
