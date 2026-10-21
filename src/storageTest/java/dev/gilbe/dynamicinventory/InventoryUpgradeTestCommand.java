package dev.gilbe.dynamicinventory;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

import baubles.api.BaublesApi;
import baubles.api.IBauble;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.qualification.QualificationCommand;
import dev.gilbe.dynamicinventory.upgrades.InventoryUpgrades;

/** Disposable runtime fixture only. Never included in the production artifact. */
public final class InventoryUpgradeTestCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "dinvupgradetest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvupgradetest <state|equip|remove|force-remove>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String world = player.worldObj.getWorldInfo()
            .getWorldName();
        if (!world.startsWith("DI-Baseline-Upgrades-") || !world.endsWith("Qualification"))
            throw new IllegalArgumentException("Disposable upgrades qualification world required");
        JsonObject report = new JsonObject();
        try {
            if (args.length != 1) throw new IllegalArgumentException(getCommandUsage(sender));
            if (args[0].equals("admin-refusal")) {
                // An explicit same-total command must not rewrite the permanent base while the
                // peer agreement is pending (especially when the belt bonus is capped at 216).
                net.minecraft.nbt.NBTTagCompound before = (net.minecraft.nbt.NBTTagCompound) player.getEntityData()
                    .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .copy();
                ((dev.gilbe.dynamicinventory.container.ExpandedContainerAccess) player.inventoryContainer)
                    .dynamicinventory$removeListener(player);
                dev.gilbe.dynamicinventory.network.ServerInventoryResizeGate.of(player)
                    .synchronize(player);
                boolean refused = false;
                try {
                    InventoryUpgrades.setCapacity(
                        player,
                        dev.gilbe.dynamicinventory.inventory.InventoryLayout.of(player.inventory)
                            .storageSlots());
                } catch (IllegalStateException expected) {
                    refused = true;
                }
                if (!refused || !before.equals(
                    player.getEntityData()
                        .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)))
                    throw new AssertionError("Rejected command changed permanent upgrade ownership");
                report.addProperty("adminRefusalPreservesBase", true);
            }
            if (!args[0].equals("state") && !args[0].equals("admin-refusal")) {
                if (player.openContainer != player.inventoryContainer || player.inventory.getItemStack() != null)
                    throw new IllegalStateException("Close other hosts and clear cursor first");
                ItemStack belt = new ItemStack(InventoryUpgrades.belt);
                Slot target = null;
                for (Object entry : player.inventoryContainer.inventorySlots) {
                    Slot slot = (Slot) entry;
                    if (slot.inventory == BaublesApi.getBaubles(player) && ((slot.getHasStack() && slot.getStack()
                        .getItem() == InventoryUpgrades.belt) || slot.isItemValid(belt))) {
                        target = slot;
                        break;
                    }
                }
                if (target == null) throw new IllegalStateException("No actual Baubles belt slot");
                if (args[0].equals("equip")) {
                    if (target.getHasStack() || !((IBauble) belt.getItem()).canEquip(belt, player))
                        throw new IllegalStateException("Belt slot already occupied / equip refused");
                    target.putStack(belt);
                } else if (args[0].equals("remove") || args[0].equals("force-remove")) {
                    report.addProperty("canRemove", target.canTakeStack(player));
                    if (args[0].equals("force-remove") || target.canTakeStack(player)) target.putStack(null);
                } else throw new IllegalArgumentException("Unknown fixture action");
            }
            report.addProperty("success", true);
            report.addProperty(
                "savedUpgrades",
                player.getEntityData()
                    .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .getCompoundTag("dynamicinventoryUpgrades")
                    .toString());
            report.add("inventory", BaselineDiagnostics.snapshot(player, "server", "upgrades"));
        } catch (Throwable error) {
            report.addProperty("success", false);
            report.addProperty("failure", error.toString());
        }
        try {
            QualificationCommand.write(player, "upgrades-" + args[0], report);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
