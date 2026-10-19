package dev.gilbe.dynamicinventory.qualification;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.AttachedChestAccess;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Render-thread checks on a real compressed chest attached to a native Tinkers crafting station. */
public final class AttachedChestClientFixtures {

    private AttachedChestClientFixtures() {}

    public static void run() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiContainer gui = (GuiContainer) minecraft.currentScreen;
        AttachedChestAccess nativeGui = (AttachedChestAccess) gui;
        check(nativeGui.dynamicinventory$splitChest(), "open a supported large attached chest first");
        int geometryCases = AttachedChestLayoutFixtures.run();
        List<Slot> original = new ArrayList<>(gui.inventorySlots.inventorySlots);
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : original) stacks.add(ItemStack.copyItemStack(slot.getStack()));
        ItemStack cursor = ItemStack.copyItemStack(minecraft.thePlayer.inventory.getItemStack());
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(gui.inventorySlots);
        gui.drawScreen(-10000, -10000, 0);
        Map<Slot, int[]> chestPositions = new IdentityHashMap<>();
        int chestBottom = 0;
        for (Slot slot : original) {
            if (nativeGui.dynamicinventory$isChestSlot(slot) && slot.func_111238_b()) {
                chestPositions.put(slot, new int[] { slot.xDisplayPosition, slot.yDisplayPosition });
                chestBottom = Math.max(chestBottom, slot.yDisplayPosition + 16);
            }
        }
        int visiblePlayer = 0;
        for (int logical = 0; logical < binding.getOrdinarySlotCount(); logical++) {
            Slot player = gui.inventorySlots.getSlot(binding.id(logical));
            check(
                player.inventory == minecraft.thePlayer.inventory
                    && player.getSlotIndex() == InventoryLayout.rawIndex(logical),
                "canonical player slot " + logical);
            if (player.xDisplayPosition < 0) continue;
            visiblePlayer++;
            check(player.yDisplayPosition >= chestBottom, "player inventory remains below attached chest");
            for (int id = 0; id < 10; id++)
                check(!overlap(player, gui.inventorySlots.getSlot(id)), "player and crafting cells do not overlap");
        }
        for (int id = 0; id < 10; id++) check(
            gui.inventorySlots.getSlot(id).yDisplayPosition >= chestBottom,
            "crafting remains below attached chest");
        for (int frame = 0; frame < 3; frame++) {
            gui.drawScreen(-10000, -10000, 0);
            for (Map.Entry<Slot, int[]> entry : chestPositions.entrySet()) check(
                entry.getKey().xDisplayPosition == entry.getValue()[0]
                    && entry.getKey().yDisplayPosition == entry.getValue()[1],
                "native chest positions do not drift");
        }
        check(original.size() == gui.inventorySlots.inventorySlots.size(), "container slot count is retained");
        for (int id = 0; id < original.size(); id++) check(
            original.get(id) == gui.inventorySlots.getSlot(id) && ItemStack.areItemStacksEqual(
                stacks.get(id),
                original.get(id)
                    .getStack()),
            "native slot identity and stack are retained at " + id);
        check(ItemStack.areItemStacksEqual(cursor, minecraft.thePlayer.inventory.getItemStack()), "cursor retained");
        JsonObject report = new JsonObject();
        report.addProperty("success", true);
        report.addProperty("geometryCases", geometryCases);
        report.addProperty("ordinarySlots", binding.getOrdinarySlotCount());
        report.addProperty("visiblePlayerSlots", visiblePlayer);
        report.addProperty("visibleChestSlots", chestPositions.size());
        QualificationCommand.write(minecraft.thePlayer, "client-attached-chest-layout", report);
    }

    private static boolean overlap(Slot first, Slot second) {
        return first.xDisplayPosition < second.xDisplayPosition + 16
            && second.xDisplayPosition < first.xDisplayPosition + 16
            && first.yDisplayPosition < second.yDisplayPosition + 16
            && second.yDisplayPosition < first.yDisplayPosition + 16;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Attached chest: " + message);
    }
}
