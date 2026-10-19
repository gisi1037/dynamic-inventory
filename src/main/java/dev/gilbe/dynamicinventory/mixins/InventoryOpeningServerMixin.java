package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C11PacketEnchantItem;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;
import dev.gilbe.dynamicinventory.network.ServerGateAccess;
import dev.gilbe.dynamicinventory.network.ServerInventoryGate;
import dev.gilbe.dynamicinventory.network.WindowPacketAccess;

/** Packet boundary guards overrides which bypass Container.slotClick or base listener registration. */
@Mixin(NetHandlerPlayServer.class)
public abstract class InventoryOpeningServerMixin implements ServerGateAccess {

    @Shadow
    public EntityPlayerMP playerEntity;
    @Unique
    private final ServerInventoryGate dynamicinventory$gate = new ServerInventoryGate();

    @Override
    public ServerInventoryGate dynamicinventory$openingGate() {
        return dynamicinventory$gate;
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$contents(Packet packet, CallbackInfo ci) {
        if (dynamicinventory$gate.filter(playerEntity, packet)) ci.cancel();
    }

    @Inject(method = "processClickWindow", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$click(C0EPacketClickWindow packet, CallbackInfo ci) {
        if (!dynamicinventory$gate.allowsInput(playerEntity, packet.func_149548_c())) ci.cancel();
    }

    @Inject(method = "processEnchantItem", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$button(C11PacketEnchantItem packet, CallbackInfo ci) {
        if (!dynamicinventory$gate.allowsInput(playerEntity, packet.func_149539_c())) ci.cancel();
    }

    @Inject(method = "processCloseWindow", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$staleClose(C0DPacketCloseWindow packet, CallbackInfo ci) {
        if (((WindowPacketAccess) packet).dynamicinventory$windowId() != playerEntity.openContainer.windowId)
            ci.cancel();
        else dynamicinventory$gate.clear();
    }

    @Inject(method = "processVanilla250Packet", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$agreement(C17PacketCustomPayload packet, CallbackInfo ci) {
        if (!InventoryOpeningProtocol.CHANNEL.equals(packet.func_149559_c())) return;
        dynamicinventory$gate.receive(playerEntity, packet.func_149558_e());
        ci.cancel();
    }

    @Inject(method = "onNetworkTick", at = @At("TAIL"))
    private void dynamicinventory$timeout(CallbackInfo ci) {
        dynamicinventory$gate.tick(playerEntity);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void dynamicinventory$disconnect(IChatComponent reason, CallbackInfo ci) {
        dynamicinventory$gate.clear();
    }
}
