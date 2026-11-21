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
        if (PackCompatibility.enabled() && loadedMods.contains("adventurebackpack")
            && OptionalInventoryAdapters.reviewedVersion("adventurebackpack")) {
            mixins.add("backpack.AdventureTransferMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient()) mixins.add("backpack.AdventureGuiMixin");
        }
        if (PackCompatibility.enabled() && loadedMods.contains("Backpack")
            && OptionalInventoryAdapters.reviewedVersion("Backpack")) {
            mixins.add("backpack.BackpackTransferMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient()) mixins.addAll(Arrays.asList("backpack.BackpackGuiMixin", "backpack.BackpackLabelMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("Thaumcraft")
            && OptionalInventoryAdapters.reviewedVersion("Thaumcraft")) {
            mixins.add("thaumcraft.WorkbenchTransferMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(
                    Arrays.asList(
                        "thaumcraft.NativeControlMixin",
                        "thaumcraft.ResearchOverlayMixin",
                        "thaumcraft.FocalOverlayMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("EnderIO")
            && loadedMods.contains("endercore")
            && OptionalInventoryAdapters.reviewedVersion("EnderIO")
            && OptionalInventoryAdapters.reviewedVersion("endercore")) {
            mixins.addAll(
                Arrays.asList(
                    "enderio.NativeMergeMixin",
                    "enderio.BaseTransferMixin",
                    "enderio.ConduitContainerMixin",
                    "enderio.CapacitorTransferMixin",
                    "enderio.InventoryPanelSlotsMixin"));
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(
                    Arrays.asList(
                        "enderio.EnderGuiMixin",
                        "enderio.ConduitGuiMixin",
                        "enderio.ConduitTabOriginMixin",
                        "enderio.ConduitSlotVisibilityMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("ExtraUtilities")
            && OptionalInventoryAdapters.reviewedVersion("ExtraUtilities")) {
            mixins.addAll(Arrays.asList("extrautils.NativeMergeMixin", "extrautils.ItemOwnerMixin"));
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(Arrays.asList("extrautils.FilingCabinetGuiMixin", "extrautils.PlayerWidgetMixin"));
        }
        if (loadedMods.contains("NotEnoughItems")
            && OptionalInventoryAdapters.profileVersion("NotEnoughItems", "NotEnoughItems")
            && cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
            mixins.addAll(
                Arrays.asList(
                    "nei.NeiPanelOverlapMixin",
                    "nei.NeiBookmarkBoundsMixin",
                    "nei.NeiPanelSpawningMixin",
                    "nei.NeiPlayerSectionOriginMixin"));
        if (PackCompatibility.enabled() && loadedMods.contains("Forestry")
            && OptionalInventoryAdapters.reviewedVersion("Forestry")) {
            mixins.addAll(
                Arrays.asList(
                    "forestry.ForestrySlotUtilMixin",
                    "forestry.ForestrySyncMixin",
                    "forestry.ForestryOpeningActionsMixin"));
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient()) mixins.addAll(Arrays.asList("forestry.ForestryGuiMixin", "forestry.ForestryLedgerMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("appliedenergistics2")
            && OptionalInventoryAdapters.profileVersion("appliedenergistics2", "Applied-Energistics-2-Unofficial")) {
            mixins.addAll(
                Arrays.asList(
                    "appliedenergistics2.AeContainerMixin",
                    "appliedenergistics2.AeOpeningActionsMixin",
                    "appliedenergistics2.AePlayerInventoryAdaptorMixin"));
            if (IntegrationProfile.selected() == IntegrationProfile.RELEASE_2_9_0)
                mixins.add("appliedenergistics2.AeInterfacePatternTransferMixin");
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(
                    Arrays.asList(
                        "appliedenergistics2.AeGuiInputMixin",
                        "appliedenergistics2.AeSecurityGuiMixin",
                        "appliedenergistics2.AeCellWorkbenchGuiMixin",
                        "appliedenergistics2.AeStorageBusGuiMixin",
                        "appliedenergistics2.AeLevelEmitterGuiMixin",
                        "appliedenergistics2.AeTerminalOriginMixin",
                        "appliedenergistics2.AePatternTerminalGuiMixin",
                        "appliedenergistics2.AeInterfaceTerminalGuiMixin",
                        "appliedenergistics2.AeDiagnosticTerminalGuiMixin"));
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient() && loadedMods.contains("NotEnoughItems"))
                mixins.add("appliedenergistics2.AeTerminalRecipeMixin");
            if (loadedMods.contains("ae2fc") && OptionalInventoryAdapters.reviewedVersion("ae2fc")) {
                mixins.addAll(
                    Arrays.asList(
                        "appliedenergistics2.FluidContainerSyncMixin",
                        "appliedenergistics2.FluidOpeningActionsMixin"));
                if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                    .isClient())
                    mixins.addAll(
                        Arrays.asList(
                            "appliedenergistics2.FluidGuiOriginMixin",
                            "appliedenergistics2.FluidGuiResizeMixin",
                            "appliedenergistics2.FluidInputMixin",
                            "appliedenergistics2.FluidKeyMixin",
                            "appliedenergistics2.FluidLevelTerminalMixin",
                            "appliedenergistics2.FluidMagnetFilterMixin",
                            "appliedenergistics2.FluidLevelMaintainerMixin",
                            "appliedenergistics2.FluidTextFieldMixin",
                            "appliedenergistics2.FluidLineFieldMixin"));
            }
        }
        if (PackCompatibility.enabled() && loadedMods.contains("DraconicEvolution")
            && OptionalInventoryAdapters.reviewedVersion("DraconicEvolution")) {
            mixins.addAll(
                Arrays.asList(
                    "draconic.ToolContainerMixin",
                    "draconic.ReactorContainerMixin",
                    "draconic.InhibitorContainerMixin",
                    "draconic.UpgradeContainerMixin"));
            if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
                .isClient())
                mixins.addAll(
                    Arrays.asList(
                        "draconic.DetectorGuiMixin",
                        "draconic.RefreshingGuiMixin",
                        "draconic.GuiOriginMixin",
                        "draconic.ReactorGuiMixin",
                        "draconic.EmbeddedInventoryArtworkMixin",
                        "draconic.ToolConfigGuiMixin",
                        "draconic.UpgradeGuiMixin"));
        }
        if (PackCompatibility.enabled() && loadedMods.contains("bogosorter")) mixins.addAll(
            Arrays.asList(
                "bogosorter.BogoPlayerSlotsMixin",
                "bogosorter.BogoRefillMixin",
                "bogosorter.BogoOpeningActionsMixin",
                "bogosorter.BogoPanelSortMixin"));
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
                "modularui2.Mui2PlayerSlotTypeMixin",
                "modularui2.Mui2PlayerLookupMixin"));
        if (loadedMods.contains("modularui"))
            mixins.addAll(Arrays.asList("modularui.Mui1PlayerWidgetsMixin", "modularui.Mui1PlayerTransferMixin"));
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            if (PackCompatibility.enabled() && loadedMods.contains("etfuturum")) {
                PackCompatibility.require("etfuturum", "EtFuturum");
                mixins.add("etfuturum.EtFuturumEnchantmentGuiMixin");
            }
            if (PackCompatibility.enabled() && loadedMods.contains("bogosorter")) mixins.addAll(
                Arrays.asList(
                    "bogosorter.BogoSortButtonMixin",
                    "bogosorter.BogoClientPanelSortMixin",
                    "bogosorter.BogoPanelSortButtonMixin"));
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
