package dev.gilbe.dynamicinventory.mixins.forge;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import cpw.mods.fml.common.network.internal.FMLMessage;
import cpw.mods.fml.common.network.internal.OpenGuiHandler;
import dev.gilbe.dynamicinventory.client.ClientGateAccess;
import io.netty.channel.ChannelHandlerContext;

@Mixin(value = OpenGuiHandler.class, remap = false)
public abstract class ForgeOpeningTraceMixin {

    @WrapMethod(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lcpw/mods/fml/common/network/internal/FMLMessage$OpenGui;)V")
    private void dynamicinventory$traceOpening(ChannelHandlerContext ctx, FMLMessage.OpenGui packet,
        Operation<Void> original) {
        Minecraft client = Minecraft.getMinecraft();
        if (client.getNetHandler() instanceof ClientGateAccess access) {
            ForgeOpenGuiAccess open = (ForgeOpenGuiAccess) packet;
            access.dynamicinventory$openingGate()
                .openingTrace()
                .open(
                    client,
                    open.dynamicinventory$window(),
                    open.dynamicinventory$mod(),
                    open.dynamicinventory$gui(),
                    () -> original.call(ctx, packet));
        } else original.call(ctx, packet);
    }
}
