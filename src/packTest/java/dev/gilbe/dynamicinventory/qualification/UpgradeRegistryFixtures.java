package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.registry.GameData;
import dev.gilbe.dynamicinventory.upgrades.InventoryUpgrades;

/** Headless regression using Forge's real registry insertion and consistency validation. */
public final class UpgradeRegistryFixtures {

    private UpgradeRegistryFixtures() {}

    public static void main(String[] args) throws Exception {
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new File(".setup"));
        Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        Class<?> beltType = Class.forName("dev.gilbe.dynamicinventory.upgrades.BaubleUpgradeSupport$CreativeBelt");
        Constructor<?> beltConstructor = beltType.getDeclaredConstructor(int.class, String.class);
        beltConstructor.setAccessible(true);
        Item belt = (Item) beltConstructor.newInstance(4, "inventory_belt_uhv");
        Class<?> legacyType = Class.forName("dev.gilbe.dynamicinventory.upgrades.LegacyInventoryControllerItem");
        Constructor<?> legacyConstructor = legacyType.getDeclaredConstructor();
        legacyConstructor.setAccessible(true);
        Item legacy = (Item) legacyConstructor.newInstance();
        check(belt != legacy, "legacy and UHV registry objects must be distinct");

        Constructor<GameData> registryConstructor = GameData.class.getDeclaredConstructor();
        registryConstructor.setAccessible(true);
        Method register = GameData.class.getDeclaredMethod("registerItem", Item.class, String.class, int.class);
        register.setAccessible(true);
        Method consistent = GameData.class.getDeclaredMethod("testConsistency");
        consistent.setAccessible(true);

        // This was the old remap: a target already present in the saved map cannot also take the source ID.
        GameData broken = registryConstructor.newInstance();
        register.invoke(broken, belt, "dynamicinventory:inventory_belt_uhv", 5000);
        int duplicate = (Integer) register.invoke(broken, belt, "dynamicinventory:inventory_belt_uhv", 5001);
        check(duplicate != 5001, "fixture must reproduce Forge's rejected two-ID remap");

        for (boolean reversed : new boolean[] { false, true }) {
            // Reconstruct repeatedly, including a fresh-world allocation and a subsequent world reload.
            for (int offset : new int[] { 0, 100, 200 }) {
                GameData registry = registryConstructor.newInstance();
                for (int step = 0; step < 2; step++) {
                    boolean old = (step == 0) != reversed;
                    int id = 5000 + offset + (old ? 1 : 0);
                    int actual = (Integer) register.invoke(
                        registry,
                        old ? legacy : belt,
                        "dynamicinventory:" + (old ? "creative_inventory_controller" : "inventory_belt_uhv"),
                        id);
                    check(actual == id, "saved registry ID changed");
                }
                int neighbor = (Integer) register.invoke(registry, new Item(), "fixture:neighbor", 5002 + offset);
                check(neighbor == 5002 + offset, "neighboring item ID changed");
                consistent.invoke(registry);
            }
        }

        Item[] before = InventoryUpgrades.belts;
        try {
            InventoryUpgrades.belts = new Item[] { new Item(), new Item(), new Item(), new Item(), belt };
            ItemStack stack = new ItemStack(legacy, 1, 7);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("customName", "Preserve me");
            tag.setInteger("customPayload", 42);
            stack.setTagCompound(tag);
            legacy.onUpdate(stack, null, null, 0, false);
            check(stack.getItem() == belt, "carried controller did not become Creative Belt");
            check(
                stack.stackSize == 1 && stack.getItemDamage() == 7 && stack.getTagCompound() == tag,
                "conversion changed stack count, damage or NBT");
            legacy.onUpdate(stack, null, null, 0, false);
            check(stack.getItem() == belt && stack.getTagCompound() == tag, "repeat migration changed stack");
            List<ItemStack> catalog = new ArrayList<>();
            legacy.getSubItems(legacy, null, catalog);
            check(catalog.isEmpty() && legacy.getCreativeTab() == null, "compatibility item appears in a catalog");

            InventoryUpgrades.belts = new Item[0];
            ItemStack unavailable = new ItemStack(legacy);
            legacy.onUpdate(unavailable, null, null, 0, false);
            check(unavailable.getItem() == legacy, "missing Baubles discarded a saved controller");
        } finally {
            InventoryUpgrades.belts = before;
        }
        System.out.println(
            "PASS Forge duplicate-remap reproduction; distinct saved IDs in both orders across repeated registries; "
                + "stack/NBT preservation, hidden catalogs and missing-Baubles compatibility");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
