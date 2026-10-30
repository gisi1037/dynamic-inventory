package dev.gilbe.dynamicinventory.upgrades;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Provisional pack recipes: Traveler's Belt leather/plate/circuit pattern with tiered conveyor components. */
public final class UpgradeRecipes {

    private UpgradeRecipes() {}

    public static void register() {
        if (!UpgradeSettings.placeholderRecipes || !Loader.isModLoaded("gregtech")
            || InventoryUpgrades.belts.length == 0) return;
        String[] plates = { "plateSteel", "plateStainlessSteel", "plateTungstenSteel", "plateNaquadahAlloy",
            "plateNeutronium" };
        String[] circuits = { "circuitBasic", "circuitAdvanced", "circuitElite", "circuitUltimate", "circuitInfinite" };
        ItemStack leather = GameRegistry.findItemStack("harvestcraft", "hardenedleatherItem", 1);
        if (leather == null) leather = new ItemStack(Items.leather);
        for (int tier = 0; tier < InventoryUpgrades.belts.length; tier++) {
            ItemStack conveyor = component("Conveyor_Module_" + UpgradeSettings.BELT_TIERS[tier]);
            if (conveyor == null) continue;
            Object previous = tier == 0 ? new ItemStack(net.minecraft.init.Blocks.chest)
                : new ItemStack(InventoryUpgrades.belts[tier - 1]);
            GameRegistry.addRecipe(
                new ShapedOreRecipe(
                    new ItemStack(InventoryUpgrades.belts[tier]),
                    "LCL",
                    "PBP",
                    "LIL",
                    'L',
                    leather,
                    'C',
                    conveyor,
                    'P',
                    plates[tier],
                    'B',
                    previous,
                    'I',
                    circuits[tier]));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ItemStack component(String name) {
        try {
            Class type = Class.forName("gregtech.api.enums.ItemList");
            Object item = Enum.valueOf(type, name);
            ItemStack stack = (ItemStack) type.getMethod("get", long.class, Object[].class)
                .invoke(item, 1L, new Object[0]);
            if (stack != null && stack.getItem() != null) return stack;
        } catch (ReflectiveOperationException | IllegalArgumentException unavailable) {
            DynamicInventory.LOG
                .warn("Skipping placeholder inventory belt recipe: missing GT component {}", name, unavailable);
        }
        return null;
    }
}
