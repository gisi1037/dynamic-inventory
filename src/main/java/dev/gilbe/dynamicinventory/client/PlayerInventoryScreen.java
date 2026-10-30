package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.BaublesInventoryBridge;
import dev.gilbe.dynamicinventory.compat.InventoryTabsCompatibility;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Client-only rearrangement of the persistent server container. Scrolling never moves item stacks. */
public final class PlayerInventoryScreen extends InventoryEffectRenderer {

    private static final int SETTINGS_HEIGHT = 186;

    private InventoryViewLayout view;
    private DetachedChestPanel floating;

    public DetachedChestPanel floating() {
        return settingsOpen ? null : floating;
    }

    private final net.minecraft.entity.player.InventoryPlayer ownerInventory;
    private PlayerHeaderLayout header;
    private BaublesPanelLayout baublesPanel;
    private boolean baublesOpen;
    private boolean preserveHeaderPosition;
    private int tabSpace;
    private int baublesScrollRow;
    private int scrollRow;
    private boolean settingsOpen;
    private boolean hotbarSettings;
    private int hotbarRow;
    private net.minecraft.client.settings.KeyBinding bindingHotbarKey;
    private boolean terminalSettings;
    private boolean scrolling;
    private boolean controlClick;

    private final java.util.List<GuiButton> integrationButtons = new java.util.ArrayList<>();
    private final java.util.Set<GuiButton> ownedButtons = java.util.Collections
        .newSetFromMap(new java.util.IdentityHashMap<GuiButton, Boolean>());
    private GuiButton layoutButton;
    private GuiButton baublesButton;

    public PlayerInventoryScreen(EntityPlayer player) {
        super(player.inventoryContainer);
        ownerInventory = player.inventory;
        allowUserInput = true;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // Match the native Baubles screen: packet synchronization blocks callbacks
        // only during the update, never during subsequent player interactions.
        if (PackCompatibility.enabled()) BaublesInventoryBridge.blockClientEvents(mc.thePlayer, false);
    }

    @Override
    public void setWorldAndResolution(net.minecraft.client.Minecraft minecraft, int width, int height) {
        // The outer Forge initialization will install fresh integration buttons after initGui.
        integrationButtons.clear();
        super.setWorldAndResolution(minecraft, width, height);
    }

