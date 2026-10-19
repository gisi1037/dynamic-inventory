package dev.gilbe.dynamicinventory.diagnostics;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;

public class BaselineCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "dinvbaseline";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvbaseline";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        BaselineDiagnostics.capture(player, "not available on logical server", "server-command", true);
    }
}
