package dev.gilbe.dynamicinventory.qualification;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Real optional container subclasses exercise native transfer fallback without registering another adapter. */
final class CompatibilityFallbackFixtures {

    private CompatibilityFallbackFixtures() {}

    static void verify(EntityPlayer owner, List<String> passed) {
        if (!PackCompatibility.enabled()) return;
        // Optional classes live in separate holders so an isolated fixture never links absent dependencies.
        if (Loader.isModLoaded("TConstruct")) Tinkers.verify(detachedPlayer(owner), passed);
        if (Loader.isModLoaded("GalacticraftCore")) Galacticraft.verify(detachedPlayer(owner), passed);
    }

    private static EntityPlayer detachedPlayer(EntityPlayer owner) {
        return new EntityPlayer(owner.worldObj, new GameProfile(UUID.randomUUID(), "DIFallbackTest")) {

            @Override
            public void addChatMessage(IChatComponent message) {}

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return false;
            }

            @Override
            public ChunkCoordinates getPlayerCoordinates() {
                return new ChunkCoordinates(0, 0, 0);
            }
        };
    }

    private static ItemStack input() {
        ItemStack stack = new ItemStack(Items.stick, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("fixture", "native fallback must preserve exact item NBT");
        NBTTagCompound nested = new NBTTagCompound();
        nested.setInteger("value", 4242);
        tag.setTag("nested", nested);
        stack.setTagCompound(tag);
        return stack;
    }

    private static void checkConservation(Container container, ItemStack expected, String family, List<String> passed) {
        int count = 0;
        for (Slot slot : container.inventorySlots) {
            ItemStack stack = slot.getStack();
            if (stack == null) continue;
            if (stack.getItem() != expected.getItem() || stack.getItemDamage() != expected.getItemDamage()
                || !ItemStack.areItemStackTagsEqual(stack, expected))
                throw new AssertionError(family + " native fallback changed item identity or nested NBT");
            count += stack.stackSize;
        }
        check(count == expected.stackSize, family + " native fallback conserves all items and nested NBT", passed);
    }

    private static void check(boolean condition, String description, List<String> passed) {
        if (!condition) throw new AssertionError(description);
        passed.add(description);
    }

    private static final class Tinkers {

        static void verify(EntityPlayer player, List<String> passed) {
            tconstruct.tools.logic.CraftingStationLogic logic = new tconstruct.tools.logic.CraftingStationLogic();
            // Compile through Minecraft's base type; Mantle is optional and need not enter this fixture's API.
            ((net.minecraft.tileentity.TileEntity) (Object) logic).setWorldObj(player.worldObj);
            Container container = new UnregisteredStation(player, logic);
            check(
                InventoryUi.findAdapter(container) == null && InventoryUi.getBinding(container) == null
                    && container.inventorySlots.size() == 46,
                "real unregistered Tinkers subclass retains native slots without a binding",
                passed);
            ItemStack expected = input();
            player.inventory.mainInventory[InventoryLayout.rawIndex(9)] = expected.copy();
            ItemStack moved = container.transferStackInSlot(player, 10);
            check(
                ItemStack.areItemStacksEqual(moved, expected)
                    && player.inventory.mainInventory[InventoryLayout.rawIndex(9)] == null,
                "real unregistered Tinkers subclass shifts native player source into crafting grid without a binding",
                passed);
            checkConservation(container, expected, "Tinkers", passed);
        }

        private static final class UnregisteredStation extends tconstruct.tools.inventory.CraftingStationContainer {

            UnregisteredStation(EntityPlayer player, tconstruct.tools.logic.CraftingStationLogic logic) {
                super(player.inventory, logic, 0, 0, 0);
            }
        }
    }

    private static final class Galacticraft {

        static void verify(EntityPlayer player, List<String> passed) {
            Container container = new UnregisteredEquipment(player);
            check(
                InventoryUi.findAdapter(container) == null && InventoryUi.getBinding(container) == null
                    && container.inventorySlots.size() == 50,
                "real unregistered Galacticraft subclass retains native slots without a binding",
                passed);
            ItemStack expected = input();
            player.inventory.mainInventory[InventoryLayout.rawIndex(9)] = expected.copy();
            ItemStack moved = container.transferStackInSlot(player, 0);
            check(
                ItemStack.areItemStacksEqual(moved, expected)
                    && player.inventory.mainInventory[InventoryLayout.rawIndex(9)] == null
                    && ItemStack.areItemStacksEqual(player.inventory.mainInventory[0], expected),
                "real unregistered Galacticraft subclass shifts native storage to hotbar without a binding",
                passed);
            checkConservation(container, expected, "Galacticraft", passed);
        }

        private static final class UnregisteredEquipment
            extends micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory {

            UnregisteredEquipment(EntityPlayer player) {
                super(player, new micdoodle8.mods.galacticraft.core.inventory.InventoryExtended());
            }
        }
    }
}
