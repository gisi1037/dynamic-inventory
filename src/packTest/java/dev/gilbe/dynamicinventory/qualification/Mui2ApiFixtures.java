package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.ResizeNode;
import com.cleanroommc.modularui.widget.sizer.StaticResizer;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;
import dev.gilbe.dynamicinventory.compat.mui2.InventoryScrollWidget;
import dev.gilbe.dynamicinventory.compat.mui2.PlayerInventoryWidgetGroup;
import dev.gilbe.dynamicinventory.compat.mui2.ResponsiveInventoryPanel;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Disposable-world external-consumer fixtures for the public MUI2 inventory widget API. */
@Mod(
    modid = "dinvmuiapifixtures",
    version = "1.0",
    name = "Dynamic Inventory MUI2 API Fixtures",
    dependencies = "required-after:dynamicinventory;required-after:modularui2")
public final class Mui2ApiFixtures {

    private static final Factory FACTORY = new Factory();
    private static int completedChecks;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GuiManager.registerFactory(FACTORY);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Command());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("dynamicinventory.mui2TransferRegression")) return;
        completedChecks = 0;
        try {
            completedChecks = Mui2TransferFixtures.run(
                FakePlayerFactory.getMinecraft(
                    MinecraftServer.getServer()
                        .worldServerForDimension(0)));
            AttachedChestLayoutFixtures.run();
            writeReport("transfer-regression", true, null);
        } catch (RuntimeException | Error failure) {
            writeReport("transfer-regression", false, failure);
            throw failure;
        }
    }

    private static final class FixtureData extends GuiData {

        final int mode;

        FixtureData(EntityPlayer player, int mode) {
            super(player);
            this.mode = mode;
        }
    }

    private static final class Factory extends AbstractUIFactory<FixtureData> {

        Factory() {
            super("dinvapi:player_inventory");
        }

        @Override
        public IGuiHolder<FixtureData> getGuiHolder(FixtureData data) {
            return new Holder();
        }

        @Override
        public void writeGuiData(FixtureData data, PacketBuffer buffer) {
            buffer.writeInt(data.mode);
        }

        @Override
        public FixtureData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            int mode = buffer.readInt();
            if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid MUI2 fixture mode " + mode);
            return new FixtureData(player, mode);
        }
    }

    private static final class Holder implements IGuiHolder<FixtureData> {

        @Override
        public ModularPanel buildUI(FixtureData data, PanelSyncManager sync, UISettings settings) {
            sync.bindPlayerInventory(data.getPlayer());
            settings.customContainer(ProbeContainer::new);
            SlotGroupWidget inventory = createInventory(data.mode, null);
            return ModularPanel.defaultPanel("mui2_api_fixture", 420, 260)
                .child(inventory);
        }

        @Override
        public ModularScreen createScreen(FixtureData data, ModularPanel mainPanel) {
            return new ModularScreen("dynamicinventory", mainPanel);
        }
    }

    /** Exposes the same hotbar/group lookup used by MUI2 quick-move handling for fixture assertions. */
    private static final class ProbeContainer extends ModularContainer {

        Slot find(EntityPlayer player, int index) {
            return findPlayerSlot(player, index);
        }
    }

    private static SlotGroupWidget createInventory(int mode, final List<Integer> receivedIndices) {
        SlotGroupWidget.SlotConsumer consumer = (index, slot) -> {
            if (receivedIndices != null) receivedIndices.add(Integer.valueOf(index));
            return slot;
        };
        if (mode == 1) return Mui2InventoryWidgets.playerInventory(consumer, 18, 4);
        if (mode == 2) return Mui2InventoryWidgets.playerInventory(consumer, 6, 2);
        return Mui2InventoryWidgets.playerInventory(consumer);
    }

    private static void assertFactory(int mode) {
        List<Integer> received = new ArrayList<Integer>();
        SlotGroupWidget group = createInventory(mode, received);
        int columns = mode == 1 ? 18 : mode == 2 ? 6 : 9;
        check(
            group.getChildren()
                .size() == 10,
            "inventory group has nine hotbar children and one storage viewport");
        for (int i = 0; i < 9; i++) check(
            group.getChildren()
                .get(i) instanceof ItemSlot,
            "hotbar widget " + i + " is an ItemSlot");
        check(
            group.getChildren()
                .get(9) instanceof InventoryScrollWidget,
            "storage widget uses the public scroll viewport");
        InventoryScrollWidget viewport = (InventoryScrollWidget) group.getChildren()
            .get(9);
        int storageSlots = InventoryLayout.storageSlots();
        int expectedTotal = InventoryLayout.mainSlots();
        check(
            viewport.getChildren()
                .size() == storageSlots,
            "viewport includes every storage slot");
        check(received.size() == expectedTotal, "consumer invoked once for each logical player slot");
        for (int i = 0; i < expectedTotal; i++) check(
            received.get(i)
                .intValue() == i,
            "consumer slot mapping remains sequential at index " + i);

        // The scroll extent is the grid's exact row count and catches column-count regressions.
        int expectedContentHeight = ((storageSlots + columns - 1) / columns) * ItemSlot.SIZE;
        check(
            viewport.getScrollArea()
                .getScrollY()
                .getScrollSize() == expectedContentHeight,
            "column count produces expected scroll content height");
    }

    private static void check(boolean condition, String message) {
        completedChecks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void assertBinding(EntityPlayer player, int protectedRawIndex) {
        PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(false), true);
        List<Integer> received = new ArrayList<Integer>();
        sync.bindPlayerInventory(player, (inventory, raw) -> {
            received.add(Integer.valueOf(raw));
            return new ModularSlot(inventory, raw).accessibility(raw != protectedRawIndex, raw != protectedRawIndex);
        });
        check(
            received.size() == InventoryLayout.mainSlots(),
            "binding invokes native callback for every ordinary slot");
        for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++) {
            int raw = InventoryLayout.rawIndex(logical);
            check(
                received.get(logical)
                    .intValue() == raw,
                "binding callback receives canonical raw index " + raw);
            ItemSlotSH handler = (ItemSlotSH) sync.findSyncHandlerNullable("player", logical);
            check(handler != null, "binding retains logical player sync key " + logical);
            ModularSlot slot = handler.getSlot();
            check(slot.getSlotIndex() == raw, "bound slot addresses canonical raw index " + raw);
            check(slot.getStack() == player.inventory.mainInventory[raw], "bound slot reads canonical stack " + raw);
            check(
                slot.isCanTake() == (raw != protectedRawIndex),
                "native take restriction applies only to its raw slot");
            check(
                slot.isCanPut() == (raw != protectedRawIndex),
                "native insert restriction applies only to its raw slot");
        }
    }

    private static void assertResponsiveBranches() {
        boolean expandBefore = InventoryViewSettings.expandMachineInventory;
        InventoryViewSettings.expandMachineInventory = true;
        try {
            ModularPanel first = ModularPanel.defaultPanel("responsive_first", 420, 180);
            ModularPanel second = ModularPanel.defaultPanel("responsive_second", 420, 260);
            PlayerInventoryWidgetGroup original = (PlayerInventoryWidgetGroup) createInventory(0, null);
            PlayerInventoryWidgetGroup secondary = (PlayerInventoryWidgetGroup) createInventory(0, null);
            Widget<?> foreign = new Widget<>().size(18, 18)
                .pos(5, 7);
            original.addChild(foreign, 0);
            first.child(original);
            second.child(secondary);
            layout(first);
            layout(second);
            ResponsiveInventoryPanel responsive = new ResponsiveInventoryPanel();
            responsive.resizePanels(Arrays.asList(first, second), 700);
            layout(first);
            layout(second);
            int firstRows = InventoryViewportLayout.rows(InventoryLayout.storageSlots(), 180, 700, true);
            int secondRows = InventoryViewportLayout.rows(InventoryLayout.storageSlots(), 260, 700, true);
            check(
                original.getInventoryViewport()
                    .getArea().height == firstRows * 18,
                "main panel inventory adapts to its own native height");
            check(
                secondary.getInventoryViewport()
                    .getArea().height == secondRows * 18,
                "secondary open panel inventory also adapts");
            check(foreign.getArea().ry == 7, "inserted first child is not mistaken for a hotbar slot");
            for (ItemSlot hotbar : original.getHotbarSlots()) check(
                hotbar.getArea().ry == firstRows * 18 + 4,
                "explicit hotbar reference receives responsive position");
            check(
                !responsive.resizePanels(Arrays.asList(first, second), 700),
                "unchanged live trees do not request resize loops");

            first.remove(original);
            PlayerInventoryWidgetGroup replacement = (PlayerInventoryWidgetGroup) createInventory(0, null);
            PlayerInventoryWidgetGroup sibling = (PlayerInventoryWidgetGroup) createInventory(0, null);
            first.child(replacement)
                .child(sibling);
            layout(first);
            responsive.resizePanels(Arrays.asList(first, second), 700);
            layout(first);
            check(
                replacement.getInventoryViewport()
                    .getArea().height == firstRows * 18,
                "replacement inventory is discovered after the original branch is removed");
            check(
                sibling.getInventoryViewport()
                    .getArea().height == firstRows * 18,
                "multiple live branches in one panel all adapt");
            check(
                first.getArea().height == 180 + (firstRows - replacement.getInitialRows()) * 18,
                "shared ancestors grow once rather than once per inventory branch");
            first.height(200);
            layout(first);
            responsive.resizePanels(Arrays.asList(first, second), 700);
            layout(first);
            int rebuiltRows = InventoryViewportLayout.rows(InventoryLayout.storageSlots(), 200, 700, true);
            check(
                first.getArea().height == 200 + (rebuiltRows - replacement.getInitialRows()) * 18,
                "dynamic native panel-height changes replace the old baseline");
            responsive.resizePanels(Collections.singletonList(first), 220);
            layout(first);
            layout(second);
            check(
                secondary.getInventoryViewport()
                    .getArea().height == secondary.getInitialRows() * 18 && second.getArea().height == 260,
                "closed cached panel restores native geometry before its baseline is forgotten");
            for (ItemSlot hotbar : secondary.getHotbarSlots()) check(
                hotbar.getArea().ry == secondary.getInitialRows() * 18 + 4,
                "closed cached panel restores its initial hotbar positions");
            responsive.resizePanels(Arrays.asList(first, second), 700);
            layout(first);
            layout(second);
            check(
                second.getArea().height == 260 + (secondRows - secondary.getInitialRows()) * 18,
                "reopening the same panel identity does not accumulate expanded height");
            first.remove(replacement);
            first.remove(sibling);
            responsive.resizePanels(Collections.singletonList(first), 700);
            layout(first);
            check(
                first.getArea().height == 200,
                "last removed inventory restores the live panel's updated native height");
            first.child(original);
            layout(first);
            responsive.resizePanels(Collections.singletonList(first), 700);
            layout(first);
            check(
                original.getArea().height == 76 + (rebuiltRows - original.getInitialRows()) * 18
                    && original.getInventoryViewport()
                        .getArea().height == rebuiltRows * 18,
                "reattaching the same inventory group identity starts from its original geometry");
            for (ItemSlot hotbar : original.getHotbarSlots()) check(
                hotbar.getArea().ry == rebuiltRows * 18 + 4,
                "reattached inventory group hotbar positions match the current viewport rows");
        } finally {
            InventoryViewSettings.expandMachineInventory = expandBefore;
        }
    }

    private static void layout(ModularPanel panel) {
        // Exercise MUI2's actual size/position calculations without initializing a screen or touching GL.
        StaticResizer root = new StaticResizer() {

            private final Area area = new Area(0, 0, 1000, 1000);

            @Override
            public Area getArea() {
                return area;
            }

            @Override
            public String getDebugDisplayName() {
                return "fixture screen area";
            }

            @Override
            public String toString() {
                return getDebugDisplayName();
            }
        };
        layout(panel, root, root);
    }

    private static void layout(IWidget widget, ResizeNode parent, ResizeNode root) {
        widget.resizer()
            .initialize(parent, root);
        // These trees exercise geometry without opening panels. The normal initResizing
        // callback also applies scroll themes and therefore requires a live screen.
        widget.resizer()
            .setResized(false);
        widget.resizer()
            .resize(false);
        for (IWidget child : widget.getChildren()) layout(child, widget.resizer(), root);
    }

    private static void writeReport(String mode, boolean success, Throwable failure) {
        File report = new File("diagnostics/dynamicinventory/mui2-api-selftest.json");
        File parent = report.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            System.err.println("Could not create MUI2 API diagnostic directory: " + parent);
            return;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"mode\": \"")
            .append(escape(mode))
            .append("\",\n")
            .append("  \"success\": ")
            .append(success)
            .append(",\n")
            .append("  \"checks\": ")
            .append(completedChecks);
        if (failure != null) json.append(",\n  \"failure\": \"")
            .append(escape(failure.toString()))
            .append("\"");
        json.append("\n}\n");
        try (FileOutputStream out = new FileOutputStream(report)) {
            out.write(
                json.toString()
                    .getBytes("UTF-8"));
        } catch (IOException error) {
            System.err.println("Could not write MUI2 API diagnostic report: " + error);
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') escaped.append("\\\\");
            else if (c == '"') escaped.append("\\\"");
            else if (c == '\r') escaped.append("\\r");
            else if (c == '\n') escaped.append("\\n");
            else if (c == '\t') escaped.append("\\t");
            else if (c < 0x20) escaped.append(String.format("\\u%04x", Integer.valueOf(c)));
            else escaped.append(c);
        }
        return escaped.toString();
    }

    private static final class Command extends CommandBase {

        @Override
        public String getCommandName() {
            return "dinvmuiapi";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvmuiapi standard|wide|narrow|selftest";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
            String world = MinecraftServer.getServer()
                .worldServerForDimension(0)
                .getWorldInfo()
                .getWorldName();
            if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification") || args.length != 1)
                throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Qualification world");
            if (player.inventory.getItemStack() != null)
                throw new IllegalArgumentException("Close the carried stack before opening the MUI2 fixture");

            final int mode;
            if ("standard".equals(args[0])) mode = 0;
            else if ("wide".equals(args[0])) mode = 1;
            else if ("narrow".equals(args[0])) mode = 2;
            else if ("selftest".equals(args[0])) mode = 0;
            else throw new IllegalArgumentException(getCommandUsage(sender));

            if ("selftest".equals(args[0])) {
                completedChecks = 0;
                try {
                    assertFactory(0);
                    assertFactory(1);
                    assertFactory(2);
                    assertResponsiveBranches();
                    assertBinding(player, player.inventory.currentItem);
                    // Pinned GT toolbox callbacks compare against raw 36 when held in Backhand.
                    // That callback must never disable the added storage slot at raw 37.
                    assertBinding(player, InventoryLayout.OFFHAND_INDEX);
                    completedChecks += Mui2TransferFixtures.run(player);
                    Mui1SlotFixtures.run(player);
                    player.closeScreen();
                    GuiManager.open(FACTORY, new FixtureData(player, mode), player);
                    checkPlayerLookup(player);
                    writeReport(args[0], true, null);
                    sender.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "DI MUI2 API PASS mode=" + args[0] + " checks=" + completedChecks));
                } catch (Throwable failure) {
                    writeReport(args[0], false, failure);
                    sender.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "DI MUI2 API FAIL mode=" + args[0] + " checks=" + completedChecks + " " + failure));
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    if (failure instanceof Error) throw (Error) failure;
                    throw new RuntimeException(failure);
                }
            } else {
                completedChecks = 0;
                try {
                    assertFactory(mode);
                    player.closeScreen();
                    GuiManager.open(FACTORY, new FixtureData(player, mode), player);
                    checkPlayerLookup(player);
                    writeReport(args[0], true, null);
                    sender.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "DI MUI2 API PASS mode=" + args[0] + " checks=" + completedChecks));
                } catch (Throwable failure) {
                    writeReport(args[0], false, failure);
                    sender.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "DI MUI2 API FAIL mode=" + args[0] + " checks=" + completedChecks + " " + failure));
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    if (failure instanceof Error) throw (Error) failure;
                    throw new RuntimeException(failure);
                }
            }
        }

        private static void checkPlayerLookup(EntityPlayerMP player) {
            if (!(player.openContainer instanceof ProbeContainer))
                throw new AssertionError("MUI2 fixture did not open its expected ModularContainer");
            ProbeContainer container = (ProbeContainer) player.openContainer;
            int last = InventoryLayout.mainSlots() - 1;
            checkFound(container, player, 0, "hotbar first slot resolves through the player group");
            checkFound(container, player, 9, "first storage slot resolves through the player group");
            checkFound(
                container,
                player,
                InventoryLayout.rawIndex(last),
                "last raw storage slot resolves through the player group");
        }

        private static void checkFound(ProbeContainer container, EntityPlayer player, int index, String message) {
            Slot slot = container.find(player, index);
            if (slot == null || slot.getSlotIndex() != index)
                throw new AssertionError(message + " (index=" + index + ")");
        }

    }
}
