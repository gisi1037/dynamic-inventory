package dev.gilbe.dynamicinventory.network;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import dev.gilbe.dynamicinventory.compat.ae2.PinIdentity;
import dev.gilbe.dynamicinventory.compat.ae2.PinnedItemTransfer;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;
import io.netty.buffer.Unpooled;

/** Pin gestures bind to exact item data across count changes, NBT wire ordering and panel requests. */
public final class AePinsRequestFixtures {

    private AePinsRequestFixtures() {}

    public static void main(String[] args) throws Exception {
        var home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new java.io.File(".setup"));
        var first = tag(false);
        var second = tag(true);
        byte[] identity = PinIdentity.of(stack(first, 2));
        if (!PinIdentity.matches(identity, stack(second, 37)))
            throw new AssertionError("Stock, craftability and compound ordering must preserve pin identity");
        second.setIntArray("BB", new int[] { 2, 1 });
        if (PinIdentity.matches(identity, stack(second, 2)) || PinIdentity.matches(identity, null))
            throw new AssertionError("A changed or removed pin must reject the old gesture");
        var request = new AePinsChannel.Request();
        request.epoch = 3;
        request.windowId = 7;
        request.index = 4;
        request.sequence = 8;
        request.kind = 5 + AePinsChannel.Transfer.SPACE_PANEL.ordinal();
        request.expected = identity;
        request.revision = 6;
        request.argument = 10;
        request.slots = new int[] { 10, 17 };
        var bytes = Unpooled.buffer();
        try {
            request.toBytes(bytes);
            var decoded = new AePinsChannel.Request();
            decoded.fromBytes(bytes.duplicate());
            if (decoded.kind != request.kind || !Arrays.equals(identity, decoded.expected)
                || !Arrays.equals(request.slots, decoded.slots)
                || decoded.revision != 6)
                throw new AssertionError("The identity, scope and revision must survive the wire format");
            for (int length = 0; length < bytes.readableBytes(); length++) {
                var partial = new AePinsChannel.Request();
                partial.fromBytes(bytes.slice(0, length));
                if (partial.kind >= 0) throw new AssertionError("Accepted truncated panel request at " + length);
            }
        } finally {
            bytes.release();
        }
        liveCrafting();
        System.out
            .println("PASS AE2 pin identity, live craft fallback, stale NBT rejection, scoped request and truncation");
    }

    private static void liveCrafting() throws Exception {
        var resolve = PinnedItemTransfer.class
            .getDeclaredMethod("resolveClick", Transfer.class, ItemStack.class, IMEMonitor.class, IAEItemStack.class);
        resolve.setAccessible(true);
        var stalePin = liveStack(64, false);
        if (resolve.invoke(null, Transfer.PICKUP, null, monitor(liveStack(0, true)), stalePin) != Transfer.CRAFT)
            throw new AssertionError("A stale in-stock pin opens crafting from the live empty network");
        if (resolve.invoke(null, Transfer.SPLIT, null, monitor(liveStack(2, true)), stalePin) != Transfer.SPLIT
            || resolve.invoke(null, Transfer.PICKUP, null, monitor(liveStack(0, false)), stalePin) != Transfer.PICKUP
            || resolve.invoke(null, Transfer.PICKUP, new ItemStack(new Item()), monitor(liveStack(0, true)), stalePin)
                != Transfer.PICKUP
            || resolve.invoke(null, Transfer.STACK, null, monitor(liveStack(0, true)), stalePin) != Transfer.STACK)
            throw new AssertionError("Craft fallback is only for normal empty-cursor clicks on a live craftable pin");
    }

    private static IAEItemStack liveStack(long count, boolean craftable) {
        return (IAEItemStack) Proxy.newProxyInstance(
            IAEItemStack.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> switch (method.getName()) {
            case "getStackSize" -> count;
            case "isCraftable" -> craftable;
            default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IMEMonitor<?> monitor(IAEItemStack live) {
        var items = Proxy.newProxyInstance(
            IItemList.class.getClassLoader(),
            new Class<?>[] { IItemList.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("findPrecise")) return live;
                throw new UnsupportedOperationException(method.getName());
            });
        return (IMEMonitor<?>) Proxy.newProxyInstance(
            IMEMonitor.class.getClassLoader(),
            new Class<?>[] { IMEMonitor.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getStorageList")) return items;
                throw new UnsupportedOperationException(method.getName());
            });
    }

    // The AE2 implementation bootstraps its entire mod on first use. Exercise its public stack contract headlessly.
    private static IAEStack<?> stack(NBTTagCompound data, long count) {
        return (IAEStack<?>) Proxy.newProxyInstance(
            IAEStack.class.getClassLoader(),
            new Class<?>[] { IAEStack.class },
            (proxy, method, args) -> switch (method.getName()) {
            case "copy" -> stack(data, count);
            case "reset" -> stack(data, 0);
            case "writeToNBTGeneric" -> {
            var output = (NBTTagCompound) args[0];
            output.setTag("tag", data);
            output.setLong("Cnt", count);
            output.setBoolean("Craft", count > 2);
            yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static NBTTagCompound tag(boolean reversed) {
        var result = new NBTTagCompound();
        // These keys have the same Java hash; inserting them in opposite orders exercises compound ordering.
        var list = new NBTTagList();
        list.appendTag(new NBTTagString("controller"));
        list.appendTag(new NBTTagString("pattern"));
        if (reversed) result.setIntArray("BB", new int[] { 1, 2 });
        result.setTag("Aa", list);
        if (!reversed) result.setIntArray("BB", new int[] { 1, 2 });
        return result;
    }
}
