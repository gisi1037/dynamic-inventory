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
        // Subpackages organize integrations; these guards still control whether
        // their mixins load. Moving a class must never change its side/mod guard.
        List<String> mixins = new ArrayList<>();
        if (PackCompatibility.enabled() && loadedMods.contains("bogosorter")) mixins.addAll(
            Arrays.asList(
                "bogosorter.BogoPlayerSlotsMixin",
                "bogosorter.BogoRefillMixin",
                "bogosorter.BogoOpeningActionsMixin"));
        if (PackCompatibility.enabled() && loadedMods.contains("craftingtweaks"))
            mixins.add("craftingtweaks.CraftingTweakOpeningActionsMixin");
        if (PackCompatibility.enabled() && loadedMods.contains("OpenBlocks")) mixins
            .addAll(Arrays.asList("openblocks.OpenBlocksInventoryRestoreMixin", "openblocks.OpenBlocksGraveMixin"));
        if (PackCompatibility.enabled() && loadedMods.contains("TConstruct")) {
            mixins.add("tconstruct.CraftingStationInventoryMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(
                    Arrays.asList(
                        "tconstruct.CraftingStationGuiMixin",
                        "tconstruct.TinkerRecipePositionMixin",
                        "tconstruct.InventoryTabFrameMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("GalacticraftCore"))
            mixins.add("galacticraft.GalacticraftInventoryMixin");
        if (loadedMods.contains("modularui2")) mixins.addAll(
            Arrays.asList(
                "modularui2.Mui2PlayerBindingMixin",
                "modularui2.Mui2PlayerWidgetsMixin",
                "modularui2.Mui2PlayerLookupMixin"));
        if (loadedMods.contains("modularui"))
            mixins.addAll(Arrays.asList("modularui.Mui1PlayerWidgetsMixin", "modularui.Mui1PlayerTransferMixin"));
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            if (PackCompatibility.enabled() && loadedMods.contains("etfuturum")) {
                PackCompatibility.require("etfuturum", "EtFuturum");
                mixins.add("etfuturum.EtFuturumEnchantmentGuiMixin");
            }
            if (PackCompatibility.enabled() && loadedMods.contains("bogosorter"))
                mixins.add("bogosorter.BogoSortButtonMixin");
            if (PackCompatibility.enabled() && loadedMods.contains("craftingtweaks"))
                mixins.add("craftingtweaks.CraftingTweakButtonMixin");
            if (loadedMods.contains("modularui2")) mixins.add("modularui2.Mui2InventoryResizeMixin");
            if (loadedMods.contains("modularui")) mixins.add("modularui.Mui1InventoryResizeMixin");
        }
        if (loadedMods.contains("NotEnoughItems") && cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient())
            mixins.addAll(
                Arrays.asList(
                    "nei.NativeRecipeTransferMixin",
                    "nei.NativeRecipePositionMixin",
                    "nei.NativeRecipeHotspotMixin"));
        return mixins;
    }
}
