package dev.gilbe.dynamicinventory.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** A settings view owned by the live container. Never replaces the host GuiScreen or its slots. */
public final class PanelSettingsOverlay {

    private static View active;

    public static boolean open(GuiContainer host, DetachedChestPanel panel) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null || mc.currentScreen != host
            || mc.thePlayer == null
            || mc.thePlayer.openContainer != host.inventorySlots
            || ClientInputGuard.isPending(host)
            || mc.thePlayer.inventory.getItemStack() != null
            || ((ContainerPanelAccess) host).dynamicinventory$draggingItems()
            || PackCompatibility.enabled() && PackClientCompatibility.neiDragging()) return false;
        active = new View(host, panel);
        active.setWorldAndResolution(mc, host.width, host.height);
        return true;
    }

    public static boolean isOpen(GuiScreen host) {
        return active != null && active.host == host;
    }

    public static boolean handleInput(GuiScreen host) {
        View view = active;
        if (view == null || view.host != host) return false;
        if (!view.current()) view.closing = true;
        // Drain this entire input batch, including events after Save/Cancel and their release.
        view.handleInput();
        if (view.closing && !Mouse.isButtonDown(0) && !Mouse.isButtonDown(1) && !Mouse.isButtonDown(2)) active = null;
        return true;
    }

    /** A modal may open after the host's input batch has already started. Own its remaining events too. */
    public static boolean handleMouse(GuiScreen host) {
        View view = active;
        if (view == null || view.host != host) return false;
        if (!view.current()) view.closing = true;
        if (!view.closing) view.handleMouseInput();
        return true;
    }

    public static boolean handleKeyboard(GuiScreen host) {
        View view = active;
        if (view == null || view.host != host) return false;
        if (!view.current()) view.closing = true;
        if (!view.closing) view.handleKeyboardInput();
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        View view = active;
        if (view == null) return;
        if (!view.current()) {
            active = null;
            return;
        }
        if (view.closing) return;
        if (view.width != view.host.width || view.height != view.host.height)
            view.setWorldAndResolution(Minecraft.getMinecraft(), view.host.width, view.host.height);
        view.drawScreen(event.mouseX, event.mouseY, event.renderPartialTicks);
    }

    private static final class View extends GuiScreen {

        private final GuiContainer host;
        private final Container container;
        private final List<Slot> slots;
        private final DetachedChestPanel panel;
        private final int capacity;
        private PlayerPanelProfile draft, mainDraft;
        private PlayerPanelSet panelDraft;
        private final Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        private String selected;
        private boolean panelList = true;
        private int panelPage;
        private GuiTextField nameField;
        private boolean global, closing, groups;
        private int page, confirm = -1, groupPage, left, top, menuWidth;
        private String status = "";
        private final List<String> groupNames = new ArrayList<>();

        private View(GuiContainer host, DetachedChestPanel panel) {
            this.host = host;
            this.panel = panel;
            container = host.inventorySlots;
            slots = new ArrayList<>(container.inventorySlots);
            capacity = InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory)
                .storageSlots();
            global = panel == null;
            selected = panel == null ? PlayerPanelSet.MAIN : panel.selected();
            load();
        }

        private void load() {
            panelDraft = PlayerPanelProfiles.panels();
            if (panelDraft.entries.stream()
                .noneMatch(entry -> entry.id.equals(selected))) selected = PlayerPanelSet.MAIN;
            mainDraft = global ? PlayerPanelProfiles.global() : PlayerPanelProfiles.resolve(panel.key());
            draft = selected.equals(PlayerPanelSet.MAIN) ? mainDraft
                : global ? panelDraft.get(selected).global
                    : panelDraft.get(selected)
                        .resolve(panel.key());
            counts.clear();
            for (PlayerPanelSet.Entry entry : panelDraft.entries) counts.put(
                entry.id,
                panelDraft.members(entry.id, capacity)
                    .size());
        }

        private boolean current() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != host || mc.thePlayer == null
                || mc.thePlayer.openContainer != container
                || host.inventorySlots != container
                || ClientInputGuard.isPending(host)
                || mc.thePlayer.inventory.getItemStack() != null
                || container.inventorySlots.size() != slots.size()
                || InventoryLayout.of(mc.thePlayer.inventory)
                    .storageSlots() != capacity)
                return false;
            for (int i = 0; i < slots.size(); i++)
                if (container.inventorySlots.get(i) != slots.get(i) || slots.get(i).slotNumber != i) return false;
            return true;
        }

        @Override
        public void initGui() {
            menuWidth = Math.min(272, width - 8);
            left = (width - menuWidth) / 2;
            top = Math.max(2, (height - 226) / 2);
            buttonList.clear();
            add(20, 8, 51, menuWidth - 16, global ? "Scope: Global default" : "Scope: This interface");
            ((GuiButton) buttonList.get(0)).enabled = panel != null;
            String[] tabs = { "Layout", "Position", "Style", "Reset", "Panels" };
            int tabWidth = (menuWidth - 16) / 5;
            for (int i = 0; i < tabs.length; i++) {
                add(30 + i, 8 + i * tabWidth, 74, tabWidth - 2, tabs[i]);
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = page != i;
            }
            nameField = null;
            if (page == 4) {
                panelControls();
            } else if (page == 0) {
                add(1, 8, 100, (menuWidth - 20) / 2, mainDraft.detached ? "Mode: Detached" : "Mode: Attached");
                add(21, 12 + (menuWidth - 20) / 2, 100, (menuWidth - 20) / 2, "Save as global");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = !global;
                adjusters(2, 128);
                adjusters(4, 156);
            } else if (page == 1) {
                adjusters(6, 100);
                adjusters(8, 128);
                add(10, 8, 156, menuWidth - 16, "Center preferred position");
            } else if (page == 2) {
                add(
                    11,
                    8,
                    98,
                    menuWidth - 16,
                    "Frame: " + (draft.frameStyle < 0 ? "Follow interface" : NativePanelFrame.NAMES[draft.frameStyle]));
                adjusters(12, 122);
                adjusters(14, 146);
                adjusters(16, 170);
            } else if (!groups) {
                if (panel != null) add(
                    40,
                    8,
                    98,
                    menuWidth - 16,
                    confirm == 40 ? "Confirm: use global for this interface" : "Use global for this interface");
                add(
                    41,
                    8,
                    122,
                    menuWidth - 16,
                    confirm == 41 ? "Confirm: reset global default" : "Reset global default");
                add(
                    42,
                    8,
                    146,
                    menuWidth - 16,
                    confirm == 42 ? "Confirm: reset all machine overrides" : "Reset all machine overrides");
                add(43, 8, 170, menuWidth - 16, "Reset mod groups...");
            } else {
                for (int i = groupPage * 4; i < Math.min(groupNames.size(), groupPage * 4 + 4); i++) add(
                    100 + i,
                    8,
                    98 + (i % 4) * 22,
                    menuWidth - 16,
                    fontRendererObj.trimStringToWidth(
                        (confirm == 100 + i ? "Confirm: " : "Reset ") + groupNames.get(i),
                        menuWidth - 26));
                add(44, 8, 180, 40, "<");
                add(45, menuWidth - 48, 180, 40, ">");
            }
            add(0, 8, 204, (menuWidth - 20) / 2, "Cancel");
            add(50, 12 + (menuWidth - 20) / 2, 204, (menuWidth - 20) / 2, "Apply / Done");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = total() == capacity;
            if (page == 2
                && (draft.frameStyle == 0 || draft.frameStyle < 0 && panel != null && panel.inheritedFrameStyle() == 0))
                for (Object entry : buttonList) {
                    GuiButton button = (GuiButton) entry;
                    if (button.id == 12 || button.id == 13 || button.id == 16 || button.id == 17)
                        button.enabled = false;
                }
        }

        private int total() {
            int total = 0;
            for (int count : counts.values()) total += count;
            return total;
        }

        private void panelControls() {
            if (panelList) {
                for (int i = panelPage * 3; i < Math.min(panelDraft.entries.size(), panelPage * 3 + 3); i++) {
                    PlayerPanelSet.Entry entry = panelDraft.entries.get(i);
                    add(
                        200 + i,
                        8,
                        98 + i % 3 * 24,
                        menuWidth - 16,
                        (i + 1) + ". " + entry.name + " [" + counts.get(entry.id) + "]");
                }
                add(62, 8, 174, 30, "<");
                add(63, menuWidth - 38, 174, 30, ">");
                add(60, 42, 174, menuWidth - 84, "Add panel");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = panelDraft.entries.size() < 64;
            } else {
                nameField = new GuiTextField(fontRendererObj, left + 8, top + 100, menuWidth - 16, 18);
                nameField.setMaxStringLength(32);
                nameField.setText(panelDraft.get(selected).name);
                adjusters(64, 128);
                add(66, 8, 152, menuWidth - 16, "Assign remaining slots to Main");
                add(61, 8, 176, (menuWidth - 20) / 2, "Panel list");
                add(67, 12 + (menuWidth - 20) / 2, 176, (menuWidth - 20) / 2, "Remove panel");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = !PlayerPanelSet.MAIN.equals(selected);
            }
        }

        private boolean storeDraft() {
            if (nameField != null) {
                try {
                    panelDraft = panelDraft.replace(
                        panelDraft.get(selected)
                            .named(nameField.getText()));
                } catch (IllegalArgumentException invalid) {
                    status = "Use a name with 1-32 printable characters";
                    return false;
                }
            }
            if (PlayerPanelSet.MAIN.equals(selected)) mainDraft = draft;
            else panelDraft = panelDraft.replace(
                panelDraft.get(selected)
                    .profile(global ? null : panel.key(), draft));
            return true;
        }

        private void choose(String id) {
            selected = id;
            if (panel != null) panel.select(id);
            draft = id.equals(PlayerPanelSet.MAIN) ? mainDraft
                : global ? panelDraft.get(id).global
                    : panelDraft.get(id)
                        .resolve(panel.key());
        }

        private void add(int id, int x, int y, int w, String label) {
            buttonList.add(new GuiButton(id, left + x, top + y, w, 18, label));
        }

        private void adjusters(int id, int y) {
            add(id, 8, y, 24, "-");
            add(id + 1, menuWidth - 32, y, 24, "+");
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (closing || !current()) return;
            int id = button.id;
            if (id == 0) {
                closing = true;
                return;
            }
            if (!storeDraft()) return;
            if (id >= 200) {
                choose(panelDraft.entries.get(id - 200).id);
                panelList = false;
                page = 4;
            } else if (id == 60) {
                panelDraft = panelDraft.add();
                PlayerPanelSet.Entry added = panelDraft.entries.get(panelDraft.entries.size() - 1);
                panelDraft = panelDraft.replace(
                    added.profile(
                        null,
                        added.global.withAppearance(
                            mainDraft.frameStyle,
                            mainDraft.borderWidth,
                            mainDraft.frameCrop,
                            mainDraft.cornerCrop)));
                choose(panelDraft.entries.get(panelDraft.entries.size() - 1).id);
                counts.put(selected, 0);
                panelList = false;
                mainDraft = mainDraft.detached(true);
            } else if (id == 61) panelList = true;
            else if (id == 62 || id == 63)
                panelPage = clamp(panelPage + (id == 62 ? -1 : 1), 0, (panelDraft.entries.size() - 1) / 3);
            else if (id == 64 || id == 65) counts.put(
                selected,
                clamp(counts.get(selected) + (id == 64 ? -1 : 1) * (isShiftKeyDown() ? 9 : 1), 0, capacity));
            else if (id == 66) {
                int other = total() - counts.get(PlayerPanelSet.MAIN);
                counts.put(PlayerPanelSet.MAIN, Math.max(0, capacity - other));
            } else if (id == 67) {
                counts.put(PlayerPanelSet.MAIN, counts.get(PlayerPanelSet.MAIN) + counts.remove(selected));
                panelDraft = panelDraft.remove(selected);
                choose(PlayerPanelSet.MAIN);
                panelList = true;
                panelPage = 0;
            } else if (id >= 30 && id <= 34) {
                page = id - 30;
                groups = false;
                confirm = -1;
            } else if (id == 20) {
                global = !global;
                load();
                status = "Scope changed; draft reloaded";
            } else if (id == 21) {
                try {
                    if (total() != capacity) throw new IOException("Allocate all slots first");
                    PlayerPanelSet allocated = allocation();
                    if (!PlayerPanelSet.MAIN.equals(selected)) allocated = allocated.replace(
                        allocated.get(selected)
                            .profile(null, draft));
                    if (panel != null) panel.apply(allocated, mainDraft, true);
                    else PlayerPanelProfiles.applyPanels(allocated, null, mainDraft);
                    status = "Saved as global default";
                } catch (IOException failure) {
                    status = failure.getMessage() == null ? "Save failed; previous layout retained"
                        : failure.getMessage();
                }
            } else if (id == 50) {
                try {
                    if (total() != capacity) throw new IOException("Allocate all slots first");
                    PlayerPanelSet allocated = allocation();
                    if (panel == null) PlayerPanelProfiles.applyPanels(allocated, null, mainDraft);
                    else panel.apply(allocated, mainDraft, global);
                    if (panel != null) panel.reload();
                    closing = true;
                } catch (IOException failure) {
                    status = failure.getMessage() == null ? "Save failed; previous layout retained"
                        : failure.getMessage();
                }
            } else if (id == 1) {
                mainDraft = mainDraft.detached(!mainDraft.detached);
                if (PlayerPanelSet.MAIN.equals(selected)) draft = mainDraft;
            } else if (id >= 2 && id <= 9) {
                int step = (id % 2 == 0 ? -1 : 1) * (isShiftKeyDown() ? 18 : 1);
                int w = draft.width, h = draft.height, x = draft.anchorX, y = draft.anchorY;
                if (id < 4) w = clamp(w + step, PlayerPanelSet.MAIN.equals(selected) ? 178 : 70, 340);
                else if (id < 6) h = clamp(h + step, PlayerPanelSet.MAIN.equals(selected) ? 64 : 44, 262);
                else if (id < 8) x = clamp(x + step * 100, 0, 10000);
                else y = clamp(y + step * 100, 0, 10000);
                draft = draft.withBounds(w, h, x, y);
            } else if (id == 10) draft = draft.withBounds(draft.width, draft.height, 5000, 5000);
            else if (id == 11) draft = draft.withAppearance(
                draft.frameStyle == 2 ? -1 : draft.frameStyle + 1,
                draft.borderWidth,
                draft.frameCrop,
                draft.cornerCrop);
            else if (id >= 12 && id <= 17) {
                int change = id % 2 == 0 ? -1 : 1;
                draft = draft.withAppearance(
                    draft.frameStyle < 0 ? panel == null ? 2 : panel.inheritedFrameStyle() : draft.frameStyle,
                    id < 14 ? clamp(draft.borderWidth + change, 0, 6) : draft.borderWidth,
                    id >= 14 && id < 16 ? clamp(draft.frameCrop + change, 0, 8) : draft.frameCrop,
                    id >= 16 ? clamp(draft.cornerCrop + change, 0, 8) : draft.cornerCrop);
            } else if (id == 43) {
                try {
                    Map<String, Integer> found = new TreeMap<>(NativeLayoutPatches.groups());
                    found.putAll(PlayerPanelProfiles.groups());
                    groupNames.clear();
                    groupNames.addAll(found.keySet());
                    groups = true;
                    groupPage = 0;
                } catch (IOException failure) {
                    status = "Could not read saved layouts";
                }
            } else if (id == 44 || id == 45) {
                groupPage = clamp(groupPage + (id == 44 ? -1 : 1), 0, Math.max(0, (groupNames.size() - 1) / 4));
                confirm = -1;
            } else if (id == 40 || id == 41 || id == 42 || id >= 100) {
                if (confirm != id) {
                    confirm = id;
                    initGui();
                    return;
                }
                try {
                    if (id == 40) {
                        panel.select(selected);
                        panel.inherit();
                        global = false;
                        closing = true;
                    } else if (id == 41) PlayerPanelProfiles.resetGlobal();
                    else {
                        String group = id >= 100 ? groupNames.get(id - 100) : null;
                        NativeLayoutPatches.reset(group);
                        PlayerPanelProfiles.reset(group);
                    }
                    if (panel != null) panel.reload();
                    load();
                    status = "Reset saved; reopen native screen";
                } catch (IOException failure) {
                    status = "Reset failed; backups retained";
                }
                confirm = -1;
            }
            if (PlayerPanelSet.MAIN.equals(selected)) mainDraft = draft;
            initGui();
        }

        private PlayerPanelSet allocation() {
            int[] values = new int[panelDraft.entries.size()];
            for (int i = 0; i < values.length; i++) values[i] = counts.get(panelDraft.entries.get(i).id);
            return panelDraft.allocate(values, capacity);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == Keyboard.KEY_ESCAPE) closing = true;
            else if (nameField != null && nameField.isFocused()) nameField.textboxKeyTyped(character, key);
            // Inventory-close, drop, number keys and NEI shortcuts have no underlying owner here.
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (nameField != null) nameField.mouseClicked(x, y, button);
            super.mouseClicked(x, y, button);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float ticks) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            InventoryGuiTheme theme = InventoryGuiTheme.get();
            drawRect(0, 0, width, height, 0x99000000);
            drawRect(left - 2, top - 2, left + menuWidth + 2, top + 228, theme.settingsOuter);
            drawRect(left, top, left + menuWidth, top + 226, theme.settingsFill);
            drawCenteredString(fontRendererObj, "Inventory panels", width / 2, top + 7, theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                total() + " / " + capacity + " storage slots",
                width / 2,
                top + 22,
                theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                fontRendererObj
                    .trimStringToWidth(panelDraft.get(selected).name + " | Hotbar stays in Main", menuWidth - 16),
                width / 2,
                top + 36,
                theme.settingsText);
            if (page == 4) {
                if (nameField != null) {
                    nameField.drawTextBox();
                    label("Storage slots: " + counts.get(selected), 133);
                }
            } else if (page == 0) {
                label("Width: " + draft.width + " px", 133);
                label("Height: " + draft.height + " px", 161);
            } else if (page == 1) {
                label("Horizontal: " + draft.anchorX / 100 + "%", 105);
                label("Vertical: " + draft.anchorY / 100 + "%", 133);
            } else if (page == 2) {
                label("Border: " + draft.borderWidth, 127);
                label("Frame crop: " + draft.frameCrop, 151);
                label("Corners: " + draft.cornerCrop, 175);
            } else if (groups && groupNames.isEmpty()) label("No saved overrides", 124);
            if (!groups) label(fontRendererObj.trimStringToWidth(status, menuWidth - 16), page == 4 ? 194 : 190);
            super.drawScreen(mouseX, mouseY, ticks);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        private void label(String text, int y) {
            drawCenteredString(fontRendererObj, text, width / 2, top + y, InventoryGuiTheme.get().settingsText);
        }
    }
}
