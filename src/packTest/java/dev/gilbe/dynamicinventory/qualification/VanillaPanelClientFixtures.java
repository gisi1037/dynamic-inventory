package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityFurnace;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfiles;
import dev.gilbe.dynamicinventory.client.PlayerPanelSet;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.compat.VanillaPanelHosts;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Real transformed native screens, isolated preferences and containers; never clicks or mutates world slots. */
public final class VanillaPanelClientFixtures {

    private VanillaPanelClientFixtures() {}

    public static void run() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        check(
            mc.thePlayer != null && mc.getIntegratedServer() != null
                && mc.getIntegratedServer()
                    .getFolderName()
                    .startsWith("DI-Baseline-Vanilla"),
            "vanilla lab required");
        Field file = PlayerPanelProfiles.class.getDeclaredField("file");
        file.setAccessible(true);
        Path original = (Path) file.get(null);
        Path isolated = Files.createTempDirectory("vanilla-panels-")
            .resolve("player-panels.json");
        GuiScreen previous = mc.currentScreen;
        List<GuiContainer> screens = new ArrayList<>();
        for (int size : new int[] { 27, 54 })
            screens.add(new GuiChest(mc.thePlayer.inventory, new InventoryBasic("Chest", false, size)));
        screens.add(new GuiCrafting(mc.thePlayer.inventory, mc.theWorld, 0, 4, 0));
        screens.add(new GuiFurnace(mc.thePlayer.inventory, new TileEntityFurnace()));
        screens.add(new GuiDispenser(mc.thePlayer.inventory, new TileEntityDispenser()));
        screens.add(new GuiHopper(mc.thePlayer.inventory, new InventoryBasic("Hopper", false, 5)));
        screens.add(new GuiBrewingStand(mc.thePlayer.inventory, new TileEntityBrewingStand()));
        screens.add(new GuiEnchantment(mc.thePlayer.inventory, mc.theWorld, 0, 4, 0, "Enchanting"));
        screens.add(new GuiBeacon(mc.thePlayer.inventory, new TileEntityBeacon()));
        screens.add(new GuiRepair(mc.thePlayer.inventory, mc.theWorld, 0, 4, 0));
        screens.add(new GuiMerchant(mc.thePlayer.inventory, new EntityVillager(mc.theWorld), mc.theWorld, "Trades"));
        for (boolean chested : new boolean[] { false, true }) {
            EntityHorse horse = new EntityHorse(mc.theWorld);
            horse.setHorseType(chested ? 1 : 0);
            horse.setChested(chested);
            screens.add(
                new GuiScreenHorseInventory(
                    mc.thePlayer.inventory,
                    new InventoryBasic("Horse", false, chested ? 17 : 2),
                    horse));
        }
        try {
            check(!VanillaPanelHosts.supports(null), "null host refused");
            check(
                !VanillaPanelHosts.supports(
                    new ContainerChest(mc.thePlayer.inventory, new InventoryBasic("Foreign subclass", false, 27)) {}),
                "unknown subclass refused");
            for (GuiContainer screen : screens) {
                PlayerPanelProfiles.initialize(isolated.toFile());
                PlayerPanelProfiles.resetGlobal();
                PlayerPanelProfiles.reset(null);
                check(VanillaPanelHosts.supports(screen.inventorySlots, screen.getClass()), "matched native pair");
                check(!VanillaPanelHosts.supports(screen.inventorySlots, GuiScreen.class), "wrong GUI refused");
                InventoryUi.attach(screen.inventorySlots, mc.thePlayer.inventory);
                mc.currentScreen = screen;
                int width = previous == null ? 640 : previous.width, height = previous == null ? 360 : previous.height;
                screen.setWorldAndResolution(mc, width, height);
                ContainerPanelAccess access = (ContainerPanelAccess) screen;
                if (access.dynamicinventory$panel() == null)
                    access.dynamicinventory$panel(new NativeInventoryPanel(screen));
                checkAttachedWidth(screen);
                NativeLayoutPatch patch = NativeLayoutPatches.resolve(screen);
                try {
                    NativeLayoutPatches.draft(screen, patch.adjust(1, 1 - patch.value(1)));
                    NativeLayoutPatches.previewMode(screen, false);
                    screen.setWorldAndResolution(mc, width, height);
                    check(
                        access.dynamicinventory$panel()
                            .maxScroll() > 0,
                        "restricted rows need a scrollbar");
                    checkAttachedWidth(screen);
                    screen.drawScreen(-10000, -10000, 0);
                } finally {
                    NativeLayoutPatches.draft(screen, null);
                    screen.setWorldAndResolution(mc, width, height);
                }
                DetachedChestPanel panel = access.dynamicinventory$panel()
                    .floating();
                check(panel != null && !panel.detached(), "attached default: " + screen.getClass());
                List<Slot> slots = new ArrayList<>(screen.inventorySlots.inventorySlots);
                List<ItemStack> stacks = new ArrayList<>();
                for (Slot slot : slots) stacks.add(ItemStack.copyItemStack(slot.getStack()));
                int window = screen.inventorySlots.windowId;
                screen.drawScreen(-10000, -10000, 0);
                panel.apply(PlayerPanelProfile.DEFAULT.detached(true));
                check(panel.detached(), "detach: " + screen.getClass());
                screen.drawScreen(-10000, -10000, 0);
                int capacity = InventoryLayout.of(mc.thePlayer.inventory)
                    .storageSlots();
                int reserved = HotbarBanks.reserved(mc.thePlayer.inventory);
                PlayerPanelSet split = PlayerPanelSet.DEFAULT.add();
                split = split.allocate(
                    new int[] { capacity - reserved - 1, 1 },
                    capacity,
                    HotbarBanks.excluded(mc.thePlayer.inventory));
                panel.apply(split, PlayerPanelProfile.DEFAULT.detached(true), false);
                check(
                    panel.bounds()
                        .size() == 2,
                    "one-slot split: " + screen.getClass());
                for (Rect bounds : panel.bounds()) check(!bounds.intersects(panel.machineBounds()), "no overlap");
                screen.drawScreen(-10000, -10000, 0);
                panel.reload();
                check(
                    panel.bounds()
                        .size() == 2,
                    "reload retains split");
                panel.apply(PlayerPanelProfile.DEFAULT);
                access.dynamicinventory$panel()
                    .position();
                check(!panel.detached(), "reattach");
                screen.drawScreen(-10000, -10000, 0);
                check(
                    screen.inventorySlots.windowId == window
                        && slots.size() == screen.inventorySlots.inventorySlots.size(),
                    "window and slot count retained");
                for (int i = 0; i < slots.size(); i++) {
                    check(slots.get(i) == screen.inventorySlots.getSlot(i), "Slot identity retained");
                    check(
                        ItemStack.areItemStacksEqual(
                            stacks.get(i),
                            slots.get(i)
                                .getStack()),
                        "stack contents retained");
                }
                System.out.println(
                    "PASS vanilla modular panel " + screen.getClass()
                        .getName());
                access.dynamicinventory$panel()
                    .restore();
            }
        } finally {
            mc.currentScreen = previous;
            PlayerPanelProfiles.initialize(original.toFile());
        }
        System.out.println(
            "PASS " + screens.size() + " native screen variants: attached, detach, split, draw, reload, reattach");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkAttachedWidth(GuiContainer screen) {
        ContainerPanelAccess access = (ContainerPanelAccess) screen;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        int[] player = panel.editorSectionBounds(1);
        check(panel.visibleColumns() == 9, "default native inventory keeps nine columns");
        check(player[2] == (panel.maxScroll() > 0 ? 188 : 178), "no phantom scrollbar padding: " + screen.getClass());
        check(access.dynamicinventory$width() >= panel.nativeUiWidth(), "native controls retain their full width");
    }
}
