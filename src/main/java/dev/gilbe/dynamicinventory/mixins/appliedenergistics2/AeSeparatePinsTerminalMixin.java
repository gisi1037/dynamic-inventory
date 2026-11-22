package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.me.ItemRepo;
import appeng.helpers.MonitorableAction;
import appeng.items.contents.PinList;
import dev.gilbe.dynamicinventory.client.AePinnedPanel;
import dev.gilbe.dynamicinventory.compat.ae2.SeparatePinsRepo;
import dev.gilbe.dynamicinventory.compat.ae2.TerminalPinsView;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class AeSeparatePinsTerminalMixin implements TerminalPinsView {

    @Shadow
    @Final
    public boolean hasPinHost;
    @Shadow
    @Final
    private ItemRepo repo;
    @Shadow
    private PinsRows craftingPinsRows;
    @Shadow
    private PinsRows playerPinsRows;
    @Shadow
    private GuiImgButton pinsStateButton;

    @Shadow
    protected abstract void reinitalize();

    @Shadow
    protected abstract boolean handleVirtualSlotClick(VirtualMESlot slot, int mouseButton);

    @Invoker("checkTypeFilter")
    public abstract boolean dynamicinventory$checkType(IAEStackType<?> type);

    @Invoker("sendAction")
    public abstract void dynamicinventory$sendAction(MonitorableAction action, IAEStack<?> stack, int custom);

    @Override
    public boolean dynamicinventory$hasPins() {
        return hasPinHost;
    }

    @Override
    public IAEStack<?> dynamicinventory$pin(int index) {
        return repo.getAEPin(index);
    }

    @Override
    public int dynamicinventory$pinSlots(boolean crafting) {
        return (crafting ? craftingPinsRows : playerPinsRows).getSlotCount();
    }

    @Override
    public boolean dynamicinventory$clickPin(int index, int mouseButton) {
        return handleVirtualSlotClick(
            new VirtualMEPinSlot(0, 0, repo, index, this::dynamicinventory$checkType, index < PinList.PLAYER_OFFSET),
            mouseButton);
    }

    @Override
    public void dynamicinventory$scrollPin(int index, int steps) {
        var slot = new VirtualMEPinSlot(
            0,
            0,
            repo,
            index,
            this::dynamicinventory$checkType,
            index < PinList.PLAYER_OFFSET);
        IAEStack<?> stack = slot.getAEStack();
        if (!(stack instanceof IAEItemStack)
            || steps > 0 && Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) return;
        MonitorableAction action = steps > 0 ? MonitorableAction.ROLL_DOWN : MonitorableAction.ROLL_UP;
        for (int i = 0; i < Math.min(64, Math.abs(steps)); i++) dynamicinventory$sendAction(action, stack, -1);
    }

    @Override
    public void dynamicinventory$rebuildPins() {
        reinitalize();
    }

    @Inject(method = "initGui", at = @At("HEAD"), remap = true)
    private void dynamicinventory$setPinMode(CallbackInfo ci) {
        GuiScreen screen = (GuiScreen) (Object) this;
        boolean separate = AePinnedPanel.separates(screen);
        ((SeparatePinsRepo) repo)
            .dynamicinventory$separatePins(separate, separate && AePinnedPanel.usesTerminalPins(screen));
    }

    @Inject(method = "adjustPinsSize", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$keepNativePinPreferences(CallbackInfo ci) {
        if (AePinnedPanel.separates((GuiScreen) (Object) this)) ci.cancel();
    }

    @ModifyExpressionValue(
        method = "initGui",
        at = @At(value = "INVOKE", target = "Lappeng/api/config/PinsRows;ordinal()I", remap = false),
        remap = true)
    private int dynamicinventory$inlineRows(int original) {
        return AePinnedPanel.separates((GuiScreen) (Object) this) ? 0 : original;
    }

    @ModifyArg(
        method = "createPinSection",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/slots/VirtualMEPinSlot;<init>(IILappeng/api/storage/data/IDisplayRepo;ILappeng/client/gui/slots/VirtualMEMonitorableSlot$TypeFilterChecker;Z)V"),
        index = 3)
    private int dynamicinventory$inlineOrder(int index) {
        return AePinnedPanel.inlinePinIndex(index);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), remap = true)
    private void dynamicinventory$keepPinGrid(GuiButton button, CallbackInfo ci) {
        if (button == pinsStateButton) AePinnedPanel.preserveGrid((GuiScreen) (Object) this);
    }

    @ModifyExpressionValue(
        method = "setScrollBar()V",
        at = @At(value = "INVOKE", target = "Lappeng/api/config/PinsRows;ordinal()I"))
    private int dynamicinventory$scrollRows(int original) {
        return AePinnedPanel.separates((GuiScreen) (Object) this) ? 0 : original;
    }
}
