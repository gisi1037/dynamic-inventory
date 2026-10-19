package dev.gilbe.dynamicinventory.qualification;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;

import com.cleanroommc.bogosorter.ClientEventHandler;
import com.cleanroommc.bogosorter.common.network.CRefill;
import com.cleanroommc.bogosorter.common.network.NetworkHandler;
import com.cleanroommc.bogosorter.common.sort.ButtonHandler;
import com.cleanroommc.bogosorter.common.sort.GuiSortingContext;
import com.cleanroommc.bogosorter.mixins.early.minecraft.GuiScreenAccessor;
import com.google.gson.JsonObject;

import baubles.common.container.SlotBauble;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.RecipeInfo;
import codechicken.nei.recipe.ShapedRecipeHandler;
import dev.gilbe.dynamicinventory.client.PlayerInventoryScreen;

/** Invoked on the actual Minecraft client thread by the local test harness. */
public final class PackClientFixtures {

    private PackClientFixtures() {}

    public static void run(String action) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        // JDI invokes actions without LWJGL's Forge input event. Use Bogo's normal
        // public input path, which enables its temporary client slot-take guard.
        if (mc.currentScreen instanceof GuiContainer gui) ClientEventHandler.handleInput(gui, false);
        JsonObject report = new JsonObject();
        if (action.equals("check")) {
            PlayerInventoryScreen screen = (PlayerInventoryScreen) mc.currentScreen;
            check(RecipeInfo.hasDefaultOverlay(screen, "crafting2x2"), "2x2 ghost overlay registered");
            check(RecipeInfo.hasOverlayHandler(screen, "crafting2x2"), "2x2 ingredient transfer registered");
            check(
                GuiSortingContext.create(screen.inventorySlots)
                    .getPlayerSlotGroup()
                    .getSlots()
                    .size() == 54,
                "54 ordinary storage slots on the client");
            List<GuiButton> buttons = ((GuiScreenAccessor) (Object) screen).getButtonList();
            check(
                buttons.stream()
                    .filter(button -> button instanceof ButtonHandler.SortButton)
                    .count() == 2,
                "Sort/config buttons survive initialization and layout changes");
            check(
                buttons.stream()
                    .filter(button -> button.id == 109)
                    .count() == 1,
                "One Baubles access button");
            com.cleanroommc.bogosorter.mixins.early.minecraft.GuiContainerAccessor geometry = (com.cleanroommc.bogosorter.mixins.early.minecraft.GuiContainerAccessor) (Object) screen;
            for (GuiButton button : buttons) {
                if (!(button instanceof ButtonHandler.SortButton) && button.id != 100 && button.id != 109) continue;
                int x = button.xPosition - geometry.getGuiLeft();
                int y = button.yPosition - geometry.getGuiTop();
                for (Slot slot : screen.inventorySlots.inventorySlots) {
                    if (slot.xDisplayPosition < 0) continue;
                    check(
                        x + button.width <= slot.xDisplayPosition - 1 || x >= slot.xDisplayPosition + 17
                            || y + button.height <= slot.yDisplayPosition - 1
                            || y >= slot.yDisplayPosition + 17,
                        "Header control does not overlap a real slot");
                }
            }
            ShapedRecipeHandler recipe = recipe();
            ArrayList<PositionedStack> ghost = new ArrayList<>();
            ghost.add(new PositionedStack(new ItemStack(Blocks.planks), 25, 6));
            RecipeInfo.getStackPositioner(screen, "crafting2x2")
                .positionStacks(ghost);
            Slot input = screen.inventorySlots.getSlot(1);
            check(
                ghost.get(0).relx == input.xDisplayPosition && ghost.get(0).rely == input.yDisplayPosition,
                "Ghost overlay follows the actual crafting slot after width changes");
            IOverlayHandler overlay = RecipeInfo.getOverlayHandler(screen, "crafting2x2");
            check(overlay.canFillCraftingGrid(screen, recipe, 0), "Crafting transfer available when idle");
            check(
                overlay.presenceOverlay(screen, recipe, 0)
                    .stream()
                    .allMatch(state -> state.isPresent()),
                "NEI finds crafting ingredients in extra storage");
            report.addProperty("storageSlots", 54);
            report.addProperty("buttonCount", buttons.size());
            report.addProperty("craftingOffsetX", screen.craftingOffsetX());
        } else if (action.equals("refill")) {
            NetworkHandler.sendToServer(new CRefill(new ItemStack(Blocks.planks), 0, false, false));
        } else if (action.equals("sort")) {
            PlayerInventoryScreen screen = (PlayerInventoryScreen) mc.currentScreen;
            check(
                ClientEventHandler.sort(screen.inventorySlots, null, 72),
                "Actual BogoSorter client sort packet sent from last extra");
        } else if (action.equals("nei-transfer")) {
            PlayerInventoryScreen screen = (PlayerInventoryScreen) mc.currentScreen;
            int transferred = RecipeInfo.getOverlayHandler(screen, "crafting2x2")
                .transferRecipe(screen, recipe(), 0, 1);
            check(transferred == 1, "Actual NEI recipe transfer sends one 2x2 recipe");
        } else
            if (action.equals("baubles-check") || action.equals("inline-check") || action.equals("side-panel-check")) {
                GuiContainer screen = (GuiContainer) mc.currentScreen;
                net.minecraft.inventory.Container container = screen.inventorySlots;
                check(container == mc.thePlayer.openContainer, "Baubles uses its actual synchronized container");
                if (action.equals("inline-check")) {
                    PlayerInventoryScreen expanded = (PlayerInventoryScreen) screen;
                    check(
                        expanded.inlineBaublesFits() && expanded.inlineBaublesOpen(),
                        "Inline equipment panel fits and is expanded");
                    check(
                        container == mc.thePlayer.inventoryContainer,
                        "Inline toggle keeps the persistent player window");
                }
                if (action.equals("side-panel-check")) {
                    PlayerInventoryScreen expanded = (PlayerInventoryScreen) screen;
                    check(expanded.baublesSidePanelOpen(), "Narrow layout shows its equipment side panel");
                    check(
                        container == mc.thePlayer.inventoryContainer && container.windowId == 0,
                        "Side panel preserves the persistent expanded inventory and crafting window");
                    for (Slot visible : container.inventorySlots) {
                        if (visible.xDisplayPosition < 0) continue;
                        for (Slot other : container.inventorySlots) {
                            if (visible == other || other.xDisplayPosition < 0) continue;
                            check(
                                visible.xDisplayPosition + 16 <= other.xDisplayPosition
                                    || other.xDisplayPosition + 16 <= visible.xDisplayPosition
                                    || visible.yDisplayPosition + 16 <= other.yDisplayPosition
                                    || other.yDisplayPosition + 16 <= visible.yDisplayPosition,
                                "Equipment side panel never overlaps another visible slot");
                        }
                    }
                }
                int ring = -1;
                int source = -1;
                for (Slot slot : container.inventorySlots) {
                    if (slot instanceof SlotBauble bauble && bauble.getSlotType()
                        .equals("ring") && ring < 0) ring = slot.slotNumber;
                    if (slot.inventory == mc.thePlayer.inventory && slot.getSlotIndex() == 9) source = slot.slotNumber;
                }
                check(ring >= 0 && source >= 0, "Native Baubles ring and ordinary source slots found");
                for (Slot slot : container.inventorySlots) {
                    if (!(slot instanceof SlotBauble)) continue;
                    slot.getStack();
                    check(
                        slot.inventory == baubles.api.BaublesApi.getBaubles(mc.thePlayer),
                        "Native slot binds the current canonical Baubles inventory");
                }
                report.addProperty("ringSlot", ring);
                report.addProperty("sourceSlot", source);
                Slot slot = container.getSlot(ring);
                if (slot.getHasStack()) {
                    report.addProperty("canTakeRing", slot.canTakeStack(mc.thePlayer));
                    report.addProperty(
                        "wornTicks",
                        slot.getStack()
                            .getTagCompound()
                            .getInteger("wornTicks"));
                }
            } else if (action.equals("held-ui-check")) {
                PlayerInventoryScreen screen = (PlayerInventoryScreen) mc.currentScreen;
                ItemStack held = mc.thePlayer.inventory.getItemStack();
                check(held != null, "A real item is held on the cursor");
                List<GuiButton> buttons = ((GuiScreenAccessor) (Object) screen).getButtonList();
                int sorts = 0;
                for (GuiButton button : buttons) {
                    if (button instanceof ButtonHandler.SortButton) {
                        check(button.visible && button.enabled, "Bogo icons remain renderable while holding an item");
                        sorts++;
                    }
                    if (button.id == 109) {
                        java.lang.reflect.Method hover = button.getClass()
                            .getDeclaredMethod("getHoverState", boolean.class);
                        hover.setAccessible(true);
                        check(
                            (int) hover.invoke(button, false) == 1 && (int) hover.invoke(button, true) == 1,
                            "Disabled native Baubles button never draws its tooltip over invalid equipment slots");
                    }
                }
                check(sorts == 2, "Both native sort/settings icons remain present");
                check(!screen.controlsAvailable(), "Layout and equipment toggles are blocked with a held item");
                Slot invalid = screen.inventorySlots.inventorySlots.stream()
                    .filter(slot -> slot instanceof SlotBauble && slot.xDisplayPosition >= 0 && !slot.isItemValid(held))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No visible invalid equipment target"));
                List<String> tooltip = dev.gilbe.dynamicinventory.compat.PackClientCompatibility.baubleTooltip(invalid);
                check(tooltip.size() == 2, "Invalid equipment has one stable type and validation tooltip");
                report.addProperty("invalidSlot", invalid.slotNumber);
                report.addProperty("tooltip", tooltip.toString());
            } else if (action.equals("client-sync-guard-set")) {
                baubles.common.lib.PlayerHandler.getPlayerBaubles(mc.thePlayer).blockEvents = true;
            } else if (action.equals("client-sync-guard-check")) {
                check(
                    !baubles.common.lib.PlayerHandler.getPlayerBaubles(mc.thePlayer).blockEvents,
                    "Inline menu clears the native transient client event guard");
            } else if (action.equals("offhand-only-check")) {
                PlayerInventoryScreen screen = (PlayerInventoryScreen) mc.currentScreen;
                check(
                    RecipeInfo.getOverlayHandler(screen, "crafting2x2")
                        .presenceOverlay(screen, recipe(), 0)
                        .stream()
                        .noneMatch(state -> state.isPresent()),
                    "NEI does not count equipped offhand planks as ordinary ingredients");
            } else {
                throw new IllegalArgumentException(action);
            }
        report.addProperty("success", true);
        report.addProperty("action", action);
        QualificationCommand.write(mc.thePlayer, "client-pack-" + action, report);
    }

    private static ShapedRecipeHandler recipe() {
        ShapedRecipeHandler handler = new ShapedRecipeHandler();
        ItemStack wood = new ItemStack(Blocks.planks);
        handler.arecipes.add(
            handler.new CachedShapedRecipe(new ShapedRecipes(
                2,
                2,
                new ItemStack[] { wood.copy(), wood.copy(), wood.copy(), wood.copy() },
                new ItemStack(Blocks.crafting_table))));
        return handler;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
