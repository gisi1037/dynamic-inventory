package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.PanelSettingsOverlay;
import dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor;

/** Own the whole input batch before native/NEI slot dispatch while the editor is modal. */
@Mixin(GuiScreen.class)
public abstract class InventoryEditorInputMixin {

    @Inject(method = "handleInput", at = @At("HEAD"), cancellable = true, order = 900)
    private void dynamicinventory$editLayout(CallbackInfo ci) {
        if (PanelSettingsOverlay.handleInput((GuiScreen) (Object) this)
            || InventoryLayoutEditor.handleInput((GuiScreen) (Object) this)) ci.cancel();
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true, order = 900)
    private void dynamicinventory$panelKeys(CallbackInfo ci) {
        if (PanelSettingsOverlay.handleKeyboard((GuiScreen) (Object) this)) ci.cancel();
    }
}
