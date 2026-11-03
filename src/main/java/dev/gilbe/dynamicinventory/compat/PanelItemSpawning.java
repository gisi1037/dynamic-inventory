package dev.gilbe.dynamicinventory.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;

/** Use NEI's existing authenticated slot-spawn packets with Main's actual canonical player slots. */
public final class PanelItemSpawning {

    private PanelItemSpawning() {}

    public static boolean give(ItemStack base, int quantity, boolean infinite) {
        Minecraft mc = Minecraft.getMinecraft();
        DetachedChestPanel panel = DetachedChestPanel.of(mc.currentScreen);
        if (panel == null || !panel.detached() || !NEIClientConfig.hasSMPCounterPart()) return false;
        int[] destinations = panel.itemSpawnSlots();
        // Consume a pending opening instead of falling back to NEI's whole-inventory search.
        if (destinations == null || base == null || !NEIClientConfig.canCheatItem(base)) return true;
        // NEI represents infinite stacks with a negative count; retain that representation in one empty slot.
        if (infinite) {
            for (int id : destinations) {
                Slot slot = mc.thePlayer.openContainer.getSlot(id);
                if (slot.getHasStack() || !slot.isItemValid(base)) continue;
                ItemStack spawned = base.copy();
                spawned.stackSize = quantity;
                slot.putStack(spawned);
                NEIClientUtils.setSlotContents(id, spawned, true);
                NEICPH.sendGiveItem(spawned, true, false);
                break;
            }
            return true;
        }
        if (quantity <= 0) return true;
        int remaining = quantity;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int id : destinations) {
            Slot slot = mc.thePlayer.openContainer.getSlot(id);
            ItemStack existing = slot.getStack();
            if (!slot.isItemValid(base) || (pass == 0) != (existing != null)) continue;
            if (existing != null && (!existing.isStackable() || existing.getItem() != base.getItem()
                || existing.getItemDamage() != base.getItemDamage()
                || !ItemStack.areItemStackTagsEqual(existing, base))) continue;
            int current = existing == null ? 0 : existing.stackSize;
            int count = Math.min(remaining, Math.min(slot.getSlotStackLimit(), base.getMaxStackSize()) - current);
            if (count <= 0) continue;
            ItemStack spawned = base.copy();
            spawned.stackSize = current + count;
            // This is the same local prediction and permission-checked server path used by NEI's native slot spawns.
            slot.putStack(spawned);
            NEIClientUtils.setSlotContents(id, spawned, true);
            remaining -= count;
            if (remaining == 0) break;
        }
        if (remaining < quantity) {
            ItemStack notice = base.copy();
            notice.stackSize = quantity - remaining;
            NEICPH.sendGiveItem(notice, infinite, false);
        }
        return true;
    }
}
