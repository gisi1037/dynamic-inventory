package dev.gilbe.dynamicinventory.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.network.handshake.FMLHandshakeMessage;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;

/** Only the actual handshake overload applies capacity; server-list compatibility probes stay read-only. */
@Mixin(targets = "cpw.mods.fml.common.network.internal.FMLNetworkHandler", remap = false)
public abstract class CapacityLoginMixin {

    @Inject(
        method = "checkModList(Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ModList;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true)
    private static void dynamicinventory$login(FMLHandshakeMessage.ModList message, Side remoteSide,
        CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) return;
        int slots = InventoryCapacity.readWireStorage(
            message.modList()
                .get(DynamicInventory.MODID));
        if (slots < 0)
            cir.setReturnValue("Dynamic Inventory: incompatible startup protocol, adapter declarations or capacity");
        else if (remoteSide == Side.SERVER) {
            try {
                InventoryCapacity.useServerCapacity(slots);
            } catch (IllegalStateException activeSession) {
                cir.setReturnValue("Dynamic Inventory: cannot change capacity while another world is active");
            }
        }
    }
}
