package dev.gilbe.dynamicinventory.mixins.cosmeticarmor;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.gilbe.dynamicinventory.client.PlayerInventoryScreen;

/** Keep Cosmetic Armor's native controls, tooltips, and packet handling on the replacement inventory. */
@Pseudo
@Mixin(targets = "lain.mods.cos.client.GuiEvents", remap = false)
public abstract class InventoryControlsMixin {

    @Definition(id = "GuiInventory", type = GuiInventory.class)
    @Expression("? instanceof GuiInventory")
    @ModifyExpressionValue(method = "guiPostInit", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean dynamicinventory$init(boolean vanilla, GuiScreenEvent.InitGuiEvent.Post event) {
        return vanilla || event.gui instanceof PlayerInventoryScreen;
    }

    @WrapOperation(
        method = "guiPostInit",
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean dynamicinventory$attach(List<?> buttons, Object entry, Operation<Boolean> original,
        @Local(argsOnly = true) GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof PlayerInventoryScreen screen && entry instanceof GuiButton button
            && !screen.attachCosmeticArmorButton(button)) return false;
        return original.call(buttons, entry);
    }

    @Definition(id = "GuiInventory", type = GuiInventory.class)
    @Expression("? instanceof GuiInventory")
    @ModifyExpressionValue(method = "guiPostAction", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean dynamicinventory$action(boolean vanilla, GuiScreenEvent.ActionPerformedEvent.Post event) {
        return vanilla
            || event.gui instanceof PlayerInventoryScreen screen && screen.isCosmeticArmorButton(event.button);
    }

    @Definition(id = "GuiInventory", type = GuiInventory.class)
    @Expression("? instanceof GuiInventory")
    @ModifyExpressionValue(method = "guiPostDraw", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean dynamicinventory$tooltip(boolean vanilla, GuiScreenEvent.DrawScreenEvent.Post event) {
        return vanilla || event.gui instanceof PlayerInventoryScreen;
    }
}
