package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import dev.gilbe.dynamicinventory.api.InventoryUi;

/** AE2 GUI actions use a custom channel rather than vanilla windowClick. */
@Pseudo
@Mixin(targets = "appeng.core.sync.network.AppEngServerPacketHandler", remap = false)
public abstract class AeOpeningActionsMixin {

    @Inject(method = "onPacketData", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pending(@Coerce Object network, FMLProxyPacket packet, EntityPlayer player,
        CallbackInfo ci) {
        // Before parsing/dispatching any action for a reviewed host, enforce the same agreement as
        // ordinary clicks. Unregistered screens keep their native behavior, including terminal navigation.
        if (InventoryUi.findAdapter(player.openContainer) != null && !InventoryUi.isOpeningReady(player)) ci.cancel();
    }
}
