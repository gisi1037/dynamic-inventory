package dev.gilbe.dynamicinventory.qualification;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.common.config.BogoSorterConfig;
import com.cleanroommc.bogosorter.common.network.CHotbarSwap;
import com.cleanroommc.bogosorter.common.network.CShortcut;
import com.cleanroommc.bogosorter.common.network.CSlotSync;
import com.cleanroommc.bogosorter.common.network.CSort;
import com.cleanroommc.bogosorter.common.network.IPacket;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Invokes the real pinned action implementations on the server thread, including scheduled callbacks. */
final class OpeningActionFixtures {

    private OpeningActionFixtures() {}

    static void register() {
        IBogoSortAPI.getInstance()
            .addGenericCompat(OpeningGateFixtures.Actions.class);
        if (!Loader.isModLoaded("craftingtweaks")) return;
        try {
            Class<?> api = Class.forName("net.blay09.mods.craftingtweaks.api.CraftingTweaksAPI");
            Class<?> contract = Class.forName("net.blay09.mods.craftingtweaks.api.SimpleTweakProvider");
            // Reflection enumerates this legacy API's unrelated client GUI signatures on a dedicated
            // server. Resolve only the named common methods, as an ordinary compiled invocation does.
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Object provider = lookup
                .findStatic(api, "registerSimpleProvider", MethodType.methodType(contract, String.class, Class.class))
                .invokeWithArguments("dinvopeningfixtures", OpeningGateFixtures.Actions.class);
            lookup.findVirtual(contract, "setGrid", MethodType.methodType(void.class, int.class, int.class))
                .invokeWithArguments(provider, 0, 9);
            for (String tweak : new String[] { "setTweakRotate", "setTweakBalance", "setTweakClear" }) lookup
                .findVirtual(
                    contract,
                    tweak,
                    MethodType.methodType(void.class, boolean.class, boolean.class, int.class, int.class))
                .invokeWithArguments(provider, true, false, 0, 0);
        } catch (Throwable failure) {
            throw new AssertionError("Could not register the real CraftingTweaks action provider", failure);
        }
    }

    static int expected() {
        return Loader.isModLoaded("craftingtweaks") ? 10 : 4;
    }

    static int verify(OpeningGateFixtures.Base target, EntityPlayerMP player, boolean ready) {
        ItemStack[] playerBefore = copy(player.inventory.mainInventory);
        ItemStack[] machineBefore = new ItemStack[target.machine.getSizeInventory()];
        for (int i = 0; i < machineBefore.length; i++) machineBefore[i] = copy(target.machine.getStackInSlot(i));
        ItemStack cursorBefore = copy(player.inventory.getItemStack());
        int updatesBefore = target.machine.updates;
        boolean debugBefore = BogoSorterConfig.enableDebugTools;
        int passed = 0;
        try {
            for (int action = 0; action < expected(); action++) {
                // Every action receives an input that it changes after approval. No real player items
                // participate: restore the exact inventory, cursor and machine payload in finally.
                for (int i = 0; i < player.inventory.mainInventory.length; i++)
                    player.inventory.mainInventory[i] = null;
                player.inventory.mainInventory[0] = new ItemStack(Items.diamond, 9);
                player.inventory.mainInventory[1] = new ItemStack(Items.redstone, 4);
                for (int i = 0; i < machineBefore.length; i++) target.machine.setInventorySlotContents(i, null);
                target.machine.setInventorySlotContents(0, new ItemStack(Items.redstone, 4));
                target.machine.setInventorySlotContents(8, new ItemStack(Items.diamond, 9));
                if (action == 5 || action == 6) {
                    for (int i = 0; i < machineBefore.length; i++) target.machine.setInventorySlotContents(i, null);
                    target.machine.setInventorySlotContents(0, new ItemStack(Items.diamond, action == 5 ? 8 : 9));
                    if (action == 5) target.machine.setInventorySlotContents(1, new ItemStack(Items.diamond, 1));
                }
                ItemStack[] before = snapshot(target, player);
                BogoSorterConfig.enableDebugTools = true;
                invoke(action, target, player);
                boolean changed = !same(before, snapshot(target, player));
                if (changed != ready)
                    throw new AssertionError("Opening action " + action + " changed=" + changed + " ready=" + ready);
                passed++;
            }
        } finally {
            System.arraycopy(playerBefore, 0, player.inventory.mainInventory, 0, playerBefore.length);
            player.inventory.setItemStack(cursorBefore);
            for (int i = 0; i < machineBefore.length; i++) target.machine.setInventorySlotContents(i, machineBefore[i]);
            target.machine.updates = updatesBefore;
            BogoSorterConfig.enableDebugTools = debugBefore;
            if (ready) target.detectAndSendChanges();
        }
        return passed;
    }

