package dev.gilbe.dynamicinventory.mixins.forge;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.ModContainer;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;

/** Advertise protocol, capacity and frozen adapter declarations in Forge's pre-play handshake. */
@Mixin(targets = "cpw.mods.fml.common.network.handshake.FMLHandshakeMessage$ModList", remap = false)
public abstract class CapacityHandshakeMixin {

    @Shadow
    private Map<String, String> modTags;

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"))
    private void dynamicinventory$capacity(List<ModContainer> mods, CallbackInfo ci) {
        if (modTags.containsKey(DynamicInventory.MODID))
            modTags.put(DynamicInventory.MODID, InventoryCapacity.wireVersion());
    }
}
