package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class LateMixinsLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.dynamicinventory.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (PackCompatibility.enabled() && loadedMods.contains("bogosorter"))
            mixins.addAll(Arrays.asList("BogoPlayerSlotsMixin", "BogoRefillMixin", "BogoOpeningActionsMixin"));
        if (PackCompatibility.enabled() && loadedMods.contains("craftingtweaks"))
            mixins.add("CraftingTweakOpeningActionsMixin");
        if (PackCompatibility.enabled() && loadedMods.contains("TConstruct")) {
            mixins.add("CraftingStationInventoryMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient()) mixins.addAll(Arrays.asList("CraftingStationGuiMixin", "TinkerRecipePositionMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("GalacticraftCore"))
            mixins.add("GalacticraftInventoryMixin");
        if (loadedMods.contains("modularui2"))
            mixins.addAll(Arrays.asList("Mui2PlayerBindingMixin", "Mui2PlayerWidgetsMixin", "Mui2PlayerLookupMixin"));
        if (loadedMods.contains("modularui"))
            mixins.addAll(Arrays.asList("Mui1PlayerWidgetsMixin", "Mui1PlayerTransferMixin"));
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            if (PackCompatibility.enabled() && loadedMods.contains("bogosorter")) mixins.add("BogoSortButtonMixin");
            if (PackCompatibility.enabled() && loadedMods.contains("craftingtweaks"))
                mixins.add("CraftingTweakButtonMixin");
            if (loadedMods.contains("modularui2")) mixins.add("Mui2InventoryResizeMixin");
            if (loadedMods.contains("modularui")) mixins.add("Mui1InventoryResizeMixin");
        }
        if (loadedMods.contains("NotEnoughItems") && cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) mixins.addAll(Arrays.asList("NativeRecipeTransferMixin", "NativeRecipePositionMixin"));
        return mixins;
    }
}
