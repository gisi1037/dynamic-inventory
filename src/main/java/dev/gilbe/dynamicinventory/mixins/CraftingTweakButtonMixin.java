package dev.gilbe.dynamicinventory.mixins;

import java.awt.Point;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import dev.gilbe.dynamicinventory.client.NativeButtonOffset;

@Pseudo
@Mixin(targets = "net.blay09.mods.craftingtweaks.client.GuiTweakButton", remap = false)
public abstract class CraftingTweakButtonMixin implements NativeButtonOffset {

    @Shadow
    @Final
    private Point relativePosition;

    @Override
    public Point dynamicinventory$buttonOffset() {
        return relativePosition;
    }
}
