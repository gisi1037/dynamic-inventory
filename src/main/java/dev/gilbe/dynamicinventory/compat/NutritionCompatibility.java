package dev.gilbe.dynamicinventory.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Uses Nutrition's native icon, configuration switches and nutrient screen on our inventory. */
public final class NutritionCompatibility {

    private static boolean initialized;
    private static Constructor<?> button;
    private static Constructor<?> screen;
    private static Object config;
    private static Field enabled;
    private static Field buttonEnabled;

    private NutritionCompatibility() {}

    public static GuiButton button(int x, int y) {
        if (!initialized) {
            initialized = true;
            if (Loader.isModLoaded("nutrition")) {
                try {
                    Class<?> settings = Class.forName("ca.wescook.nutrition.NutritionConfig");
                    config = settings.getField("gui")
                        .get(null);
                    enabled = config.getClass()
                        .getField("enable");
                    buttonEnabled = config.getClass()
                        .getField("enableButton");
                    button = Class.forName("ca.wescook.nutrition.gui.GuiButtonNutrition")
                        .getConstructor(int.class, int.class);
                    screen = Class.forName("ca.wescook.nutrition.gui.NutritionGui")
                        .getConstructor(GuiScreen.class);
                } catch (ReflectiveOperationException failure) {
                    button = null;
                    DynamicInventory.LOG.warn("Unable to attach Nutrition's inventory button", failure);
                }
            }
        }
        if (button == null) return null;
        try {
            return enabled.getBoolean(config) && buttonEnabled.getBoolean(config) ? (GuiButton) button.newInstance(x, y)
                : null;
        } catch (ReflectiveOperationException failure) {
            DynamicInventory.LOG.warn("Unable to create Nutrition's inventory button", failure);
            return null;
        }
    }

    public static void open(GuiScreen parent) {
        if (screen == null) return;
        try {
            Minecraft.getMinecraft()
                .displayGuiScreen((GuiScreen) screen.newInstance(parent));
        } catch (ReflectiveOperationException failure) {
            DynamicInventory.LOG.warn("Unable to open Nutrition's nutrient screen", failure);
        }
    }
}
