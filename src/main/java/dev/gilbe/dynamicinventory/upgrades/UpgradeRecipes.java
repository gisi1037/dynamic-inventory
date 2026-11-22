package dev.gilbe.dynamicinventory.upgrades;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Provisional pack recipes based on the Traveler's Belt leather, plate and circuit pattern. */
public final class UpgradeRecipes {

    private UpgradeRecipes() {}

    public static void register() {
        if (!UpgradeSettings.placeholderRecipes || !Loader.isModLoaded("gregtech")
            || InventoryUpgrades.belts.length == 0) return;
        String[] plates = { "plateSteel", "plateStainlessSteel", "plateTungstenSteel", "plateNaquadahAlloy",
            "plateInfinity" };
        String[] circuits = { "circuitBasic", "circuitAdvanced", "circuitElite", "circuitUltimate", "circuitBio" };
        ItemStack leather = GameRegistry.findItemStack("harvestcraft", "hardenedleatherItem", 1);
        if (leather == null) leather = new ItemStack(Items.leather);
        for (int tier = 0; tier < InventoryUpgrades.belts.length; tier++) {
            String beltTier = UpgradeSettings.BELT_TIERS[tier];
            ItemStack part = component("UHV".equals(beltTier) ? "Field_Generator_UEV" : "Conveyor_Module_" + beltTier);
            if (part == null) continue;
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
                    part,
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
