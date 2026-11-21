package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ClientInputGuard;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** The native controls compute a centered origin after vanilla has handled the slot click. */
@Pseudo
@Mixin(
    targets = { "thaumcraft.client.gui.GuiDeconstructionTable", "thaumcraft.client.gui.GuiFocalManipulator",
        "thaumcraft.client.gui.GuiPech", "thaumcraft.client.gui.GuiResearchTable",
        "thaumcraft.client.gui.GuiThaumatorium" },
    remap = false)
public abstract class NativeControlMixin {

    @Inject(method = "func_73864_a(III)V", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(int x, int y, int button, CallbackInfo ci) {
        if (ClientInputGuard.isPending((GuiScreen) (Object) this)) ci.cancel();
    }

    @ModifyVariable(method = "func_73864_a(III)V", at = @At(value = "STORE", ordinal = 0), index = 4)
    private int dynamicinventory$controlLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "func_73864_a(III)V", at = @At(value = "STORE", ordinal = 0), index = 5)
    private int dynamicinventory$controlTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }
}
