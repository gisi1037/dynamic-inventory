package dev.gilbe.dynamicinventory.rc2;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularui.ModularUI;
import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.builder.UIBuilder;
import com.gtnewhorizons.modularui.common.builder.UIInfo;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Uses unmodified public binding helpers, exactly as ordinary GT machines do. Never packaged. */
@Mod(
    modid = "dinvnativefixture",
    name = "Native GUI Binding Tests",
    version = "1.0",
    dependencies = "required-after:dynamicinventory;required-after:modularui;required-after:modularui2")
public final class NativeBindingFixture {

    private static final Factory MUI2 = new Factory();
    private static UIInfo<?, ?> mui1;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GuiManager.registerFactory(MUI2);
        mui1 = UIBuilder.of()
            .container(
                (player, world, x, y, z) -> ModularUI.createContainer(player, context -> window(player), () -> {}))
            .gui((player, world, x, y, z) -> ModularUI.createGuiScreen(player, context -> window(player)))
            .build();
    }

    private static ModularWindow window(EntityPlayer player) {
        ModularWindow.Builder builder = ModularWindow.builder(176, 166)
            .setBackground(ModularUITextures.VANILLA_BACKGROUND);
        com.gtnewhorizons.modularui.api.forge.ItemStackHandler machine = new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(
            2);
        if (!player.worldObj.isRemote) machine.setStackInSlot(1, new ItemStack(Items.diamond, 7));
        com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot input = new com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot(
            machine,
            0);
        input.setFilter(stack -> stack.getItem() == Items.iron_ingot);
        com.gtnewhorizons.modularui.common.widget.SlotWidget inputWidget = new com.gtnewhorizons.modularui.common.widget.SlotWidget(
            input);
        inputWidget.setPos(55, 25);
        com.gtnewhorizons.modularui.common.widget.SlotWidget outputWidget = new com.gtnewhorizons.modularui.common.widget.SlotWidget(
            new com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot(machine, 1).setAccess(true, false));
        outputWidget.setPos(105, 25);
        builder.widget(inputWidget)
            .widget(outputWidget);
        // Set positions through actual widgets; machine state and packets stay native.
        return builder.bindPlayerInventory(player)
            .build();
    }

    @Mod.EventHandler
    public void commands(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {

            @Override
            public String getCommandName() {
                return "dinvnative";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/dinvnative mui1|mui2";
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 4;
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
                String world = player.worldObj.getWorldInfo()
                    .getWorldName();
                if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification")
                    || args.length != 1
                    || player.inventory.getItemStack() != null)
                    throw new IllegalArgumentException("Qualification world and empty cursor required");
                player.closeScreen();
                if (args[0].equals("mui1")) mui1.open(player);
                else if (args[0].equals("mui2")) GuiManager.open(MUI2, new GuiData(player), player);
                else throw new IllegalArgumentException(getCommandUsage(sender));
            }
        });
    }

    private static final class Factory extends AbstractUIFactory<GuiData> {

        private Factory() {
            super("dynamicinventory:native-bindings");
        }

        @Override
        public IGuiHolder<GuiData> getGuiHolder(GuiData data) {
            return new Holder();
        }

        @Override
        public void writeGuiData(GuiData data, PacketBuffer buffer) {}

        @Override
        public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            return new GuiData(player);
        }
    }

    private static final class Holder implements IGuiHolder<GuiData> {

        @Override
        public ModularPanel buildUI(GuiData data, PanelSyncManager sync, UISettings settings) {
            sync.bindPlayerInventory(data.getPlayer());
            sync.registerSlotGroup("machine", 1, 0);
            ItemStackHandler machine = new ItemStackHandler(2);
            if (!data.getPlayer().worldObj.isRemote) machine.setStackInSlot(1, new ItemStack(Items.diamond, 7));
            return ModularPanel.defaultPanel("native_binding_test", 176, 166)
                .child(
                    new ItemSlot().slot(
                        new ModularSlot(machine, 0).filter(stack -> stack.getItem() == Items.iron_ingot)
                            .slotGroup("machine"))
                        .pos(55, 25))
                .child(
                    new ItemSlot().slot(
                        new ModularSlot(machine, 1).canPut(false)
                            .slotGroup("machine"))
                        .pos(105, 25))
                .bindPlayerInventory();
        }
    }
}
