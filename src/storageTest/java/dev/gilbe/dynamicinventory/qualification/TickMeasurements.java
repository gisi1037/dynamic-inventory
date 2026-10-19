package dev.gilbe.dynamicinventory.qualification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** START/END delimit the real player update. Records callbacks without modifying ItemStack NBT or invoking updates. */
public final class TickMeasurements {

    private static final Map<String, Sample> SAMPLES = new ConcurrentHashMap<>();

    private static String key(EntityPlayer player) {
        return (player.worldObj.isRemote ? "client:" : "server:") + player.getUniqueID();
    }

    public static void begin(EntityPlayer player, String label) {
        SAMPLES.put(key(player), new Sample(label));
    }

    public static void update(EntityPlayer player, int index, boolean selected) {
        Sample sample = SAMPLES.get(key(player));
        if (sample == null || !sample.active || !sample.inFrame) return;
        for (int i = 0; i < sample.slots.length; i++) {
            if (sample.slots[i] == index) {
                sample.calls[i]++;
                boolean offhand = BackhandCompatibility.enabled() && index == InventoryLayout.OFFHAND_INDEX;
                if (selected != offhand || (offhand && (player.inventory.currentItem != index
                    || player.getHeldItem() != player.inventory.mainInventory[index]))) sample.selectedCallbacks++;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Sample sample = SAMPLES.get(key(event.player));
        if (sample == null || !sample.active) return;
        if (event.phase == TickEvent.Phase.START) {
            sample.inFrame = true;
            for (int i = 0; i < sample.slots.length; i++) {
                sample.calls[i] = 0;
                sample.expected[i] = event.player.inventory.mainInventory[sample.slots[i]] != null
                    && event.player.inventory.mainInventory[sample.slots[i]].getItem() == CoreFixtures.probe;
            }
        } else if (sample.inFrame) {
            for (int i = 0; i < sample.slots.length; i++) {
                if (sample.expected[i]) {
                    sample.frames[i]++;
                    sample.totalCalls[i] += sample.calls[i];
                    if (sample.calls[i] != 1) sample.badFrames[i]++;
                }
            }
            sample.inFrame = false;
        }
    }

    public static JsonObject finish(EntityPlayer player) {
        Sample sample = SAMPLES.get(key(player));
        if (sample == null) throw new IllegalStateException("No tick measurement active");
        sample.active = false;
        JsonObject report = new JsonObject();
        report.addProperty("label", sample.label);
        report.addProperty("side", player.worldObj.isRemote ? "client" : "server");
        report.addProperty("player", player.getCommandSenderName());
        report.addProperty(
            "uuid",
            player.getUniqueID()
                .toString());
        boolean success = sample.selectedCallbacks == 0;
        JsonArray slots = new JsonArray();
        for (int i = 0; i < sample.slots.length; i++) {
            JsonObject slot = new JsonObject();
            slot.addProperty("mainIndex", sample.slots[i]);
            slot.addProperty(
                "expectedSelected",
                BackhandCompatibility.enabled() && sample.slots[i] == InventoryLayout.OFFHAND_INDEX);
            slot.addProperty("observedPlayerUpdates", sample.frames[i]);
            slot.addProperty("onUpdateCalls", sample.totalCalls[i]);
            slot.addProperty("updatesWithWrongCallCount", sample.badFrames[i]);
            slots.add(slot);
            success &= sample.frames[i] >= 40 && sample.badFrames[i] == 0 && sample.frames[i] == sample.totalCalls[i];
        }
        report.add("slots", slots);
        report.addProperty("unexpectedSelectedCallbacks", sample.selectedCallbacks);
        report.addProperty("success", success);
        return report;
    }

    private static final class Sample {

        final String label;
        final int[] slots = BackhandCompatibility.enabled()
            ? new int[] { 9, InventoryLayout.rawIndex(36), InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1),
                InventoryLayout.OFFHAND_INDEX }
            : new int[] { 9, 36, InventoryLayout.mainSlots() - 1 };
        final int[] calls = new int[slots.length];
        final boolean[] expected = new boolean[slots.length];
        final long[] frames = new long[slots.length];
        final long[] totalCalls = new long[slots.length];
        final long[] badFrames = new long[slots.length];
        int selectedCallbacks;
        boolean active = true;
        boolean inFrame;

        Sample(String label) {
            this.label = label;
        }
    }
}
