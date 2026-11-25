package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ThaumcraftCompatibility;

@Pseudo
@Mixin(targets = ThaumcraftCompatibility.TWEAKS_GUI, remap = false)
public abstract class ResearchTweaksGuiMixin {

    @Shadow
    @Final
    @Mutable
    private List<?> backgrounds;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$researchBackground(CallbackInfo ci) {
        GuiContainer gui = (GuiContainer) (Object) this;
        if (backgrounds.size() > 1 && InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) != null
            && backgrounds.get(1)
                .getClass()
                .getName()
                .equals("elan.tweaks.common.gui.component.texture.TextureBackgroundUIComponent")) {
            // The factory supplies the table and the old player sheet first, followed by live overlays.
            List<?> components = new ArrayList<>(backgrounds);
            components.remove(1);
            backgrounds = components;
        }
    }

    @ModifyExpressionValue(
        method = "getUiScreenOrigin",
        require = 1,
        at = { @At(value = "FIELD", target = "Lelan/tweaks/common/gui/ComposableContainerGui;field_147003_i:I"),
            @At(value = "FIELD", target = "Lelan/tweaks/common/gui/ComposableContainerGui;guiLeft:I") })
    private int dynamicinventory$researchLeft(int left) {
        NativeInventoryPanel panel = panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyExpressionValue(
        method = "getUiScreenOrigin",
        require = 1,
        at = { @At(value = "FIELD", target = "Lelan/tweaks/common/gui/ComposableContainerGui;field_147009_r:I"),
            @At(value = "FIELD", target = "Lelan/tweaks/common/gui/ComposableContainerGui;guiTop:I") })
    private int dynamicinventory$researchTop(int top) {
        NativeInventoryPanel panel = panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }

    @Unique
    private NativeInventoryPanel panel() {
        return ((ContainerPanelAccess) this).dynamicinventory$panel();
    }
}
