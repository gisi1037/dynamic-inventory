package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S31PacketWindowProperty;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.gilbe.dynamicinventory.network.WindowPacketAccess;

/** Only exposes the wire's window ID; no client-only Minecraft methods are called by the server gate. */
public final class WindowPacketMixins {

    private WindowPacketMixins() {}

    @Mixin(C0DPacketCloseWindow.class)
    public interface ClientClose extends WindowPacketAccess {

        @Override
        @Accessor("field_149556_a")
        int dynamicinventory$windowId();
    }

    @Mixin(S2EPacketCloseWindow.class)
    public interface ServerClose extends WindowPacketAccess {

        @Override
        @Accessor("field_148896_a")
        int dynamicinventory$windowId();
    }

    @Mixin(S30PacketWindowItems.class)
    public interface Contents extends WindowPacketAccess {

        @Override
        @Accessor("field_148914_a")
        int dynamicinventory$windowId();

        @Override
        @Accessor("field_148913_b")
        ItemStack[] dynamicinventory$contents();
    }

    @Mixin(S2FPacketSetSlot.class)
    public interface Slot extends WindowPacketAccess {

        @Override
        @Accessor("field_149179_a")
        int dynamicinventory$windowId();
    }

    @Mixin(S31PacketWindowProperty.class)
    public interface Property extends WindowPacketAccess {

        @Override
        @Accessor("field_149186_a")
        int dynamicinventory$windowId();
    }

    @Mixin(S32PacketConfirmTransaction.class)
    public interface Transaction extends WindowPacketAccess {

        @Override
        @Accessor("field_148894_a")
        int dynamicinventory$windowId();
    }
}
