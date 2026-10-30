package dev.gilbe.dynamicinventory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Installed-pack recipe fixture. JDK-only reflection permits the same helper in MCP and SRG clients. */
public final class PanelRecipeAudit {

    private PanelRecipeAudit() {}

    private static Method method(Class<?> type, String mcp, String srg) throws Exception {
        try {
            return type.getMethod(mcp);
        } catch (NoSuchMethodException missing) {
            return type.getMethod(srg);
        }
    }

    public static String run() throws Exception {
        Class<?> upgrades = Class.forName("dev.gilbe.dynamicinventory.upgrades.InventoryUpgrades");
        Set<Object> expected = new HashSet<>(
            Arrays.asList(
                (Object[]) upgrades.getField("belts")
                    .get(null)));
        Class<?> manager = Class.forName("net.minecraft.item.crafting.CraftingManager");
        Object instance = method(manager, "getInstance", "func_77594_a").invoke(null);
        List<?> recipes = (List<?>) method(manager, "getRecipeList", "func_77592_b").invoke(instance);
        Method output = method(Class.forName("net.minecraft.item.crafting.IRecipe"), "getRecipeOutput", "func_77571_b");
        Method item = method(Class.forName("net.minecraft.item.ItemStack"), "getItem", "func_77973_b");
        Set<Object> found = new HashSet<>();
        int count = 0;
        for (Object recipe : recipes) {
            Object stack = output.invoke(recipe);
            if (stack == null) continue;
            Object result = item.invoke(stack);
            if (!expected.contains(result)) continue;
            Object[] ingredients = (Object[]) recipe.getClass()
                .getMethod("getInput")
                .invoke(recipe);
            for (Object ingredient : ingredients) if (ingredient instanceof List && ((List<?>) ingredient).isEmpty())
                throw new AssertionError("Unresolved ore ingredient");
            found.add(result);
            count++;
        }
        if (count != 5 || !found.equals(expected)) throw new AssertionError("Missing belt recipes: " + count + "/5");
        return "PASS all five GTNH belt recipes, including the Creative Belt, resolve their ore ingredients";
    }
}
