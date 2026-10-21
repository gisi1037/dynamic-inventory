package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor;

/** Own the whole input batch before native/NEI slot dispatch while the editor is modal. */
@Mixin(GuiScreen.class)
public abstract class InventoryEditorInputMixin {

    @Inject(method = "handleInput", at = @At("HEAD"), cancellable = true, order = 900)
    private void dynamicinventory$editLayout(CallbackInfo ci) {
        if (InventoryLayoutEditor.handleInput((GuiScreen) (Object) this)) ci.cancel();
    }
}
