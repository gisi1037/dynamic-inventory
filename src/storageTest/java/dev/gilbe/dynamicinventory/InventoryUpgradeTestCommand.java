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
            if (args.length < 1 || args.length > 2) throw new IllegalArgumentException(getCommandUsage(sender));
            if (args[0].equals("recipes")) {
                java.util.Set<net.minecraft.item.Item> outputs = new java.util.HashSet<>();
                java.util.Collections.addAll(outputs, InventoryUpgrades.belts);
                outputs.add(InventoryUpgrades.creativeSizer);
                int recipes = 0;
                for (Object object : net.minecraft.item.crafting.CraftingManager.getInstance()
                    .getRecipeList()) {
                    net.minecraft.item.crafting.IRecipe recipe = (net.minecraft.item.crafting.IRecipe) object;
                    ItemStack output = recipe.getRecipeOutput();
                    if (output == null || !outputs.contains(output.getItem())) continue;
                    if (!(recipe instanceof net.minecraftforge.oredict.ShapedOreRecipe))
                        throw new AssertionError("Unexpected recipe type");
                    for (Object ingredient : ((net.minecraftforge.oredict.ShapedOreRecipe) recipe).getInput())
                        if (ingredient instanceof java.util.List && ((java.util.List<?>) ingredient).isEmpty())
                            throw new AssertionError("Unresolved recipe ore ingredient");
                    recipes++;
                }
                if (recipes != 6) throw new AssertionError("Expected six placeholder recipes, got " + recipes);
                report.addProperty("placeholderRecipes", recipes);
            }
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
            if (!args[0].equals("state") && !args[0].equals("admin-refusal") && !args[0].equals("recipes")) {
                if (player.openContainer != player.inventoryContainer || player.inventory.getItemStack() != null)
                    throw new IllegalStateException("Close other hosts and clear cursor first");
                int tier = args.length == 2 ? Integer.parseInt(args[1]) : 0;
                if (tier < 0 || tier >= InventoryUpgrades.belts.length)
                    throw new IllegalArgumentException("Unknown belt tier");
                ItemStack belt = new ItemStack(InventoryUpgrades.belts[tier]);
                Slot target = null;
                for (Object entry : player.inventoryContainer.inventorySlots) {
                    Slot slot = (Slot) entry;
                    if (slot.inventory == BaublesApi.getBaubles(player) && ((slot.getHasStack() && slot.getStack()
                        .getItem() instanceof baubles.api.IBauble
                        && java.util.Arrays.asList(InventoryUpgrades.belts)
                            .contains(
                                slot.getStack()
                                    .getItem()))
                        || slot.isItemValid(belt))) {
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
