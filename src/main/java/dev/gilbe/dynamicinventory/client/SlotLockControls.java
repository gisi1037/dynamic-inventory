package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;
import dev.gilbe.dynamicinventory.network.InventoryLockSync;

public final class SlotLockControls {

    private static java.lang.ref.WeakReference<GuiScreen> toggledScreen = new java.lang.ref.WeakReference<>(null);
    private static int toggledButton;
    private static final RenderItem GHOST_RENDERER = new RenderItem();

    private SlotLockControls() {}

    public static final class Lifecycle {

        private EntityPlayer requestedPlayer;
        private World requestedWorld;
        private long requestedRevision = -1;

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.getNetHandler() == null) {
                requestedPlayer = null;
                requestedWorld = null;
                requestedRevision = -1;
                return;
            }
            long revision = InventoryLayout.of(mc.thePlayer.inventory)
                .revision();
            if (mc.thePlayer == requestedPlayer && mc.theWorld == requestedWorld && requestedRevision == revision)
                return;
            if (!((ClientGateAccess) mc.getNetHandler()).dynamicinventory$resizeGate()
                .allowsInput(mc.thePlayer)) return;
            // Wait for the replacement player and world to exist before asking for the persisted locks.
            mc.getNetHandler()
                .addToSendQueue(new C17PacketCustomPayload(InventoryLockSync.REQUEST, new byte[] { 0 }));
            requestedPlayer = mc.thePlayer;
            requestedWorld = mc.theWorld;
            requestedRevision = revision;
        }
    }

    public static boolean isToggle(int button) {
        return (button == InventoryViewSettings.slotLockMouseButton || button == typeButton())
            && GuiScreen.isShiftKeyDown()
            && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }

    private static int typeButton() {
        return InventoryViewSettings.slotLockMouseButton == 1 ? 0 : 1;
    }

    public static boolean toggle(Slot slot, int button) {
        if (!isToggle(button)) return false;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || !(mc.currentScreen instanceof GuiContainer gui)
            || mc.getNetHandler() == null
            || PanelSettingsOverlay.isOpen(gui)
            || ClientInputGuard.isPending(gui)
            || InventorySlotLocks.index(player.inventory, slot) < 0
            || !slot.func_111238_b()
            || gui.inventorySlots != player.openContainer
            || slot.slotNumber < 0
            || slot.slotNumber >= gui.inventorySlots.inventorySlots.size()
            || gui.inventorySlots.getSlot(slot.slotNumber) != slot) return false;
        mc.getNetHandler()
            .addToSendQueue(
                new C17PacketCustomPayload(
                    InventoryLockSync.REQUEST,
                    InventoryLockSync.request(
                        gui.inventorySlots.windowId,
                        slot.slotNumber,
                        InventoryLayout.of(player.inventory)
                            .revision(),
                        button == typeButton())));
        toggledScreen = new java.lang.ref.WeakReference<>(gui);
        toggledButton = button;
        return true;
    }

    public static boolean finishToggle(GuiScreen screen, int button) {
        if (toggledScreen.get() != screen || toggledButton != button) return false;
        toggledScreen.clear();
        return true;
    }

    public static void beginClick() {
        toggledScreen.clear();
    }

    public static void draw(Slot slot, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        boolean hard = InventorySlotLocks.locked(slot, player);
        ItemStack type = hard ? null
            : InventorySlotLocks.template(player, InventorySlotLocks.index(player.inventory, slot));
        if (!hard && type == null) return;
        // A small gold padlock in the upper-left corner leaves the item count visible.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            if (type != null) {
                if (!slot.getHasStack()) {
                    RenderHelper.enableGUIStandardItemLighting();
                    GHOST_RENDERER.renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), type, x, y);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    Gui.drawRect(x, y, x + 16, y + 16, 0x55333333);
                }
                Gui.drawRect(x + 1, y + 1, x + 5, y + 5, 0xFF284A52);
                Gui.drawRect(x + 2, y + 2, x + 4, y + 4, 0xFF90DCE4);
                return;
            }
            Gui.drawRect(x + 1, y + 1, x + 6, y + 8, 0xDC2B2113);
            Gui.drawRect(x + 2, y + 1, x + 5, y + 2, 0xFFFFD482);
            Gui.drawRect(x + 2, y + 2, x + 3, y + 4, 0xFFFFD482);
            Gui.drawRect(x + 4, y + 2, x + 5, y + 4, 0xFFFFD482);
            Gui.drawRect(x + 1, y + 4, x + 6, y + 8, 0xFFF0B44E);
            Gui.drawRect(x + 3, y + 5, x + 4, y + 7, 0xFF684626);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
