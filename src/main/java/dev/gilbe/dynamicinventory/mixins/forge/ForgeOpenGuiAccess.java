package dev.gilbe.dynamicinventory.mixins.forge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import cpw.mods.fml.common.network.internal.FMLMessage;

@Mixin(value = FMLMessage.OpenGui.class, remap = false)
public interface ForgeOpenGuiAccess {

    @Accessor("windowId")
    int dynamicinventory$window();

    @Accessor("modId")
    String dynamicinventory$mod();

    @Accessor("modGuiId")
    int dynamicinventory$gui();
}
