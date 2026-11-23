package dev.gilbe.dynamicinventory.mixins.minecraft;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.village.MerchantRecipeList;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Trade previews are drawn after the base container has restored its outer bounds. */
@Mixin(GuiMerchant.class)
public abstract class VanillaMerchantGuiMixin {

    @Shadow
    private int field_147041_z;

    @Unique
    private boolean dynamicinventory$selectionNeedsSync;

    @Unique
    private int dynamicinventory$previousSelection;

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(GuiButton button, CallbackInfo ci) {
        dynamicinventory$previousSelection = field_147041_z;
        if (((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !InventoryUi.isOpeningReady(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    }

    @Inject(
        method = "actionPerformed",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/GuiMerchant;field_147041_z:I",
            opcode = Opcodes.GETFIELD,
            ordinal = 2),
        cancellable = true)
    private void dynamicinventory$limitTradeNavigation(GuiButton button, CallbackInfo ci) {
        GuiMerchant gui = (GuiMerchant) (Object) this;
        MerchantRecipeList recipes = gui.func_147035_g()
            .getRecipes(Minecraft.getMinecraft().thePlayer);
        if (recipes == null || recipes.isEmpty()) {
            field_147041_z = dynamicinventory$previousSelection;
            ci.cancel();
            return;
        }
        // After either arrow changes the index, clamp before vanilla reads the index for the container.
        field_147041_z = dynamicinventory$validSelection(recipes.size());
        if (field_147041_z == dynamicinventory$previousSelection && !dynamicinventory$selectionNeedsSync) {
            ci.cancel();
            return;
        }
        dynamicinventory$selectionNeedsSync = false; // Vanilla sends the corrected selection below.
    }

    @ModifyExpressionValue(
        method = { "updateScreen", "drawGuiContainerBackgroundLayer", "drawScreen" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/IMerchant;getRecipes(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/village/MerchantRecipeList;"))
    private MerchantRecipeList dynamicinventory$validateTradeSelection(MerchantRecipeList recipes) {
        if (recipes == null || recipes.isEmpty()) return recipes;
        int selection = dynamicinventory$validSelection(recipes.size());
        if (selection == field_147041_z && !dynamicinventory$selectionNeedsSync) return recipes;
        GuiMerchant gui = (GuiMerchant) (Object) this;
        ContainerMerchant container = (ContainerMerchant) gui.inventorySlots;
        if (selection != field_147041_z) {
            field_147041_z = selection;
            container.setCurrentRecipeIndex(selection);
            dynamicinventory$selectionNeedsSync = true;
        }
        Minecraft client = Minecraft.getMinecraft();
        var player = client.thePlayer;
        if (dynamicinventory$selectionNeedsSync && client.currentScreen == gui
            && player != null
            && player.openContainer == container
            && InventoryUi.isOpeningReady(player)
            && client.getNetHandler() != null) {
            client.getNetHandler()
                .addToSendQueue(
                    new C17PacketCustomPayload(
                        "MC|TrSel",
                        ByteBuffer.allocate(4)
                            .putInt(selection)
                            .array()));
            dynamicinventory$selectionNeedsSync = false;
        }
        return recipes;
    }

    @Unique
    private int dynamicinventory$validSelection(int count) {
        return Math.max(0, Math.min(field_147041_z, count - 1));
    }

    // Vanilla drawScreen stores the recipe list in local 4 and its native origin in locals 5/6.
    // Change only these preview coordinates; the base container still draws and hits real slots normally.
    @ModifyVariable(method = "drawScreen", at = @At("STORE"), index = 5, require = 1, allow = 1)
    private int dynamicinventory$previewLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "drawScreen", at = @At("STORE"), index = 6, require = 1, allow = 1)
    private int dynamicinventory$previewTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }

    @ModifyArgs(
        method = "drawScreen",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMerchant;func_146978_c(IIIIII)Z"),
        require = 3,
        allow = 3)
    private void dynamicinventory$previewHover(Args args) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            // Preserve vanilla's hover padding while compensating for its outer guiLeft/guiTop subtraction.
            args.set(4, (Integer) args.get(4) - panel.headerOffsetX());
            args.set(5, (Integer) args.get(5) - panel.headerOffsetY());
        }
    }
}
