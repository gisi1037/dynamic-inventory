package dev.gilbe.dynamicinventory.mixins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixinsLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    // Keep this bootstrap class at its stable manifest path. Mixin names below are
    // relative to the JSON package and grouped by the integration they support.

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.dynamicinventory.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        boolean backhand = loadedCoreMods.contains("xonin.backhand.coremod.BackhandLoadingPlugin")
            || getClass().getClassLoader()
                .getResource("xonin/backhand/coremod/BackhandLoadingPlugin.class") != null;
        BackhandCompatibility.configure(backhand);
        List<String> mixins = new ArrayList<>(
            Arrays.asList(
                "forge.CapacityHandshakeMixin",
                "forge.CapacityLoginMixin",
                "minecraft.InventoryPlayerMixin",
                "minecraft.EntityPlayerMixin",
                "minecraft.ContainerPlayerMixin",
                "minecraft.SlotInventoryAccess",
                "minecraft.ContainerRegionMixin",
                "minecraft.RegisteredInventoryClickMixin",
                "minecraft.InventoryOpeningServerMixin",
                "minecraft.WindowPacketMixins$Contents",
                "minecraft.WindowPacketMixins$Slot",
                "minecraft.WindowPacketMixins$Property",
                "minecraft.WindowPacketMixins$Transaction",
                "minecraft.WindowPacketMixins$ClientClose",
                "minecraft.WindowPacketMixins$ServerClose",
                "minecraft.VanillaContainerMixin",
                "minecraft.VanillaMerchantOpeningMixin"));
        boolean nei = getClass().getClassLoader()
            .getResource("codechicken/nei/asm/NEICorePlugin.class") != null;
        if (nei) mixins.add("nei.WorkbenchCapacityMixin");
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            mixins.addAll(
                Arrays.asList(
                    "minecraft.PendingInventoryInputMixin",
                    "minecraft.InventoryEditorInputMixin",
                    "minecraft.NativeInventoryLabelMixin",
                    "minecraft.GuiContainerRegionMixin",
                    "minecraft.InventoryOpeningClientMixin",
                    "minecraft.InventoryOpeningPredictionMixin",
                    "minecraft.GuiScreenWheelMixin",
                    "minecraft.GuiBackgroundClipMixin",
                    "minecraft.VanillaRepairGuiMixin",
                    "minecraft.VanillaMerchantGuiMixin",
                    "minecraft.VanillaEnchantmentGuiMixin",
                    "minecraft.VanillaBeaconGuiMixin",
                    "minecraft.PickBlockSlotMixin"));
            if (nei) mixins.add("nei.NeiContainerWheelMixin");
        }
        return mixins;
    }
}
