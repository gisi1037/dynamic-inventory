package dev.gilbe.dynamicinventory.mixins.modularui2;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.value.sync.ISyncRegistrar;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

@Mixin(value = ISyncRegistrar.class, remap = false)
public interface Mui2PlayerBindingMixin {

    @Inject(
        method = "bindPlayerInventory(Lnet/minecraft/entity/player/EntityPlayer;Lcom/cleanroommc/modularui/value/sync/PanelSyncManager$SlotFunction;)Lcom/cleanroommc/modularui/value/sync/ISyncRegistrar;",
        at = @At("HEAD"),
        cancellable = true)
    default void dynamicinventory$bind(EntityPlayer player, PanelSyncManager.SlotFunction factory,
        CallbackInfoReturnable<ISyncRegistrar<?>> cir) {
        ISyncRegistrar<?> registrar = (ISyncRegistrar<?>) this;
        if (registrar.getSlotGroup(PlayerSlotGroup.NAME) != null)
            throw new IllegalStateException("Player slot group already registered");
        Mui2PlayerInventory inventory = new Mui2PlayerInventory(player.inventory);
        // Sync keys are logical and contiguous; the native factory receives canonical raw indices.
        // In particular, Backhand's raw offhand slot must never lock an added storage slot.
        for (int i = 0; i < InventoryLayout.of(player.inventory)
            .mainSlots(); i++)
            registrar.itemSlot(
                "player",
                i,
                factory.apply(
                    inventory,
                    InventoryLayout.of(player.inventory)
                        .rawIndex(i))
                    .slotGroup(PlayerSlotGroup.NAME));
        // MUI2 excludes the source group when quick-moving. Hotbar and storage must therefore
        // share one transfer group, or a player stack goes to the other player group before a machine.
        // The widgets and BogoSorter still distinguish hotbar/storage by their canonical slot indices.
        registrar.registerSlotGroup(new PlayerSlotGroup(PlayerSlotGroup.NAME));
        cir.setReturnValue(registrar);
    }
}
