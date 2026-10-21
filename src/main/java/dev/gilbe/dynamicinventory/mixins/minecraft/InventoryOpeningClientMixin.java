package dev.gilbe.dynamicinventory.mixins.minecraft;

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
import dev.gilbe.dynamicinventory.client.ClientInventoryResizeGate;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol;
import dev.gilbe.dynamicinventory.network.WindowPacketAccess;

@Mixin(NetHandlerPlayClient.class)
public abstract class InventoryOpeningClientMixin implements ClientGateAccess {

    @Shadow
    private Minecraft gameController;
    @Unique
    private final ClientInventoryGate dynamicinventory$gate = new ClientInventoryGate();
    @Unique
    private final ClientInventoryResizeGate dynamicinventory$resize = new ClientInventoryResizeGate();

    @Override
    public ClientInventoryResizeGate dynamicinventory$resizeGate() {
        return dynamicinventory$resize;
    }

    @Override
    public ClientInventoryGate dynamicinventory$openingGate() {
        return dynamicinventory$gate;
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$agreement(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (InventoryResizeProtocol.CHANNEL.equals(packet.func_149169_c())) {
            dynamicinventory$resize.receive(gameController, packet.func_149168_d());
            ci.cancel();
            return;
        }
        if (!InventoryOpeningProtocol.CHANNEL.equals(packet.func_149169_c())) return;
        dynamicinventory$gate.receive(gameController, packet.func_149168_d());
        ci.cancel();
    }

    @Inject(method = "handleWindowItems", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$contents(S30PacketWindowItems packet, CallbackInfo ci) {
        if (dynamicinventory$resize.filter(gameController.thePlayer)
            || dynamicinventory$gate.filter(gameController, packet.func_148911_c())) ci.cancel();
    }

    @Inject(method = "handleSetSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$slot(S2FPacketSetSlot packet, CallbackInfo ci) {
        if (dynamicinventory$resize.filter(gameController.thePlayer)
            || dynamicinventory$gate.filter(gameController, packet.func_149175_c())) ci.cancel();
    }

    @Inject(method = "handleWindowProperty", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$property(S31PacketWindowProperty packet, CallbackInfo ci) {
        if (dynamicinventory$resize.filter(gameController.thePlayer)
            || dynamicinventory$gate.filter(gameController, packet.func_149182_c())) ci.cancel();
    }

    @Inject(method = "handleConfirmTransaction", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transaction(S32PacketConfirmTransaction packet, CallbackInfo ci) {
        if (dynamicinventory$resize.filter(gameController.thePlayer)
            || dynamicinventory$gate.filter(gameController, packet.func_148889_c())) ci.cancel();
    }

    @Inject(method = "handleCloseWindow", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$close(S2EPacketCloseWindow packet, CallbackInfo ci) {
        if (gameController.thePlayer == null) return;
        // NEI temporarily displays a client-only container, but the server may still close its real host.
        if (!dynamicinventory$gate
            .acceptsClose(gameController, ((WindowPacketAccess) packet).dynamicinventory$windowId())) ci.cancel();
    }

    @Inject(method = "onNetworkTick", at = @At("TAIL"))
    private void dynamicinventory$timeout(CallbackInfo ci) {
        dynamicinventory$resize.tick(gameController);
        dynamicinventory$gate.tick(gameController);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void dynamicinventory$disconnect(IChatComponent reason, CallbackInfo ci) {
        dynamicinventory$gate.clear();
        dynamicinventory$resize.clear();
    }
}
