package dev.gilbe.dynamicinventory.mixins.craftingtweaks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.gilbe.dynamicinventory.api.InventoryUi;

/** Pinned CraftingTweaks 1.0.3 schedules every inventory action in this game-thread callback. */
@Pseudo
@Mixin(
    targets = { "net.blay09.mods.craftingtweaks.net.HandlerRotate", "net.blay09.mods.craftingtweaks.net.HandlerBalance",
        "net.blay09.mods.craftingtweaks.net.HandlerClear", "net.blay09.mods.craftingtweaks.net.HandlerCompress",
        "net.blay09.mods.craftingtweaks.net.HandlerTransferStack" },
    remap = false)
public abstract class CraftingTweakOpeningActionsMixin {

    @Inject(method = "lambda$onMessage$0", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$pending(MessageContext context, @Coerce Object message, CallbackInfo ci) {
        // Check when the scheduled action actually executes, before looking up or mutating its container.
        // Unknown containers and persistent window zero keep their native behavior through the shared API.
        if (!InventoryUi.isOpeningReady(context.getServerHandler().playerEntity)) ci.cancel();
    }
}
