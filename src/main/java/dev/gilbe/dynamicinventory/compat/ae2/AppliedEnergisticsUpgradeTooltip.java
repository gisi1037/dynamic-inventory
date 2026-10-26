package dev.gilbe.dynamicinventory.compat.ae2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.localization.GuiText;
import appeng.items.materials.MaterialType;
import appeng.util.inv.IUpgradeInventory;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

/** Native upgrade rules for AE2 machines which do not inherit GuiUpgradeable's hover tooltip. */
public final class AppliedEnergisticsUpgradeTooltip {

    private static final Map<Upgrades, ItemStack> CARDS = new EnumMap<>(Upgrades.class);
    private static boolean cached;

    private AppliedEnergisticsUpgradeTooltip() {}

    public static void draw(AEBaseGui gui, int mouseX, int mouseY) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!slot.func_111238_b() || mouseX < access.dynamicinventory$left() + slot.xDisplayPosition - 1
                || mouseX >= access.dynamicinventory$left() + slot.xDisplayPosition + 17
                || mouseY < access.dynamicinventory$top() + slot.yDisplayPosition - 1
                || mouseY >= access.dynamicinventory$top() + slot.yDisplayPosition + 17) continue;
            if (!(slot instanceof SlotRestrictedInput restricted) || slot.getHasStack()
                || restricted.getItemType() != SlotRestrictedInput.PlacableItemType.UPGRADES
                || !(slot.inventory instanceof IUpgradeInventory inventory)) return;
            cacheCards();
            List<String> lines = new ArrayList<>();
            lines.add(GuiText.Accepts.getLocal());
            for (Upgrades upgrade : Upgrades.values()) {
                int limit = inventory.getMaxInstalled(upgrade);
                ItemStack card = CARDS.get(upgrade);
                if (limit > 0 && card != null)
                    lines.add("- " + card.getDisplayName() + (limit > 1 ? " (" + limit + ")" : ""));
            }
            // Queue AE2's normal tooltip. Its drawScreen renders it after the machine/player frames.
            if (lines.size() > 1) gui.drawTooltip(mouseX, mouseY, lines.toArray(new String[0]));
            return;
        }
    }

    private static void cacheCards() {
        if (cached) return;
        for (MaterialType material : MaterialType.VALUES) {
            if (!material.isRegistered() || material.getItemInstance() == null) continue;
            ItemStack stack = material.stack(1);
            if (stack.getItem() instanceof IUpgradeModule module && module.getType(stack) != null)
                CARDS.putIfAbsent(module.getType(stack), stack);
        }
        cached = true;
    }
}
