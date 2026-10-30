package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.HotbarRequest;

/** Local bindings are unbound by default to avoid stealing keys from the pack. */
public final class HotbarControls {

    public static final KeyBinding CYCLE = new KeyBinding(
        "key.dynamicinventory.hotbarCycle",
        0,
        "key.categories.dynamicinventory.hotbars");
    public static final KeyBinding REVEAL = new KeyBinding(
        "key.dynamicinventory.hotbarReveal",
        0,
        "key.categories.dynamicinventory.hotbars");
    public static final KeyBinding[] ROWS = new KeyBinding[HotbarBanks.MAX_BANKS];
    private static volatile int[] received;

    public static void initialize() {
        ClientRegistry.registerKeyBinding(CYCLE);
        ClientRegistry.registerKeyBinding(REVEAL);
        for (int i = 0; i < ROWS.length; i++) {
            ROWS[i] = new KeyBinding(
                "key.dynamicinventory.hotbarRow" + (i + 1),
                0,
                "key.categories.dynamicinventory.hotbars");
            ClientRegistry.registerKeyBinding(ROWS[i]);
        }
        FMLCommonHandler.instance()
            .bus()
            .register(new HotbarControls());
    }

    public static void receive(int[] slots) {
        received = slots.clone();
    }

    public static void swap(int row) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) HotbarRequest.swap(
            row,
            InventoryLayout.of(mc.thePlayer.inventory)
                .revision());
    }

    public static void configure(int count) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || count < 0 || count > HotbarBanks.MAX_BANKS) return;
        int[] previous = HotbarBanks.slots(mc.thePlayer);
        int[] next = java.util.Arrays.copyOf(previous, count * 9);
        java.util.List<Integer> donors = mainSlots();
        if (next.length > previous.length && donors.size() < next.length - previous.length) {
            mc.thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentText("Main needs nine available slots for each extra hotbar."));
            return;
        }
        for (int i = previous.length; i < next.length; i++)
            next[i] = donors.get(donors.size() - 1 - (i - previous.length));
        HotbarRequest.configure(
            next,
            InventoryLayout.of(mc.thePlayer.inventory)
                .revision());
    }

    private static java.util.List<Integer> mainSlots() {
        Minecraft mc = Minecraft.getMinecraft();
        String key = PlayerPanelProfiles.key(
            "dynamicinventory:player",
            mc.thePlayer.inventoryContainer.getClass()
                .getName(),
            PlayerInventoryScreen.class.getName());
        return PlayerPanelProfiles.panels(key)
            .members(
                PlayerPanelSet.MAIN,
                InventoryLayout.of(mc.thePlayer.inventory)
                    .storageSlots(),
                HotbarBanks.excluded(mc.thePlayer.inventory));
    }

    public static int maximumRows() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? 0 : HotbarBanks.count(mc.thePlayer) + mainSlots().size() / 9;
    }

    public static void toggle() {
        InventoryViewSettings.setHotbarPanelVisible(!InventoryViewSettings.hotbarPanelVisible);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.currentScreen == null)
            mc.displayGuiScreen(new PlayerInventoryScreen(mc.thePlayer));
    }

    public static String keyName(KeyBinding key) {
        return key.getKeyCode() == 0 ? "Unbound"
            : net.minecraft.client.settings.GameSettings.getKeyDisplayString(key.getKeyCode());
    }

    public static void bind(KeyBinding key, int code) {
        key.setKeyCode(code == Keyboard.KEY_ESCAPE ? 0 : code);
        KeyBinding.resetKeyBindingArrayAndHash();
        Minecraft.getMinecraft().gameSettings.saveOptions();
    }

    @SubscribeEvent
    public void keys(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (CYCLE.isPressed()) swap(-1);
        if (REVEAL.isPressed()) toggle();
        int count = HotbarBanks.count(mc.thePlayer);
        for (int i = 0; i < ROWS.length; i++) if (ROWS[i].isPressed() && i < count) swap(i);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (received == null || mc.thePlayer == null) return;
        int[] slots = received;
        received = null;
        HotbarBanks.receive(mc.thePlayer, slots);
        GuiScreen screen = mc.currentScreen;
        if (screen instanceof GuiContainer && !PanelSettingsOverlay.isOpen(screen))
            screen.setWorldAndResolution(mc, screen.width, screen.height);
    }
}
