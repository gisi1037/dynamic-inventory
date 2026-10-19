package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S31PacketWindowProperty;
import net.minecraft.world.World;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol.Message;
import dev.gilbe.dynamicinventory.network.ServerInventoryGate;

/** Fault injection lives only in this disposable test mod, never in the shipped artifact. */
@Mod(modid = "dinvopeningfixtures", version = "1.0", dependencies = "required-after:dynamicinventory")
public final class OpeningGateFixtures implements IGuiHandler {

    private static final String[] CASES = { "match", "server-refusal", "client-refusal", "mapping", "timeout",
        "malformed", "budget", "late-binding", "populated", "tinkers-oversized", "actions" };
    private static Container serverContainer;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Class<?>[] types = { Match.class, ServerRefusal.class, ClientRefusal.class, Mapping.class, Timeout.class,
            Malformed.class, Budget.class, LateBinding.class, Populated.class };
        for (int i = 0; i < types.length; i++) InventoryUi.register(
            InventoryUiAdapter.builder("dinvopeningfixtures:" + CASES[i], types[i].getName())
                .expectedNativeSlots(i == 8 ? 180 : i == 9 ? 47 : 63)
                .nativeTransfers()
                .build());
        // The small RC-2 fixture has compile stubs, but does not install the full Tinkers/Mantle runtime.
        // Keep that optional case loadable only in a fixture which actually includes those mods.
        if (Loader.isModLoaded("TConstruct")) InventoryUi.register(
            InventoryUiAdapter.builder("dinvopeningfixtures:tinkers-oversized", Tinkers.Station.class.getName())
                .expectedNativeSlots(47)
                .nativeTransfers()
                .build());
        NetworkRegistry.INSTANCE.registerGuiHandler(this, this);
        InventoryUi.register(
            InventoryUiAdapter.builder("dinvopeningfixtures:actions", Actions.class.getName())
                .expectedNativeSlots(45)
                .nativeTransfers()
                .build());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        OpeningActionFixtures.register();
    }

    @Mod.EventHandler
    public void commands(FMLServerStartingEvent event) {
        event.registerServerCommand(new Command());
    }

    private static Container create(int id, InventoryPlayer owner) {
        switch (id) {
            case 0:
                return new Match(owner);
            case 1:
                return new ServerRefusal(owner);
            case 2:
                return new ClientRefusal(owner);
            case 3:
                return new Mapping(owner);
            case 4:
                return new Timeout(owner);
            case 5:
                return new Malformed(owner);
            case 6:
                return new Budget(owner);
            case 7:
                return new LateBinding(owner);
            case 8:
                return new Populated(owner);
            case 9:
                return Tinkers.create(owner);
            case 10:
                return new Actions(owner);
            default:
                throw new IllegalArgumentException("Unknown gate case");
        }
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return serverContainer = create(id, player.inventory);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        Client.last = create(id, player.inventory);
        return new Client.Gui(Client.last);
    }

    public static class Base extends Container {

        final int mode;
        final TrackingInventory machine;
        int clicks, pendingActionsPassed, readyActionsPassed;
        boolean probed, predictionBlocked, forgedCommitBlocked, pendingInputBlocked;

        Base(InventoryPlayer owner, int mode) {
            this.mode = mode;
            machine = new TrackingInventory(mode == 8 ? 144 : mode == 10 ? 9 : 27);
            boolean client = owner.player.worldObj.isRemote;
            int machines = (mode == 1 && !client) || (mode == 2 && client) ? 26 : machine.getSizeInventory();
            if (!client) {
                int occupied = mode == 8 ? machines : 1;
                for (int i = 0; i < occupied; i++) machine.setInventorySlotContents(i, new ItemStack(Items.diamond, 7));
            }
            for (int i = 0; i < machines; i++)
                addSlotToContainer(new ProbeSlot(machine, i, 8 + i % 9 * 18, 18 + i / 9 * 18, this, owner));
            for (int i = 9; i < 36; i++)
                addSlotToContainer(new ProbeSlot(owner, i, 8 + (i - 9) % 9 * 18, 84 + (i - 9) / 9 * 18, this, owner));
            for (int i = 0; i < 9; i++) {
                int raw = client && mode == 3 && i < 2 ? 1 - i : i;
                addSlotToContainer(new ProbeSlot(owner, raw, 8 + i * 18, 142, this, owner));
            }
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public ItemStack slotClick(int id, int button, int mode, EntityPlayer player) {
            clicks++;
            return super.slotClick(id, button, mode, player);
        }

        @Override
        public void addCraftingToCrafters(ICrafting listener) {
            EntityPlayerMP player = (EntityPlayerMP) listener;
            if (mode == 7) {
                // Deliberately builds a short contents packet before attachment; boundary guard must refuse it.
                player.sendContainerAndContentsToPlayer(this, getInventory());
                return;
            }
            super.addCraftingToCrafters(listener);
            if (mode == 10) pendingActionsPassed = OpeningActionFixtures.verify(this, player, false);
            if (mode == 4) {
                // Advance only the pending deadline, without waiting five seconds or adding a production test flag.
                try {
                    Field deadline = ServerInventoryGate.class.getDeclaredField("deadline");
                    deadline.setAccessible(true);
                    deadline.setInt(ServerInventoryGate.of(player), player.ticksExisted - 1);
                    ServerInventoryGate.of(player)
                        .tick(player);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            } else if (mode == 6) {
                // Exceed the slot-sized initial-sync allowance too; the flood must still be refused.
                for (int i = 0; i < inventorySlots.size() + 130; i++)
                    player.playerNetServerHandler.sendPacket(new S31PacketWindowProperty(windowId, 0, i));
            }
        }
    }

    public static final class Match extends Base {

        Match(InventoryPlayer owner) {
            super(owner, 0);
        }
    }

    public static final class ServerRefusal extends Base {

        ServerRefusal(InventoryPlayer owner) {
            super(owner, 1);
        }
    }

    public static final class ClientRefusal extends Base {

        ClientRefusal(InventoryPlayer owner) {
            super(owner, 2);
        }
    }

    public static final class Mapping extends Base {

        Mapping(InventoryPlayer owner) {
            super(owner, 3);
        }
    }

    public static final class Timeout extends Base {

        Timeout(InventoryPlayer owner) {
            super(owner, 4);
        }
    }

    public static final class Malformed extends Base {

        Malformed(InventoryPlayer owner) {
            super(owner, 5);
        }
    }

    public static final class Budget extends Base {

        Budget(InventoryPlayer owner) {
            super(owner, 6);
        }
    }

    public static final class LateBinding extends Base {

        LateBinding(InventoryPlayer owner) {
            super(owner, 7);
        }
    }

    public static final class Populated extends Base {

        Populated(InventoryPlayer owner) {
            super(owner, 8);
        }
    }

    public static final class Actions extends Base {

        Actions(InventoryPlayer owner) {
            super(owner, 10);
        }
    }

    /** Uses the actual pinned Tinkers container and custom packet, not a simulation of its serializer. */
    private static final class Tinkers {

        static Container create(InventoryPlayer owner) {
            tconstruct.tools.logic.CraftingStationLogic logic = new tconstruct.tools.logic.CraftingStationLogic();
            ((net.minecraft.tileentity.TileEntity) (Object) logic).setWorldObj(owner.player.worldObj);
            ExtendedInventory side = new ExtendedInventory();
            if (!owner.player.worldObj.isRemote) side.setInventorySlotContents(0, new ItemStack(Items.diamond, 300));
            logic.chest = new WeakReference<IInventory>(side);
            logic.chestDirection = net.minecraftforge.common.util.ForgeDirection.NORTH;
            logic.slotCount = logic.chestSize = logic.invRows = 1;
            logic.invColumns = 6;
            try {
                Field slots = ((Object) logic).getClass()
                    .getDeclaredField("firstInventorySlots");
                slots.setAccessible(true);
                slots.set(logic, new int[] { 0 });
                Field inventories = ((Object) logic).getClass()
                    .getDeclaredField("inventories");
                inventories.setAccessible(true);
                inventories.set(logic, new WeakReference[] { logic.chest, null, null, null });
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            return new Station(owner, logic);
        }

        private static final class Station extends tconstruct.tools.inventory.CraftingStationContainer {

            Station(InventoryPlayer owner, tconstruct.tools.logic.CraftingStationLogic logic) {
                super(owner, logic, 0, 0, 0);
            }

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                // Exercise native sync without replacing any blocks in the qualification world.
                return true;
            }
        }

        private static final class ExtendedInventory extends InventoryBasic
            implements tconstruct.api.IExtendedStackLimitProvider {

            private ItemStack stack;

            ExtendedInventory() {
                super("Opening gate oversized stack", true, 1);
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return stack;
            }

            @Override
            public void setInventorySlotContents(int slot, ItemStack value) {
                stack = value; // InventoryBasic's default setter clamps to 64; this provider preserves 300.
            }

            @Override
            public int getExtendedStackLimit(int slot, ItemStack value) {
                return 4096;
            }
        }
    }

    static final class TrackingInventory extends InventoryBasic {

        int updates;

        TrackingInventory(int slots) {
            super("Opening gate", true, slots);
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            updates++;
            super.setInventorySlotContents(slot, stack);
        }
    }

    private static final class ProbeSlot extends Slot {

        final Base container;
        final InventoryPlayer owner;

        ProbeSlot(net.minecraft.inventory.IInventory inventory, int index, int x, int y, Base container,
            InventoryPlayer owner) {
            super(inventory, index, x, y);
            this.container = container;
            this.owner = owner;
        }

        @Override
        public int getSlotIndex() {
            if (owner.player.worldObj.isRemote && !container.probed
                && owner.player.openContainer == container
                && container.windowId != 0) Client.probe(container);
            return super.getSlotIndex();
        }
    }

    private final class Command extends CommandBase {

        @Override
        public String getCommandName() {
            return "dinvgate";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvgate <case>|status";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
            String name = player.worldObj.getWorldInfo()
                .getWorldName();
            if (!name.startsWith("DI-Baseline-") || !name.contains("Qualification") || args.length != 1)
                throw new IllegalArgumentException("Disposable qualification world required");
            if ("status".equals(args[0])) {
                JsonObject report = state(serverContainer, player);
                report.addProperty(
                    "ready",
                    ServerInventoryGate.of(player)
                        .allowsInput(player, player.openContainer.windowId));
                write("opening-server.json", report);
                return;
            }
            if ("actions-ready".equals(args[0])) {
                if (!(serverContainer instanceof Actions) || player.openContainer != serverContainer
                    || !InventoryUi.isOpeningReady(player))
                    throw new IllegalStateException("Ready action fixture required");
                Base target = (Base) serverContainer;
                target.readyActionsPassed = OpeningActionFixtures.verify(target, player, true);
                return;
            }
            for (int i = 0; i < CASES.length; i++) if (CASES[i].equals(args[0])) {
                // Reuse the same wire ID on every test opening. Old replies must not approve a new object.
                try {
                    Field counter = EntityPlayerMP.class.getDeclaredField("currentWindowId");
                    counter.setAccessible(true);
                    counter.setInt(player, 0);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                player.openGui(OpeningGateFixtures.this, i, player.worldObj, 0, 0, 0);
                return;
            }
            throw new IllegalArgumentException("Unknown opening case");
        }
    }

    private static JsonObject state(Container container, EntityPlayer player) {
        Base target = container instanceof Base ? (Base) container : null;
        JsonObject report = new JsonObject();
        report.addProperty("mode", container == null ? -1 : target == null ? 9 : target.mode);
        report.addProperty("open", container != null && player.openContainer == container);
        report.addProperty("window", player.openContainer.windowId);
        report.addProperty("clicks", container == null ? -1 : target == null ? 0 : target.clicks);
        report.addProperty("machineUpdates", target == null ? -1 : target.machine.updates);
        report.addProperty("pendingActionsPassed", target == null ? 0 : target.pendingActionsPassed);
        report.addProperty("readyActionsPassed", target == null ? 0 : target.readyActionsPassed);
        report.addProperty("actionsExpected", OpeningActionFixtures.expected());
        report.addProperty(
            "machineCount",
            container == null ? 0
                : count(
                    container.getSlot(target == null ? 46 : 0)
                        .getStack()));
        int occupied = 0, total = 0;
        if (target != null) for (int i = 0; i < target.machine.getSizeInventory(); i++) {
            int size = count(target.machine.getStackInSlot(i));
            if (size > 0) occupied++;
            total += size;
        }
        report.addProperty("machineOccupiedSlots", occupied);
        report.addProperty("machineTotalCount", total);
        report.addProperty("cursorOccupied", player.inventory.getItemStack() != null);
        return report;
    }

    private static int count(ItemStack stack) {
        return stack == null ? 0 : stack.stackSize;
    }

    private static void write(String name, JsonObject report) {
        try {
            File directory = new File("diagnostics/dynamicinventory");
            directory.mkdirs();
            Files.write(
                new File(directory, name).toPath(),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class Client {

        static Container last;
        static Message previousOffer;

        private Client() {}

        static void probe(Base target) {
            target.probed = true;
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
            dev.gilbe.dynamicinventory.client.ClientInventoryGate gate = ((dev.gilbe.dynamicinventory.client.ClientGateAccess) client
                .getNetHandler()).dynamicinventory$openingGate();
            try {
                Field field = gate.getClass()
                    .getDeclaredField("offer");
                field.setAccessible(true);
                Message offer = (Message) field.get(gate);
                if (offer == null) {
                    target.probed = false;
                    return;
                }
                if (previousOffer != null) client.getNetHandler()
                    .addToSendQueue(
                        new C17PacketCustomPayload(
                            InventoryOpeningProtocol.CHANNEL,
                            previousOffer.reply(InventoryOpeningProtocol.ACCEPT)
                                .encode()));
                previousOffer = offer;
                Message stale = new Message(
                    InventoryOpeningProtocol.ACCEPT,
                    offer.window,
                    offer.nonce + 1,
                    offer.digest);
                client.getNetHandler()
                    .addToSendQueue(new C17PacketCustomPayload(InventoryOpeningProtocol.CHANNEL, stale.encode()));
                byte[] wrongDigest = offer.digest.clone();
                wrongDigest[0] ^= 1;
                client.getNetHandler()
                    .addToSendQueue(
                        new C17PacketCustomPayload(
                            InventoryOpeningProtocol.CHANNEL,
                            new Message(InventoryOpeningProtocol.ACCEPT, offer.window, offer.nonce, wrongDigest)
                                .encode()));
                gate.receive(
                    client,
                    stale.reply(InventoryOpeningProtocol.COMMIT)
                        .encode());
                // Try the correct nonce BEFORE ACCEPT too. Neither message may authorize applying contents.
                gate.receive(
                    client,
                    offer.reply(InventoryOpeningProtocol.COMMIT)
                        .encode());
                gate.receive(
                    client,
                    offer.reply(InventoryOpeningProtocol.READY)
                        .encode());
                Field committed = gate.getClass()
                    .getDeclaredField("committed");
                committed.setAccessible(true);
                target.forgedCommitBlocked = !committed.getBoolean(gate)
                    && !gate.allowsInput(client.thePlayer, target.windowId);
                target.pendingInputBlocked = dev.gilbe.dynamicinventory.client.ClientInputGuard
                    .isPending(client.currentScreen);
                if (Loader.isModLoaded("modularui2")) {
                    com.cleanroommc.modularui.api.event.MouseInputEvent.Pre mouse = new com.cleanroommc.modularui.api.event.MouseInputEvent.Pre(
                        client.currentScreen);
                    com.cleanroommc.modularui.api.event.KeyboardInputEvent.Pre keyboard = new com.cleanroommc.modularui.api.event.KeyboardInputEvent.Pre(
                        client.currentScreen);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(mouse);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(keyboard);
                    target.pendingInputBlocked &= mouse.isCanceled() && keyboard.isCanceled();
                }
                client.playerController.windowClick(target.windowId, 0, 0, 0, client.thePlayer);
                target.predictionBlocked = target.clicks == 0 && client.thePlayer.inventory.getItemStack() == null;
                // Bypass client prediction intentionally: the server must reject this packet before slotClick runs.
                client.getNetHandler()
                    .addToSendQueue(new C0EPacketClickWindow(target.windowId, 0, 0, 0, null, (short) 1));
                if (target.mode == 5) client.getNetHandler()
                    .addToSendQueue(new C17PacketCustomPayload(InventoryOpeningProtocol.CHANNEL, new byte[] { 1 }));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        /** Invoked on the game thread by StorageHarness after the exchange settles. */
        public static void capture() {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
            JsonObject report = state(last, client.thePlayer);
            Base target = last instanceof Base ? (Base) last : null;
            report.addProperty("probed", target != null && target.probed);
            report.addProperty("predictionBlocked", target != null && target.predictionBlocked);
            report.addProperty("forgedCommitBlocked", target != null && target.forgedCommitBlocked);
            report.addProperty("pendingInputBlocked", target != null && target.pendingInputBlocked);
            report.addProperty(
                "inputReady",
                !dev.gilbe.dynamicinventory.client.ClientInputGuard.isPending(client.currentScreen));
            report.addProperty(
                "ready",
                ((dev.gilbe.dynamicinventory.client.ClientGateAccess) client.getNetHandler())
                    .dynamicinventory$openingGate()
                    .allowsInput(client.thePlayer, client.thePlayer.openContainer.windowId));
            write("opening-client.json", report);
        }

        static final class Gui extends net.minecraft.client.gui.inventory.GuiContainer {

            Gui(Container container) {
                super(container);
            }

            @Override
            protected void drawGuiContainerBackgroundLayer(float ticks, int x, int y) {}
        }
    }
}
