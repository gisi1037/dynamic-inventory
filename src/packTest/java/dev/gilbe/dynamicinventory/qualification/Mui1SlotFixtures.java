package dev.gilbe.dynamicinventory.qualification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.internal.network.NetworkUtils;
import com.gtnewhorizons.modularui.common.widget.SlotGroup;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import dev.gilbe.dynamicinventory.compat.mui1.InventoryViewport;
import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import io.netty.buffer.Unpooled;

/** Focused native MUI1 callback and visibility checks, run by /dinvmuiapi selftest. */
final class Mui1SlotFixtures {

    private Mui1SlotFixtures() {}

    static void run(EntityPlayer player) throws IOException {
        SlotGroup group = SlotGroup.playerInventoryGroup(player);
        InventoryViewport viewport = (InventoryViewport) group.getChildren()
            .get(0);
        SlotWidget storage = (SlotWidget) viewport.getChildren()
            .get(
                viewport.getChildren()
                    .size() - 1);
        PlayerInventorySlot slot = (PlayerInventorySlot) storage.getMcSlot();
        // Replay the viewport's visibility packet without requiring a client window or GL context.
        setViewportVisible(storage, false);
        check(!storage.isEnabled() && !slot.isEnabled(), "offscreen storage rejects direct interaction");
        check(slot.isTransferEnabled(), "offscreen storage retains shift-insert capacity");
        storage.setEnabled(false);
        check(!slot.isTransferEnabled(), "explicitly disabled offscreen storage rejects shift insertion");
        viewport.onFirstRebuild();
        check(!slot.isTransferEnabled(), "viewport refresh preserves explicit disabled state");
        setViewportVisible(storage, false);
        storage.setEnabled(true);
        check(slot.isTransferEnabled(), "explicit re-enable works while the slot remains hidden");
        slot.setEnabled(false);
        check(!slot.isTransferEnabled(), "direct BaseSlot disable also rejects shift insertion");
        slot.setEnabled(true);
        storage.setEnabled(widget -> false);
        check(!slot.isTransferEnabled(), "dynamic widget disable also rejects shift insertion");

        List<Widget> callbacks = new ArrayList<Widget>();
        group.setOnDragAndDropComplete(callbacks::add);
        SlotWidget hotbar = (SlotWidget) group.getChildren()
            .get(1);
        // Disabled widgets let us exercise the native completion packet without changing any items.
        for (SlotWidget widget : new SlotWidget[] { storage, hotbar }) {
            widget.setEnabled(false);
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                new Widget.ClickData(0, false, false, false).writeToPacket(buffer);
                NetworkUtils.writeItemStack(buffer, null);
                widget.readOnServer(5, buffer);
            } finally {
                buffer.release();
            }
        }
        check(
            callbacks.size() == 2 && callbacks.get(0) == storage && callbacks.get(1) == hotbar,
            "group completion callback reaches nested storage and direct hotbar slots");
        check(
            viewport.getChildren()
                .size() == InventoryLayout.storageSlots(),
            "MUI1 viewport retains all storage slots");
    }

    private static void setViewportVisible(SlotWidget widget, boolean visible) throws IOException {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeBoolean(visible);
            widget.readOnServer(6, buffer);
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("MUI1: " + message);
    }
}
