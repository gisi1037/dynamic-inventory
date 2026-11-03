package dev.gilbe.dynamicinventory.client;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.MainInventoryRoutingRequest;

/** Publish canonical membership, including offscreen rows, before native click prediction and its packet. */
public final class MainInventoryRoutingClient {

    private static EntityPlayer owner;
    private static Container host;
    private static int window;
    private static long revision;
    private static int[] pickup, transfer;

    public static void initialize() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MainInventoryRoutingClient());
    }

    public static void sync() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.getNetHandler() == null) {
            owner = null;
            host = null;
            pickup = transfer = null;
            return;
        }
        Container container = player.openContainer;
        // NEI and uninitialized native windows may temporarily use a client-only window zero.
        if (container == null || container.windowId == 0 && container != player.inventoryContainer) return;
        int capacity = InventoryLayout.of(player.inventory)
            .storageSlots();
        long nextRevision = InventoryLayout.of(player.inventory)
            .revision();
        String key = PlayerPanelProfiles.key(
            "dynamicinventory:player",
            player.inventoryContainer.getClass()
                .getName(),
            PlayerInventoryScreen.class.getName());
        int[] nextPickup = PlayerPanelProfiles.panels(key)
            .automaticMainSlots(capacity, PlayerPanelProfiles.resolve(key).detached);
        DetachedChestPanel panel = DetachedChestPanel.of(mc.currentScreen);
        int[] nextTransfer = panel != null ? panel.mainStorageIndices()
            : mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer
                ? java.util.stream.IntStream.range(0, capacity)
                    .toArray()
                : container != player.inventoryContainer && player == owner && container == host && transfer != null
                    ? transfer
                    : nextPickup;
        if (player == owner && container == host
            && window == container.windowId
            && revision == nextRevision
            && Arrays.equals(pickup, nextPickup)
            && Arrays.equals(transfer, nextTransfer)) return;
        owner = player;
        host = container;
        window = container.windowId;
        revision = nextRevision;
        pickup = nextPickup;
        transfer = nextTransfer;
        AutomaticInventoryRouting.receive(player, revision, window, pickup, transfer);
        MainInventoryRoutingRequest.send(window, revision, pickup, transfer);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) sync();
    }
}
