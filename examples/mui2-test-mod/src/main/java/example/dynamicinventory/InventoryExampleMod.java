package example.dynamicinventory;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Ordinary Forge addon: no mixins, custom packets, or Dynamic Inventory internals. */
@Mod(
    modid = InventoryExampleMod.ID,
    name = "Dynamic Inventory MUI2 Example",
    version = "1.0.0",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:dynamicinventory@[0.8.0-dev];required-after:modularui2@[2.3.92-1.7.10]")
public final class InventoryExampleMod {

    public static final String ID = "dinvmui2example";
    private static final ExampleGui FACTORY = new ExampleGui();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register on both sides. MUI2 recreates the same panel for the opening player.
        GuiManager.registerFactory(FACTORY);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new OpenCommand());
    }

    private static final class OpenCommand extends CommandBase {

        @Override
        public String getCommandName() {
            return "dinvexample";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvexample";
        }

        @Override
        public int getRequiredPermissionLevel() {
            // Players can only open their own inventory and their own ender chest.
            return 0;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length != 0) throw new WrongUsageException(getCommandUsage(sender));
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            // Open on the server; MUI2 owns the container, opening packet and slot synchronization.
            player.closeScreen();
            GuiManager.open(FACTORY, new GuiData(player), player);
        }
    }
}
