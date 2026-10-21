package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Operator-only first entry point; upgrade items will use the same transaction later. */
public final class InventorySizeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "dinvsize";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvsize <player> <storage-slots>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP player = getPlayer(sender, args[0]);
        int storage = parseIntBounded(sender, args[1], InventoryShape.MIN_STORAGE, InventoryShape.MAX_STORAGE);
        try {
            dev.gilbe.dynamicinventory.upgrades.InventoryUpgrades.setCapacity(player, storage);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            throw new CommandException(refused.getMessage());
        }
        sender.addChatMessage(
            new ChatComponentText(
                "Dynamic Inventory: " + player.getCommandSenderName()
                    + " now has "
                    + storage
                    + " storage slots plus 9 hotbar slots; synchronization may still be pending."));
    }
}
