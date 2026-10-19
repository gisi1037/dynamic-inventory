package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.api.IPosSetter;
import com.cleanroommc.bogosorter.common.sort.ButtonHandler;
import com.cleanroommc.bogosorter.common.sort.ButtonPos;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;

import baubles.client.gui.GuiBaublesButton;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.OffsetPositioner;
import codechicken.nei.PositionedStack;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.util.EmptyContainer;
import dev.gilbe.dynamicinventory.client.NativeSortButton;
import dev.gilbe.dynamicinventory.client.PlayerInventoryScreen;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Loaded on the client only after the pinned optional APIs have been checked. */
public final class PackClientCompatibility {

    private PackClientCompatibility() {}

    /** Mirror NEI's wheel-transfer eligibility; NEI retains ownership of the actual transfer. */
    public static boolean usesItemWheelTransfer(GuiContainer gui) {
        return NEIClientConfig.isEnabled() && NEIClientConfig.isMouseScrollTransferEnabled()
            && !GuiInfo.hasCustomSlots(gui)
            && !(gui.inventorySlots instanceof EmptyContainer);
    }

    public static void initialize() {
        IBogoSortAPI.getInstance()
            .addPlayerSortButtonPosition(net.minecraft.inventory.ContainerPlayer.class, (group, position) -> {
                if (Minecraft.getMinecraft().currentScreen instanceof PlayerInventoryScreen screen) {
                    position.setTopLeft();
                    position.setPos(screen.sortButtonX(), screen.sortButtonY());
                    position.setEnabled(!screen.layoutSettingsOpen());
                } else {
                    IPosSetter.TOP_RIGHT_HORIZONTAL.setButtonPos(group, position);
                }
            });
        API.registerGuiOverlay(PlayerInventoryScreen.class, "crafting2x2", stacks -> {
            int offset = Minecraft.getMinecraft().currentScreen instanceof PlayerInventoryScreen screen
                ? screen.craftingOffsetX()
                : 63;
            int offsetY = Minecraft.getMinecraft().currentScreen instanceof PlayerInventoryScreen screen
                ? screen.craftingOffsetY()
                : 16;
            return new OffsetPositioner(offset, offsetY).positionStacks(stacks);
        });
        API.registerGuiOverlayHandler(PlayerInventoryScreen.class, new CraftingOverlay(), "crafting2x2");
        API.registerNEIGuiHandler(new INEIGuiHandler() {

            @Override
            public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData visibility) {
                if (gui instanceof PlayerInventoryScreen screen && screen.layoutSettingsOpen())
                    visibility.showNEI = false;
                return visibility;
            }
        });
    }

    public static GuiButton baublesButton(int x, int y) {
        return new GuiBaublesButton(109, x, y, 10, 10, net.minecraft.client.resources.I18n.format("button.baubles")) {

            @Override
            public int getHoverState(boolean hovered) {
                // The native renderer treats every state except 1 as hovered,
                // including disabled state 0. That draws a stray tooltip over slots.
                return enabled && hovered ? 2 : 1;
            }
        };
    }

    public static void positionSortButtons(PlayerInventoryScreen screen, List<GuiButton> buttons, int left, int top) {
        positionSortButtons(
            buttons,
            left,
            top,
            screen.sortButtonX(),
            screen.sortButtonY(),
            !screen.layoutSettingsOpen());
    }

    public static void positionSortButtons(List<GuiButton> buttons, int left, int top, int x, int y, boolean enabled) {
        positionSortButtons(buttons, left, top, x, y, enabled, true);
    }

    public static void positionChestSortButtons(List<GuiButton> buttons, int left, int top, int x, int y) {
        positionSortButtons(buttons, left, top, x, y, true, false);
    }

    private static void positionSortButtons(List<GuiButton> buttons, int left, int top, int x, int y, boolean enabled,
        boolean player) {
        ButtonHandler.SortButton sort = null;
        ButtonHandler.SortButton settings = null;
        SlotGroup selected = null;
        for (GuiButton button : buttons) {
            if (!(button instanceof ButtonHandler.SortButton nativeButton) || !(button instanceof NativeSortButton))
                continue;
            SlotGroup group = ((NativeSortButton) button).dynamicinventory$sortGroup();
            if (group.isPlayerInventory() != player || group.isHotbar()) continue;
            if (!player && (group.isEmpty() || !(group.getSlots()
                .get(0) instanceof tconstruct.tools.gui.ChestSlot))) continue;
            if (selected == null) selected = group;
            if (group != selected) continue;
            if (button.id == 394658246) sort = nativeButton;
            if (button.id == 394658247) settings = nativeButton;
        }
        if (sort == null || settings == null) return;
        // IDs repeat for every inventory. Match the pair by group identity before applying its own header anchor.
        ButtonPos position = new ButtonPos();
        position.setTopLeft();
        position.setPos(x, y);
        position.applyPos(left, top, sort, settings);
        sort.enabled = settings.enabled = enabled;
    }

    public static List<String> baubleTooltip(Slot slot) {
        if (!(slot instanceof baubles.common.container.SlotBauble bauble) || slot.getHasStack()
            || !baubles.common.BaublesConfig.displayTooltipOnHover) return java.util.Collections.emptyList();
        List<String> lines = new ArrayList<>();
        lines.add(net.minecraft.client.resources.I18n.format("slot." + bauble.getSlotType()));
        net.minecraft.item.ItemStack held = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (held != null) lines.add(
            net.minecraft.client.resources.I18n
                .format(slot.isItemValid(held) ? "tooltip.fitsInSlot" : "tooltip.doesNotFitInSlot"));
        return lines;
    }

    private static boolean ordinary(Slot slot, GuiContainer gui) {
        return slot.inventory instanceof InventoryPlayer && slot.inventory == gui.mc.thePlayer.inventory
            && InventoryLayout.ordinaryIndex(slot.getSlotIndex()) >= 0;
    }

    private static final class CraftingOverlay extends DefaultOverlayHandler {

        private void align(GuiContainer gui) {
            offsetx = ((PlayerInventoryScreen) gui).craftingOffsetX();
            offsety = ((PlayerInventoryScreen) gui).craftingOffsetY();
        }

        @Override
        public boolean canMoveFrom(Slot slot, GuiContainer gui) {
            return ordinary(slot, gui);
        }

        @Override
        public boolean canFillCraftingGrid(GuiContainer gui, IRecipeHandler handler, int recipe) {
            if (!((PlayerInventoryScreen) gui).controlsAvailable()) return false;
            List<PositionedStack> ingredients = handler.getIngredientStacks(recipe);
            return !ingredients.isEmpty() && ingredients.stream()
                .allMatch(stack -> stack.relx >= 25 && stack.relx <= 43 && stack.rely >= 6 && stack.rely <= 24);
        }

        @Override
        protected Set<Slot> getCraftMatrixSlots(GuiContainer gui, IRecipeHandler handler) {
            align(gui);
            return super.getCraftMatrixSlots(gui, handler);
        }

        @Override
        public Slot[][] mapIngredSlots(GuiContainer gui, List<PositionedStack> ingredients) {
            align(gui);
            return super.mapIngredSlots(gui, ingredients);
        }

        @Override
        public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipe, int multiplier) {
            if (!canFillCraftingGrid(gui, handler, recipe)) return 0;
            align(gui);
            return super.transferRecipe(gui, handler, recipe, multiplier);
        }

        @Override
        public List<ItemOverlayState> presenceOverlay(GuiContainer gui, IRecipeHandler handler, int recipe) {
            return NEIClientUtils.presenceOverlay(
                handler.getIngredientStacks(recipe),
                gui.inventorySlots.inventorySlots.stream()
                    .filter(slot -> ordinary(slot, gui) && slot.getHasStack() && slot.canTakeStack(gui.mc.thePlayer))
                    .map(
                        slot -> slot.getStack()
                            .copy())
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }
}
