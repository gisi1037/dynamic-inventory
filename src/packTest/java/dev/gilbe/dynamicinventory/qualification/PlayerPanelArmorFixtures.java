package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.network.PanelTransferRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Exercises the detached panel's real server request, including equipment priority and fallback. */
public final class PlayerPanelArmorFixtures {

    private PlayerPanelArmorFixtures() {}

    public static int run(EntityPlayerMP player) {
        ItemStack[] main = player.inventory.mainInventory.clone();
        ItemStack[] armor = player.inventory.armorInventory.clone();
        ItemStack cursor = player.inventory.getItemStack();
        Container opened = player.openContainer;
        InventoryShape shape = InventoryLayout.of(player.inventory);
        try {
            Method apply = PanelTransferRequest.class.getDeclaredMethod("apply", EntityPlayerMP.class);
            apply.setAccessible(true);
            player.openContainer = player.inventoryContainer;
            player.inventory.setItemStack(null);
            Item[] items = { Items.iron_helmet, Items.iron_chestplate, Items.iron_leggings, Items.iron_boots };
            int checks = 0;
            for (int logical : new int[] { 0, 9, 36, shape.mainSlots() - 1 }) {
                for (int type = 0; type < items.length; type++) {
                    Arrays.fill(player.inventory.mainInventory, null);
                    Arrays.fill(player.inventory.armorInventory, null);
                    ItemStack stack = new ItemStack(items[type], 1, 7);
                    stack.setTagCompound(new NBTTagCompound());
                    stack.getTagCompound()
                        .setString("fixture", "armor-" + logical);
                    ItemStack expected = stack.copy();
                    player.inventory.mainInventory[shape.rawIndex(logical)] = stack;
                    int destination = logical == 0 ? 9 : 0;
                    move(player, apply, logical, destination);
                    check(
                        ItemStack.areItemStacksEqual(expected, player.inventory.armorInventory[3 - type]),
                        "armor damage and NBT survive equipping");
                    check(player.inventory.mainInventory[shape.rawIndex(logical)] == null, "source emptied");
                    check(
                        player.inventory.mainInventory[shape.rawIndex(destination)] == null,
                        "armor equips before the panel's ordinary destination");
                    ItemStack occupied = player.inventory.armorInventory[3 - type];
                    ItemStack second = new ItemStack(items[type]);
                    ItemStack expectedSecond = second.copy();
                    player.inventory.mainInventory[shape.rawIndex(logical)] = second;
                    move(player, apply, logical, destination);
                    check(player.inventory.armorInventory[3 - type] == occupied, "equipped armor retained");
                    check(
                        ItemStack.areItemStacksEqual(
                            expectedSecond,
                            player.inventory.mainInventory[shape.rawIndex(destination)]),
                        "occupied armor uses panel fallback");
                    checks += 5;
                }
            }
            return checks;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Player panel armor request", failure);
        } finally {
            System.arraycopy(main, 0, player.inventory.mainInventory, 0, main.length);
            System.arraycopy(armor, 0, player.inventory.armorInventory, 0, armor.length);
            player.inventory.setItemStack(cursor);
            player.openContainer = opened;
        }
    }

    private static void move(EntityPlayerMP player, Method apply, int source, int destination)
        throws ReflectiveOperationException {
        ByteBuf bytes = Unpooled.buffer();
        try {
            bytes.writeInt(player.inventoryContainer.windowId)
                .writeLong(
                    InventoryLayout.of(player.inventory)
                        .revision())
                .writeInt(PlayerContainerLayout.containerId(player.inventory, source))
                .writeInt(1)
                .writeInt(1)
                .writeInt(PlayerContainerLayout.containerId(player.inventory, destination));
            PanelTransferRequest request = new PanelTransferRequest();
            request.fromBytes(bytes);
            apply.invoke(request, player);
        } finally {
            bytes.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
