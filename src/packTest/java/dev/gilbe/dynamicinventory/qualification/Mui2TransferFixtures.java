package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Real MUI2 binding/quick-move regression, with saved player contents restored even on failure. */
public final class Mui2TransferFixtures {

    private Mui2TransferFixtures() {}

    public static int run(EntityPlayer player) {
        ItemStack[] before = player.inventory.mainInventory.clone();
        ItemStack cursor = player.inventory.getItemStack();
        // Client-mode sync handlers suppress outgoing packets. This tests native container transfers,
        // without opening a window, touching GL, or needing a connection for the disposable fake player.
        ModularSyncManager manager = new ModularSyncManager(true);
        PanelSyncManager sync = new PanelSyncManager(manager, true);
        sync.bindPlayerInventory(player);
        ItemStackHandler chest = new ItemStackHandler(27);
        sync.registerSlotGroup("test_chest", 9);
        for (int i = 0; i < chest.getSlots(); i++)
            sync.itemSlot("chest", i, new ModularSlot(chest, i).slotGroup("test_chest"));
        ModularContainer container = new ModularContainer();
        container.construct(
            player,
            manager,
            new UISettings(RecipeViewerSettings.DUMMY),
            "transfer_regression",
            new GuiData(player));
        int checks = 0;
        try {
            for (int logical : new int[] { 0, 9, Math.min(36, InventoryLayout.mainSlots() - 1),
                InventoryLayout.mainSlots() - 1 }) {
                Arrays.fill(player.inventory.mainInventory, null);
                for (int i = 0; i < chest.getSlots(); i++) chest.setStackInSlot(i, null);
                ModularSlot source = ((ItemSlotSH) sync.findSyncHandlerNullable("player", logical)).getSlot();
                check(source.getSlotGroup() == sync.getSlotGroup(PlayerSlotGroup.NAME), "one player transfer group");
                player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = new ItemStack(
                    Blocks.cobblestone,
                    12);
                container.transferStackInSlot(player, source.slotNumber);
                check(source.getStack() == null && countPlayer(player) == 0, "player source leaves no internal hop");
                check(chest.getStackInSlot(0) != null && chest.getStackInSlot(0).stackSize == 12, "player to chest");
                ModularSlot chestSlot = ((ItemSlotSH) sync.findSyncHandlerNullable("chest", 0)).getSlot();
                container.transferStackInSlot(player, chestSlot.slotNumber);
                check(chest.getStackInSlot(0) == null && countPlayer(player) == 12, "chest to player");
                checks += 4;
            }
            Arrays.fill(player.inventory.mainInventory, null);
            for (int i = 0; i < chest.getSlots(); i++) chest.setStackInSlot(i, new ItemStack(Blocks.dirt, 64));
            chest.setStackInSlot(0, new ItemStack(Blocks.cobblestone, 62));
            ModularSlot source = ((ItemSlotSH) sync.findSyncHandlerNullable("player", 9)).getSlot();
            source.putStack(new ItemStack(Blocks.cobblestone, 8));
            container.transferStackInSlot(player, source.slotNumber);
            check(
                chest.getStackInSlot(0).stackSize == 64 && source.getStack().stackSize == 6,
                "partial merge respects limit");
            check(countPlayer(player) == 6, "remainder stays in its original player slot");
            check(
                container.transferStackInSlot(player, source.slotNumber) == null && countPlayer(player) == 6,
                "full chest refuses transfer without moving within player inventory");
            check(player.inventory.getItemStack() == cursor, "cursor untouched");
            return checks + 4;
        } finally {
            System.arraycopy(before, 0, player.inventory.mainInventory, 0, before.length);
            player.inventory.setItemStack(cursor);
        }
    }

    private static int countPlayer(EntityPlayer player) {
        int count = 0;
        for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++) {
            ItemStack stack = player.inventory.mainInventory[InventoryLayout.rawIndex(logical)];
            if (stack != null) count += stack.stackSize;
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("MUI2 transfer: " + message);
    }
}
