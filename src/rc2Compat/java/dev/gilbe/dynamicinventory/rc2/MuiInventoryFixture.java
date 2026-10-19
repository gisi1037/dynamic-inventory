package dev.gilbe.dynamicinventory.rc2;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;

/** Development-only MUI2 binding fixture; never included in the production jar. */
@Mod(
    modid = "dinvrc2fixture",
    name = "Dynamic Inventory RC-2 Fixture",
    version = "1.0",
    dependencies = "required-after:dynamicinventory;required-after:modularui2")
public final class MuiInventoryFixture {

    private static final InventoryFactory FACTORY = new InventoryFactory();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GuiManager.registerFactory(FACTORY);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {

            @Override
            public String getCommandName() {
                return "dinvmui";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/dinvmui";
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 4;
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
                String world = MinecraftServer.getServer()
                    .worldServerForDimension(0)
                    .getWorldInfo()
                    .getWorldName();
                if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification")) {
                    throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Qualification world");
                }
                if (player.inventory.getItemStack() != null) {
                    throw new IllegalStateException("Clear the cursor before opening the fixture");
                }
                GuiManager.open(FACTORY, new GuiData(player), player);
            }
        });
    }

    private static final class InventoryFactory extends AbstractUIFactory<GuiData> {

        private final InventoryHolder holder = new InventoryHolder();

        private InventoryFactory() {
            super("dynamicinventory:rc2fixture");
        }

        @Override
        public IGuiHolder<GuiData> getGuiHolder(GuiData data) {
            return holder;
        }

        @Override
        public void writeGuiData(GuiData data, PacketBuffer buffer) {
            // The authenticated opening player is the complete context. No item data is copied.
        }

        @Override
        public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            return new GuiData(player);
        }

    }

    private static final class InventoryHolder implements IGuiHolder<GuiData> {

        @Override
        public ModularPanel buildUI(GuiData data, PanelSyncManager sync, UISettings settings) {
            ModularPanel panel = ModularPanel.defaultPanel("expanded_inventory_fixture", 180, 108);
            panel.child(
                IKey.str("Expanded inventory")
                    .asWidget()
                    .pos(8, 6));
            sync.bindPlayerInventory(data.getPlayer());
            // The public factory supplies every configured slot while keeping the hotbar
            // below a responsive storage viewport at every supported capacity.
            panel.child(
                Mui2InventoryWidgets.playerInventory((index, slot) -> slot)
                    .pos(8, 22));
            return panel;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ModularScreen createScreen(GuiData data, ModularPanel panel) {
            return new ModularScreen(DynamicInventory.MODID, panel);
        }
    }
}
