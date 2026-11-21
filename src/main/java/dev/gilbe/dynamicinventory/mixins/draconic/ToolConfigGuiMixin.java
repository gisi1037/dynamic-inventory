package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.brandon3055.draconicevolution.client.gui.componentguis.GUIToolConfig;
import com.brandon3055.draconicevolution.client.gui.guicomponents.ComponentConfigItemButton;
import com.brandon3055.draconicevolution.client.utils.guicomponents.ComponentBase;
import com.brandon3055.draconicevolution.client.utils.guicomponents.GUIBase;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** The tool picker uses components rather than the container's hidden slots. */
@Mixin(value = GUIToolConfig.class, remap = false)
public abstract class ToolConfigGuiMixin extends GUIBase {

    @Shadow
    private EntityPlayer player;
    @Unique
    private int dynamicinventory$storageRow;

    protected ToolConfigGuiMixin(net.minecraft.inventory.Container container, int width, int height) {
        super(container, width, height);
    }

    @ModifyArg(
        method = "addDependentComponents",
        at = @At(
            value = "INVOKE",
            target = "Lcom/brandon3055/draconicevolution/client/gui/guicomponents/ComponentConfigItemButton;<init>(IIILnet/minecraft/entity/player/EntityPlayer;)V"),
        index = 2)
    private int dynamicinventory$canonicalPickerSlot(int x, int y, int slot, EntityPlayer owner) {
        InventoryShape shape = InventoryLayout.of(owner.inventory);
        // The left-hand column is equipment. Backhand can already have rewritten its slot argument.
        return x == 6 ? shape.rawMainSlots() + 3 - (y - 7) / 19 : shape.rawIndex(slot);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;
        if (x < 29 || x >= 191 || y < 7 || y >= 61) return;
        InventoryShape shape = InventoryLayout.of(player.inventory);
        int extra = shape.storageSlots() - 27;
        int next = Math.max(0, Math.min((extra + 8) / 9, dynamicinventory$storageRow - Integer.signum(wheel)));
        if (next == dynamicinventory$storageRow) return;
        dynamicinventory$storageRow = next;
        int offset = Math.min(next * 9, extra);
        for (ComponentBase component : collection.getComponents()) {
            if (component instanceof ComponentConfigItemButton button && button.isEnabled()
                && button.getX() >= 29
                && button.getY() < 61) {
                int cell = (button.getX() - 29) / 18 + (button.getY() - 7) / 18 * 9;
                button.slot = shape.rawIndex(9 + offset + cell);
                button.refreshState();
            }
        }
    }
}
