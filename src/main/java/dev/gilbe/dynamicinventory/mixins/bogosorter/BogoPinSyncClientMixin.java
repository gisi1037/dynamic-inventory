package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.common.network.SPlayerPins;

import dev.gilbe.dynamicinventory.compat.BogoExpandedPins;

/** Forge strips the packet's client handler on dedicated servers. */
@Mixin(value = SPlayerPins.class, remap = false)
public abstract class BogoPinSyncClientMixin {

    @Inject(method = "executeClient", at = @At("RETURN"))
    private void dynamicinventory$applyExpandedPins(NetHandlerPlayClient handler, CallbackInfo ci) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null)
            BogoExpandedPins.setMask(player, ((BogoExpandedPins.Sync) this).dynamicinventory$getExpandedPins());
    }
}
