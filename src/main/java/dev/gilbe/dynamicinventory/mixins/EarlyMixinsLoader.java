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
                "CapacityHandshakeMixin",
                "CapacityLoginMixin",
                "InventoryPlayerMixin",
                "EntityPlayerMixin",
                "ContainerPlayerMixin",
                "SlotInventoryAccess",
                "ContainerRegionMixin",
                "RegisteredInventoryClickMixin",
                "InventoryOpeningServerMixin",
                "WindowPacketMixins$Contents",
                "WindowPacketMixins$Slot",
                "WindowPacketMixins$Property",
                "WindowPacketMixins$Transaction",
                "WindowPacketMixins$ClientClose",
                "WindowPacketMixins$ServerClose",
                "VanillaContainerMixin"));
        boolean nei = getClass().getClassLoader()
            .getResource("codechicken/nei/asm/NEICorePlugin.class") != null;
        if (nei) mixins.add("WorkbenchCapacityMixin");
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) {
            mixins.addAll(
                Arrays.asList(
                    "PendingInventoryInputMixin",
                    "GuiContainerRegionMixin",
                    "InventoryOpeningClientMixin",
                    "InventoryOpeningPredictionMixin",
                    "GuiScreenWheelMixin",
                    "GuiBackgroundClipMixin",
                    "PickBlockSlotMixin"));
            if (nei) mixins.add("NeiContainerWheelMixin");
        }
        return mixins;
    }
}
