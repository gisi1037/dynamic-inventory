package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gilbe.dynamicinventory.api.InventoryTransferPolicy;
import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.InventoryUiProtocol;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.api.PlayerInventorySlotFactory;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Disposable-world API fixtures. These containers rely on the public adapter and normal lifecycle hooks. */
@Mod(
    modid = "dinvuiapifixtures",
    version = "1.0",
    name = "Dynamic Inventory UI API Fixtures",
    dependencies = "required-after:dynamicinventory")
public final class UiApiFixtures implements IGuiHandler {

    private static final String NATIVE_ID = "dinvuiapifixtures:native-chest";
    private static final String FILTERED_ID = "dinvuiapifixtures:filtered-machine";
    private static final String PROTOCOL_LIMIT_ID = "dinvuiapifixtures:protocol-limit";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        InventoryUi.register(
            InventoryUiAdapter.builder(NATIVE_ID, NativeContainer.class.getName())
                .gui(UiApiFixtures.class.getName() + "$NativeGui")
                .expectedNativeSlots(63)
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(0)
                        .minimumHeaderHeight(0)
                        .backgroundInset(3)
                        .build())
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder(FILTERED_ID, FilteredContainer.class.getName())
                .gui(UiApiFixtures.class.getName() + "$FilteredGui")
                .expectedNativeSlots(39)
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder(PROTOCOL_LIMIT_ID, ProtocolLimitContainer.class.getName())
                .nativeTransfers()
                .build());
        // Fixtures follow the public startup contract on both sides; commands never alter the negotiated registry.
        InventoryUi.register(
            InventoryUiAdapter.builder("dinvuiapifixtures:ambiguous-owner", AmbiguousContainer.class.getName())
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder("dinvuiapifixtures:hierarchy-base", HierarchyBaseContainer.class.getName())
                .includeSubclasses()
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder("dinvuiapifixtures:hierarchy-child", HierarchyChildContainer.class.getName())
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder("dinvuiapifixtures:slot-factory", FactoryContainer.class.getName())
                .additionalSlots(
                    (container, canonical, raw) -> ((FactoryContainer) container).factory
                        .create(container, canonical, raw))
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
        NetworkRegistry.INSTANCE.registerGuiHandler(this, this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Command(this));
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 0) return new NativeContainer(player.inventory, 27);
        if (id == 1) return new FilteredContainer(player.inventory);
        if (id == 2) return new UnknownContainer(player.inventory);
        if (id == 3) return new net.minecraft.inventory.ContainerChest(
            player.inventory,
            new InventoryBasic("Automatic scroll fixture", true, 27));
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 0) return new NativeGui(new NativeContainer(player.inventory, 27));
        if (id == 1) return new FilteredGui(new FilteredContainer(player.inventory));
        if (id == 2) return new UnknownGui(new UnknownContainer(player.inventory));
        if (id == 3) return new AutomaticStorageGui(player);
        return null;
    }

    /** Native 27-slot chest followed by the complete vanilla 36-slot player section. */
    public static class NativeContainer extends FixtureContainer {

        public NativeContainer(net.minecraft.entity.player.InventoryPlayer player, int machineSlots) {
            super(player, machineSlots, false);
        }

        private void addRepairSlot() {
            addSlotToContainer(new Slot(new InventoryBasic("Repaired fixture", true, 1), 0, 0, 0));
        }
    }

    /** Three-slot input/fuel/output machine with explicit input and output rules. */
    public static final class FilteredContainer extends FixtureContainer {

        public FilteredContainer(net.minecraft.entity.player.InventoryPlayer player) {
            super(player, 3, true);
        }
    }

    /** Subclass used to prove that exact class matching leaves unregistered variants untouched. */
    public static final class UnknownContainer extends NativeContainer {

        public UnknownContainer(net.minecraft.entity.player.InventoryPlayer player) {
            super(player, 27);
        }
    }

    public static final class AmbiguousContainer extends NativeContainer {

        public AmbiguousContainer(net.minecraft.entity.player.InventoryPlayer player) {
            super(player, 27);
            addSlotToContainer(new Slot(new net.minecraft.entity.player.InventoryPlayer(player.player), 0, 0, 0));
        }
    }

    /** One adapter exercises distinct pure factories without changing global registrations between tests. */
    public static final class FactoryContainer extends FixtureContainer {

        final PlayerInventorySlotFactory factory;

        FactoryContainer(InventoryPlayer player, PlayerInventorySlotFactory factory) {
            super(player, 1, false);
            this.factory = factory;
        }
    }

    private static final class RuleSlot extends Slot {

        final int logical;
        int changed;
        int pickups;

        RuleSlot(InventoryPlayer inventory, int raw) {
            super(inventory, raw, -10000, -10000);
            logical = InventoryLayout.ordinaryIndex(raw);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return logical != 36;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return logical != 37;
        }

        @Override
        public int getSlotStackLimit() {
            return 2;
        }

        @Override
        public void onSlotChanged() {
            changed++;
            super.onSlotChanged();
        }

        @Override
        public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
            pickups++;
            super.onPickupFromSlot(player, stack);
        }
    }

    /** Large disposable-only container used to exercise the signed-short wire boundary. */
    public static final class ProtocolLimitContainer extends Container {

        public ProtocolLimitContainer(net.minecraft.entity.player.InventoryPlayer player, int nativeSlotCount) {
            super();
            int machineSlots = nativeSlotCount - InventoryLayout.LEGACY_MAIN_SLOTS;
            if (machineSlots < 0) throw new IllegalArgumentException("nativeSlotCount below legacy section");
            InventoryBasic machine = new InventoryBasic("UI API protocol boundary", true, machineSlots);
            for (int i = 0; i < machineSlots; i++) addSlotToContainer(new Slot(machine, i, 0, 0));
            for (int logical = 9; logical < 36; logical++) addSlotToContainer(new Slot(player, logical, 0, 0));
            for (int logical = 0; logical < 9; logical++) addSlotToContainer(new Slot(player, logical, 0, 0));
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int id) {
            return null;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    public static class HierarchyBaseContainer extends Container {

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int id) {
            return null;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    public static class HierarchyChildContainer extends HierarchyBaseContainer {
    }

    public static final class HierarchyGrandchildContainer extends HierarchyChildContainer {
    }

    private abstract static class FixtureContainer extends Container {

        private static final InventoryTransferPolicy TO_PLAYER_INVENTORY = InventoryTransfers.toPlayerInventory();

        final InventoryBasic machine;
        private final int nativeMachineSlots;

        FixtureContainer(net.minecraft.entity.player.InventoryPlayer player, int machineSlots, boolean filtered) {
            machine = new InventoryBasic("UI API Fixture", true, machineSlots);
            nativeMachineSlots = machineSlots;
            if (filtered) {
                addSlotToContainer(new FilteredSlot(machine, 0, 56, 26, 0));
                addSlotToContainer(new FilteredSlot(machine, 1, 56, 62, 1));
                addSlotToContainer(new FilteredSlot(machine, 2, 116, 44, 2));
            } else {
                for (int i = 0; i < machineSlots; i++)
                    addSlotToContainer(new Slot(machine, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
            }
            for (int logical = 9; logical < 36; logical++)
                addSlotToContainer(new Slot(player, logical, 8 + (logical - 9) % 9 * 18, 84 + (logical - 9) / 9 * 18));
            for (int logical = 0; logical < 9; logical++)
                addSlotToContainer(new Slot(player, logical, 8 + logical * 18, 142));
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int id) {
            PlayerInventoryBinding binding = InventoryUi.getBinding(this);
            // This null return makes added-slot extraction exercise the generic registered policy in slotClick.
            if (binding != null && id >= binding.getNativeSlotCount()) return null;
            if (id < 0 || id >= nativeMachineSlots || binding == null) return null;
            return TO_PLAYER_INVENTORY.transfer(this, player, id, binding);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class FilteredSlot extends Slot {

        private final int kind;

        FilteredSlot(IInventory inventory, int index, int x, int y, int kind) {
            super(inventory, index, x, y);
            this.kind = kind;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (kind == 2) return false;
            if (kind == 0) return stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.iron_ore);
            return stack != null && stack.getItem() == Items.coal;
        }
    }

    @SideOnly(Side.CLIENT)
    private abstract static class FixtureGui extends net.minecraft.client.gui.inventory.GuiContainer {

        FixtureGui(Container container, int height) {
            super(container);
            xSize = 176;
            ySize = height;
        }

        @Override
        protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
            drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff777777);
            drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + ySize - 4, 0xffc6c6c6);
            drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + 20, 0xffa0a0a0);
        }

        @Override
        protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
            fontRendererObj.drawString(getTitle(), 8, 6, 0x404040);
        }

        abstract String getTitle();
    }

    @SideOnly(Side.CLIENT)
    public static final class NativeGui extends FixtureGui {

        public NativeGui(NativeContainer container) {
            super(container, 222);
        }

        @Override
        String getTitle() {
            return "Native chest";
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class FilteredGui extends FixtureGui {

        public FilteredGui(FilteredContainer container) {
            super(container, 166);
        }

        @Override
        String getTitle() {
            return "Filtered machine";
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class UnknownGui extends FixtureGui {

        public UnknownGui(UnknownContainer container) {
            super(container, 222);
        }

        @Override
        String getTitle() {
            return "Unregistered subclass";
        }
    }

    /** Uses a registered HOST without registering this GUI: the fallback must approve the presentation. */
    @SideOnly(Side.CLIENT)
    public static final class AutomaticStorageGui extends net.minecraft.client.gui.inventory.GuiContainer {

        private static final net.minecraft.util.ResourceLocation TEXTURE = new net.minecraft.util.ResourceLocation(
            "textures/gui/container/generic_54.png");

        AutomaticStorageGui(EntityPlayer player) {
            super(
                new net.minecraft.inventory.ContainerChest(
                    player.inventory,
                    new InventoryBasic("Automatic scroll fixture", true, 27)));
            ySize = 168;
        }

        @Override
        protected void drawGuiContainerBackgroundLayer(float tick, int mouseX, int mouseY) {
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(TEXTURE);
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, 71);
            drawTexturedModalRect(guiLeft, guiTop + 71, 0, 126, xSize, 96);
        }

        @Override
        protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
            fontRendererObj.drawString("Automatic scroll fixture", 8, 6, 0x404040);
        }
    }

    private static final class Command extends CommandBase {

        private final UiApiFixtures owner;

        Command(UiApiFixtures owner) {
            this.owner = owner;
        }

        @Override
        public String getCommandName() {
            return "dinvuiapi";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvuiapi native|filtered|unknown|auto-scroll|selftest";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            String world = MinecraftServer.getServer()
                .worldServerForDimension(0)
                .getWorldInfo()
                .getWorldName();
            if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification") || args.length != 1)
                throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Qualification world");
            if ("native".equals(args[0])) player.openGui(owner, 0, player.worldObj, 0, 0, 0);
            else if ("filtered".equals(args[0])) player.openGui(owner, 1, player.worldObj, 0, 0, 0);
            else if ("unknown".equals(args[0])) player.openGui(owner, 2, player.worldObj, 0, 0, 0);
            else if ("auto-scroll".equals(args[0])) player.openGui(owner, 3, player.worldObj, 0, 0, 0);
            else if ("selftest".equals(args[0])) {
                List<String> passed = new ArrayList<String>();
                JsonObject report = new JsonObject();
                ItemStack[] main = copy(player.inventory.mainInventory);
                ItemStack[] armor = copy(player.inventory.armorInventory);
                ItemStack cursor = copy(player.inventory.getItemStack());
                Throwable failure = null;
                try {
                    selftest(player, passed);
                } catch (Throwable thrown) {
                    failure = thrown;
                } finally {
                    restore(player.inventory.mainInventory, main);
                    restore(player.inventory.armorInventory, armor);
                    player.inventory.setItemStack(copy(cursor));
                }
                report.addProperty("success", failure == null);
                if (failure != null) report.addProperty("failure", failure.toString());
                report.addProperty("world", world);
                report.addProperty("storageSlots", InventoryLayout.storageSlots());
                report.addProperty("ordinarySlots", InventoryLayout.mainSlots());
                report.addProperty("canonicalInventoryOwner", player.getCommandSenderName());
                report.addProperty("checkCount", passed.size());
                JsonArray checks = new JsonArray();
                for (String check : passed) checks.add(new JsonPrimitive(check));
                report.add("checks", checks);
                writeReport(report);
                if (failure == null)
                    sender.addChatMessage(new ChatComponentText("DI UI API PASS " + passed.size() + " checks"));
                else sender
                    .addChatMessage(new ChatComponentText("DI UI API FAIL after " + passed.size() + ": " + failure));
            } else throw new IllegalArgumentException(getCommandUsage(sender));
        }

        private static void writeReport(JsonObject report) {
            try {
                File directory = new File("diagnostics/dynamicinventory");
                if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory())
                    throw new java.io.IOException("Could not create qualification output directory");
                Files.write(
                    new File(directory, "ui-api-selftest.json").toPath(),
                    new GsonBuilder().setPrettyPrinting()
                        .create()
                        .toJson(report)
                        .getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException("Could not write UI API selftest report", error);
            }
        }

        private static void selftest(EntityPlayer player, List<String> passed) {
            FakePlayer fake = new FakePlayer(
                (WorldServer) player.worldObj,
                new GameProfile(UUID.fromString("9543900e-7a72-4c40-a16e-000000000001"), "[DI API Fixture]"));
            check(
                fake.playerNetServerHandler == null && InventoryUi.isOpeningReady(fake),
                "real Forge FakePlayer with no play handler requires no remote opening agreement",
                passed);
            ItemStack fakeClick = fake.inventoryContainer.slotClick(36, 0, 0, fake);
            check(
                fakeClick == null && fake.inventory.getItemStack() == null,
                "real Forge FakePlayer retains its empty default-container click path without a network handler",
                passed);
            NativeContainer automated = new NativeContainer(fake.inventory, 27);
            InventoryUi.attachRegistered(automated, fake.inventory);
            automated.windowId = 7;
            fake.openContainer = automated;
            check(
                InventoryUi.isOpeningReady(fake) && automated.slotClick(0, 0, 0, fake) == null
                    && fake.inventory.getItemStack() == null,
                "real Forge FakePlayer can use a registered local container without a remote peer",
                passed);
            NativeContainer valid = new NativeContainer(player.inventory, 27);
            PlayerInventoryBinding binding = InventoryUi.attach(valid, player.inventory);
            check(
                binding != null && binding.getNativeSlotCount() == 63,
                "explicit fixture binding exposes 63 native slots",
                passed);
            check(
                binding.getOrdinarySlotCount() == InventoryLayout.mainSlots(),
                "binding exposes ordinary logical slot count",
                passed);
            int[] hotbar = binding.getHotbarSlotIds();
            int firstHotbar = hotbar[0];
            hotbar[0] = -100;
            check(binding.getHotbarSlotIds()[0] == firstHotbar, "binding returns defensive hotbar array", passed);
            int[] storage = binding.getStorageSlotIds();
            int firstStorage = storage[0];
            storage[0] = -100;
            check(binding.getStorageSlotIds()[0] == firstStorage, "binding returns defensive storage array", passed);
            check(
                binding.getPlayerInventory() == player.inventory,
                "binding retains canonical inventory reference",
                passed);

            InventoryUiAdapter exact = InventoryUiAdapter
                .builder("dinvuiapifixtures:matcher", NativeContainer.class.getName())
                .gui(String.class.getName())
                .nativeTransfers()
                .build();
            check(exact.matchesContainerClass(NativeContainer.class), "adapter matches exact container class", passed);
            check(
                !exact.matchesContainerClass(UnknownContainer.class),
                "adapter leaves subclasses out by default",
                passed);
            check(
                exact.matchesGuiClass(String.class) && !exact.matchesGuiClass(Object.class),
                "GUI class matcher is exact",
                passed);
            UnknownContainer unknown = new UnknownContainer(player.inventory);
            check(
                InventoryUi.findAdapter(unknown) == null && unknown.inventorySlots.size() == 63,
                "unregistered subclass remains at 63 native slots with no adapter",
                passed);
            check(
                InventoryUi.attachRegistered(unknown, player.inventory) == null
                    && InventoryUi.getRefusalReason(unknown) == null,
                "unknown container is not reported as a refused registered integration",
                passed);

            testAdapterHierarchy(passed);
            testPresentations(valid, passed);

            InventoryUiAdapter inherited = InventoryUiAdapter
                .builder("dinvuiapifixtures:matcher-inherited", NativeContainer.class.getName())
                .includeSubclasses()
                .nativeTransfers()
                .build();
            check(
                inherited.matchesContainerClass(UnknownContainer.class),
                "includeSubclasses opts into inherited match",
                passed);
            int registeredBeforeRejectedAdds = InventoryUi.getRegisteredAdapters()
                .size();
            expectIllegal(
                () -> InventoryUi.register(
                    InventoryUiAdapter.builder(NATIVE_ID, UnknownContainer.class.getName())
                        .nativeTransfers()
                        .build()),
                "registry rejects duplicate adapter ID",
                passed);
            expectIllegal(
                () -> InventoryUi.register(
                    InventoryUiAdapter.builder("dinvuiapifixtures:duplicate-type", NativeContainer.class.getName())
                        .nativeTransfers()
                        .build()),
                "registry rejects duplicate container type",
                passed);
            check(
                InventoryUi.getRegisteredAdapters()
                    .size() == registeredBeforeRejectedAdds,
                "failed adapter registrations leave registry unchanged",
                passed);

            testBuilderBounds(passed);
            testProtocolDeclarations(passed);
            testExpandedSlotWireLimit(player, passed);
            testAdditionalSlotFactories(player, passed);

            NativeContainer missing = new NativeContainer(player.inventory, 27);
            replacePlayerSlot(missing, player.inventory, 9, false);
            int missingCount = missing.inventorySlots.size();
            expectAttachRefusal(
                missing,
                player,
                missingCount,
                "registry refuses missing legacy index before slot mutation",
                passed);

            NativeContainer duplicate = new NativeContainer(player.inventory, 27);
            replacePlayerSlot(duplicate, player.inventory, 1, true);
            int duplicateCount = duplicate.inventorySlots.size();
            expectAttachRefusal(
                duplicate,
                player,
                duplicateCount,
                "registry refuses duplicate ordinary index before slot mutation",
                passed);

            NativeContainer wrongCount = new NativeContainer(player.inventory, 26);
            int wrongCountBefore = wrongCount.inventorySlots.size();
            expectAttachRefusal(
                wrongCount,
                player,
                wrongCountBefore,
                "expected-native-slot guard refuses before slot mutation",
                passed);
            check(
                InventoryUi.getRefusalReason(wrongCount) == null,
                "explicit throwing attachment does not pretend an automatic lifecycle attempt occurred",
                passed);
            check(
                InventoryUi.attachRegistered(wrongCount, player.inventory) == null
                    && "Expected 63 native slots; found 62".equals(InventoryUi.getRefusalReason(wrongCount))
                    && wrongCount.inventorySlots.size() == wrongCountBefore
                    && InventoryUi.getBinding(wrongCount) == null,
                "automatic refusal exposes its reason without mutating slots or installing a binding",
                passed);
            check(
                InventoryUi.attachRegistered(wrongCount, player.inventory) == null
                    && "Expected 63 native slots; found 62".equals(InventoryUi.getRefusalReason(wrongCount)),
                "repeated automatic attachment preserves the recorded refusal",
                passed);
            // Repair the missing native slot using the container's protected addSlot path, then explicitly retry.
            wrongCount.addRepairSlot();
            check(
                InventoryUi.attach(wrongCount, player.inventory) != null
                    && InventoryUi.getRefusalReason(wrongCount) == null,
                "successful explicit attachment clears a stale automatic refusal",
                passed);

            InventoryUiAdapter ambiguousAdapter = InventoryUiAdapter
                .builder("dinvuiapifixtures:ambiguous-owner", AmbiguousContainer.class.getName())
                .nativeTransfers()
                .build();
            ensureRegistered(ambiguousAdapter);
            AmbiguousContainer ambiguous = new AmbiguousContainer(player.inventory);
            int ambiguousCount = ambiguous.inventorySlots.size();
            check(
                InventoryUi.attachRegistered(ambiguous) == null && ambiguous.inventorySlots.size() == ambiguousCount,
                "automatic attach refuses two canonical player inventories without mutation",
                passed);
            expectAttachRefusal(
                ambiguous,
                player,
                ambiguousCount,
                "explicit attach refuses a second canonical player inventory before mutation",
                passed);

            InventoryTransferPolicy fixedSlots = InventoryTransfers.toSlots(2);
            int[] callerSlots = new int[] { 1 };
            InventoryTransferPolicy copiedSlots = InventoryTransfers.toSlots(callerSlots);
            callerSlots[0] = 999;
            NativeContainer transfer = new NativeContainer(player.inventory, 27);
            transfer.machine.setInventorySlotContents(0, new ItemStack(Items.diamond, 1));
            check(
                copiedSlots.transfer(transfer, player, 0, null) != null && transfer.machine.getStackInSlot(1) != null,
                "toSlots copies caller destination array",
                passed);
            transfer.machine.setInventorySlotContents(0, new ItemStack(Items.emerald, 1));
            check(
                fixedSlots.transfer(transfer, player, 0, null) != null && transfer.machine.getStackInSlot(2)
                    .getItem() == Items.emerald,
                "slot transfer preserves explicit native destination",
                passed);
            check(
                InventoryTransfers.toSlots(0)
                    .transfer(transfer, player, 0, null) == null,
                "slot transfer rejects source self destination",
                passed);
            transfer.machine.setInventorySlotContents(0, new ItemStack(Items.gold_ingot, 1));
            ItemStack beforeInvalid = transfer.machine.getStackInSlot(0);
            int beforeInvalidCount = beforeInvalid.stackSize;
            check(
                InventoryTransfers.toSlots(-1)
                    .transfer(transfer, player, 0, null) == null && transfer.machine.getStackInSlot(0) == beforeInvalid
                    && beforeInvalid.stackSize == beforeInvalidCount,
                "invalid destination is rejected atomically",
                passed);
            check(
                InventoryTransfers.toSlots(1)
                    .transfer(transfer, player, 999, null) == null
                    && transfer.machine.getStackInSlot(0) == beforeInvalid
                    && beforeInvalid.stackSize == beforeInvalidCount,
                "invalid source bound is rejected atomically",
                passed);

            PlayerInventoryBinding transferBinding = InventoryUi.attach(transfer, player.inventory);
            PlayerInventoryBinding crossedOwner = withInventory(
                transferBinding,
                new net.minecraft.entity.player.InventoryPlayer(player));
            assertCrossedOwnerRejected(
                InventoryTransfers.toPlayerInventory(),
                transfer,
                player,
                crossedOwner,
                "toPlayerInventory rejects crossed owner atomically",
                passed);
            assertCrossedOwnerRejected(
                InventoryTransfers.toStorage(),
                transfer,
                player,
                crossedOwner,
                "toStorage rejects crossed owner atomically",
                passed);
            assertCrossedOwnerRejected(
                InventoryTransfers.toHotbar(),
                transfer,
                player,
                crossedOwner,
                "toHotbar rejects crossed owner atomically",
                passed);
            assertCrossedOwnerRejected(
                InventoryTransfers.toNativeSlots(),
                transfer,
                player,
                crossedOwner,
                "toNativeSlots rejects crossed owner atomically",
                passed);

            exerciseIncomingExtra(player, passed);
            exerciseFilteredOutgoing(player, passed);
            CompatibilityFallbackFixtures.verify(player, passed);
        }

        private static void testAdapterHierarchy(List<String> passed) {
            ensureRegistered(
                InventoryUiAdapter.builder("dinvuiapifixtures:hierarchy-base", HierarchyBaseContainer.class.getName())
                    .includeSubclasses()
                    .nativeTransfers()
                    .build());
            ensureRegistered(
                InventoryUiAdapter.builder("dinvuiapifixtures:hierarchy-child", HierarchyChildContainer.class.getName())
                    .nativeTransfers()
                    .build());
            HierarchyChildContainer child = new HierarchyChildContainer();
            HierarchyGrandchildContainer grandchild = new HierarchyGrandchildContainer();
            check(
                "dinvuiapifixtures:hierarchy-child".equals(
                    InventoryUi.findAdapter(child)
                        .getId()),
                "registry resolves the most specific registered class",
                passed);
            check(
                "dinvuiapifixtures:hierarchy-base".equals(
                    InventoryUi.findAdapter(grandchild)
                        .getId()),
                "registry matches inherited classes only when the base opts in",
                passed);
        }

        private static PlayerInventoryBinding withInventory(final PlayerInventoryBinding source,
            final net.minecraft.entity.player.InventoryPlayer inventory) {
            return new PlayerInventoryBinding() {

                @Override
                public int getNativeSlotCount() {
                    return source.getNativeSlotCount();
                }

                @Override
                public int getOrdinarySlotCount() {
                    return source.getOrdinarySlotCount();
                }

                @Override
                public int getContainerSlotId(int logicalIndex) {
                    return source.getContainerSlotId(logicalIndex);
                }

                @Override
                public int[] getHotbarSlotIds() {
                    return source.getHotbarSlotIds();
                }

                @Override
                public int[] getStorageSlotIds() {
                    return source.getStorageSlotIds();
                }

                @Override
                public net.minecraft.entity.player.InventoryPlayer getPlayerInventory() {
                    return inventory;
                }
            };
        }

        private static void assertCrossedOwnerRejected(InventoryTransferPolicy policy, NativeContainer container,
            EntityPlayer player, PlayerInventoryBinding binding, String description, List<String> passed) {
            ItemStack stack = new ItemStack(Items.gold_ingot, 1);
            container.machine.setInventorySlotContents(0, stack);
            check(
                policy.transfer(container, player, 0, binding) == null && container.machine.getStackInSlot(0) == stack
                    && stack.stackSize == 1,
                description,
                passed);
        }

        private static void testPresentations(NativeContainer valid, List<String> passed) {
            String frozenDigest = InventoryUi.getCompatibilityDigest();
            NativeUiLayout custom = NativeUiLayout.builder()
                .topPadding(7)
                .minimumHeaderHeight(21)
                .backgroundInset(5)
                .build();
            if (InventoryUi.findLayout(valid, String.class) == null)
                InventoryUi.registerPresentation(NATIVE_ID, String.class.getName(), custom);
            NativeUiLayout first = InventoryUi.findLayout(valid, String.class);
            check(
                first != null && first.getTopPadding() == 7
                    && first.getMinimumHeaderHeight() == 21
                    && first.getBackgroundInset() == 5,
                "additional GUI presentation resolves its own layout",
                passed);
            NativeUiLayout alternate = NativeUiLayout.builder()
                .topPadding(11)
                .build();
            if (InventoryUi.findLayout(valid, Integer.class) == null)
                InventoryUi.registerPresentation(NATIVE_ID, Integer.class.getName(), alternate);
            check(
                InventoryUi.findLayout(valid, Integer.class) != null
                    && InventoryUi.findLayout(valid, Object.class) == null,
                "multiple additional GUI presentations remain exact",
                passed);
            check(
                frozenDigest.equals(InventoryUi.getCompatibilityDigest()),
                "late local presentation registration leaves startup protocol agreement unchanged",
                passed);
            expectIllegal(
                () -> InventoryUi.registerPresentation(NATIVE_ID, String.class.getName(), custom),
                "registry rejects duplicate presentation",
                passed);
            expectIllegal(
                () -> InventoryUi.registerPresentation("dinvuiapifixtures:missing", Long.class.getName(), custom),
                "registry rejects presentation for unknown adapter",
                passed);
            expectIllegal(
                () -> InventoryUi.registerPresentation(NATIVE_ID, NATIVE_ID, custom),
                "registry rejects malformed presentation class",
                passed);
            expectIllegal(
                () -> InventoryUi.registerPresentation(NATIVE_ID, Long.class.getName(), null),
                "registry rejects null presentation layout",
                passed);
            expectIllegal(
                () -> InventoryUi.registerPresentation(NATIVE_ID, UiApiFixtures.class.getName() + "$NativeGui", custom),
                "registry rejects a duplicate primary GUI presentation",
                passed);
        }

        private static void testBuilderBounds(List<String> passed) {
            check(
                NativeUiLayout.builder()
                    .topPadding(4096)
                    .minimumHeaderHeight(4096)
                    .backgroundInset(8)
                    .build()
                    .getTopPadding() == 4096,
                "layout accepts documented upper bounds",
                passed);
            check(
                InventoryUiAdapter.builder("dinvuiapifixtures:bounds", NativeContainer.class.getName())
                    .expectedNativeSlots(32767)
                    .nativeTransfers()
                    .build()
                    .getExpectedNativeSlots() == 32767,
                "adapter accepts documented slot-count upper bound",
                passed);
            expectIllegal(
                () -> InventoryUiAdapter.builder("Invalid ID", NativeContainer.class.getName()),
                "adapter rejects malformed namespaced ID",
                passed);
            expectIllegal(
                () -> InventoryUiAdapter.builder("dinvuiapifixtures:bad-class", "bad..Class"),
                "adapter rejects malformed class name",
                passed);
            expectIllegal(
                () -> InventoryUiAdapter.builder("dinvuiapifixtures:low-count", NativeContainer.class.getName())
                    .expectedNativeSlots(35),
                "adapter rejects native slot count below legacy minimum",
                passed);
            expectIllegal(
                () -> InventoryUiAdapter.builder("dinvuiapifixtures:high-count", NativeContainer.class.getName())
                    .expectedNativeSlots(32768),
                "adapter rejects native slot count above supported maximum",
                passed);
            expectIllegal(
                () -> NativeUiLayout.builder()
                    .topPadding(-1),
                "layout rejects negative top padding",
                passed);
            expectIllegal(
                () -> NativeUiLayout.builder()
                    .minimumHeaderHeight(4097),
                "layout rejects excessive header height",
                passed);
            expectIllegal(
                () -> NativeUiLayout.builder()
                    .backgroundInset(9),
                "layout rejects excessive inset",
                passed);
            expectIllegal(
                () -> InventoryTransfers.toSlots((int[]) null),
                "transfer factory rejects null destination list",
                passed);
        }

        private static void testExpandedSlotWireLimit(EntityPlayer player, List<String> passed) {
            int added = InventoryLayout.mainSlots() - InventoryLayout.LEGACY_MAIN_SLOTS;
            int overflowNativeCount = 32768 - added;
            ProtocolLimitContainer overflow = new ProtocolLimitContainer(player.inventory, overflowNativeCount);
            int before = overflow.inventorySlots.size();
            boolean refused = false;
            try {
                InventoryUi.attach(overflow, player.inventory);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(
                refused && overflow.inventorySlots.size() == before,
                "expanded container above 32767 slots is refused before mutation",
                passed);

            int exactNativeCount = 32767 - added;
            ProtocolLimitContainer exact = new ProtocolLimitContainer(player.inventory, exactNativeCount);
            PlayerInventoryBinding binding = InventoryUi.attach(exact, player.inventory);
            check(
                binding != null && exact.inventorySlots.size() == 32767,
                "expanded container at exactly 32767 slots attaches successfully",
                passed);
        }

        private static void testAdditionalSlotFactories(EntityPlayer player, List<String> passed) {
            ensureRegistered(
                InventoryUiAdapter.builder("dinvuiapifixtures:slot-factory", FactoryContainer.class.getName())
                    .additionalSlots(
                        (container, canonical, raw) -> ((FactoryContainer) container).factory
                            .create(container, canonical, raw))
                    .extraTransfers(InventoryTransfers.toNativeSlots())
                    .build());
            expectIllegal(
                () -> InventoryUiAdapter.builder("dinvuiapifixtures:null-factory", FactoryContainer.class.getName())
                    .additionalSlots(null),
                "builder rejects a null additional-slot factory",
                passed);
            FactoryContainer container = new FactoryContainer(
                player.inventory,
                (target, canonical, raw) -> new RuleSlot(canonical, raw));
            List<Slot> original = new ArrayList<>(container.inventorySlots);
            PlayerInventoryBinding binding = InventoryUi.attach(container, player.inventory);
            boolean unchanged = true;
            for (int i = 0; i < original.size(); i++)
                unchanged &= container.getSlot(i) == original.get(i) && original.get(i).slotNumber == i;
            check(unchanged, "custom additional-slot factory preserves every native slot identity and ID", passed);
            if (!InventoryLayout.isExtra(36)) {
                check(
                    container.inventorySlots.size() == original.size(),
                    "minimum capacity never invokes an additional-slot factory",
                    passed);
                return;
            }
            clearOrdinary(player);
            int first = binding.getContainerSlotId(36);
            RuleSlot deniedInsert = (RuleSlot) container.getSlot(first);
            player.inventory.setItemStack(new ItemStack(Items.diamond, 5));
            container.slotClick(first, 0, 0, player);
            check(
                deniedInsert.getStack() == null && player.inventory.getItemStack().stackSize == 5,
                "custom added slot denies insertion through normal click handling",
                passed);
            player.inventory.setItemStack(null);
            if (InventoryLayout.isExtra(37)) {
                RuleSlot deniedTake = (RuleSlot) container.getSlot(binding.getContainerSlotId(37));
                player.inventory.mainInventory[InventoryLayout.rawIndex(37)] = new ItemStack(Items.emerald, 1);
                container.slotClick(deniedTake.slotNumber, 0, 0, player);
                container.slotClick(deniedTake.slotNumber, 0, 1, player);
                check(
                    deniedTake.getStack() != null && deniedTake.getStack().stackSize == 1
                        && player.inventory.getItemStack() == null
                        && container.machine.getStackInSlot(0) == null,
                    "custom added slot denies extraction through ordinary and shift clicks",
                    passed);
            }
            if (InventoryLayout.isExtra(38)) {
                RuleSlot limited = (RuleSlot) container.getSlot(binding.getContainerSlotId(38));
                player.inventory.setItemStack(new ItemStack(Items.diamond, 5));
                container.slotClick(limited.slotNumber, 0, 0, player);
                check(
                    limited.getStack() != null && limited.getStack().stackSize == 2
                        && player.inventory.getItemStack().stackSize == 3
                        && limited.changed > 0,
                    "custom added slot retains stack limit and change callback during placement",
                    passed);
                player.inventory.setItemStack(null);
                container.slotClick(limited.slotNumber, 0, 0, player);
                check(
                    limited.getStack() == null && player.inventory.getItemStack().stackSize == 2 && limited.pickups > 0,
                    "custom added slot retains native pickup callback",
                    passed);
                player.inventory.setItemStack(null);
            }
            assertFactoryRefused(player, (target, canonical, raw) -> null, "null slot", passed);
            assertFactoryRefused(
                player,
                (target, canonical, raw) -> new Slot(new InventoryPlayer(player), raw, 0, 0),
                "wrong owner",
                passed);
            assertFactoryRefused(
                player,
                (target, canonical, raw) -> new Slot(canonical, 0, 0, 0),
                "wrong raw index",
                passed);
            assertFactoryRefused(
                player,
                (target, canonical, raw) -> { throw new IllegalStateException("fixture factory failure"); },
                "throwing factory",
                passed);
            assertFactoryRefused(player, (target, canonical, raw) -> target.getSlot(1), "attached native slot", passed);
            assertFactoryRefused(player, (target, canonical, raw) -> {
                target.inventorySlots.add(new Slot(canonical, raw, 0, 0));
                return new Slot(canonical, raw, 0, 0);
            }, "factory mutates container list", passed);
            if (InventoryLayout.isExtra(37)) {
                final Slot[] reused = new Slot[1];
                assertFactoryRefused(player, (target, canonical, raw) -> {
                    if (reused[0] == null) reused[0] = new Slot(canonical, raw, 0, 0) {

                        @Override
                        public int getSlotIndex() {
                            return InventoryLayout.rawIndex(36 + factoryCalls++);
                        }

                        private int factoryCalls;
                    };
                    return reused[0];
                }, "reused prepared slot identity", passed);
                assertFactoryRefused(
                    player,
                    (target, canonical, raw) -> raw == InventoryLayout.rawIndex(36) ? new Slot(canonical, raw, 0, 0)
                        : null,
                    "late failure after a valid candidate",
                    passed);
            }
        }

        private static void assertFactoryRefused(EntityPlayer player, PlayerInventorySlotFactory factory,
            String scenario, List<String> passed) {
            FactoryContainer container = new FactoryContainer(player.inventory, factory);
            List<Slot> slots = new ArrayList<>(container.inventorySlots);
            List<ItemStack> stacks = new ArrayList<>(container.inventoryItemStacks);
            boolean refused = false;
            try {
                InventoryUi.attach(container, player.inventory);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(
                refused && InventoryUi.getBinding(container) == null
                    && slots.equals(container.inventorySlots)
                    && stacks.equals(container.inventoryItemStacks),
                "additional-slot factory refused before appending slots: " + scenario,
                passed);
            check(
                InventoryUi.attachRegistered(container, player.inventory) == null
                    && slots.equals(container.inventorySlots)
                    && stacks.equals(container.inventoryItemStacks),
                "automatic attachment safely refuses invalid additional-slot factory: " + scenario,
                passed);
        }

        private static void ensureRegistered(InventoryUiAdapter adapter) {
            for (InventoryUiAdapter existing : InventoryUi.getRegisteredAdapters()) if (existing.getId()
                .equals(adapter.getId())) return;
            throw new AssertionError("Fixture adapter was not registered during init: " + adapter.getId());
        }

        private static void testProtocolDeclarations(List<String> passed) {
            String id = "dinvuiapifixtures:declaration";
            String type = NativeContainer.class.getName();
            InventoryUiAdapter base = InventoryUiAdapter.builder(id, type)
                .nativeTransfers()
                .build();
            InventoryUiAdapter other = InventoryUiAdapter.builder("dinvuiapifixtures:other-declaration", type)
                .nativeTransfers()
                .build();
            String digest = InventoryUiProtocol.digest(Collections.singletonList(base));
            check(
                InventoryUiProtocol.digest(Arrays.asList(base, other))
                    .equals(InventoryUiProtocol.digest(Arrays.asList(other, base))),
                "adapter protocol digest is independent of registration order",
                passed);
            check(
                !digest.equals(InventoryUiProtocol.digest(Arrays.asList(base, other))),
                "missing or additional adapter changes protocol agreement",
                passed);
            for (InventoryUiAdapter changed : new InventoryUiAdapter[] { InventoryUiAdapter.builder(id, type)
                .compatibilityRevision(2)
                .nativeTransfers()
                .build(),
                InventoryUiAdapter.builder(id, FactoryContainer.class.getName())
                    .nativeTransfers()
                    .build(),
                InventoryUiAdapter.builder(id, type)
                    .includeSubclasses()
                    .nativeTransfers()
                    .build(),
                InventoryUiAdapter.builder(id, type)
                    .expectedNativeSlots(63)
                    .nativeTransfers()
                    .build(),
                InventoryUiAdapter.builder(id, type)
                    .extraTransfers(InventoryTransfers.toNativeSlots())
                    .build(),
                InventoryUiAdapter.builder(id, type)
                    .additionalSlots(PlayerInventorySlotFactory.plainSlot())
                    .nativeTransfers()
                    .build() })
                check(
                    !digest.equals(InventoryUiProtocol.digest(Collections.singletonList(changed))),
                    "adapter semantic declaration changes digest: " + changed.getCompatibilityRevision()
                        + "/"
                        + changed.getContainerClassName()
                        + "/"
                        + changed.isIncludingSubclasses()
                        + "/"
                        + changed.getExpectedNativeSlots()
                        + "/"
                        + (changed.getTransferPolicy() != null)
                        + "/"
                        + changed.hasCustomAdditionalSlots(),
                    passed);
            InventoryUiAdapter presentation = InventoryUiAdapter.builder(id, type)
                .gui(String.class.getName())
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(11)
                        .build())
                .nativeTransfers()
                .build();
            check(
                digest.equals(InventoryUiProtocol.digest(Collections.singletonList(presentation))),
                "client-only GUI declarations do not affect protocol agreement",
                passed);
            expectIllegal(
                () -> InventoryUiAdapter.builder(id, type)
                    .compatibilityRevision(0),
                "adapter rejects nonpositive compatibility revision",
                passed);
            String frozen = InventoryUi.getCompatibilityDigest();
            boolean refused = false;
            try {
                InventoryUi.register(
                    InventoryUiAdapter.builder("dinvuiapifixtures:late", String.class.getName())
                        .nativeTransfers()
                        .build());
            } catch (IllegalStateException expected) {
                refused = true;
            }
            check(
                refused && frozen.equals(InventoryUi.getCompatibilityDigest()),
                "startup registry closure refuses a late adapter without changing negotiated declarations",
                passed);
        }

        private static void exerciseIncomingExtra(EntityPlayer player, List<String> passed) {
            clearOrdinary(player);
            NativeContainer container = new NativeContainer(player.inventory, 27);
            PlayerInventoryBinding binding = InventoryUi.attach(container, player.inventory);
            int last = binding.getOrdinarySlotCount() - 1;
            for (int logical = 0; logical < last; logical++)
                player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = new ItemStack(
                    Blocks.cobblestone,
                    64);
            container.machine.setInventorySlotContents(0, new ItemStack(Items.diamond, 1));
            container.slotClick(0, 0, 1, player);
            ItemStack atLast = player.inventory.mainInventory[InventoryLayout.rawIndex(last)];
            check(
                atLast != null && atLast.getItem() == Items.diamond && container.machine.getStackInSlot(0) == null,
                "native shift-click reaches the final player slot when earlier slots are full",
                passed);
        }

        private static void exerciseFilteredOutgoing(EntityPlayer player, List<String> passed) {
            clearOrdinary(player);
            FilteredContainer container = new FilteredContainer(player.inventory);
            PlayerInventoryBinding binding = InventoryUi.attach(container, player.inventory);
            int last = binding.getOrdinarySlotCount() - 1;
            check(
                binding.getPlayerInventory() == player.inventory,
                "filtered container binds the canonical player inventory",
                passed);
            check(
                !container.getSlot(2)
                    .isItemValid(new ItemStack(Items.iron_ingot)),
                "filtered output slot rejects insertion",
                passed);
            check(
                container.getSlot(1)
                    .isItemValid(new ItemStack(Items.coal)),
                "filtered fuel slot accepts coal",
                passed);
            if (binding.getOrdinarySlotCount() == InventoryLayout.LEGACY_MAIN_SLOTS) {
                check(
                    binding.getContainerSlotId(last) < binding.getNativeSlotCount()
                        && container.inventorySlots.size() == binding.getNativeSlotCount(),
                    "minimum-capacity filtered container has no added player slots",
                    passed);
                return;
            }
            player.inventory.mainInventory[InventoryLayout.rawIndex(last)] = new ItemStack(
                Item.getItemFromBlock(Blocks.iron_ore),
                1);
            int addedId = binding.getContainerSlotId(last);
            check(
                addedId >= binding.getNativeSlotCount() && container.transferStackInSlot(player, addedId) == null,
                "native container transfer intentionally returns null for added source IDs",
                passed);
            container.slotClick(addedId, 0, 1, player);
            check(
                container.machine.getStackInSlot(0) != null && container.machine.getStackInSlot(0)
                    .getItem() == Item.getItemFromBlock(Blocks.iron_ore)
                    && player.inventory.mainInventory[InventoryLayout.rawIndex(last)] == null,
                "registered generic policy shifts an extra iron ore into the filtered input",
                passed);
        }

        private static void replacePlayerSlot(FixtureContainer container,
            net.minecraft.entity.player.InventoryPlayer inventory, int logical, boolean duplicate) {
            int removeAt = -1;
            for (int i = 0; i < container.inventorySlots.size(); i++) {
                Slot slot = container.getSlot(i);
                if (slot.inventory == inventory && InventoryLayout.ordinaryIndex(slot.getSlotIndex()) == logical) {
                    removeAt = i;
                    break;
                }
            }
            if (removeAt < 0) throw new AssertionError("Fixture player slot not found: " + logical);
            container.inventorySlots.remove(removeAt);
            if (duplicate) container.inventorySlots.add(new Slot(inventory, InventoryLayout.rawIndex(0), 0, 0));
            else container.inventorySlots.add(new Slot(container.machine, 0, 0, 0));
        }

        private static void expectAttachRefusal(Container container, EntityPlayer player, int count, String description,
            List<String> passed) {
            boolean refused = false;
            try {
                InventoryUi.attach(container, player.inventory);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(refused && container.inventorySlots.size() == count, description, passed);
        }

        private static void expectIllegal(Runnable action, String description, List<String> passed) {
            boolean rejected = false;
            try {
                action.run();
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, description, passed);
        }

        private static void clearOrdinary(EntityPlayer player) {
            for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++)
                player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = null;
        }

        private static ItemStack[] copy(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
            return result;
        }

        private static ItemStack copy(ItemStack stack) {
            return stack == null ? null : stack.copy();
        }

        private static void restore(ItemStack[] destination, ItemStack[] source) {
            for (int i = 0; i < destination.length; i++) destination[i] = source[i] == null ? null : source[i].copy();
        }

        private static void check(boolean condition, String description, List<String> passed) {
            if (!condition) throw new AssertionError(description);
            passed.add(description);
        }
    }
}
