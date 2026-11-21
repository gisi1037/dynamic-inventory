package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.gilbe.dynamicinventory.api.InventoryUi;

/** The add-on also sends control actions through its own packet channel. */
@Mixin(
    targets = { "com.glodblock.github.network.CPacketCustomButtonUpdate$Handler",
        "com.glodblock.github.network.CPacketDumpTank$Handler",
        "com.glodblock.github.network.CPacketFluidPatternTermBtns$Handler",
        "com.glodblock.github.network.CPacketLevelMaintainer$Handler",
        "com.glodblock.github.network.CPacketLevelTerminalCommands$Handler",
        "com.glodblock.github.network.CPacketSwitchGuis$Handler",
        "com.glodblock.github.network.CPacketValueConfig$Handler" },
    remap = false)
public abstract class FluidOpeningActionsMixin {

    @Inject(method = "onMessage", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$opening(@Coerce Object message, MessageContext context,
        CallbackInfoReturnable<IMessage> cir) {
        EntityPlayerMP player = context.getServerHandler().playerEntity;
        if (InventoryUi.findAdapter(player.openContainer) != null && !InventoryUi.isOpeningReady(player))
            cir.setReturnValue(null);
    }
}
