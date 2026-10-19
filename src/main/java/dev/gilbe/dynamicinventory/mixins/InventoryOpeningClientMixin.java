package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S31PacketWindowProperty;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ClientGateAccess;
import dev.gilbe.dynamicinventory.client.ClientInventoryGate;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;
import dev.gilbe.dynamicinventory.network.WindowPacketAccess;

@Mixin(NetHandlerPlayClient.class)
public abstract class InventoryOpeningClientMixin implements ClientGateAccess {

    @Shadow
    private Minecraft gameController;
    @Unique
    private final ClientInventoryGate dynamicinventory$gate = new ClientInventoryGate();

    @Override
    public ClientInventoryGate dynamicinventory$openingGate() {
        return dynamicinventory$gate;
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$agreement(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (!InventoryOpeningProtocol.CHANNEL.equals(packet.func_149169_c())) return;
        dynamicinventory$gate.receive(gameController, packet.func_149168_d());
        ci.cancel();
    }

    @Inject(method = "handleWindowItems", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$contents(S30PacketWindowItems packet, CallbackInfo ci) {
        if (dynamicinventory$gate.filter(gameController, packet.func_148911_c())) ci.cancel();
    }

    @Inject(method = "handleSetSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$slot(S2FPacketSetSlot packet, CallbackInfo ci) {
        if (dynamicinventory$gate.filter(gameController, packet.func_149175_c())) ci.cancel();
    }

    @Inject(method = "handleWindowProperty", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$property(S31PacketWindowProperty packet, CallbackInfo ci) {
        if (dynamicinventory$gate.filter(gameController, packet.func_149182_c())) ci.cancel();
    }

    @Inject(method = "handleConfirmTransaction", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transaction(S32PacketConfirmTransaction packet, CallbackInfo ci) {
        if (dynamicinventory$gate.filter(gameController, packet.func_148889_c())) ci.cancel();
    }

    @Inject(method = "handleCloseWindow", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$close(S2EPacketCloseWindow packet, CallbackInfo ci) {
        if (gameController.thePlayer == null) return;
        if (((WindowPacketAccess) packet).dynamicinventory$windowId()
            != gameController.thePlayer.openContainer.windowId) ci.cancel();
        else dynamicinventory$gate.clear();
    }

    @Inject(method = "onNetworkTick", at = @At("TAIL"))
    private void dynamicinventory$timeout(CallbackInfo ci) {
        dynamicinventory$gate.tick(gameController);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void dynamicinventory$disconnect(IChatComponent reason, CallbackInfo ci) {
        dynamicinventory$gate.clear();
    }
}