    @Override
    public void initGui() {
        int previousLeft = guiLeft, previousTop = guiTop;
        if (floating != null) floating.cancel();
        floating = null;
        // Layout changes call initGui directly, without Forge's outer Post event.
        // Keep buttons installed by other mods so their input/render hooks survive relayout.
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            if (!ownedButtons.contains(button) && !InventoryTabsCompatibility.registeredTab(button)
                && !integrationButtons.contains(button)) integrationButtons.add(button);
        }
        ownedButtons.clear();
        layoutButton = null;
        baublesButton = null;
        tabSpace = InventoryTabsCompatibility.topSpace();
        view = new InventoryViewLayout(
            width,
            height - tabSpace,
            InventoryViewSettings.columns,
            InventoryViewSettings.rows,
            Math.max(
                1,
                InventoryLayout.of(ownerInventory)
                    .storageSlots() - dev.gilbe.dynamicinventory.inventory.HotbarBanks.reserved(ownerInventory)));
        int equipmentSlots = PackCompatibility.enabled() ? BaublesInventoryBridge.visibleSlotCount() : 0;
        boolean detachedLayout = PlayerPanelProfiles.resolve(
            PlayerPanelProfiles.key(
                "dynamicinventory:player",
                inventorySlots.getClass()
                    .getName(),
                getClass().getName())).detached;
        header = new PlayerHeaderLayout(view.width, equipmentSlots);
        int baseLeft = preserveHeaderPosition ? previousLeft : (width - view.width) / 2;
        baublesPanel = baublesOpen && !header.inlineBaublesFits && equipmentSlots > 0
            ? new BaublesPanelLayout(
                height - tabSpace,
                equipmentSlots,
                detachedLayout ? PlayerHeaderLayout.DETACHED_HEIGHT : height - tabSpace,
                Math.max(44, width - baseLeft - view.width - 4))
            : null;
        xSize = view.width + (baublesPanel == null ? 0 : baublesPanel.width + 4);
        ySize = tabSpace + Math.max(view.height, baublesPanel == null ? 0 : baublesPanel.height);
        header = new PlayerHeaderLayout(view.width, equipmentSlots);
        super.initGui();
        if (baublesPanel != null) guiLeft = baseLeft;
        if (preserveHeaderPosition) {
            guiLeft = previousLeft;
            guiTop = previousTop;
            preserveHeaderPosition = false;
        }
        guiLeft = Math.max(0, Math.min(guiLeft, width - xSize));
        guiTop = Math.max(0, Math.min(guiTop, height - ySize));
        scrollRow = Math.max(0, Math.min(scrollRow, view.maxScroll()));
        baublesScrollRow = baublesPanel == null ? 0 : Math.min(baublesScrollRow, baublesPanel.maxScroll());
        positionSlots();
        buttonList.clear();
        if (!settingsOpen) {
            layoutButton = new LayoutIconButton(guiLeft + header.layoutButtonX, guiTop + tabSpace + 6);
            addOwnedButton(layoutButton);
            if (PackCompatibility.enabled()) {
                baublesButton = PackClientCompatibility
                    .baublesButton(guiLeft + header.baublesButtonX, guiTop + tabSpace + 8);
                addOwnedButton(baublesButton);
            }
            buttonList.addAll(integrationButtons);
            InventoryTabsCompatibility.addTabs(buttonList, guiLeft, guiTop + tabSpace);
            floating = new DetachedChestPanel(
                this,
                new InventoryPanelLayout.Rect(
                    guiLeft,
                    guiTop + tabSpace,
                    view.width,
                    PlayerHeaderLayout.DETACHED_HEIGHT),
                new InventoryPanelLayout.Rect(guiLeft, guiTop + tabSpace + 64, view.width, view.height - 64),
                2,
                2,
                0,
                2,
                baublesPanel == null ? java.util.Collections.emptyList()
                    : java.util.Collections.singletonList(
                        new InventoryPanelLayout.Rect(
                            guiLeft + view.width + 4,
                            guiTop + tabSpace,
                            baublesPanel.width,
                            baublesPanel.height)));
            if (floating != null) floating.position();
        } else {
            floating = null;
            int x = (width - 172) / 2;
            int y = (height - SETTINGS_HEIGHT) / 2;
            if (hotbarSettings) {
                int count = dev.gilbe.dynamicinventory.inventory.HotbarBanks.count(mc.thePlayer);
                GuiButton minus = new GuiButton(125, x + 6, y + 25, 24, 20, "-");
                GuiButton plus = new GuiButton(126, x + 142, y + 25, 24, 20, "+");
                minus.enabled = count > 0;
                plus.enabled = count < HotbarControls.maximumRows();
                addOwnedButton(minus);
                addOwnedButton(plus);
                addOwnedButton(
                    new GuiButton(
                        127,
                        x + 6,
                        y + 53,
                        160,
                        20,
                        InventoryViewSettings.hotbarPanelVisible ? "Hotbar panel: Shown" : "Hotbar panel: Hidden"));
                addOwnedButton(
                    new GuiButton(
                        128,
                        x + 6,
                        y + 81,
                        160,
                        20,
                        "Cycle: " + HotbarControls.keyName(HotbarControls.CYCLE)));
                hotbarRow = count == 0 ? 0 : hotbarRow % count;
                addOwnedButton(new GuiButton(129, x + 6, y + 109, 76, 20, "Row: " + (hotbarRow + 1)));
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = count > 0;
                addOwnedButton(
                    new GuiButton(
                        130,
                        x + 90,
                        y + 109,
                        76,
                        20,
                        "Key: " + HotbarControls.keyName(HotbarControls.ROWS[hotbarRow])));
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = count > 0;
                addOwnedButton(new GuiButton(114, x + 6, y + 157, 76, 20, "Back"));
                addOwnedButton(new GuiButton(108, x + 90, y + 157, 76, 20, "Done"));
                return;
            }
            if (terminalSettings) {
                for (int setting = 0; setting < 2; setting++) {
                    int value = setting == 0 ? InventoryViewSettings.terminalSmallRows
                        : InventoryViewSettings.terminalTallMinimumRows;
                    GuiButton minus = new GuiButton(120 + setting * 2, x + 6, y + 57 + setting * 28, 24, 20, "-");
                    GuiButton plus = new GuiButton(121 + setting * 2, x + 142, y + 57 + setting * 28, 24, 20, "+");
                    minus.enabled = value > 3;
                    plus.enabled = value < 24;
                    addOwnedButton(minus);
                    addOwnedButton(plus);
                }
                addOwnedButton(new GuiButton(114, x + 6, y + 157, 76, 20, "Back"));
                addOwnedButton(new GuiButton(108, x + 90, y + 157, 76, 20, "Done"));
                return;
            }
            addOwnedButton(new GuiButton(101, x + 6, y + 25, 76, 20, "Wide"));
            addOwnedButton(new GuiButton(102, x + 90, y + 25, 76, 20, "Compact"));
            addOwnedButton(new GuiButton(104, x + 6, y + 57, 24, 20, "-"));
            addOwnedButton(new GuiButton(105, x + 142, y + 57, 24, 20, "+"));
            addOwnedButton(new GuiButton(106, x + 6, y + 85, 24, 20, "-"));
            addOwnedButton(new GuiButton(107, x + 142, y + 85, 24, 20, "+"));
            addOwnedButton(
                new GuiButton(
                    116,
                    x + 6,
                    y + 109,
                    160,
                    20,
                    InventoryViewSettings.centerLastRow ? "Last row: Centered" : "Last row: Left"));
            GuiButton terminals = new GuiButton(113, x + 6, y + 157, 94, 20, "AE2 terminals");
            addOwnedButton(new GuiButton(117, x + 6, y + 133, 160, 20, "Swappable hotbars"));
            terminals.enabled = cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2");
            addOwnedButton(terminals);
            addOwnedButton(new GuiButton(108, x + 104, y + 157, 62, 20, "Done"));
        }
    }

    private void addOwnedButton(GuiButton button) {
        ownedButtons.add(button);
        buttonList.add(button);
    }

    void positionSlots() {
        inventorySlots.getSlot(0).xDisplayPosition = header.resultX;
        inventorySlots.getSlot(0).yDisplayPosition = 36;
        for (int id = 1; id <= 4; id++) {
            inventorySlots.getSlot(id).xDisplayPosition = header.craftingX + (id - 1) % 2 * 18;
            inventorySlots.getSlot(id).yDisplayPosition = 26 + (id - 1) / 2 * 18;
        }
        for (int id = 5; id <= 8; id++) {
            inventorySlots.getSlot(id).xDisplayPosition = PlayerHeaderLayout.ARMOR_X;
            inventorySlots.getSlot(id).yDisplayPosition = 8 + (id - 5) * 18;
        }
        if (BackhandCompatibility.enabled()) {
            Slot offhand = inventorySlots.getSlot(PlayerContainerLayout.OFFHAND);
            offhand.xDisplayPosition = PlayerHeaderLayout.OFFHAND_X;
            offhand.yDisplayPosition = PlayerHeaderLayout.OFFHAND_Y;
        }
        int mainSlots = InventoryLayout.of(ownerInventory)
            .mainSlots();
        int storageCell = 0;
        for (int main = 0; main < mainSlots; main++) {
            Slot slot = inventorySlots.getSlot(PlayerContainerLayout.containerId(ownerInventory, main));
            if (main < 9) {
                slot.xDisplayPosition = (view.width - 162) / 2 + main * 18;
                slot.yDisplayPosition = view.hotbarY;
            } else {
                int reserved = dev.gilbe.dynamicinventory.inventory.HotbarBanks.reserved(ownerInventory);
                if (dev.gilbe.dynamicinventory.inventory.HotbarBanks.isReserved(ownerInventory, slot.getSlotIndex())) {
                    slot.xDisplayPosition = slot.yDisplayPosition = -10000;
                    continue;
                }
                int cell = storageCell++;
                int row = cell / view.columns - scrollRow;
                int inset = InventoryViewSettings.centerLastRow
                    ? InventoryPanelLayout.rowOffset(cell, mainSlots - 9 - reserved, view.columns)
                    : 0;
                slot.xDisplayPosition = row >= 0 && row < view.visibleRows
                    ? view.gridX + inset + cell % view.columns * 18
                    : -10000;
                slot.yDisplayPosition = row >= 0 && row < view.visibleRows ? 84 + row * 18 : -10000;
            }
        }
        int cell = 0;
        for (int id = PlayerContainerLayout.slotCount(ownerInventory); id
            < inventorySlots.inventorySlots.size(); id++) {
            Slot slot = inventorySlots.getSlot(id);
            boolean assigned = BaublesInventoryBridge.visible(slot);
            boolean visible = baublesOpen && assigned;
            int row = baublesPanel == null ? cell % 4 : cell / baublesPanel.columns - baublesScrollRow;
            if (baublesPanel != null) visible &= row >= 0 && row < baublesPanel.visibleRows;
            slot.xDisplayPosition = !visible ? -10000
                : baublesPanel == null ? PlayerHeaderLayout.BAUBLES_X + cell / 4 * 18
                    : view.width + 13 + cell % baublesPanel.columns * 18;
            // Slot artwork starts one pixel before the item coordinate; center that whole 18px cell.
            slot.yDisplayPosition = visible ? (baublesPanel == null ? 8 : 9) + row * 18 : -10000;
            if (assigned) cell++;
        }
        for (Slot slot : inventorySlots.inventorySlots)
            if (slot.xDisplayPosition >= 0) slot.yDisplayPosition += tabSpace;
        // Floating positions already include the tab offset and the screen origin.
        // Apply them after native coordinates are complete, never before the tab shift.
        if (floating != null && floating.detached()) floating.position();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!ownedButtons.contains(button)) return;
        int columns = InventoryViewSettings.columns;
        int rows = InventoryViewSettings.rows;
        switch (button.id) {
            case 100:
                settingsOpen = true;
                terminalSettings = false;
                hotbarSettings = false;
                break;
            case 117:
                hotbarSettings = true;
                initGui();
                return;
            case 125:
            case 126:
                HotbarControls.configure(
                    dev.gilbe.dynamicinventory.inventory.HotbarBanks.count(mc.thePlayer) + (button.id == 125 ? -1 : 1));
                return;
            case 127:
                HotbarControls.toggle();
                initGui();
                return;
            case 128:
                bindingHotbarKey = HotbarControls.CYCLE;
                return;
            case 129:
                hotbarRow++;
                initGui();
                return;
            case 130:
                bindingHotbarKey = HotbarControls.ROWS[hotbarRow];
                return;
            case 101:
                columns = 18;
                rows = (InventoryLayout.of(ownerInventory)
                    .storageSlots() + 17) / 18;
                break;
            case 102:
                columns = 9;
                rows = (InventoryLayout.of(ownerInventory)
                    .storageSlots() + 8) / 9;
                break;
            case 104:
                columns--;
                break;
            case 105:
                columns++;
                break;
            case 106:
                rows--;
                break;
            case 107:
                rows++;
                break;
            case 108:
                bindingHotbarKey = null;
                settingsOpen = false;
                terminalSettings = false;
                break;
            case 109:
                if (PackCompatibility.enabled() && controlsAvailable()) {
                    preserveHeaderPosition = true;
                    baublesOpen = !baublesOpen;
                    initGui();
                }
                return;
            case 116:
                InventoryViewSettings.setCenterLastRow(!InventoryViewSettings.centerLastRow);
                initGui();
                return;
            case 113:
            case 114:
                hotbarSettings = false;
                terminalSettings = button.id == 113;
                initGui();
                return;
            case 120:
            case 121:
            case 122:
            case 123:
                if (!settingsOpen || !terminalSettings) return;
                int delta = button.id % 2 == 0 ? -1 : 1;
                InventoryViewSettings.setTerminalRows(
                    InventoryViewSettings.terminalSmallRows + (button.id < 122 ? delta : 0),
                    InventoryViewSettings.terminalTallMinimumRows + (button.id >= 122 ? delta : 0));
                initGui();
                return;
            default:
                return;
        }
        InventoryViewSettings.set(columns, rows);
        scrollRow = 0;
        initGui();
    }

    private boolean pressControl(int mouseX, int mouseY) {
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            // Foreign buttons must use GuiScreen's Forge ActionPerformed events.
            if (!ownedButtons.contains(button)) continue;
            if (button.mousePressed(mc, mouseX, mouseY)) {
                button.func_146113_a(mc.getSoundHandler());
                actionPerformed(button);
                controlClick = true;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (settingsOpen) {
            if (button == 0) pressControl(mouseX, mouseY);
            return;
        }
        // Some integration buttons hide themselves when disabled. Keep their visuals
        // intact and consume their clicks while the cursor or a drag owns an item.
        if (!controlsAvailable()) {
            for (Object entry : buttonList) {
                GuiButton control = (GuiButton) entry;
                if (control.visible && mouseX >= control.xPosition
                    && mouseX < control.xPosition + control.width
                    && mouseY >= control.yPosition
                    && mouseY < control.yPosition + control.height) {
                    controlClick = true;
                    return;
                }
            }
        }
        if (button == 0 && !field_147007_t
            && mc.thePlayer.inventory.getItemStack() == null
            && pressControl(mouseX, mouseY)) return;
        if (button == 0 && !field_147007_t && overScroll(mouseX, mouseY)) {
            scrolling = true;
            dragScroll(mouseY);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (settingsOpen || controlClick) return;
        if (scrolling) {
            dragScroll(mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, elapsed);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (controlClick || scrolling || settingsOpen) {
            if (button != -1) {
                controlClick = false;
                scrolling = false;
            }
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (settingsOpen || field_147007_t || floating != null && floating.detached()) return;
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop - tabSpace;
        if (baublesPanel != null && wheel != 0
            && x >= view.width + 4
            && x < xSize
            && y >= 0
            && y < baublesPanel.height) {
            baublesScrollRow = Math.max(0, Math.min(baublesPanel.maxScroll(), baublesScrollRow + (wheel > 0 ? -1 : 1)));
            positionSlots();
            return;
        }
        if (wheel != 0 && x >= view.gridX
            && x < view.gridX + view.columns * 18 + 10
            && y >= 84
            && y < 84 + view.visibleRows * 18) scroll(wheel > 0 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (bindingHotbarKey != null) {
            HotbarControls.bind(bindingHotbarKey, key);
            bindingHotbarKey = null;
            initGui();
            return;
        }
        if (settingsOpen) {
            if (key == Keyboard.KEY_ESCAPE || key == mc.gameSettings.keyBindInventory.getKeyCode()) {
                if (terminalSettings || hotbarSettings) {
                    terminalSettings = false;
                    hotbarSettings = false;
                } else settingsOpen = false;
                initGui();
            }
            return;
        }
        if (!field_147007_t && (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            scroll(key == Keyboard.KEY_PRIOR ? -view.visibleRows : view.visibleRows);
            return;
        }
        super.keyTyped(character, key);
    }

    private void scroll(int rows) {
        if (floating != null && floating.detached()) {
            floating.scroll(rows);
            return;
        }
        scrollRow = Math.max(0, Math.min(view.maxScroll(), scrollRow + rows));
        positionSlots();
    }

    private boolean overScroll(int x, int y) {
        if (floating != null && floating.detached()) return false;
        int left = guiLeft + view.gridX + view.columns * 18 + 2;
        return view.maxScroll() > 0 && x >= left
            && x < left + 8
            && y >= guiTop + tabSpace + 84
            && y < guiTop + tabSpace + 84 + view.visibleRows * 18;
    }

    private void dragScroll(int mouseY) {
        int track = view.visibleRows * 18;
        int thumb = Math.max(12, track * view.visibleRows / view.totalRows);
        float fraction = (mouseY - guiTop - tabSpace - 84 - thumb / 2f) / (track - thumb);
        scrollRow = Math.max(0, Math.min(view.maxScroll(), Math.round(fraction * view.maxScroll())));
        positionSlots();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(
            I18n.format("container.crafting"),
            header.craftingX,
            tabSpace + 14,
            InventoryGuiTheme.get().label);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        positionButtons();
        int mainTop = guiTop + tabSpace;
        boolean detached = floating != null && floating.detached();
        drawPanel(guiLeft, mainTop, view.width, detached ? PlayerHeaderLayout.DETACHED_HEIGHT : view.height);
        if (baublesPanel != null) {
            int left = guiLeft + view.width + 4;
            drawPanel(left, mainTop, baublesPanel.width, baublesPanel.height);
            if (baublesPanel.maxScroll() > 0) {
                int x = left + baublesPanel.width - 12;
                int track = baublesPanel.visibleRows * 18;
                int thumb = Math.max(12, track * baublesPanel.visibleRows / baublesPanel.totalRows);
                int top = mainTop + 8 + (track - thumb) * baublesScrollRow / baublesPanel.maxScroll();
                drawRect(x, mainTop + 8, x + 8, mainTop + 8 + track, InventoryGuiTheme.get().scrollTrack);
                drawRect(x + 1, top, x + 7, top + thumb, InventoryGuiTheme.get().scrollThumb);
            }
        }
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(field_147001_a);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            java.util.List<Slot> visible = new java.util.ArrayList<>();
            for (Object entry : inventorySlots.inventorySlots) {
                Slot slot = (Slot) entry;
                if (slot.xDisplayPosition == -10000 || detached && ordinarySlot(slot)) continue;
                visible.add(slot);
            }
            ResourceSlotGrid.draw(visible, guiLeft, guiTop);
        } finally {
            GL11.glPopAttrib();
        }
        // The player stays at the left edge while crafting follows the right edge.
        drawFrame(guiLeft + 26, mainTop + 7, 52, 73, 1, InventoryGuiTheme.get().playerFrame);
        drawRect(guiLeft + 27, mainTop + 8, guiLeft + 77, mainTop + 79, InventoryGuiTheme.get().playerFill);
        // Keep the native arrow's shape and resource-pack artwork instead of approximating it with rectangles.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(field_147001_a);
            drawTexturedModalRect(guiLeft + header.craftingX + 36, mainTop + 36, 124, 36, 18, 14);
        } finally {
            GL11.glPopAttrib();
        }
        GuiInventory.func_147046_a(
            guiLeft + PlayerHeaderLayout.PLAYER_X,
            mainTop + 75,
            30,
            guiLeft + PlayerHeaderLayout.PLAYER_X - mouseX,
            mainTop + 25 - mouseY,
            mc.thePlayer);
        if (!detached && view.maxScroll() > 0) {
            int x = guiLeft + view.gridX + view.columns * 18 + 2;
            int track = view.visibleRows * 18;
            int thumb = Math.max(12, track * view.visibleRows / view.totalRows);
            int top = mainTop + 84 + (track - thumb) * scrollRow / view.maxScroll();
            drawRect(x, mainTop + 84, x + 8, mainTop + 84 + track, InventoryGuiTheme.get().scrollTrack);
            drawRect(x + 1, top, x + 7, top + thumb, InventoryGuiTheme.get().scrollThumb);
        }
        if (detached) floating.drawPanels();
    }

    private boolean ordinarySlot(Slot slot) {
        return slot.inventory == ownerInventory && InventoryLayout.of(ownerInventory)
            .ordinaryIndex(slot.getSlotIndex()) >= 0;
    }

    private void positionButtons() {
        InventoryTabsCompatibility.positionTabs(buttonList, guiLeft, guiTop + tabSpace);
        if (PackCompatibility.enabled()) PackClientCompatibility.positionSortButtons(this, buttonList, guiLeft, guiTop);
        if (settingsOpen) return;
        if (layoutButton != null) {
            layoutButton.xPosition = guiLeft + header.layoutButtonX;
            layoutButton.yPosition = guiTop + tabSpace + 6;
        }
        if (baublesButton != null) {
            baublesButton.xPosition = guiLeft + header.baublesButtonX;
            baublesButton.yPosition = guiTop + tabSpace + 8;
        }
    }

    private void drawPanel(int x, int y, int panelWidth, int panelHeight) {
        NativePanelFrame.drawPlayerFrame(x, y, panelWidth, panelHeight);
    }

    private void drawFrame(int x, int y, int panelWidth, int panelHeight, int thickness, int color) {
        drawRect(x, y, x + panelWidth, y + thickness, color);
        drawRect(x, y + panelHeight - thickness, x + panelWidth, y + panelHeight, color);
        drawRect(x, y + thickness, x + thickness, y + panelHeight - thickness, color);
        drawRect(x + panelWidth - thickness, y + thickness, x + panelWidth, y + panelHeight - thickness, color);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        positionButtons();
        if (!settingsOpen) {
            if (layoutButton != null) layoutButton.enabled = controlsAvailable();
            if (baublesButton != null) baublesButton.enabled = controlsAvailable();
        }
        super.drawScreen(settingsOpen ? -1000 : mouseX, settingsOpen ? -1000 : mouseY, ticks);
        if (!settingsOpen) {
            if (floating != null) floating.drawControl();
            if (mc.thePlayer.inventory.getItemStack() == null && mouseX >= guiLeft + header.layoutButtonX
                && mouseX < guiLeft + header.layoutButtonX + 16
                && mouseY >= guiTop + tabSpace + 6
                && mouseY < guiTop + tabSpace + 22)
                drawHoveringText(
                    java.util.Collections.singletonList("Inventory layout"),
                    mouseX,
                    mouseY,
                    fontRendererObj);
            if (baublesOpen) {
                for (int id = PlayerContainerLayout.slotCount(ownerInventory); id
                    < inventorySlots.inventorySlots.size(); id++) {
                    Slot slot = inventorySlots.getSlot(id);
                    int x = guiLeft + slot.xDisplayPosition;
                    int y = guiTop + slot.yDisplayPosition;
                    if (slot.xDisplayPosition >= 0 && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16)
                        drawHoveringText(PackClientCompatibility.baubleTooltip(slot), mouseX, mouseY, fontRendererObj);
                }
            }
            return;
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int x = (width - 172) / 2;
        int y = (height - SETTINGS_HEIGHT) / 2;
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        drawFrame(x - 2, y - 2, 176, SETTINGS_HEIGHT + 4, 2, theme.settingsOuter);
        drawRect(x, y, x + 172, y + SETTINGS_HEIGHT, theme.settingsFill);
        drawCenteredString(
            fontRendererObj,
            hotbarSettings ? "Swappable hotbars" : terminalSettings ? "AE2 terminals" : "Inventory layout",
            width / 2,
            y + 8,
            theme.settingsText);
        if (hotbarSettings) {
            drawCenteredString(
                fontRendererObj,
                "Extra rows: " + dev.gilbe.dynamicinventory.inventory.HotbarBanks.count(mc.thePlayer),
                width / 2,
                y + 31,
                theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                bindingHotbarKey == null ? "9 storage slots per extra row" : "Press key; Esc clears",
                width / 2,
                y + 139,
                theme.settingsText);
        } else if (terminalSettings) {
            drawCenteredString(
                fontRendererObj,
                "Small rows: " + InventoryViewSettings.terminalSmallRows,
                width / 2,
                y + 63,
                theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                "Tall minimum: " + InventoryViewSettings.terminalTallMinimumRows,
                width / 2,
                y + 91,
                theme.settingsText);
            drawCenteredString(fontRendererObj, "Saved immediately", width / 2, y + 125, theme.settingsText);
            drawCenteredString(fontRendererObj, "Reopen terminal to apply", width / 2, y + 141, theme.settingsText);
        } else {
            drawCenteredString(
                fontRendererObj,
                "Columns: " + InventoryViewSettings.columns,
                width / 2,
                y + 63,
                theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                "Visible rows: " + InventoryViewSettings.rows,
                width / 2,
                y + 91,
                theme.settingsText);
        }
        for (Object entry : buttonList) ((GuiButton) entry).drawButton(mc, mouseX, mouseY);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public boolean layoutSettingsOpen() {
        return settingsOpen;
    }

    public boolean inlineBaublesFits() {
        return header.inlineBaublesFits;
    }

    public boolean inlineBaublesOpen() {
        return baublesOpen && baublesPanel == null && header.inlineBaublesFits;
    }

    public boolean baublesSidePanelOpen() {
        return baublesOpen && baublesPanel != null;
    }

    java.util.List<InventoryPanelLayout.Rect> fixedPanelBounds() {
        java.util.List<InventoryPanelLayout.Rect> result = new java.util.ArrayList<>();
        result.add(
            new InventoryPanelLayout.Rect(guiLeft, guiTop + tabSpace, view.width, PlayerHeaderLayout.DETACHED_HEIGHT));
        if (baublesPanel != null) result.add(
            new InventoryPanelLayout.Rect(
                guiLeft + view.width + 4,
                guiTop + tabSpace,
                baublesPanel.width,
                baublesPanel.height));
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            String name = button.getClass()
                .getName();
            // Sort controls belong to the floating panel itself, so they are not fixed obstacles.
            if (!button.visible || button.width <= 0
                || button.height <= 0
                || ownedButtons.contains(button)
                || name.contains("bogosorter.")
                || name.equals("dev.gilbe.dynamicinventory.client.NativeSortButton")) continue;
            result.add(
                new InventoryPanelLayout.Rect(
                    button.xPosition - 1,
                    button.yPosition - 1,
                    button.width + 2,
                    button.height + 2));
        }
        return result;
    }

    public boolean controlsAvailable() {
        return !settingsOpen && mc.thePlayer.inventory.getItemStack() == null && !field_147007_t;
    }

    public int craftingOffsetX() {
        return inventorySlots.getSlot(1).xDisplayPosition - 25;
    }

    public int sortButtonX() {
        return floating != null && floating.detached() ? floating.playerBounds()
            .right() - guiLeft
            - 46 : view.width - 46;
    }

    public int sortButtonY() {
        return floating != null && floating.detached() ? floating.playerBounds().y - guiTop + 2 : tabSpace + 64;
    }

    public int craftingOffsetY() {
        return inventorySlots.getSlot(1).yDisplayPosition - 6;
    }

    @Override
    public void onGuiClosed() {
        if (floating != null) floating.cancel();
        super.onGuiClosed();
        // Geometry is local to this screen; restore the persistent container's default positions afterward.
        for (int main = 0; main < InventoryLayout.of(ownerInventory)
            .mainSlots(); main++) {
            Slot slot = inventorySlots.getSlot(PlayerContainerLayout.containerId(ownerInventory, main));
            slot.xDisplayPosition = main < 9 ? 8 + main * 18 : PlayerContainerLayout.storageX(main);
            slot.yDisplayPosition = main < 9 ? PlayerContainerLayout.hotbarY(ownerInventory)
                : PlayerContainerLayout.storageY(main);
        }
        inventorySlots.getSlot(0).xDisplayPosition = 144;
        inventorySlots.getSlot(0).yDisplayPosition = 36;
        for (int id = 1; id <= 4; id++) {
            inventorySlots.getSlot(id).xDisplayPosition = 88 + (id - 1) % 2 * 18;
            inventorySlots.getSlot(id).yDisplayPosition = 22 + (id - 1) / 2 * 18;
        }
        for (int id = 5; id <= 8; id++) {
            inventorySlots.getSlot(id).xDisplayPosition = 8;
            inventorySlots.getSlot(id).yDisplayPosition = 8 + (id - 5) * 18;
        }
        if (BackhandCompatibility.enabled()) {
            Slot offhand = inventorySlots.getSlot(PlayerContainerLayout.OFFHAND);
            offhand.xDisplayPosition = 80;
            offhand.yDisplayPosition = 65;
        }
        for (int id = PlayerContainerLayout.slotCount(ownerInventory); id
            < inventorySlots.inventorySlots.size(); id++) {
            inventorySlots.getSlot(id).xDisplayPosition = -10000;
            inventorySlots.getSlot(id).yDisplayPosition = -10000;
        }
    }
}
