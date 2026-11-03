package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.HotbarPrediction;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.HotbarRequest;
import dev.gilbe.dynamicinventory.network.HotbarSwapReply;

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
    private static final HotbarPrediction PREDICTION = new HotbarPrediction();
    private static final java.util.Queue<Update> RECEIVED = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static boolean configuring;
    private static EntityPlayer owner;
    private static long ownerRevision = -1;

    private static final class Update {

        final EntityPlayer player;
        final long revision;
        final Runnable apply;

        Update(EntityPlayer player, Runnable apply) {
            this.player = player;
            revision = InventoryLayout.of(player.inventory)
                .revision();
            this.apply = apply;
        }
    }

    private static void queue(EntityPlayer player, Runnable apply) {
        if (player != null) RECEIVED.add(new Update(player, apply));
    }

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
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        int[] copy = slots.clone();
        queue(player, () -> {
            HotbarBanks.receive(player, copy);
            configuring = false;
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen screen = mc.currentScreen;
            if (screen instanceof GuiContainer && !PanelSettingsOverlay.isOpen(screen))
                screen.setWorldAndResolution(mc, screen.width, screen.height);
        });
    }

    public static void capability(long revision, int next) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        queue(player, () -> PREDICTION.enable(player, revision, next));
    }

    public static void receive(HotbarSwapReply reply) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        queue(
            player,
            () -> PREDICTION.reconcile(player, reply.sequence, reply.revision, reply.next, reply.slots, reply.stacks));
    }

    public static void swap(int row) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || configuring) return;
        long revision = InventoryLayout.of(mc.thePlayer.inventory)
            .revision();
        try {
            HotbarBanks.requireReady(mc.thePlayer);
            if (PREDICTION.supported(mc.thePlayer)) {
                HotbarPrediction.Swap swap = PREDICTION.predict(mc.thePlayer, row);
                if (swap != null) HotbarRequest.swap(swap.row, revision, swap.sequence);
            } else HotbarRequest.swap(row, revision);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Hotbars: " + refused.getMessage()));
        }
    }

    public static void configure(int count) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || configuring
            || PREDICTION.pending(mc.thePlayer)
            || count < 0
            || count > HotbarBanks.MAX_BANKS) return;
        int[] previous = HotbarBanks.slots(mc.thePlayer);
        if (count == previous.length / 9) return;
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
        configuring = true;
    }

    private static int rawSlot(EntityPlayer player, int window, int id) {
        if (window == -2) return id >= 0 && id < player.inventory.mainInventory.length ? id : -1;
        Container container = window == 0 ? player.inventoryContainer
            : player.openContainer != null && player.openContainer.windowId == window ? player.openContainer : null;
        if (container == null || id < 0 || id >= container.inventorySlots.size()) return -1;
        Slot slot = container.getSlot(id);
        return slot.inventory == player.inventory ? slot.getSlotIndex() : -1;
    }

    /** Keep intermediate server permutations from replacing later predicted swaps between acknowledgements. */
    public static boolean deferSlot(EntityPlayer player, int window, int id, ItemStack incoming) {
        if (player == null) return false;
        int raw = rawSlot(player, window, id);
        if (!PREDICTION.protects(player, raw)) return false;
        ItemStack copy = ItemStack.copyItemStack(incoming);
        queue(player, () -> {
            // Updates following the final acknowledgement still matter (durability, use, pickup).
            if (!PREDICTION.pending(player)) player.inventory.mainInventory[raw] = copy;
        });
        return true;
    }

    public static void preserveContents(EntityPlayer player, int window, ItemStack[] contents) {
        if (player == null || !PREDICTION.pending(player)) return;
        for (int id = 0; id < contents.length; id++) {
            int raw = rawSlot(player, window, id);
            if (PREDICTION.protects(player, raw) && deferSlot(player, window, id, contents[id]))
                contents[id] = ItemStack.copyItemStack(player.inventory.mainInventory[raw]);
        }
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
        long revision = mc.thePlayer == null ? -1
            : InventoryLayout.of(mc.thePlayer.inventory)
                .revision();
        if (owner != mc.thePlayer || ownerRevision != revision) {
            owner = mc.thePlayer;
            ownerRevision = revision;
            configuring = false;
            if (owner != null) HotbarRequest.capabilities(revision);
        }
        PREDICTION.refresh(mc.thePlayer);
        Update update;
        while ((update = RECEIVED.poll()) != null)
            if (update.player == mc.thePlayer && update.revision == InventoryLayout.of(update.player.inventory)
                .revision()) update.apply.run();
    }
}