    private static void invoke(int action, OpeningGateFixtures.Base target, EntityPlayerMP player) {
        try {
            IPacket bogo = null;
            switch (action) {
                case 0:
                    CSort outbound = new CSort(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        0,
                        false);
                    CSort decoded = new CSort();
                    ByteBuf bytes = Unpooled.buffer();
                    try {
                        outbound.encode(new PacketBuffer(bytes));
                        decoded.decode(new PacketBuffer(bytes));
                    } finally {
                        bytes.release();
                    }
                    bogo = decoded;
                    break;
                case 1:
                    bogo = new CShortcut(CShortcut.Type.MOVE_SINGLE, 0);
                    break;
                case 2:
                    bogo = new CHotbarSwap(0, 1);
                    break;
                case 3:
                    bogo = new CSlotSync(CSlotSync.Operation.CLEAR, 0);
                    break;
                default:
                    craftingTweak(action - 4, target, player);
            }
            if (bogo != null) bogo.executeServer(player.playerNetServerHandler);
        } catch (Exception failure) {
            throw new AssertionError("Could not execute native opening action " + action, failure);
        }
    }

    private static void craftingTweak(int action, OpeningGateFixtures.Base target, EntityPlayerMP player)
        throws ReflectiveOperationException {
        String kind = new String[] { "Rotate", "Balance", "Balance", "Clear", "Compress", "TransferStack" }[action];
        Class<?> messageType = Class.forName("net.blay09.mods.craftingtweaks.net.Message" + kind);
        Object message;
        if (action == 4) message = messageType.getConstructor(int.class, boolean.class, boolean.class)
            .newInstance(9 + 27, false, false); // First hotbar slot after the nine grid and 27 storage slots.
        else if (action == 5) message = messageType.getConstructor(int.class, int.class)
            .newInstance(0, 9 + 27);
        else message = messageType.getConstructor(int.class, boolean.class)
            .newInstance(0, action == 2);
        Constructor<MessageContext> constructor = MessageContext.class
            .getDeclaredConstructor(INetHandler.class, Side.class);
        constructor.setAccessible(true);
        MessageContext context = constructor.newInstance(player.playerNetServerHandler, Side.SERVER);
        Class<?> handler = Class.forName("net.blay09.mods.craftingtweaks.net.Handler" + kind);
        Method callback = handler.getDeclaredMethod("lambda$onMessage$0", MessageContext.class, messageType);
        callback.setAccessible(true);
        // This is the exact scheduled body used by onMessage, invoked here on the server game thread
        // before ACCEPT can be processed. Scheduling from onMessage would let approval win the race.
        callback.invoke(null, context, message);
    }

    private static ItemStack[] snapshot(OpeningGateFixtures.Base target, EntityPlayerMP player) {
        ItemStack[] result = new ItemStack[player.inventory.mainInventory.length + target.machine.getSizeInventory()
            + 1];
        int index = 0;
        for (ItemStack stack : player.inventory.mainInventory) result[index++] = copy(stack);
        for (int i = 0; i < target.machine.getSizeInventory(); i++)
            result[index++] = copy(target.machine.getStackInSlot(i));
        result[index] = copy(player.inventory.getItemStack());
        return result;
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] result = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) result[i] = copy(stacks[i]);
        return result;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static boolean same(ItemStack[] first, ItemStack[] second) {
        for (int i = 0; i < first.length; i++) if (!ItemStack.areItemStacksEqual(first[i], second[i])) return false;
        return true;
    }
}
