package dev.gilbe.dynamicinventory.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.Container;

/** Exact native hosts shared by client presentation and server-side player-to-player panel transfers. */
public final class VanillaPanelHosts {

    private static final Map<String, String> SCREENS;

    static {
        Map<String, String> screens = new HashMap<>();
        host(screens, "ContainerChest", "inventory.GuiChest");
        host(screens, "ContainerWorkbench", "inventory.GuiCrafting");
        host(screens, "ContainerFurnace", "inventory.GuiFurnace");
        host(screens, "ContainerDispenser", "inventory.GuiDispenser");
        host(screens, "ContainerHopper", "GuiHopper");
        host(screens, "ContainerBrewingStand", "inventory.GuiBrewingStand");
        host(screens, "ContainerEnchantment", "GuiEnchantment");
        host(screens, "ContainerBeacon", "inventory.GuiBeacon");
        host(screens, "ContainerHorseInventory", "inventory.GuiScreenHorseInventory");
        host(screens, "ContainerRepair", "GuiRepair");
        host(screens, "ContainerMerchant", "GuiMerchant");
        // The pack replaces these vanilla block screens. Names keep optional classes off a dedicated server.
        screens.put(
            "ganymedes01.etfuturum.inventory.ContainerNewBrewingStand",
            "ganymedes01.etfuturum.client.gui.inventory.GuiNewBrewingStand");
        screens.put(
            "ganymedes01.etfuturum.inventory.ContainerEnchantment",
            "ganymedes01.etfuturum.client.gui.inventory.GuiEnchantment");
        screens.put(
            "ganymedes01.etfuturum.inventory.ContainerAnvil",
            "ganymedes01.etfuturum.client.gui.inventory.GuiAnvil");
        SCREENS = Collections.unmodifiableMap(screens);
    }

    private VanillaPanelHosts() {}

    private static void host(Map<String, String> screens, String container, String screen) {
        screens.put("net.minecraft.inventory." + container, "net.minecraft.client.gui." + screen);
    }

    public static boolean supports(Container container) {
        if (container == null) return false;
        String name = container.getClass()
            .getName();
        return name.equals("net.minecraft.inventory.ContainerPlayer") || SCREENS.containsKey(name);
    }

    /** Subclasses can have different drawing/input contracts and require their own integration. */
    public static boolean supports(Container container, Class<?> screen) {
        return container != null && screen != null
            && screen.getName()
                .equals(
                    SCREENS.get(
                        container.getClass()
                            .getName()));
    }
}
