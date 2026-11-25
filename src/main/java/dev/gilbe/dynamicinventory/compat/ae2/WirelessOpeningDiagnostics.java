package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.glodblock.github.util.UltraTerminalModes;

import appeng.api.AEApi;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import dev.gilbe.dynamicinventory.client.ClientInventoryOpeningTrace;

/** Reads the item AE2 actually looked up, even when its client factory cannot create the terminal. */
public final class WirelessOpeningDiagnostics {

    private WirelessOpeningDiagnostics() {}

    public static void ae2(int guiId, int slot, ItemStack stack, int modeArg) {
        if (!ClientInventoryOpeningTrace.isObserving("appliedenergistics2")) return;
        String requested = GuiBridge.values()[guiId >> 5].name();
        String item = itemName(stack);
        String mode = "unavailable";
        String storedMode = "unavailable";
        if ("ae2fc:wireless_ultra_terminal".equals(item)) {
            // AE2FC's mode getter creates a tag on an uninitialized item. Read the same key without changing it.
            NBTTagCompound tag = stack.getTagCompound();
            storedMode = modeName(tag == null ? 0 : tag.getInteger("mode"));
            mode = modeArg == Integer.MIN_VALUE ? storedMode : modeName(modeArg);
        } else if (stack != null) {
            mode = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(stack) == null ? "not-wireless" : "fixed";
        }
        ClientInventoryOpeningTrace.terminal(
            "appliedenergistics2",
            "requested=" + requested
                + " "
                + device(slot, item)
                + " clientMode="
                + mode
                + " storedMode="
                + storedMode
                + " modeArg="
                + modeArg,
            true);
    }

    public static void standalone(Integer slot, ItemStack stack) {
        if (!ClientInventoryOpeningTrace.isObserving("ae2wct")) return;
        ClientInventoryOpeningTrace.terminal(
            "ae2wct",
            "requested=CRAFTING " + device(slot, itemName(stack))
                + " clientMode="
                + (stack == null ? "unavailable" : "CRAFTING"),
            slot != null);
    }

    private static String modeName(int ordinal) {
        UltraTerminalModes[] modes = UltraTerminalModes.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal].name() : "unknown(" + ordinal + ")";
    }

    private static String itemName(ItemStack stack) {
        return stack == null ? "empty" : Item.itemRegistry.getNameForObject(stack.getItem());
    }

    private static String device(Integer slot, String item) {
        String location = slot == null ? "unresolved"
            : slot >= Platform.baublesSlotsOffset ? "baubles[" + (slot - Platform.baublesSlotsOffset) + "]"
                : "inventory[" + slot + "]";
        return "terminalSlot=" + location + " item=" + item;
    }
}
