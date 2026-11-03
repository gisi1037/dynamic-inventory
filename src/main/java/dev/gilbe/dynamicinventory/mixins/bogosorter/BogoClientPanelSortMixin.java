package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cleanroommc.bogosorter.common.network.CSort;
import com.cleanroommc.bogosorter.common.network.IPacket;
import com.cleanroommc.bogosorter.common.network.NetworkHandler;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.PanelSortRequest;

@Mixin(targets = "com.cleanroommc.bogosorter.ClientEventHandler", remap = false)
public abstract class BogoClientPanelSortMixin {

    @Redirect(
        method = "doSort",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/network/NetworkHandler;sendToServer(Lcom/cleanroommc/bogosorter/common/network/IPacket;)V"))
    private static void dynamicinventory$scope(IPacket packet, Container container, SlotAccessor slot, int slotNumber) {
        Minecraft mc = Minecraft.getMinecraft();
        DetachedChestPanel panel = DetachedChestPanel.of(mc.currentScreen);
        int[] ids = panel == null ? null : panel.sortSlots(slot == null ? slotNumber : slot.getSlotNumber());
        if (ids != null && packet instanceof CSort sort) {
            PanelSortRequest.send(
                container,
                InventoryLayout.of(mc.thePlayer.inventory)
                    .revision(),
                ids,
                sort);
        } else NetworkHandler.sendToServer(packet);
    }
}
