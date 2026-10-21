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

    private InventoryViewLayout view;
    private final net.minecraft.entity.player.InventoryPlayer ownerInventory;
    private PlayerHeaderLayout header;
    private BaublesPanelLayout baublesPanel;
    private boolean baublesOpen;
    private int tabSpace;
    private int baublesScrollRow;
    private int scrollRow;
    private boolean settingsOpen;
    private boolean scrolling;
    private boolean controlClick;
    private final InventoryPresentation presentation = new InventoryPresentation(this);

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
            InventoryLayout.of(ownerInventory)
                .storageSlots());
        int equipmentSlots = PackCompatibility.enabled() ? BaublesInventoryBridge.visibleSlotCount() : 0;
        header = new PlayerHeaderLayout(view.width, equipmentSlots);
        baublesPanel = baublesOpen && !header.inlineBaublesFits && equipmentSlots > 0
            ? new BaublesPanelLayout(height - tabSpace, equipmentSlots)
            : null;
        if (baublesPanel != null) view = new InventoryViewLayout(
            width - baublesPanel.width - 4,
            height - tabSpace,
            InventoryViewSettings.columns,
            InventoryViewSettings.rows,
            InventoryLayout.of(ownerInventory)
                .storageSlots());
        xSize = view.width + (baublesPanel == null ? 0 : baublesPanel.width + 4);
        ySize = tabSpace + Math.max(view.height, baublesPanel == null ? 0 : baublesPanel.height);
        header = new PlayerHeaderLayout(view.width, equipmentSlots);
        super.initGui();
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
        } else {
            int x = (width - 172) / 2;
            int y = (height - 226) / 2;
            addOwnedButton(new GuiButton(101, x + 6, y + 25, 50, 20, "Wide"));
            addOwnedButton(new GuiButton(102, x + 61, y + 25, 50, 20, "Compact"));
            addOwnedButton(new GuiButton(103, x + 116, y + 25, 50, 20, "Tall"));
            addOwnedButton(new GuiButton(104, x + 6, y + 57, 24, 20, "-"));
            addOwnedButton(new GuiButton(105, x + 142, y + 57, 24, 20, "+"));
            addOwnedButton(new GuiButton(106, x + 6, y + 85, 24, 20, "-"));
            addOwnedButton(new GuiButton(107, x + 142, y + 85, 24, 20, "+"));
            addOwnedButton(
                new GuiButton(
                    110,
                    x + 6,
                    y + 125,
                    160,
                    20,
                    InventoryViewSettings.expandMachineInventory ? "Machines: Vertical" : "Machines: Scroll"));
            addOwnedButton(
                new GuiButton(
                    111,
                    x + 6,
                    y + 149,
                    160,
                    20,
                    InventoryViewSettings.compactNativePanels ? "Containers: Compact" : "Containers: Full box"));
            GuiButton renderer = new GuiButton(
                112,
                x + 6,
                y + 173,
                160,
                20,
                InventoryViewSettings.mui2Presentation ? "Presentation: MUI2 (test)" : "Presentation: Native");
            renderer.enabled = InventoryPresentation.available();
            addOwnedButton(renderer);
            addOwnedButton(new GuiButton(108, x + 36, y + 200, 100, 20, "Done"));
        }
    }

    private void addOwnedButton(GuiButton button) {
        ownedButtons.add(button);
        buttonList.add(button);
    }

    private void positionSlots() {
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
        for (int main = 0; main < InventoryLayout.of(ownerInventory)
            .mainSlots(); main++) {
            Slot slot = inventorySlots.getSlot(PlayerContainerLayout.containerId(ownerInventory, main));
            if (main < 9) {
                slot.xDisplayPosition = (view.width - 162) / 2 + main * 18;
                slot.yDisplayPosition = view.hotbarY;
            } else {
                int cell = main - 9;
                int row = cell / view.columns - scrollRow;
                slot.xDisplayPosition = row >= 0 && row < view.visibleRows ? view.gridX + cell % view.columns * 18
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
            int row = baublesPanel == null ? cell % 4 : cell / BaublesPanelLayout.COLUMNS - baublesScrollRow;
            if (baublesPanel != null) visible &= row >= 0 && row < baublesPanel.visibleRows;
            slot.xDisplayPosition = !visible ? -10000
                : baublesPanel == null ? PlayerHeaderLayout.BAUBLES_X + cell / 4 * 18
                    : view.width + 12 + cell % BaublesPanelLayout.COLUMNS * 18;
            slot.yDisplayPosition = visible ? 8 + row * 18 : -10000;
            if (assigned) cell++;
        }
        for (Slot slot : inventorySlots.inventorySlots)
            if (slot.xDisplayPosition >= 0) slot.yDisplayPosition += tabSpace;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!ownedButtons.contains(button)) return;
        int columns = InventoryViewSettings.columns;
        int rows = InventoryViewSettings.rows;
        switch (button.id) {
            case 100:
                settingsOpen = true;
                break;
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
            case 103:
                columns = 6;
                rows = (InventoryLayout.of(ownerInventory)
                    .storageSlots() + 5) / 6;
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
                settingsOpen = false;
                break;
            case 109:
                if (PackCompatibility.enabled() && controlsAvailable()) {
                    baublesOpen = !baublesOpen;
                    initGui();
                }
                return;
            case 110:
                InventoryViewSettings.setMachineInventoryExpanded(!InventoryViewSettings.expandMachineInventory);
                initGui();
                return;
            case 111:
                InventoryViewSettings.setCompactNativePanels(!InventoryViewSettings.compactNativePanels);
                initGui();
                return;
            case 112:
                InventoryViewSettings.setMui2Presentation(!InventoryViewSettings.mui2Presentation);
                presentation.dispose();
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
        if (settingsOpen || field_147007_t) return;
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
        if (settingsOpen) {
            if (key == Keyboard.KEY_ESCAPE || key == mc.gameSettings.keyBindInventory.getKeyCode()) {
                settingsOpen = false;
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
        scrollRow = Math.max(0, Math.min(view.maxScroll(), scrollRow + rows));
        positionSlots();
    }

    private boolean overScroll(int x, int y) {
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
        drawPanel(guiLeft, mainTop, view.width, view.height);
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
            if (!presentation.slots(inventorySlots.inventorySlots)) for (Object entry : inventorySlots.inventorySlots) {
                Slot slot = (Slot) entry;
                if (slot.xDisplayPosition < 0) continue;
                drawTexturedModalRect(
                    guiLeft + slot.xDisplayPosition - 1,
                    guiTop + slot.yDisplayPosition - 1,
                    7,
                    83,
                    18,
                    18);
            }
        } finally {
            GL11.glPopAttrib();
        }
        // The player stays at the left edge while crafting follows the right edge.
        drawFrame(guiLeft + 26, mainTop + 7, 52, 73, 1, InventoryGuiTheme.get().playerFrame);
        drawRect(guiLeft + 27, mainTop + 8, guiLeft + 77, mainTop + 79, InventoryGuiTheme.get().playerFill);
        int arrowX = guiLeft + header.craftingX + 39;
        int arrowY = mainTop + 42;
        drawRect(arrowX, arrowY, arrowX + 11, arrowY + 4, InventoryGuiTheme.get().arrow);
        for (int line = 0; line < 7; line++) drawRect(
            arrowX + 10,
            arrowY - 4 + line,
            arrowX + 11 + line,
            arrowY + 12 - line,
            InventoryGuiTheme.get().arrow);
        GuiInventory.func_147046_a(
            guiLeft + PlayerHeaderLayout.PLAYER_X,
            mainTop + 75,
            30,
            guiLeft + PlayerHeaderLayout.PLAYER_X - mouseX,
            mainTop + 25 - mouseY,
            mc.thePlayer);
        if (view.maxScroll() > 0) {
            int x = guiLeft + view.gridX + view.columns * 18 + 2;
            int track = view.visibleRows * 18;
            int thumb = Math.max(12, track * view.visibleRows / view.totalRows);
            int top = mainTop + 84 + (track - thumb) * scrollRow / view.maxScroll();
            drawRect(x, mainTop + 84, x + 8, mainTop + 84 + track, InventoryGuiTheme.get().scrollTrack);
            drawRect(x + 1, top, x + 7, top + thumb, InventoryGuiTheme.get().scrollThumb);
        }
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
        if (presentation.frame(x, y, panelWidth, panelHeight, true)) return;
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        // Disjoint borders keep transparent fills from exposing an opaque layer underneath.
        drawFrame(x, y, panelWidth, panelHeight, 1, theme.panelOuter);
        drawFrame(x + 1, y + 1, panelWidth - 2, panelHeight - 2, 1, theme.panelShadow);
        drawRect(x + 2, y + 2, x + panelWidth - 2, y + 4, theme.panelHighlight);
        drawRect(x + 2, y + 4, x + 4, y + panelHeight - 2, theme.panelHighlight);
        drawRect(x + 4, y + 4, x + panelWidth - 2, y + panelHeight - 2, theme.panelFill);
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
        int y = (height - 226) / 2;
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        drawFrame(x - 2, y - 2, 176, 230, 2, theme.settingsOuter);
        drawRect(x, y, x + 172, y + 226, theme.settingsFill);
        drawCenteredString(fontRendererObj, "Inventory layout", width / 2, y + 8, theme.settingsText);
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
        drawCenteredString(
            fontRendererObj,
            "Fits: " + view.columns + " x " + view.visibleRows,
            width / 2,
            y + 112,
            theme.settingsText);
        for (Object entry : buttonList) ((GuiButton) entry).drawButton(mc, mouseX, mouseY);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public boolean layoutSettingsOpen() {
        return settingsOpen;
    }

    public String presentationStatus() {
        return presentation.status();
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

    public boolean controlsAvailable() {
        return !settingsOpen && mc.thePlayer.inventory.getItemStack() == null && !field_147007_t;
    }

    public int craftingOffsetX() {
        return inventorySlots.getSlot(1).xDisplayPosition - 25;
    }

    public int sortButtonX() {
        return view.width - 46;
    }

    public int sortButtonY() {
        return tabSpace + 64;
    }

    public int craftingOffsetY() {
        return inventorySlots.getSlot(1).yDisplayPosition - 6;
    }

    @Override
    public void onGuiClosed() {
        presentation.dispose();
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
