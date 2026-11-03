package dev.gilbe.dynamicinventory.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;

/** Ordered acknowledgement, including rejected swaps. Only hotbar/bank cells are synchronized here. */
public final class HotbarSwapReply implements IMessage {

    public long sequence = -1, revision;
    public int next;
    public int[] slots = new int[0];
    public ItemStack[] stacks = new ItemStack[0];

    public HotbarSwapReply() {}

    public static HotbarSwapReply capture(EntityPlayerMP player, long sequence) {
        HotbarSwapReply reply = new HotbarSwapReply();
        reply.sequence = sequence;
        InventoryShape shape = InventoryLayout.of(player.inventory);
        reply.revision = shape.revision();
        reply.next = HotbarBanks.next(player);
        reply.slots = HotbarBanks.slots(player);
        reply.stacks = new ItemStack[9 + reply.slots.length];
        for (int i = 0; i < reply.stacks.length; i++) reply.stacks[i] = ItemStack
            .copyItemStack(player.inventory.mainInventory[i < 9 ? i : shape.rawIndex(9 + reply.slots[i - 9])]);
        return reply;
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        if (bytes.readableBytes() < 24) return;
        long id = bytes.readLong();
        revision = bytes.readLong();
        next = bytes.readInt();
        int count = bytes.readInt();
        if (id <= 0 || revision < 0
            || next < 0
            || next > HotbarBanks.MAX_BANKS
            || count < 0
            || count > InventoryShape.MAX_STORAGE
            || count % 9 != 0
            || bytes.readableBytes() < count * 4 + (9 + count) * 2) return;
        slots = new int[count];
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            int slot = bytes.readInt();
            if (slot < 0 || slot >= InventoryShape.MAX_STORAGE || !seen.add(slot)) return;
            slots[i] = slot;
        }
        stacks = new ItemStack[9 + count];
        for (int i = 0; i < stacks.length; i++) stacks[i] = ByteBufUtils.readItemStack(bytes);
        if (bytes.readableBytes() == 0) sequence = id;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeLong(sequence)
            .writeLong(revision)
            .writeInt(next)
            .writeInt(slots.length);
        for (int slot : slots) bytes.writeInt(slot);
        for (ItemStack stack : stacks) ByteBufUtils.writeItemStack(bytes, stack);
    }

    public static final class Handler implements IMessageHandler<HotbarSwapReply, IMessage> {

        @Override
        public IMessage onMessage(HotbarSwapReply reply, MessageContext context) {
            if (reply.sequence > 0) DynamicInventory.proxy.receiveHotbarSwap(reply);
            return null;
        }
    }
}
