package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.compat.ae2.WirelessOpeningDiagnostics;
import it.unimi.dsi.fastutil.ints.IntObjectPair;

@Pseudo
@Mixin(targets = "net.p455w0rd.wirelesscraftingterminal.common.utils.RandomUtils", remap = false)
public abstract class StandaloneWirelessOpeningTraceMixin {

    @Inject(method = "getWirelessTerm", at = @At("RETURN"))
    private static void dynamicinventory$traceTerminal(InventoryPlayer inventory,
        CallbackInfoReturnable<ItemStack> cir) {
        WirelessOpeningDiagnostics.standalone(null, cir.getReturnValue());
    }

    @Inject(method = "getWirelessTermWithSlot", at = @At("RETURN"))
    private static void dynamicinventory$traceTerminalSlot(InventoryPlayer inventory,
        CallbackInfoReturnable<IntObjectPair<ItemStack>> cir) {
        IntObjectPair<ItemStack> terminal = cir.getReturnValue();
        if (terminal != null) WirelessOpeningDiagnostics.standalone(terminal.firstInt(), terminal.second());
    }
}
