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

    private static View currentFor(GuiScreen host) {
        if (active != null && !active.hostCurrent()) active = null;
        return active != null && active.host == host ? active : null;
    }

    public static boolean open(GuiContainer host, DetachedChestPanel panel) {
        Minecraft mc = Minecraft.getMinecraft();
        currentFor(host);
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
        return currentFor(host) != null;
    }

    public static boolean handleInput(GuiScreen host) {
        View view = currentFor(host);
        if (view == null) return false;
        if (!view.current()) view.closing = true;
        // Drain this entire input batch, including events after Save/Cancel and their release.
        view.handleInput();
        if (view.closing && !Mouse.isButtonDown(0) && !Mouse.isButtonDown(1) && !Mouse.isButtonDown(2)) active = null;
        return true;
    }

    /** A modal may open after the host's input batch has already started. Own its remaining events too. */
    public static boolean handleMouse(GuiScreen host) {
        View view = currentFor(host);
        if (view == null) return false;
        if (!view.current()) view.closing = true;
        if (!view.closing) view.handleMouseInput();
        return true;
    }

    public static boolean handleKeyboard(GuiScreen host) {
        View view = currentFor(host);
        if (view == null) return false;
        if (!view.current()) view.closing = true;
        if (!view.closing) view.handleKeyboardInput();
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        draw(event.gui, event.mouseX, event.mouseY, event.renderPartialTicks);
    }

    /** MUI2 owns rendering and consumes Forge's draw event before the usual Post callback. */
    public static void draw(GuiScreen host, int mouseX, int mouseY, float ticks) {
        View view = currentFor(host);
        if (view == null) return;
        if (!view.current()) {
            active = null;
            return;
        }
        if (view.closing) return;
        if (view.width != view.host.width || view.height != view.host.height)
            view.setWorldAndResolution(Minecraft.getMinecraft(), view.host.width, view.host.height);
        view.drawScreen(mouseX, mouseY, ticks);
    }

    private static final class View extends GuiScreen {

        private final GuiContainer host;
        private final Container container;
        private final List<Slot> slots;
        private final DetachedChestPanel panel;
        private final int capacity;
        private final int storageCapacity;
        private final java.util.Set<Integer> excluded;
        private PlayerPanelProfile draft, mainDraft;
        private PlayerPanelSet panelDraft;
        private PlayerPanelSet initialPanels;
        private PlayerPanelProfile initialMain;
        private final Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        private String selected;
        private int panelPage, panelRows = 4;
        private GuiTextField nameField;
        private GuiTextField presetNameField;
        private String presetName = "";
        private int presetPage;
        private int presetRows = 3;
        private List<String> presetNames = new ArrayList<>();
        private final Map<String, GuiTextField> slotFields = new java.util.LinkedHashMap<>();
        private final Map<String, GuiTextField> geometryFields = new java.util.LinkedHashMap<>();
        private int listWidth, editorX, menuHeight, nameY, sizeY, positionY, toolsY, layoutY, footerY;
        private boolean roomy;
        private boolean dispatchingClick, rebuildPending;
        private boolean global, closing, groups;
        private int page, confirm = -1, groupPage, left, top, menuWidth;
        private String status = "";
        private final List<String> groupNames = new ArrayList<>();

        private View(GuiContainer host, DetachedChestPanel panel) {
            this.host = host;
            this.panel = panel;
            container = host.inventorySlots;
            slots = new ArrayList<>(container.inventorySlots);
            storageCapacity = InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory)
                .storageSlots();
            excluded = dev.gilbe.dynamicinventory.inventory.HotbarBanks
                .excluded(Minecraft.getMinecraft().thePlayer.inventory);
            capacity = storageCapacity - excluded.size();
            global = panel == null;
            selected = panel == null ? PlayerPanelSet.MAIN : panel.selected();
            load();
        }

        private void load() {
            panelDraft = global ? PlayerPanelProfiles.panels() : PlayerPanelProfiles.panels(panel.key());
            if (panelDraft.entries.stream()
                .noneMatch(entry -> entry.id.equals(selected))) selected = PlayerPanelSet.MAIN;
            mainDraft = global ? PlayerPanelProfiles.global() : PlayerPanelProfiles.resolve(panel.key());
            if (!global && panel != null) {
                mainDraft = panel.displayedProfile(PlayerPanelSet.MAIN);
                for (PlayerPanelSet.Entry entry : new ArrayList<>(panelDraft.entries))
                    panelDraft = panelDraft.replace(entry.profile(panel.key(), panel.displayedProfile(entry.id)));
            }
            draft = selected.equals(PlayerPanelSet.MAIN) ? mainDraft
                : global ? panelDraft.get(selected).global
                    : panelDraft.get(selected)
                        .resolve(panel.key());
            counts.clear();
            for (PlayerPanelSet.Entry entry : panelDraft.entries) counts.put(
                entry.id,
                panelDraft.members(entry.id, storageCapacity, excluded)
                    .size());
            panelPage = panelDraft.entries.indexOf(panelDraft.get(selected)) / panelRows;
            initialPanels = panelDraft;
            initialMain = mainDraft;
        }

        private boolean hostCurrent() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.currentScreen == host && mc.thePlayer != null
                && mc.thePlayer.openContainer == container
                && host.inventorySlots == container;
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
                    .storageSlots() - dev.gilbe.dynamicinventory.inventory.HotbarBanks.reserved(mc.thePlayer.inventory)
                    != capacity
                || !excluded.equals(dev.gilbe.dynamicinventory.inventory.HotbarBanks.excluded(mc.thePlayer.inventory)))
                return false;
            for (int i = 0; i < slots.size(); i++)
                if (container.inventorySlots.get(i) != slots.get(i) || slots.get(i).slotNumber != i) return false;
            return true;
        }

        @Override
        public void initGui() {
            // GuiScreen.mouseClicked iterates this live list. Replacing it during
            // Add lets the same click activate the new Add button repeatedly.
            if (dispatchingClick) {
                rebuildPending = true;
                return;
            }
            menuWidth = Math.min(560, width - 16);
            roomy = height >= 308;
            presetRows = roomy ? 5 : 3;
            menuHeight = roomy ? 300 : 232;
            int rows = roomy ? 5 : 4;
            if (rows != panelRows) panelPage = panelDraft.entries.indexOf(panelDraft.get(selected)) / rows;
            panelRows = rows;
            panelPage = clamp(panelPage, 0, (panelDraft.entries.size() - 1) / panelRows);
            nameY = roomy ? 110 : 88;
            sizeY = roomy ? 152 : 114;
            positionY = roomy ? 196 : 140;
            toolsY = roomy ? 224 : 160;
            layoutY = roomy ? 248 : 180;
            footerY = menuHeight - 22;
            left = (width - menuWidth) / 2;
            top = Math.max(2, (height - menuHeight) / 2);
            listWidth = (menuWidth - 28) / 3;
            editorX = 20 + listWidth;
            buttonList.clear();
            int scopeWidth = (menuWidth - 20) / 2;
            add(21, 8, roomy ? 42 : 35, scopeWidth, "Make global default");
            ((GuiButton) buttonList.get(0)).enabled = !global && total() == capacity;
            add(40, 12 + scopeWidth, roomy ? 42 : 35, scopeWidth, "Reset to global default");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = panel != null;
            String[] tabs = { "Panels", "Style", "Profiles", "Reset" };
            int[] pages = { 0, 2, 4, 3 };
            int tabWidth = (menuWidth - 16) / 4;
            for (int i = 0; i < tabs.length; i++) {
                add(30 + pages[i], 8 + i * tabWidth, roomy ? 70 : 56, tabWidth - 2, tabs[i]);
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = page != pages[i];
            }
            nameField = null;
            presetNameField = null;
            slotFields.clear();
            geometryFields.clear();
            if (page == 0) {
                panelControls();
            } else if (page == 2) {
                add(
                    11,
                    8,
                    roomy ? 110 : 86,
                    menuWidth - 16,
                    "Frame: " + (draft.frameStyle < 0 ? "Follow interface" : NativePanelFrame.NAMES[draft.frameStyle]));
                adjusters(12, roomy ? 140 : 110);
                adjusters(14, roomy ? 168 : 134);
                adjusters(16, roomy ? 196 : 158);
                adjusters(18, roomy ? 224 : 182);
            } else if (page == 4) {
                presetNames = PanelLayoutPresets.names();
                presetPage = clamp(presetPage, 0, Math.max(0, (presetNames.size() - 1) / presetRows));
                presetNameField = textField(8, nameY, menuWidth - 128, presetName, 32);
                add(
                    70,
                    menuWidth - 116,
                    nameY,
                    108,
                    PanelLayoutPresets.contains(presetName) ? "Update profile" : "Save profile");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = !presetName.trim()
                    .isEmpty() && total() == capacity;
                for (int i = presetPage * presetRows; i
                    < Math.min(presetNames.size(), (presetPage + 1) * presetRows); i++) {
                    int y = nameY + 26 + i % presetRows * 22;
                    add(
                        400 + i,
                        8,
                        y,
                        menuWidth - 44,
                        fontRendererObj.trimStringToWidth("Load: " + presetNames.get(i), menuWidth - 52));
                    add(500 + i, menuWidth - 32, y, 24, "X");
                }
                add(71, 8, layoutY, 24, "<");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = presetPage > 0;
                add(72, menuWidth - 32, layoutY, 24, ">");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = (presetPage + 1) * presetRows
                    < presetNames.size();
            } else if (!groups) {
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
            add(0, 8, footerY, (menuWidth - 20) / 2, "Cancel");
            add(50, 12 + (menuWidth - 20) / 2, footerY, (menuWidth - 20) / 2, "Apply / Done");
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
            for (int i = panelPage * panelRows; i
                < Math.min(panelDraft.entries.size(), panelPage * panelRows + panelRows); i++) {
                PlayerPanelSet.Entry entry = panelDraft.entries.get(i);
                add(
                    200 + i,
                    8,
                    nameY + i % panelRows * (roomy ? 28 : 22),
                    listWidth - 70,
                    fontRendererObj
                        .trimStringToWidth((entry.id.equals(selected) ? "> " : "") + entry.name, listWidth - 78));
                GuiTextField field = textField(
                    8 + listWidth - 66,
                    nameY + i % panelRows * (roomy ? 28 : 22),
                    44,
                    Integer.toString(counts.get(entry.id)),
                    3);
                slotFields.put(entry.id, field);
                if (PlayerPanelSet.MAIN.equals(entry.id)) field.setEnabled(false);
                add(300 + i, 8 + listWidth - 20, nameY + i % panelRows * (roomy ? 28 : 22), 20, "X");
                ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = !PlayerPanelSet.MAIN.equals(entry.id);
            }
            add(62, 8, layoutY, 20, "<");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = panelPage > 0;
            add(60, 32, layoutY, listWidth - 48, "+ Add panel");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = panelDraft.entries.size() < 64;
            add(63, 8 + listWidth - 20, layoutY, 20, ">");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = panelPage
                < (panelDraft.entries.size() - 1) / panelRows;
            int editorWidth = menuWidth - editorX - 8;
            nameField = textField(editorX, nameY, editorWidth - 68, panelDraft.get(selected).name, 32);
            add(68, editorX + editorWidth - 64, nameY, 44, draft.visible ? "Hide" : "Show");
            add(67, editorX + editorWidth - 20, nameY, 20, "X");
            ((GuiButton) buttonList.get(buttonList.size() - 1)).enabled = !PlayerPanelSet.MAIN.equals(selected);
            int half = editorWidth / 2;
            int inset = roomy ? 0 : 40;
            geometryFields
                .put("width", textField(editorX + inset, sizeY, half - inset - 8, Integer.toString(draft.width), 4));
            geometryFields.put(
                "height",
                textField(editorX + half + inset, sizeY, half - inset, Integer.toString(draft.height), 4));
            geometryFields.put("x", textField(editorX + inset, positionY, half - inset - 8, percent(draft.anchorX), 6));
            geometryFields
                .put("y", textField(editorX + half + inset, positionY, half - inset, percent(draft.anchorY), 6));
            add(10, editorX, toolsY, editorWidth, "Center selected panel");
            add(1, editorX, layoutY, editorWidth, mainDraft.detached ? "Main: Detached" : "Main: Attached");
        }

        private GuiTextField textField(int x, int y, int width, String value, int length) {
            GuiTextField field = new GuiTextField(fontRendererObj, left + x, top + y, width, 18);
            field.setMaxStringLength(length);
            field.setText(value);
            return field;
        }

        private static String percent(int value) {
            return java.math.BigDecimal.valueOf(value, 2)
                .stripTrailingZeros()
                .toPlainString();
        }

        private boolean readFields() {
            Map<String, Integer> edited = new java.util.LinkedHashMap<>(counts);
            for (Map.Entry<String, GuiTextField> field : slotFields.entrySet()) {
                if (PlayerPanelSet.MAIN.equals(field.getKey())) continue;
                String value = field.getValue()
                    .getText();
                if (!value.matches("[0-9]{1,3}") || Integer.parseInt(value) > capacity) {
                    status = "Slot counts must be 0-" + capacity;
                    return false;
                }
                edited.put(field.getKey(), Integer.parseInt(value));
            }
            int allocated = 0;
            for (Map.Entry<String, Integer> entry : edited.entrySet())
                if (!PlayerPanelSet.MAIN.equals(entry.getKey())) allocated += entry.getValue();
            if (allocated > capacity) {
                status = "Not enough slots in Main; reduce another panel first";
                return false;
            }
            edited.put(PlayerPanelSet.MAIN, capacity - allocated);
            if (!geometryFields.isEmpty()) {
                try {
                    int w = Integer.parseInt(
                        geometryFields.get("width")
                            .getText());
                    int h = Integer.parseInt(
                        geometryFields.get("height")
                            .getText());
                    String x = geometryFields.get("x")
                        .getText(),
                        y = geometryFields.get("y")
                            .getText();
                    if (!x.matches("[0-9]{1,3}(?:\\.[0-9]{1,2})?") || !y.matches("[0-9]{1,3}(?:\\.[0-9]{1,2})?"))
                        throw new IllegalArgumentException();
                    int ax = new java.math.BigDecimal(x).movePointRight(2)
                        .intValueExact();
                    int ay = new java.math.BigDecimal(y).movePointRight(2)
                        .intValueExact();
                    if (w < 26 || w > 340 || h < (PlayerPanelSet.MAIN.equals(selected) ? 60 : 40) || h > 262)
                        throw new IllegalArgumentException();
                    draft = draft.withBounds(w, h, ax, ay);
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                    status = "Use valid pixel sizes and positions from 0-100%";
                    return false;
                }
            }
            counts.putAll(edited);
            if (slotFields.containsKey(PlayerPanelSet.MAIN)) slotFields.get(PlayerPanelSet.MAIN)
                .setText(Integer.toString(counts.get(PlayerPanelSet.MAIN)));
            return true;
        }

        private boolean storeDraft() {
            if (!readFields()) return false;
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

        private void removePanel(String id) {
            if (PlayerPanelSet.MAIN.equals(id)) return;
            counts.put(PlayerPanelSet.MAIN, counts.get(PlayerPanelSet.MAIN) + counts.remove(id));
            panelDraft = panelDraft.remove(id);
            if (id.equals(selected)) choose(PlayerPanelSet.MAIN);
            status = "Panel removed; slots returned to Main";
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
            buttonList.add(new CompactButton(id, left + x, top + y, w, label));
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
            if (id >= 400 && id < 532) {
                try {
                    if (id < 432) {
                        presetName = presetNames.get(id - 400);
                        PanelLayoutPresets.load(presetName, panel == null ? null : panel.key());
                        if (panel != null) panel.reload();
                        selected = PlayerPanelSet.MAIN;
                        load();
                        status = "Loaded global profile: " + presetName;
                    } else if (id >= 500) {
                        String removed = presetNames.get(id - 500);
                        PanelLayoutPresets.delete(removed);
                        if (presetName.equals(removed)) presetName = "";
                        status = "Deleted profile: " + removed;
                    }
                } catch (IOException | IllegalArgumentException failure) {
                    status = failure.getMessage() == null ? "Could not change saved profile" : failure.getMessage();
                }
                initGui();
                return;
            }
            // Inheriting discards the pending draft, including incomplete numeric fields.
            if (id != 40 && !storeDraft()) return;
            if (id == 70) {
                try {
                    if (total() != capacity) throw new IllegalArgumentException("Allocate all slots first");
                    presetName = presetNameField.getText()
                        .trim();
                    PanelLayoutPresets.save(
                        presetName,
                        allocation(),
                        mainDraft,
                        global ? null : panel.key(),
                        panel == null ? PlayerPanelProfiles.globalReference() : panel.layoutReference());
                    status = "Saved profile: " + presetName;
                } catch (IOException | IllegalArgumentException failure) {
                    status = failure.getMessage() == null ? "Could not save layout profile" : failure.getMessage();
                }
            } else if (id == 71 || id == 72) {
                presetPage = Math.max(0, presetPage + (id == 71 ? -1 : 1));
            } else if (id >= 300) {
                removePanel(panelDraft.entries.get(id - 300).id);
            } else if (id >= 200) {
                choose(panelDraft.entries.get(id - 200).id);
                page = 0;
                panelPage = (id - 200) / panelRows;
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
                panelPage = (panelDraft.entries.size() - 1) / panelRows;
                mainDraft = mainDraft.detached(true);
            } else if (id == 62 || id == 63)
                panelPage = clamp(panelPage + (id == 62 ? -1 : 1), 0, (panelDraft.entries.size() - 1) / panelRows);
            else if (id == 64 || id == 65) counts.put(
                selected,
                clamp(counts.get(selected) + (id == 64 ? -1 : 1) * (isShiftKeyDown() ? 9 : 1), 0, capacity));
            else if (id == 66) {
                int other = total() - counts.get(PlayerPanelSet.MAIN);
                counts.put(PlayerPanelSet.MAIN, Math.max(0, capacity - other));
            } else if (id == 67) {
                removePanel(selected);
            } else if (id == 68) {
                draft = draft.visible(!draft.visible);
                if (!draft.visible) {
                    mainDraft = mainDraft.detached(true);
                    if (PlayerPanelSet.MAIN.equals(selected)) draft = draft.detached(true);
                }
            } else if (id >= 30 && id <= 34) {
                page = id == 31 ? 0 : id - 30;
                groups = false;
                confirm = -1;
            } else if (id == 21) {
                try {
                    if (total() != capacity) throw new IOException("Allocate all slots first");
                    PlayerPanelSet allocated = allocation();
                    if (panel != null) for (PlayerPanelSet.Entry entry : new ArrayList<>(allocated.entries))
                        allocated = allocated.replace(entry.profile(null, entry.resolve(panel.key())));
                    if (panel != null) panel.apply(allocated, mainDraft, true);
                    else PlayerPanelProfiles.applyPanels(allocated, null, mainDraft);
                    global = panel == null;
                    load();
                    status = "Global default saved; other custom layouts kept";
                } catch (IOException failure) {
                    status = failure.getMessage() == null ? "Save failed; previous layout retained"
                        : failure.getMessage();
                }
            } else if (id == 50) {
                try {
                    if (total() != capacity) throw new IOException("Allocate all slots first");
                    PlayerPanelSet allocated = allocation();
                    if (allocated.toJson()
                        .equals(initialPanels.toJson())
                        && mainDraft.toJson()
                            .equals(initialMain.toJson())) {
                        closing = true;
                        return;
                    }
                    if (panel != null && panel.isInherited()
                        && allocated.toJson()
                            .equals(initialPanels.toJson())
                        && mainDraft.detached(initialMain.detached)
                            .toJson()
                            .equals(initialMain.toJson()))
                        PlayerPanelProfiles.setGlobal(
                            PlayerPanelProfiles.global()
                                .detached(mainDraft.detached));
                    else if (panel == null) PlayerPanelProfiles.applyPanels(allocated, null, mainDraft);
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
                if (id < 4) w = clamp(w + step, 26, 340);
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
            } else if (id == 18 || id == 19) {
                draft = draft.withMinimumMargin(clamp(draft.minimumMargin + (id == 18 ? -1 : 1), 4, 18));
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
                if (id != 40 && confirm != id) {
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
                    status = "Global defaults applied";
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
            return panelDraft.allocate(values, storageCapacity, excluded);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == Keyboard.KEY_ESCAPE) {
                closing = true;
                return;
            }
            List<GuiTextField> fields = fields();
            for (GuiTextField field : fields) if (field.isFocused()) {
                if (field == slotFields.get(PlayerPanelSet.MAIN)) break;
                String before = field.getText();
                field.textboxKeyTyped(character, key);
                if (field == presetNameField) {
                    presetName = field.getText();
                    for (Object entry : buttonList) {
                        GuiButton button = (GuiButton) entry;
                        if (button.id == 70) {
                            button.displayString = PanelLayoutPresets.contains(presetName) ? "Update profile"
                                : "Save profile";
                            button.enabled = !presetName.trim()
                                .isEmpty() && total() == capacity;
                        }
                    }
                    return;
                }
                if (!before.equals(field.getText())) {
                    boolean valid = readFields();
                    if (valid) status = "";
                    for (Object entry : buttonList) {
                        GuiButton button = (GuiButton) entry;
                        if (button.id == 50 || button.id == 21)
                            button.enabled = valid && total() == capacity && (button.id != 21 || !global);
                    }
                }
                break;
            }
        }

        private List<GuiTextField> fields() {
            List<GuiTextField> fields = new ArrayList<>(slotFields.values());
            fields.addAll(geometryFields.values());
            if (nameField != null) fields.add(nameField);
            if (presetNameField != null) fields.add(presetNameField);
            return fields;
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (x < left - 2 || y < top - 2 || x >= left + menuWidth + 2 || y >= top + menuHeight + 2) {
                closing = true;
                return;
            }
            for (GuiTextField field : fields()) field.mouseClicked(x, y, button);
            dispatchingClick = true;
            try {
                super.mouseClicked(x, y, button);
            } finally {
                dispatchingClick = false;
                if (rebuildPending) {
                    rebuildPending = false;
                    initGui();
                }
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float ticks) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            InventoryGuiTheme theme = InventoryGuiTheme.get();
            drawRect(0, 0, width, height, 0x99000000);
            drawRect(left - 2, top - 2, left + menuWidth + 2, top + menuHeight + 2, theme.settingsOuter);
            drawRect(left, top, left + menuWidth, top + menuHeight, theme.settingsFill);
            drawCenteredString(fontRendererObj, "Inventory panels", width / 2, top + 7, theme.settingsText);
            drawCenteredString(
                fontRendererObj,
                total() + " / " + capacity + " slots | Main keeps remaining slots",
                width / 2,
                top + 22,
                theme.settingsText);
            if (page == 0) {
                fontRendererObj.drawString("Panels / slots", left + 8, top + nameY - 12, theme.settingsText);
                fontRendererObj.drawString("Selected panel", left + editorX, top + nameY - 12, theme.settingsText);
                for (GuiTextField field : fields()) field.drawTextBox();
                int half = (menuWidth - editorX - 8) / 2;
                fontRendererObj.drawString(
                    roomy ? "Width (pixels)" : "Width",
                    left + editorX,
                    top + sizeY + (roomy ? -12 : 5),
                    theme.settingsText);
                fontRendererObj.drawString(
                    roomy ? "Height (pixels)" : "Height",
                    left + editorX + half,
                    top + sizeY + (roomy ? -12 : 5),
                    theme.settingsText);
                fontRendererObj
                    .drawString("X (%)", left + editorX, top + positionY + (roomy ? -12 : 5), theme.settingsText);
                fontRendererObj.drawString(
                    "Y (%)",
                    left + editorX + half,
                    top + positionY + (roomy ? -12 : 5),
                    theme.settingsText);
                drawRect(
                    left + editorX - 6,
                    top + nameY - 2,
                    left + editorX - 5,
                    top + layoutY + 18,
                    theme.settingsOuter);
            } else if (page == 2) {
                label("Bevel width: " + draft.borderWidth, roomy ? 145 : 115);
                label("Frame crop: " + draft.frameCrop, roomy ? 173 : 139);
                label("Corners: " + draft.cornerCrop, roomy ? 201 : 163);
                label("Minimum bevel margin: " + draft.minimumMargin, roomy ? 229 : 187);
            } else if (page == 4) {
                fontRendererObj
                    .drawString("Save the current panel layout as:", left + 8, top + nameY - 12, theme.settingsText);
                presetNameField.drawTextBox();
                if (presetNames.isEmpty()) label("No saved profiles", nameY + 52);
            } else if (groups && groupNames.isEmpty()) label("No saved overrides", 124);
            if (!groups) label(fontRendererObj.trimStringToWidth(status, menuWidth - 16), footerY - 10);
            super.drawScreen(mouseX, mouseY, ticks);
            if (page == 0) for (Object entry : buttonList) {
                GuiButton button = (GuiButton) entry;
                if ((button.id == 68 || button.id == 67 || button.id >= 300) && mouseX >= button.xPosition
                    && mouseX < button.xPosition + button.width
                    && mouseY >= button.yPosition
                    && mouseY < button.yPosition + button.height)
                    drawHoveringText(
                        java.util.Collections.singletonList(
                            button.id == 68
                                ? (draft.visible ? "Hide panel in this layout" : "Show panel in this layout")
                                : "Remove panel; return slots to Main"),
                        mouseX,
                        mouseY,
                        fontRendererObj);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        private void label(String text, int y) {
            drawCenteredString(fontRendererObj, text, width / 2, top + y, InventoryGuiTheme.get().settingsText);
        }

        private static final class CompactButton extends GuiButton {

            CompactButton(int id, int x, int y, int width, String label) {
                super(id, x, y, width, 18, label);
            }

            @Override
            public void drawButton(Minecraft mc, int mouseX, int mouseY) {
                if (!visible) return;
                mc.getTextureManager()
                    .bindTexture(buttonTextures);
                GL11.glColor4f(1, 1, 1, 1);
                field_146123_n = mouseX >= xPosition && mouseX < xPosition + width
                    && mouseY >= yPosition
                    && mouseY < yPosition + height;
                int v = 46 + getHoverState(field_146123_n) * 20;
                int t = height / 2, b = height - t;
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                buttonRow(yPosition, v, t);
                buttonRow(yPosition + t, v + 20 - b, b);
                mouseDragged(mc, mouseX, mouseY);
                int color = !enabled ? 0xa0a0a0 : field_146123_n ? 0xffffa0 : 0xe0e0e0;
                drawCenteredString(
                    mc.fontRenderer,
                    displayString,
                    xPosition + width / 2,
                    yPosition + (height - 8) / 2,
                    color);
            }

            private void buttonRow(int y, int v, int height) {
                drawTexturedModalRect(xPosition, y, 0, v, 2, height);
                for (int offset = 2; offset < width - 2; offset += 196)
                    drawTexturedModalRect(xPosition + offset, y, 2, v, Math.min(196, width - 2 - offset), height);
                drawTexturedModalRect(xPosition + width - 2, y, 198, v, 2, height);
            }
        }
    }
}
