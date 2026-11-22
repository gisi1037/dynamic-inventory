package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.me.ItemRepo;
import appeng.items.contents.PinList;
import dev.gilbe.dynamicinventory.client.AePinnedPanel;
import dev.gilbe.dynamicinventory.compat.ae2.SeparatePinsRepo;

@Mixin(value = ItemRepo.class, remap = false)
public abstract class AeSeparatePinsRepoMixin implements SeparatePinsRepo {

    @Shadow
    private IAEStack<?>[] pinsRepo;

    @Shadow
    private int visibleCraftingRows, visiblePlayerRows;

    @Unique
    private boolean dynamicinventory$separate;

    @Unique
    private boolean dynamicinventory$useTerminalPins;

    @Override
    public void dynamicinventory$separatePins(boolean separate, boolean useTerminalPins) {
        if (dynamicinventory$separate == separate && dynamicinventory$useTerminalPins == useTerminalPins) return;
        dynamicinventory$separate = separate;
        dynamicinventory$useTerminalPins = useTerminalPins;
        ((ItemRepo) (Object) this).updateView();
    }

    @WrapOperation(
        method = "updateView",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/me/ItemRepo;getPinsCache(ZZ)Lappeng/api/storage/data/IItemList;"))
    private IItemList<IAEStack<?>> dynamicinventory$excludeAllPins(ItemRepo repo, boolean crafting, boolean player,
        Operation<IItemList<IAEStack<?>>> original) {
        if (!dynamicinventory$separate && !AePinnedPanel.hasCustomGrid()) return original.call(repo, crafting, player);
        IItemList<IAEStack<?>> pins = AEApi.instance()
            .storage()
            .createAEStackList();
        if (!dynamicinventory$separate) {
            if (crafting) dynamicinventory$collectVisible(pins, 0, visibleCraftingRows * 9);
            if (player) dynamicinventory$collectVisible(pins, PinList.PLAYER_OFFSET, visiblePlayerRows * 9);
        } else if (dynamicinventory$useTerminalPins) {
            for (IAEStack<?> stack : pinsRepo) if (stack != null) pins.add(stack);
        }
        return pins;
    }

    @Unique
    private void dynamicinventory$collectVisible(IItemList<IAEStack<?>> pins, int start, int size) {
        for (int i = start; i < start + size; i++) {
            int index = AePinnedPanel.inlinePinIndex(i);
            if (index < pinsRepo.length && pinsRepo[index] != null) pins.add(pinsRepo[index]);
        }
    }
}
