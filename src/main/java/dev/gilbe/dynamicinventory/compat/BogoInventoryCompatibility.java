package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.api.ISortingContextBuilder;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import tconstruct.tools.gui.ChestSlot;
import tconstruct.tools.inventory.CraftingStationContainer;

/** Common-side sorting policy: machine groups must not absorb appended canonical player slots. */
public final class BogoInventoryCompatibility {

    private BogoInventoryCompatibility() {}

    public static void initialize() {
        if (!Loader.isModLoaded("TConstruct")) return;
        IBogoSortAPI.getInstance()
            .addCompat(CraftingStationContainer.class, BogoInventoryCompatibility::addChestGroup);
    }

    /** Internal native-role policy, shared with the disposable sorting-context fixture. */
    public static void addChestGroup(Container container, ISortingContextBuilder builder) {
        ContainerInventoryRegion player = ContainerInventoryRegion.of(container);
        List<Slot> chest = new ArrayList<>();
        for (Slot slot : container.inventorySlots) {
            // Bogo's native 46..size range also includes our appended player slots. Use real chest roles.
            if (slot instanceof ChestSlot && (player == null || slot.inventory != player.inventory)) chest.add(slot);
        }
        if (chest.size() < 2) return;
        builder.addSlotGroupOf(chest, Math.min(6, chest.size()))
            .buttonPosSetter((group, position) -> {
                position.setPos(0, 1000);
                for (SlotAccessor slot : group.getSlots()) {
                    if (slot.bogo$getX() >= 0 && slot.bogo$getY() >= 0 && slot.callIsEnabled()) position.setPos(
                        Math.max(position.getX(), slot.bogo$getX() + 17),
                        Math.min(position.getY(), slot.bogo$getY() - 2));
                }
            });
    }
}
