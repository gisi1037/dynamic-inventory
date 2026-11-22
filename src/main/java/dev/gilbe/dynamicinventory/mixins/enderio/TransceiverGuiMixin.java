package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "crazypants.enderio.machine.transceiver.gui.GuiTransceiver", remap = false)
public abstract class TransceiverGuiMixin {

    @ModifyVariable(method = { "keyTyped", "func_73869_a" }, at = @At("HEAD"), argsOnly = true, index = 2)
    private int dynamicinventory$inventoryKey(int code) {
        return code == Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode() ? Keyboard.KEY_ESCAPE : code;
    }
}
