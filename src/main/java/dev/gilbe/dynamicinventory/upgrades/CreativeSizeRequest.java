package dev.gilbe.dynamicinventory.upgrades;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;

/** One bounded request per connected player. Network handlers queue; the player's END tick validates and applies. */
public final class CreativeSizeRequest implements IMessage {

    private static final Map<EntityPlayerMP, Integer> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static SimpleNetworkWrapper channel;
    private int storage;

    public CreativeSizeRequest() {}

    public CreativeSizeRequest(int storage) {
        this.storage = storage;
    }

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-size-item");
        channel.registerMessage(Handler.class, CreativeSizeRequest.class, 0, Side.SERVER);
    }

    public static void send(int storage) {
        channel.sendToServer(new CreativeSizeRequest(storage));
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        storage = bytes.readableBytes() == 4 ? bytes.readInt() : -1;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeInt(storage);
    }

    public static final class Handler implements IMessageHandler<CreativeSizeRequest, IMessage> {

        @Override
        public IMessage onMessage(CreativeSizeRequest request, MessageContext context) {
            if (request.storage == 0
                || request.storage >= InventoryShape.MIN_STORAGE && request.storage <= InventoryShape.MAX_STORAGE)
                PENDING.put(context.getServerHandler().playerEntity, request.storage);
            return null;
        }
    }

    static void applyPending(EntityPlayerMP player) {
        Integer target = PENDING.remove(player);
        if (target == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null || held.stackSize < 1
            || held.getItem() != InventoryUpgrades.creativeSizer
            || player.openContainer != player.inventoryContainer) return;
        try {
            InventoryUpgrades.setControlledCapacity(player, target);
            player.addChatMessage(
                new ChatComponentText(
                    target == 0 ? "Normal belt capacity rules restored."
                        : "Inventory storage set to " + target + " slots."));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            player.addChatMessage(new ChatComponentText("Inventory: " + refused.getMessage()));
        }
    }
}
