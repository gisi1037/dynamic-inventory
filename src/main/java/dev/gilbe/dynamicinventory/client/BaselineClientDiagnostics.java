package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;

public class BaselineClientDiagnostics {

    private final KeyBinding captureKey = new KeyBinding(
        "key.dynamicinventory.baseline",
        Keyboard.KEY_F8,
        "key.categories.dynamicinventory");
    private EntityClientPlayerMP lastPlayer;
    private boolean wasDown;
    private final KeyBinding auditKey = new KeyBinding(
        "key.dynamicinventory.uiAudit",
        Keyboard.KEY_F9,
        "key.categories.dynamicinventory");
    private boolean auditWasDown;

    public BaselineClientDiagnostics() {
        ClientRegistry.registerKeyBinding(captureKey);
        ClientRegistry.registerKeyBinding(auditKey);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        boolean down = GameSettings.isKeyDown(captureKey);
        boolean auditDown = GameSettings.isKeyDown(auditKey);
        if (client.thePlayer != null) {
            if (client.thePlayer != lastPlayer) capture(client, "client-join", false);
            if (down && !wasDown) capture(client, "client-hotkey", true);
            if (auditDown && !auditWasDown && client.currentScreen instanceof GuiContainer) {
                try {
                    java.io.File report = InventoryUiAudit.capture((GuiContainer) client.currentScreen);
                    client.thePlayer
                        .addChatMessage(new ChatComponentText("Inventory UI audit: " + report.getAbsolutePath()));
                } catch (Exception failure) {
                    DynamicInventory.LOG.warn("Could not capture inventory UI audit", failure);
                    client.thePlayer
                        .addChatMessage(new ChatComponentText("Inventory UI audit failed; see the game log."));
                }
            }
        }
        lastPlayer = client.thePlayer;
        wasDown = down;
        auditWasDown = auditDown;
    }

    public static void capture(Minecraft client, String reason, boolean notify) {
        if (client.thePlayer == null) return;
        String screen = client.currentScreen == null ? "none (in-game)"
            : client.currentScreen.getClass()
                .getName();
        BaselineDiagnostics.capture(client.thePlayer, screen, reason, notify);
    }
}
