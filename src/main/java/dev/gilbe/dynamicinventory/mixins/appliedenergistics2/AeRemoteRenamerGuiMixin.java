package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.GuiNull;
import appeng.client.gui.implementations.GuiRenamer;
import appeng.core.sync.GuiBridge;
import appeng.tile.AEBaseTile;

/** A server-approved remote rename can target an interface outside the client's loaded chunks. */
@Mixin(value = GuiBridge.class, remap = false)
public abstract class AeRemoteRenamerGuiMixin {

    @Inject(method = "getClientGuiElement", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$openRemoteRenamer(int guiId, EntityPlayer player, World world, int x, int y, int z,
        CallbackInfoReturnable<Object> result) {
        if ((guiId >> 5) != GuiBridge.GUI_RENAMER.ordinal() || !(result.getReturnValue() instanceof GuiNull)) return;
        // The native dialog receives the current name and return tab from the real server
        // container. Its client anchor only satisfies the constructor and is never added to the world.
        result.setReturnValue(new GuiRenamer(player.inventory, new AEBaseTile()));
    }
}
