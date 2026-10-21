import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

public class StorageHarness {
    static VirtualMachine vm;
    static ThreadReference thread;
    static ClassLoaderReference loader;
    static ObjectReference client;
    static boolean awaitingGraveSeed;
    static final Map<String, String> mappedFields = new HashMap<>();
    static final Map<String, Set<String>> mappedMethods = new HashMap<>();

    static void loadMappings() throws Exception {
        String path = System.getProperty("dinv.srgMapping");
        if (path == null) return;
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))) {
            String[] parts = line.split(" ");
            if (parts[0].equals("FD:")) {
                mappedFields.put(parts[1], parts[2].substring(parts[2].lastIndexOf('/') + 1));
            } else if (parts[0].equals("MD:")) {
                String name = parts[1].substring(parts[1].lastIndexOf('/') + 1);
                mappedMethods.computeIfAbsent(name + parts[2], key -> new HashSet<>())
                    .add(parts[3].substring(parts[3].lastIndexOf('/') + 1));
            }
        }
    }
    static Field fieldInfo(ReferenceType reference, String name) {
        Field direct = reference.fieldByName(name);
        if (direct != null) return direct;
        return reference.allFields().stream().filter(candidate -> candidate.name().equals(mappedFields.get(
            candidate.declaringType().name().replace('.', '/') + "/" + name))).findFirst().orElseThrow(
                () -> new IllegalStateException("Missing field " + reference.name() + "." + name));
    }
    static List<Method> methods(ReferenceType reference, String name, String signature) {
        List<Method> direct = signature == null ? reference.methodsByName(name) : reference.methodsByName(name, signature);
        if (!direct.isEmpty()) return direct;
        return reference.allMethods().stream().filter(candidate -> (signature == null || signature.equals(candidate.signature()))
            && mappedMethods.getOrDefault(name + candidate.signature(), Set.of()).contains(candidate.name())).toList();
    }

    static ClassType type(String name) throws Exception {
        ClassType clazz = (ClassType) vm.classesByName("java.lang.Class").getFirst();
        Method method = clazz.methodsByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;").getFirst();
        StringReference className = vm.mirrorOf(name);
        className.disableCollection();
        return (ClassType) ((ClassObjectReference) clazz.invokeMethod(thread, method,
            List.of(className, vm.mirrorOf(true), loader), ObjectReference.INVOKE_SINGLE_THREADED)).reflectedType();
    }
    static Value call(ObjectReference target, String method, String signature, Value... args) throws Exception {
        for (Value value : args) if (value instanceof ObjectReference object) object.disableCollection();
        return target.invokeMethod(thread, methods(target.referenceType(), method, signature).getFirst(),
            Arrays.asList(args), ObjectReference.INVOKE_SINGLE_THREADED);
    }
    static ObjectReference field(ObjectReference target, String name) {
        return (ObjectReference) target.getValue(fieldInfo(target.referenceType(), name));
    }
    static ObjectReference make(String name, String signature, Value... args) throws Exception {
        for (Value value : args) if (value instanceof ObjectReference object) object.disableCollection();
        ClassType clazz = type(name);
        ObjectReference result = clazz.newInstance(thread, clazz.methodsByName("<init>", signature).getFirst(),
            Arrays.asList(args), ObjectReference.INVOKE_SINGLE_THREADED);
        // Locals in this harness do not root objects in the target VM. Keep newly
        // constructed GUI/settings objects alive until the short JDI session ends.
        result.disableCollection();
        return result;
    }
    static int layoutInt(String method) throws Exception {
        ClassType layout = type("dev.gilbe.dynamicinventory.inventory.InventoryLayout");
        return ((IntegerValue) layout.invokeMethod(thread, layout.methodsByName(method).getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED)).value();
    }
    static int ownedLayoutInt(ObjectReference inventory, String method) throws Exception {
        ClassType layout = type("dev.gilbe.dynamicinventory.inventory.InventoryLayout");
        ObjectReference shape = (ObjectReference) layout.invokeMethod(thread,
            layout.methodsByName("of").getFirst(), List.of(inventory), ObjectReference.INVOKE_SINGLE_THREADED);
        return ((IntegerValue) call(shape, method, "()I")).value();
    }
    static String canonicalNbt(ObjectReference nbt) throws Exception {
        nbt.disableCollection();
        ClassType diagnostics = type("dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics");
        ObjectReference json = (ObjectReference) diagnostics.invokeMethod(thread,
            methods(diagnostics, "canonicalNbt", "(Lnet/minecraft/nbt/NBTBase;)Lcom/google/gson/JsonObject;").getFirst(),
            List.of(nbt), ObjectReference.INVOKE_SINGLE_THREADED);
        json.disableCollection();
        return ((StringReference) call(json, "toString", "()Ljava/lang/String;")).value();
    }
    static void act(String[] args) throws Exception {
        ObjectReference player = field(client, "thePlayer");
        if (Boolean.getBoolean("dinv.packInput") && Set.of("click", "visible-click", "layout-control", "nei-fill-recipe").contains(args[0])) {
            ClassType events = type("com.cleanroommc.bogosorter.ClientEventHandler");
            events.invokeMethod(thread, events.methodsByName("handleInput").getFirst(),
                List.of(field(client, "currentScreen"), vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
        }
        switch (args[0]) {
            case "seed-large-grave": {
                act(new String[]{"require-qualification", "DI-Baseline-Large-Grave-Smoke-2-Qualification"});
                if (!thread.name().equals("Server thread")) {
                    // Arm a normal server packet handler before queuing a harmless command.
                    // Fixture writes must happen on the authoritative server thread, never the render thread.
                    ClassType handler = type("net.minecraft.network.NetHandlerPlayServer");
                    Method chat = methods(handler, "processChatMessage", "(Lnet/minecraft/network/play/client/C01PacketChatMessage;)V").getFirst();
                    BreakpointRequest seed = vm.eventRequestManager().createBreakpointRequest(chat.location());
                    seed.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                    seed.enable();
                    awaitingGraveSeed = true;
                    call(player, "sendChatMessage", "(Ljava/lang/String;)V", vm.mirrorOf("/dinvbaseline"));
                    break;
                }
                awaitingGraveSeed = false;
                ObjectReference serverPlayer = field(thread.frame(0).thisObject(), "playerEntity");
                if (layoutInt("storageSlots") != 216) throw new IllegalStateException("Requires cold-start capacity216");
                ObjectReference inventory = field(serverPlayer, "inventory");
                for (String array : List.of("mainInventory", "armorInventory"))
                    for (Value stack : ((ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), array))).getValues())
                        if (stack != null) throw new IllegalStateException("Seed requires empty inventory and armor");
                if (call(inventory, "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null)
                    throw new IllegalStateException("Seed requires empty cursor");
                ArrayReference main = (ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), "mainInventory"));
                ClassType items = type("net.minecraft.init.Items"), json = type("net.minecraft.nbt.JsonToNBT");
                for (int i = 0; i < 130; i++) {
                    ObjectReference stack = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V",
                        items.getValue(fieldInfo(items, "diamond")), vm.mirrorOf(1));
                    ObjectReference nbt = (ObjectReference) json.invokeMethod(thread,
                        methods(json, "func_150315_a", "(Ljava/lang/String;)Lnet/minecraft/nbt/NBTBase;").getFirst(),
                        List.of(vm.mirrorOf("{dinv_grave_smoke:\"large-grave-130\",index:" + i + ",typed:{byte:1b,long:17l}}")), ObjectReference.INVOKE_SINGLE_THREADED);
                    call(stack, "setTagCompound", "(Lnet/minecraft/nbt/NBTTagCompound;)V", nbt);
                    int raw = i < 36 ? i : i + 1;
                    main.setValue(raw, stack);
                }
                call(inventory, "markDirty", "()V");
                call(field(serverPlayer, "inventoryContainer"), "detectAndSendChanges", "()V");
                System.out.println("Seeded130 unique fixture stacks on the server thread; normal death/reclaim remain native");
                break;
            }
            case "sneak": {
                act(new String[]{"require-qualification"});
                if (args.length != 2 || !(args[1].equals("true") || args[1].equals("false")))
                    throw new IllegalArgumentException("sneak true|false");
                // Unfocused LWJGL windows clear held keys. Use the ordinary client packet instead;
                // the server still validates/applies it through its native handler, with no server field writes.
                ObjectReference packet = make("net.minecraft.network.play.client.C0BPacketEntityAction",
                    "(Lnet/minecraft/entity/Entity;I)V", player, vm.mirrorOf(Boolean.parseBoolean(args[1]) ? 1 : 2));
                call(field(player, "sendQueue"), "addToSendQueue", "(Lnet/minecraft/network/Packet;)V", packet);
                System.out.println("sneakPacket=" + args[1] + " awaiting normal server handler");
                break;
            }
            case "hotbar-select": {
                act(new String[]{"require-qualification"});
                if (args.length != 2) throw new IllegalArgumentException("hotbar-select 0..8");
                int selected = Integer.parseInt(args[1]);
                if (selected < 0 || selected > 8) throw new IllegalArgumentException("Hotbar index must be 0..8");
                ObjectReference inventory = field(player, "inventory");
                inventory.setValue(fieldInfo(inventory.referenceType(), "currentItem"), vm.mirrorOf(selected));
                // syncCurrentPlayItem inside the normal controller path sends C09 to the server.
                call(field(client, "playerController"), "updateController", "()V");
                System.out.println("selectedHotbar=" + selected);
                break;
            }
            case "fixture-player-state":
            case "grave-info": {
                act(new String[]{"require-qualification"});
                if (args.length != 1 && !(args[0].equals("grave-info") && args.length == 4))
                    throw new IllegalArgumentException("grave-info [x y z] or fixture-player-state");
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                ObjectReference world = (ObjectReference) call(server, "worldServerForDimension", "(I)Lnet/minecraft/world/WorldServer;", vm.mirrorOf(0));
                ObjectReference manager = (ObjectReference) call(server, "getConfigurationManager", "()Lnet/minecraft/server/management/ServerConfigurationManager;");
                ObjectReference serverPlayer = (ObjectReference) call(manager, "func_152612_a", "(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;",
                    call(player, "getCommandSenderName", "()Ljava/lang/String;"));
                if (serverPlayer == null) throw new IllegalStateException("No matching integrated server player");
                System.out.println("serverSneaking=" + call(serverPlayer, "isSneaking", "()Z"));
                ObjectReference inventory = field(serverPlayer, "inventory");
                System.out.println("serverCurrentItem=" + inventory.getValue(fieldInfo(inventory.referenceType(), "currentItem")));
                System.out.println("serverDimension=" + serverPlayer.getValue(fieldInfo(serverPlayer.referenceType(), "dimension")));
                ObjectReference gameType = (ObjectReference) call(field(serverPlayer, "theItemInWorldManager"), "getGameType", "()Lnet/minecraft/world/WorldSettings$GameType;");
                System.out.println("serverGameMode=" + ((StringReference) call(gameType, "toString", "()Ljava/lang/String;")).value());
                System.out.println("serverDifficulty=" + ((StringReference) call(field(world, "difficultySetting"), "toString", "()Ljava/lang/String;")).value());
                ObjectReference rules = (ObjectReference) call(world, "getGameRules", "()Lnet/minecraft/world/GameRules;");
                System.out.println("gameRulesNbt=" + canonicalNbt((ObjectReference) call(rules, "writeGameRulesToNBT", "()Lnet/minecraft/nbt/NBTTagCompound;")));
                if (args[0].equals("fixture-player-state")) break;
                double[] near = new double[3];
                for (int i = 0; i < 3; i++) near[i] = args.length == 4 ? Double.parseDouble(args[i + 1])
                    : ((DoubleValue) serverPlayer.getValue(fieldInfo(serverPlayer.referenceType(), new String[]{"posX", "posY", "posZ"}[i]))).value();
                ArrayReference tiles = (ArrayReference) call(field(world, "loadedTileEntityList"), "toArray", "()[Ljava/lang/Object;");
                ObjectReference grave = null;
                int count = 0;
                double nearest = Double.POSITIVE_INFINITY;
                for (Value value : tiles.getValues()) {
                    ObjectReference tile = (ObjectReference) value;
                    if (!tile.referenceType().name().equals("openblocks.common.tileentity.TileEntityGrave")) continue;
                    count++;
                    double distance = 0;
                    for (int i = 0; i < 3; i++) {
                        double delta = ((IntegerValue) tile.getValue(fieldInfo(tile.referenceType(), new String[]{"xCoord", "yCoord", "zCoord"}[i]))).value() - near[i];
                        distance += delta * delta;
                    }
                    if (distance < nearest) { nearest = distance; grave = tile; }
                }
                System.out.println("loadedGraveCount=" + count);
                if (grave == null) { System.out.println("graveNbt=null"); break; }
                System.out.println("graveDistanceSquared=" + nearest);
                for (String coordinate : List.of("xCoord", "yCoord", "zCoord"))
                    System.out.println("grave." + coordinate + "=" + grave.getValue(fieldInfo(grave.referenceType(), coordinate)));
                StringReference owner = (StringReference) call(grave, "getUsername", "()Ljava/lang/String;");
                System.out.println("graveOwner=" + (owner == null ? "null" : owner.value()));
                ObjectReference nbt = make("net.minecraft.nbt.NBTTagCompound", "()V");
                // Serialization observes the stable server TE; no client-thread item mutation is performed.
                call(grave, "writeToNBT", "(Lnet/minecraft/nbt/NBTTagCompound;)V", nbt);
                System.out.println("graveNbt=" + canonicalNbt(nbt));
                break;
            }
            case "openblocks-guard-info": {
                ClassType store = type("openblocks.common.PlayerInventoryStore");
                boolean guarded = store.methods().stream()
                    .anyMatch(method -> method.name().contains("dynamicinventory$checkBackupSize"));
                System.out.println("openBlocksRestoreGuard=" + guarded);
                if (!guarded) throw new IllegalStateException("OpenBlocks restore guard is not installed in this process");
                break;
            }
            case "opening-report": {
                ClassType fixture = type("dev.gilbe.dynamicinventory.qualification.OpeningGateFixtures$Client");
                fixture.invokeMethod(thread, fixture.methodsByName("capture").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "mui-widget-info": {
                ObjectReference screen = field(client,"currentScreen");
                ObjectReference modular = (ObjectReference) call(screen,"getScreen",null);
                ArrayDeque<ObjectReference> pending = new ArrayDeque<>(); pending.add((ObjectReference)call(modular,"getMainPanel",null));
                while (!pending.isEmpty()) {
                    ObjectReference widget=pending.removeFirst();
                    String kind=widget.referenceType().name();
                    StringReference name=(StringReference)call(widget,"getName",null);
                    String widgetName=name==null?"":name.value();
                    if (kind.contains("InventoryScrollWidget") || Set.of("slot_9","slot_27","slot_80","player_inventory").contains(widgetName)) {
                        ObjectReference area=(ObjectReference)call(widget,"getArea",null);
                        StringBuilder bounds=new StringBuilder();
                        for(String key:List.of("x","y","width","height","rx","ry"))bounds.append(key).append("=").append(area.getValue(fieldInfo(area.referenceType(),key))).append(" ");
                        System.out.println(kind+" "+widgetName+" "+bounds+" enabled="+call(widget,"isEnabled",null)+" hover="+call(widget,"canHover",null));
                    }
                    ObjectReference children=(ObjectReference)call(widget,"getChildren",null);
                    int count=((IntegerValue)call(children,"size","()I")).value();
                    for(int i=0;i<count;i++)pending.add((ObjectReference)call(children,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)));
                }
                break;
            }
            case "ui-binding": {
                ObjectReference container = field(player, "openContainer");
                ObjectReference screen = field(client, "currentScreen");
                ClassType api = type("dev.gilbe.dynamicinventory.api.InventoryUi");
                ObjectReference binding = (ObjectReference) api.invokeMethod(thread,
                    api.methodsByName("getBinding").getFirst(), List.of(container), ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference adapter = (ObjectReference) api.invokeMethod(thread,
                    api.methodsByName("findAdapter").getFirst(), List.of(container), ObjectReference.INVOKE_SINGLE_THREADED);
                int count = ((IntegerValue) call(field(container,"inventorySlots"),"size","()I")).value();
                if (args.length > 1 && args[1].equals("none")) {
                    if (binding != null || adapter != null) throw new AssertionError("Unknown container was expanded/registered");
                    System.out.println("PASS unregistered count=" + count + " container=" + container.referenceType().name());
                    break;
                }
                if (binding == null || adapter == null) throw new AssertionError("Registered container has no binding/adapter");
                if (!field(player,"inventory").equals(call(binding,"getPlayerInventory",null)))
                    throw new AssertionError("Binding lost canonical inventory");
                ObjectReference layout = (ObjectReference) api.invokeMethod(thread,
                    api.methodsByName("findLayout").getFirst(), List.of(container,screen.referenceType().classObject()),
                    ObjectReference.INVOKE_SINGLE_THREADED);
                if (layout == null) throw new AssertionError("Registered GUI has no shared layout");
                System.out.println("PASS registered adapter=" + ((StringReference) call(adapter,"getId",null)).value()
                    + " native=" + call(binding,"getNativeSlotCount",null) + " ordinary=" + call(binding,"getOrdinarySlotCount",null)
                    + " count=" + count + " canonical=true layout=true");
                break;
            }
            case "automatic-scroll-screen": {
                act(new String[]{"require-qualification", "DI-Baseline-Automatic-Scroll-Qualification"});
                ObjectReference active = field(player, "openContainer");
                ObjectReference old = field(client, "currentScreen");
                if (!active.referenceType().name().equals("net.minecraft.inventory.ContainerChest")
                    || old == null || !old.referenceType().name().equals("net.minecraft.client.gui.inventory.GuiChest"))
                    throw new IllegalStateException("Open the disposable vanilla chest first");
                if (call(field(player,"inventory"),"getItemStack","()Lnet/minecraft/item/ItemStack;") != null)
                    throw new IllegalStateException("Put down the cursor stack first");
                act(new String[]{"gate-state", "ready"});
                ObjectReference panel = (ObjectReference)call(old,"dynamicinventory$panel",
                    "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                if(panel!=null) call(panel,"restore","()V");
                ObjectReference slot = (ObjectReference)call(active,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(0));
                ObjectReference replacement = make("thaumcraft.client.gui.GuiMagicBox",
                    "(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/inventory/IInventory;)V",
                    field(player,"inventory"),field(slot,"inventory"));
                // Synthetic PRESENTATION fixture only: keep the already negotiated vanilla host on
                // both peers. This does not qualify Thaumcraft's actual ContainerMagicBox opening.
                replacement.setValue(fieldInfo(replacement.referenceType(),"inventorySlots"),active);
                call(client,"displayGuiScreen","(Lnet/minecraft/client/gui/GuiScreen;)V",replacement);
                act(new String[]{"ui-binding"});
                ObjectReference decorated = (ObjectReference)call(replacement,"dynamicinventory$panel",
                    "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                if(decorated==null || ((IntegerValue)call(decorated,"visibleColumns","()I")).value()!=9
                    || ((IntegerValue)call(decorated,"visibleRows","()I")).value()!=3)
                    throw new AssertionError("Fallback did not supply the compact nine-by-three viewport");
                System.out.println("PASS synthetic storage presentation fallback; native server/client host retained");
                break;
            }
            case "automatic-scroll-bottom": {
                act(new String[]{"require-qualification", "DI-Baseline-Automatic-Scroll-Qualification"});
                ObjectReference screen = field(client,"currentScreen");
                ObjectReference panel = (ObjectReference)call(screen,"dynamicinventory$panel",
                    "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                if(panel==null) throw new AssertionError("Fallback panel required");
                call(panel,"scroll","(I)V",vm.mirrorOf(1000));
                ObjectReference host = field(player,"openContainer");
                ObjectReference slots = field(host,"inventorySlots");
                int last = ((IntegerValue)call(slots,"size","()I")).value()-1;
                ObjectReference slot = (ObjectReference)call(host,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(last));
                if(((IntegerValue)slot.getValue(fieldInfo(slot.referenceType(),"xDisplayPosition"))).value()<0
                    || ((IntegerValue)slot.getValue(fieldInfo(slot.referenceType(),"yDisplayPosition"))).value()<0)
                    throw new AssertionError("Last expanded slot is still hidden after scrolling");
                System.out.println("PASS last expanded slot visible after scrolling: "+last);
                break;
            }
            case "ui-audit": {
                ClassType audit = type("dev.gilbe.dynamicinventory.client.InventoryUiAudit");
                ObjectReference report = (ObjectReference) audit.invokeMethod(thread,
                    audit.methodsByName("capture").getFirst(), List.of(field(client, "currentScreen")),
                    ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("uiAudit=" + ((StringReference) call(report,"getAbsolutePath","()Ljava/lang/String;")).value());
                break;
            }
            case "editor-save-if-open": {
                ClassType editorType = type("dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor");
                if (editorType.getValue(editorType.fieldByName("active")) != null) {
                    act(new String[]{"editor-control", "100"});
                    if (editorType.getValue(editorType.fieldByName("active")) != null)
                        throw new IllegalStateException("Editor save failed; preserve the open draft before restarting");
                }
                break;
            }
            case "editor-open": {
                ClassType editor = type("dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor");
                BooleanValue opened = (BooleanValue) editor.invokeMethod(thread, editor.methodsByName("open").getFirst(),
                    List.of(field(client, "currentScreen")), ObjectReference.INVOKE_SINGLE_THREADED);
                if (!opened.value()) throw new IllegalStateException("Editor rejected the current screen");
                break;
            }
            case "editor-control": {
                if (args.length > 2) {
                    for (int i = 0; i < Integer.parseInt(args[2]); i++) act(new String[]{"editor-control", args[1]});
                    break;
                }
                ClassType editorType = type("dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor");
                ObjectReference editor = (ObjectReference) editorType.getValue(editorType.fieldByName("active"));
                if (editor == null) throw new AssertionError("Editor is not open");
                int id = Integer.parseInt(args[1]);
                ObjectReference buttons = field(editor, "buttonList");
                ArrayReference entries = (ArrayReference) call(buttons, "toArray", "()[Ljava/lang/Object;");
                ObjectReference selected = null;
                for (Value entry : entries.getValues()) {
                    ObjectReference button = (ObjectReference) entry;
                    if (((IntegerValue) button.getValue(fieldInfo(button.referenceType(), "id"))).value() == id)
                        selected = button;
                }
                if (selected == null || !((BooleanValue) selected.getValue(fieldInfo(selected.referenceType(), "enabled"))).value())
                    throw new AssertionError("Missing/disabled editor control " + id);
                call(editor, "actionPerformed", "(Lnet/minecraft/client/gui/GuiButton;)V", selected);
                break;
            }
            case "editor-crop-check": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference panel = (ObjectReference) call(screen, "dynamicinventory$panel", "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                ClassType panelType = type("dev.gilbe.dynamicinventory.client.NativeInventoryPanel");
                Field scope = panelType.fieldByName("drawingBackground");
                Value previous = panelType.getValue(scope);
                try {
                    // Probe the real scoped crop math while the render thread is paused, restoring the scope immediately.
                    panelType.setValue(scope, panel);
                    int width = ((IntegerValue) panel.getValue(fieldInfo(panel.referenceType(), "nativeWidth"))).value();
                    int height = ((IntegerValue) panel.getValue(fieldInfo(panel.referenceType(), "nativeHeight"))).value();
                    int left = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value();
                    int top = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value();
                    int inset = ((IntegerValue) call(field(panel, "layout"), "getBackgroundInset", "()I")).value();
                    if (inset < 1) throw new AssertionError("Increase Texture crop before checking it");
                    if (((IntegerValue) panel.getValue(fieldInfo(panel.referenceType(), "frameStyle"))).value() != 0) inset = Math.max(4, inset);
                    ArrayReference result = (ArrayReference) panelType.invokeMethod(thread, panelType.methodsByName("clipBackground").getFirst(),
                        List.of(vm.mirrorOf(left), vm.mirrorOf(top), vm.mirrorOf(0), vm.mirrorOf(0), vm.mirrorOf(width), vm.mirrorOf(height)), ObjectReference.INVOKE_SINGLE_THREADED);
                    if (result == null || ((IntegerValue) result.getValue(0)).value() != left + inset
                        || ((IntegerValue) result.getValue(4)).value() != width - 2 * inset)
                        throw new AssertionError("Native texture crop did not change the drawn rectangle");
                    System.out.println("PASS live native crop inset=" + inset);
                } finally { panelType.setValue(scope, previous); }
                break;
            }
            case "runtime-info": {
                ClassType system = (ClassType) vm.classesByName("java.lang.System").getFirst();
                for (String key : new String[] {"java.version", "java.vendor", "java.home"}) {
                    StringReference value = (StringReference) system.invokeMethod(thread,
                        system.methodsByName("getProperty", "(Ljava/lang/String;)Ljava/lang/String;").getFirst(),
                        List.of(vm.mirrorOf(key)), ObjectReference.INVOKE_SINGLE_THREADED);
                    System.out.println(key + "=" + value.value());
                }
                ClassType runtime = (ClassType) vm.classesByName("java.lang.Runtime").getFirst();
                ObjectReference instance = (ObjectReference) runtime.invokeMethod(thread,
                    runtime.methodsByName("getRuntime").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("maxHeapBytes=" + call(instance,"maxMemory","()J"));
                break;
            }
            case "theme-info": {
                ClassType themeType = type("dev.gilbe.dynamicinventory.client.InventoryGuiTheme");
                ObjectReference theme = (ObjectReference) themeType.invokeMethod(thread,
                    methods(themeType, "get", "()Ldev/gilbe/dynamicinventory/client/InventoryGuiTheme;").getFirst(),
                    List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                StringReference name = (StringReference) theme.getValue(fieldInfo(theme.referenceType(), "name"));
                boolean custom = ((BooleanValue) theme.getValue(fieldInfo(theme.referenceType(), "custom"))).value();
                int panelFill = ((IntegerValue) theme.getValue(fieldInfo(theme.referenceType(), "panelFill"))).value();
                int playerFill = ((IntegerValue) theme.getValue(fieldInfo(theme.referenceType(), "playerFill"))).value();
                System.out.println("Theme name=" + name.value() + " custom=" + custom + " panelFill=#" +
                    String.format("%08X", panelFill) + " playerFill=#" + String.format("%08X", playerFill));
                break;
            }
            case "resource-pack": {
                if (args.length != 3 || !args[1].equals("Glass-UI-Dynamic-Inventory.zip")
                    || !(args[2].equals("true") || args[2].equals("false")))
                    throw new IllegalArgumentException("Usage: resource-pack Glass-UI-Dynamic-Inventory.zip true|false");
                boolean enable = Boolean.parseBoolean(args[2]);
                ObjectReference repository = (ObjectReference) call(client, "getResourcePackRepository",
                    "()Lnet/minecraft/client/resources/ResourcePackRepository;");
                call(repository, "updateRepositoryEntriesAll", "()V");
                ObjectReference allEntries = (ObjectReference) call(repository, "getRepositoryEntriesAll", "()Ljava/util/List;");
                ObjectReference activeEntries = (ObjectReference) make("java.util.ArrayList", "()V");
                ObjectReference selectedEntry = null;
                int allCount = ((IntegerValue) call(allEntries, "size", "()I")).value();
                for (int i = 0; i < allCount; i++) {
                    ObjectReference entry = (ObjectReference) call(allEntries, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    StringReference entryName = (StringReference) call(entry, "getResourcePackName", "()Ljava/lang/String;");
                    if (entryName.value().equals(args[1])) selectedEntry = entry;
                }
                if (enable && selectedEntry == null)
                    throw new IllegalStateException("Resource pack file not found: " + args[1]);

                ObjectReference oldEntries = (ObjectReference) call(repository, "getRepositoryEntries", "()Ljava/util/List;");
                int activeCount = ((IntegerValue) call(oldEntries, "size", "()I")).value();
                for (int i = 0; i < activeCount; i++) {
                    ObjectReference entry = (ObjectReference) call(oldEntries, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    StringReference entryName = (StringReference) call(entry, "getResourcePackName", "()Ljava/lang/String;");
                    if (!entryName.value().equals(args[1]))
                        call(activeEntries, "add", "(Ljava/lang/Object;)Z", entry);
                }
                if (enable) call(activeEntries, "add", "(Ljava/lang/Object;)Z", selectedEntry);
                call(repository, "func_148527_a", "(Ljava/util/List;)V", activeEntries);

                ObjectReference gameSettings = field(client, "gameSettings");
                ObjectReference configuredNames = field(gameSettings, "resourcePacks");
                call(configuredNames, "clear", "()V");
                int resultingCount = ((IntegerValue) call(activeEntries, "size", "()I")).value();
                for (int i = 0; i < resultingCount; i++) {
                    ObjectReference entry = (ObjectReference) call(activeEntries, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    StringReference entryName = (StringReference) call(entry, "getResourcePackName", "()Ljava/lang/String;");
                    call(configuredNames, "add", "(Ljava/lang/Object;)Z", vm.mirrorOf(entryName.value()));
                }
                call(gameSettings, "saveOptions", "()V");
                call(client, "refreshResources", "()V");
                System.out.println("Resource pack " + args[1] + " enabled=" + enable);
                break;
            }
            case "screen-status": {
                ObjectReference screen = field(client, "currentScreen");
                System.out.println("Screen=" + (screen == null ? "none" : screen.referenceType().name()) +
                    " health=" + (player == null ? "none" : call(player,"getHealth","()F")));
                if (screen != null && screen.referenceType().name().equals("net.minecraft.client.gui.GuiDisconnected"))
                    System.out.println("Disconnect reason=" + call(field(screen,"field_146304_f"), "getUnformattedText", "()Ljava/lang/String;"));
                if (screen != null) {
                    ObjectReference buttons = field(screen, "buttonList");
                    int count = ((IntegerValue) call(buttons,"size","()I")).value();
                    for (int i=0;i<count;i++) {
                        ObjectReference button=(ObjectReference) call(buttons,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        System.out.println("button="+button.referenceType().name()+" id="+button.getValue(fieldInfo(button.referenceType(),"id")));
                    }
                }
                break;
            }
            case "button-geometry": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference buttons = field(screen, "buttonList");
                StringBuilder json = new StringBuilder("{\"guiLeft\":")
                    .append(screen.getValue(fieldInfo(screen.referenceType(), "guiLeft")))
                    .append(",\"guiTop\":").append(screen.getValue(fieldInfo(screen.referenceType(), "guiTop")))
                    .append(",\"guiWidth\":").append(screen.getValue(fieldInfo(screen.referenceType(), "xSize")))
                    .append(",\"guiHeight\":").append(screen.getValue(fieldInfo(screen.referenceType(), "ySize")))
                    .append(",\"buttons\":[");
                int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                for (int i = 0; i < count; i++) {
                    ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    if (i > 0) json.append(',');
                    json.append("{\"class\":\"").append(button.referenceType().name()).append('"');
                    for (String name : new String[] {"id", "xPosition", "yPosition", "width", "height", "visible", "enabled"})
                        json.append(",\"").append(name).append("\":").append(button.getValue(fieldInfo(button.referenceType(), name)));
                    if (button.referenceType().name().equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton")) {
                        ObjectReference group = field(button, "slotGroup");
                        json.append(",\"playerGroup\":").append(call(group, "isPlayerInventory", "()Z"));
                        json.append(",\"hotbarGroup\":").append(call(group, "isHotbar", "()Z"));
                        ObjectReference groupSlots = (ObjectReference) call(group, "getSlots", "()Ljava/util/List;");
                        ArrayReference members = (ArrayReference) call(groupSlots, "toArray", "()[Ljava/lang/Object;");
                        json.append(",\"groupSlotIds\":[");
                        boolean first = true;
                        for (Value member : members.getValues()) {
                            if (!first) json.append(',');
                            first = false;
                            ObjectReference slot = (ObjectReference) member;
                            json.append(slot.getValue(fieldInfo(slot.referenceType(), "slotNumber")));
                        }
                        json.append(']');
                    }
                    json.append('}');
                }
                json.append(']');
                ObjectReference panel = (ObjectReference) call(screen, "dynamicinventory$panel", "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                ObjectReference attached = panel == null ? null : field(panel, "chestLayout");
                if (attached != null) {
                    json.append(",\"attachedLayout\":{");
                    boolean first = true;
                    for (String name : new String[] {"width", "chestColumns", "lowerY", "lowerHeight", "playerY", "craftingY", "sideBySide"}) {
                        if (!first) json.append(',');
                        first = false;
                        json.append('"').append(name).append("\":").append(attached.getValue(fieldInfo(attached.referenceType(), name)));
                    }
                    json.append('}');
                }
                System.out.println("buttonGeometry=" + json.append('}'));
                break;
            }
            case "held-ui-check": {
                ObjectReference screen = field(client, "currentScreen");
                if (!screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))
                    throw new AssertionError("Expanded inventory must stay open");
                ObjectReference inventory = field(player, "inventory");
                if (call(inventory, "getItemStack", "()Lnet/minecraft/item/ItemStack;") == null)
                    throw new AssertionError("Expected a held cursor item");
                ObjectReference buttons = field(screen, "buttonList");
                int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                int nativeSortButtons = 0;
                boolean baubles = false;
                for (int i = 0; i < count; i++) {
                    ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    int id = ((IntegerValue) button.getValue(fieldInfo(button.referenceType(), "id"))).value();
                    if (id == 394658246 || id == 394658247) {
                        if (!((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "enabled"))).value()
                            || !((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "visible"))).value())
                            throw new AssertionError("Native Bogo sort/config icon disappeared while holding an item");
                        nativeSortButtons++;
                    }
                    if (id == 109) {
                        if (((IntegerValue) call(button, "getHoverState", "(Z)I", vm.mirrorOf(false))).value() != 1
                            || ((IntegerValue) call(button, "getHoverState", "(Z)I", vm.mirrorOf(true))).value() != 1)
                            throw new AssertionError("Disabled Baubles button draws a stray tooltip");
                        baubles = true;
                    }
                }
                if (nativeSortButtons != 2 || !baubles) throw new AssertionError("Missing pack controls");
                ClassType compat = type("dev.gilbe.dynamicinventory.compat.PackClientCompatibility");
                ObjectReference container = field(player, "inventoryContainer");
                ArrayReference main = (ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), "mainInventory"));
                ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(main.length() + 9));
                ObjectReference tooltip = (ObjectReference) compat.invokeMethod(thread,
                    compat.methodsByName("baubleTooltip").getFirst(), List.of(slot), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("Equipment tooltip=" + call(tooltip, "toString", "()Ljava/lang/String;"));
                System.out.println("PASS held item: both Bogo icons visible, Baubles button tooltip suppressed");
                break;
            }
            case "pack-input": {
                ClassType events = type("com.cleanroommc.bogosorter.ClientEventHandler");
                events.invokeMethod(thread, events.methodsByName("handleInput").getFirst(),
                    List.of(field(client, "currentScreen"), vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "pack-overlay-check": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference container = field(screen, "inventorySlots");
                ClassType info = type("codechicken.nei.recipe.RecipeInfo");
                for (String registration : new String[] {"hasDefaultOverlay", "hasOverlayHandler"}) {
                    Value present = info.invokeMethod(thread, info.methodsByName(registration).getFirst(), List.of(screen, vm.mirrorOf("crafting2x2")), ObjectReference.INVOKE_SINGLE_THREADED);
                    if (!((BooleanValue) present).value()) throw new AssertionError("Missing NEI " + registration);
                }
                ObjectReference overlay = (ObjectReference) info.invokeMethod(thread, info.methodsByName("getOverlayHandler").getFirst(), List.of(screen, vm.mirrorOf("crafting2x2")), ObjectReference.INVOKE_SINGLE_THREADED);
                for (int id : new int[] {9,45,46,72}) {
                    ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(id));
                    boolean ordinary = ((BooleanValue) call(overlay,"canMoveFrom","(Lnet/minecraft/inventory/Slot;Lnet/minecraft/client/gui/inventory/GuiContainer;)Z", slot,screen)).value();
                    if (ordinary != (id != 45)) throw new AssertionError("NEI ordinary-slot classification: " + id);
                }
                System.out.println("PASS real-pack NEI ghost/transfer registration and ordinary/offhand slot classification");
                break;
            }
            case "nei-open-recipe": {
                ClassType itemType = type("net.minecraft.item.Item");
                ObjectReference registry = (ObjectReference) itemType.getValue(fieldInfo(itemType, "itemRegistry"));
                ObjectReference item = (ObjectReference) call(registry, "getObject", "(Ljava/lang/Object;)Ljava/lang/Object;", vm.mirrorOf(args[1]));
                ObjectReference stack = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", item, vm.mirrorOf(1));
                ArrayReference query = ((ArrayType) vm.classesByName("java.lang.Object[]").getFirst()).newInstance(1);
                query.setValue(0, stack);
                ClassType recipes = type("codechicken.nei.recipe.GuiCraftingRecipe");
                recipes.invokeMethod(thread, recipes.methodsByName("openRecipeGui").getFirst(),
                    List.of(vm.mirrorOf("item"), query), ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference recipe = field(client, "currentScreen");
                ObjectReference handlers = field(recipe, "currenthandlers");
                ArrayReference handlerEntries = (ArrayReference) call(handlers, "toArray", "()[Ljava/lang/Object;");
                boolean selected = false;
                for (int i = 0; i < handlerEntries.length(); i++) {
                    ObjectReference handler = (ObjectReference) handlerEntries.getValue(i);
                    StringReference ident = (StringReference) call(handler, "getOverlayIdentifier", "()Ljava/lang/String;");
                    String overlay = ident == null ? "" : ident.value();
                    System.out.println("recipeHandler=" + handler.referenceType().name() + " overlay=" + overlay);
                    if (!selected && overlay.equals("crafting")) {
                        int recipesCount = ((IntegerValue) call(handler, "numRecipes", "()I")).value();
                        for (int r = 0; r < recipesCount; r++) {
                            ObjectReference inputs = (ObjectReference) call(handler, "getIngredientStacks", "(I)Ljava/util/List;", vm.mirrorOf(r));
                            System.out.println("craftRecipe=" + r + " " + call(inputs, "toString", "()Ljava/lang/String;"));
                        }
                        call(recipe, "setRecipePage", "(I)V", vm.mirrorOf(i));
                        selected = true;
                    }
                }
                if (!selected) throw new AssertionError("No native crafting recipe for " + args[1]);
                break;
            }
            case "nei-fill-recipe":
            case "nei-return": {
                ObjectReference recipe = field(client, "currentScreen");
                ObjectReference host = field(recipe, "firstGui");
                if (host == null) throw new AssertionError("A recipe with a real host is required");
                if (args[0].equals("nei-return")) {
                    call(recipe, "keyTyped", "(CI)V", vm.mirrorOf('\0'), vm.mirrorOf(1));
                } else {
                    ArrayReference buttons = (ArrayReference) recipe.getValue(fieldInfo(recipe.referenceType(), "overlayButtons"));
                    ObjectReference button = buttons.length() > 0 ? (ObjectReference) buttons.getValue(0) : null;
                    if (button == null) {
                        // New NEI keeps its real buttons in recipe widgets; the legacy array stays empty.
                        ObjectReference widgets = (ObjectReference) call(field(recipe, "container"), "getWidgets", "()Ljava/util/List;");
                        ArrayReference entries = (ArrayReference) call(widgets, "toArray", "()[Ljava/lang/Object;");
                        for (Value entry : entries.getValues()) {
                            ObjectReference widget = (ObjectReference) entry;
                            if (!widget.referenceType().name().equals("codechicken.nei.recipe.widget.RecipeWidget")) continue;
                            ObjectReference nativeButtons = (ObjectReference) call(widget, "getRecipeButtons", "()Ljava/util/List;");
                            ArrayReference candidates = (ArrayReference) call(nativeButtons, "toArray", "()[Ljava/lang/Object;");
                            for (Value candidate : candidates.getValues()) {
                                ObjectReference nativeButton = (ObjectReference) candidate;
                                if (nativeButton.referenceType().name().equals("codechicken.nei.recipe.GuiOverlayButton")) {
                                    button = nativeButton;
                                    break;
                                }
                            }
                            if (button != null) break;
                        }
                    }
                    if (button == null) throw new AssertionError("No NEI fill button");
                    if (!((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "canFillCraftingGrid"))).value())
                        throw new AssertionError("NEI says this recipe cannot fill the native crafting grid");
                    ObjectReference ref = field(button, "handlerRef");
                    ObjectReference handler = field(ref, "handler");
                    Value index = ref.getValue(fieldInfo(ref.referenceType(), "recipeIndex"));
                    ObjectReference ingredients = (ObjectReference) call(handler, "getIngredientStacks", "(I)Ljava/util/List;", index);
                    System.out.println("fillHandler=" + handler.referenceType().name() + " recipeIndex=" + index
                        + " ingredients=" + call(ingredients, "toString", "()Ljava/lang/String;"));
                    // Exercise the actual overlay handler after the same host-return step as NEI's button.
                    call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", host);
                    ObjectReference overlay = (ObjectReference) call(ref, "getOverlayHandler", "(Lnet/minecraft/client/gui/inventory/GuiContainer;)Lcodechicken/nei/api/IOverlayHandler;", host);
                    ArrayReference mapping = (ArrayReference) call(overlay, "mapIngredSlots", "(Lnet/minecraft/client/gui/inventory/GuiContainer;Ljava/util/List;)[[Lnet/minecraft/inventory/Slot;", host, ingredients);
                    int mapped = 0;
                    for (Value value : mapping.getValues()) mapped += ((ArrayReference) value).length();
                    System.out.println("mappedIngredients=" + mapped + "/" + mapping.length());
                    call(button, "overlayRecipe", "(Z)V", vm.mirrorOf(true));
                }
                break;
            }
            case "nei-stale-close": {
                ObjectReference recipe = field(client, "currentScreen");
                ObjectReference host = field(recipe, "firstGui");
                if (host == null) throw new AssertionError("A recipe with a real host is required");
                ObjectReference connection = (ObjectReference) call(client, "getNetHandler", "()Lnet/minecraft/client/network/NetHandlerPlayClient;");
                ObjectReference packet = make("net.minecraft.network.play.server.S2EPacketCloseWindow", "(I)V", vm.mirrorOf(0));
                call(connection, "handleCloseWindow", "(Lnet/minecraft/network/play/server/S2EPacketCloseWindow;)V", packet);
                if (!recipe.equals(field(client, "currentScreen"))) throw new AssertionError("Stale placeholder close replaced the recipe screen");
                act(new String[]{"gate-state", "detour"});
                break;
            }
            case "gate-state": {
                ObjectReference access = (ObjectReference) call(client, "getNetHandler", "()Lnet/minecraft/client/network/NetHandlerPlayClient;");
                ObjectReference gate = (ObjectReference) call(access, "dynamicinventory$openingGate", "()Ldev/gilbe/dynamicinventory/client/ClientInventoryGate;");
                ObjectReference active = field(player, "openContainer");
                System.out.println("gate ready=" + gate.getValue(fieldInfo(gate.referenceType(), "ready")) + " committed=" + gate.getValue(fieldInfo(gate.referenceType(), "committed"))
                    + " host=" + field(gate, "container") + " active=" + active
                    + " input=" + call(gate, "allowsInput", "(Lnet/minecraft/entity/player/EntityPlayer;I)Z", player, active.getValue(fieldInfo(active.referenceType(), "windowId"))));
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                if (server != null) System.out.println("world=" + call(server, "getFolderName", "()Ljava/lang/String;"));
                if (args.length > 1) {
                    boolean expectedInput = args[1].equals("ready");
                    boolean input = ((BooleanValue) call(gate, "allowsInput", "(Lnet/minecraft/entity/player/EntityPlayer;I)Z", player,
                        active.getValue(fieldInfo(active.referenceType(), "windowId")))).value();
                    if (input != expectedInput || !((BooleanValue) gate.getValue(fieldInfo(gate.referenceType(), "ready"))).value())
                        throw new AssertionError("Opening agreement or input state lost: " + args[1]);
                }
                break;
            }
            case "wait-screen":
                System.out.println("PASS screen: " + field(client, "currentScreen").referenceType().name());
                break;
            case "pack-action": {
                if (field(client, "currentScreen") == null && !args[1].equals("baubles-check")) {
                    call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V",
                        make("net.minecraft.client.gui.inventory.GuiInventory", "(Lnet/minecraft/entity/player/EntityPlayer;)V", player));
                }
                ClassType tests = type("dev.gilbe.dynamicinventory.qualification.PackClientFixtures");
                tests.invokeMethod(thread, tests.methodsByName("run").getFirst(), List.of(vm.mirrorOf(args[1])), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "screen-fixtures":
            case "editor-fixtures":
            case "presentation-fixtures":
            case "mui-lifecycle-fixtures": {
                if (player == null || field(client, "currentScreen") == null)
                    throw new IllegalStateException("Open the disposable player inventory first");
                String fixture = switch (args[0]) {
                    case "screen-fixtures" -> "InventoryScreenFixtures";
                    case "editor-fixtures" -> "NativeEditorClientFixtures";
                    case "mui-lifecycle-fixtures" -> "Mui2LifecycleClientFixtures";
                    default -> "PresentationClientFixtures";
                };
                ClassType tests = type("dev.gilbe.dynamicinventory.qualification." + fixture);
                tests.invokeMethod(thread, tests.methodsByName("run", "()V").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "open-native-baubles": {
                ClassType handler = type("baubles.common.network.PacketHandler");
                ObjectReference wrapper = (ObjectReference) handler.getValue(handler.fieldByName("INSTANCE"));
                ObjectReference packet = make("baubles.common.network.PacketOpenBaublesInventory",
                    "(Lnet/minecraft/entity/player/EntityPlayer;)V", player);
                call(wrapper, "sendToServer", "(Lcpw/mods/fml/common/network/simpleimpl/IMessage;)V", packet);
                break;
            }
            case "player-window-check": {
                ObjectReference container = field(player,"openContainer");
                if (!container.equals(field(player,"inventoryContainer"))
                    || ((IntegerValue) container.getValue(fieldInfo(container.referenceType(),"windowId"))).value() != 0)
                    throw new AssertionError("Player screen uses the wrong local container after native-menu return");
                System.out.println("PASS client persistent player container active, window zero");
                break;
            }
            case "backhand-observe": {
                ObjectReference world = field(client, "theWorld");
                ObjectReference players = field(world, "playerEntities");
                int size = ((IntegerValue) call(players, "size", "()I")).value();
                ObjectReference observed = null;
                for (int i = 0; i < size; i++) {
                    ObjectReference candidate = (ObjectReference) call(players, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    String name = ((StringReference) call(candidate, "getCommandSenderName", "()Ljava/lang/String;")).value();
                    if (name.equals(args[1])) { observed = candidate; break; }
                }
                if (observed == null || observed.equals(player)) throw new AssertionError("Remote tracked player not present");
                ClassType backhand = type("xonin.backhand.api.core.BackhandUtils");
                ObjectReference stack = (ObjectReference) backhand.invokeMethod(thread, backhand.methodsByName("getOffhandItem").getFirst(), List.of(observed), ObjectReference.INVOKE_SINGLE_THREADED);
                if (args[2].equals("empty")) {
                    if (stack != null) throw new AssertionError("Remote offhand should be empty");
                } else {
                    if (stack == null) throw new AssertionError("Remote offhand should contain a stack");
                    ObjectReference tag = (ObjectReference) call(stack, "getTagCompound", "()Lnet/minecraft/nbt/NBTTagCompound;");
                    String role = ((StringReference) call(tag, "getString", "(Ljava/lang/String;)Ljava/lang/String;", vm.mirrorOf("role"))).value();
                    if (!role.equals(args[2])) throw new AssertionError("Remote offhand role mismatch: " + role);
                    String owner = ((StringReference) call(tag, "getString", "(Ljava/lang/String;)Ljava/lang/String;", vm.mirrorOf("owner"))).value();
                    ObjectReference uuid = (ObjectReference) call(observed, "getUniqueID", "()Ljava/util/UUID;");
                    String expectedOwner = ((StringReference) call(uuid, "toString", "()Ljava/lang/String;")).value();
                    if (!owner.equals(expectedOwner)) throw new AssertionError("Remote offhand belongs to another identity");
                }
                ClassType diagnostics = type("dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics");
                Value report = diagnostics.invokeMethod(thread, diagnostics.methodsByName("snapshot").getFirst(), List.of(observed, vm.mirrorOf("client"), vm.mirrorOf("backhand-tracking")), ObjectReference.INVOKE_SINGLE_THREADED);
                ClassType command = type("dev.gilbe.dynamicinventory.qualification.QualificationCommand");
                command.invokeMethod(thread, command.methodsByName("write").getFirst(), List.of(player, vm.mirrorOf("client-tracking-" + args[1] + "-" + args[2]), report), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("PASS tracked remote offhand: " + args[1] + " = " + args[2]);
                break;
            }
            case "backhand-key": {
                ClassType proxy = type("xonin.backhand.CommonProxy");
                ObjectReference key = (ObjectReference) proxy.getValue(fieldInfo(proxy, "SWAP_KEY"));
                Value code = call(key, "getKeyCode", "()I");
                ClassType bindings = type("net.minecraft.client.settings.KeyBinding");
                bindings.invokeMethod(thread, bindings.methodsByName("setKeyBindState", "(IZ)V").getFirst(),
                    List.of(code, vm.mirrorOf(args[1].equals("down"))), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "input-click":
            case "input-sort":
            case "input-wheel": {
                ObjectReference screen = field(client,"currentScreen");
                ObjectReference container = field(player,"openContainer");
                int x, y;
                if (args[0].equals("input-sort")) {
                    if (!Set.of("player", "chest").contains(args[1])) throw new IllegalArgumentException("Sort role must be player or chest");
                    ObjectReference buttons = field(screen, "buttonList");
                    int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                    ObjectReference selected = null;
                    for (int i = 0; i < count; i++) {
                        ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                        if (!button.referenceType().name().equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton")) continue;
                        ObjectReference group = field(button, "slotGroup");
                        if (((BooleanValue) call(group, "isPlayerInventory", "()Z")).value() != args[1].equals("player")
                            || !((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "sort"))).value()) continue;
                        if (!((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "visible"))).value()
                            || !((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "enabled"))).value()) continue;
                        if (selected != null) throw new AssertionError("Ambiguous sort button role");
                        selected = button;
                    }
                    if (selected == null) throw new AssertionError("Missing visible sort button for " + args[1]);
                    x = ((IntegerValue) selected.getValue(fieldInfo(selected.referenceType(), "xPosition"))).value() + 5;
                    y = ((IntegerValue) selected.getValue(fieldInfo(selected.referenceType(), "yPosition"))).value() + 5;
                } else {
                    ObjectReference slot = (ObjectReference) call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(Integer.parseInt(args[1])));
                    x = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(),"xDisplayPosition"))).value();
                    y = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(),"yDisplayPosition"))).value();
                    if (x < 0 || y < 0) throw new AssertionError("Hidden input slot");
                    x += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(),"guiLeft"))).value()+7;
                    y += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(),"guiTop"))).value()+7;
                }
                int sw=((IntegerValue)screen.getValue(fieldInfo(screen.referenceType(),"width"))).value();
                int sh=((IntegerValue)screen.getValue(fieldInfo(screen.referenceType(),"height"))).value();
                int dw=((IntegerValue)client.getValue(fieldInfo(client.referenceType(),"displayWidth"))).value();
                int dh=((IntegerValue)client.getValue(fieldInfo(client.referenceType(),"displayHeight"))).value();
                ClassType mouse=type("org.lwjglx.input.Mouse");
                Map<Field,Value> previous=new HashMap<>();
                for(String name:new String[]{"x","y","latestX","latestY","lastEventX","lastEventY"}) {
                    Field f=mouse.fieldByName(name); previous.put(f,mouse.getValue(f));
                    mouse.setValue(f,vm.mirrorOf(name.endsWith("X")||name.equals("x") ? x*dw/sw : (sh-y-1)*dh/sh));
                }
                ObjectReference framebuffer=(ObjectReference)call(client,"getFramebuffer","()Lnet/minecraft/client/shader/Framebuffer;");
                try {
                    call(framebuffer,"bindFramebuffer","(Z)V",vm.mirrorOf(true));
                    call(field(client,"entityRenderer"),"setupOverlayRendering","()V");
                    ClassType forge=type("net.minecraftforge.common.MinecraftForge");
                    ObjectReference bus=(ObjectReference)forge.getValue(forge.fieldByName("EVENT_BUS"));
                    ObjectReference pre=make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Pre","(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
                    if (!((BooleanValue)call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",pre)).value()) {
                        call(screen,"drawScreen","(IIF)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
                        call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post","(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f)));
                    }
                    if (screen.referenceType().name().equals("com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui")) {
                        ObjectReference cursor=(ObjectReference)call(screen,"getCursor",null);
                        call(cursor,"updateHovered","()V");
                    }
                    if (!methods(screen.referenceType(), "getScreen", null).isEmpty()
                        && screen.referenceType().name().equals("com.cleanroommc.modularui.screen.GuiContainerWrapper")) {
                        ObjectReference modular=(ObjectReference)call(screen,"getScreen",null);
                        call(modular,"onFrameUpdate","()V");
                        ObjectReference context=(ObjectReference)call(modular,"getContext",null);
                        ObjectReference hovered=(ObjectReference)call(context,"getHovered",null);
                        System.out.println("Input hovered="+(hovered==null ? "none" : hovered.referenceType().name()));
                    }
                    if(args[0].equals("input-click") || args[0].equals("input-sort")) {
                        for(boolean down:new boolean[]{true,false}) mouse.invokeMethod(thread,methods(mouse,"addButtonEvent","(IZ)V").getFirst(),List.of(vm.mirrorOf(0),vm.mirrorOf(down)),ObjectReference.INVOKE_SINGLE_THREADED);
                    } else mouse.invokeMethod(thread,methods(mouse,"addWheelEvent","(D)V").getFirst(),List.of(vm.mirrorOf(Double.parseDouble(args[2]))),ObjectReference.INVOKE_SINGLE_THREADED);
                    call(screen,"handleInput","()V");
                    if (args[0].equals("input-wheel")) {
                        // Tinkers polls the global wheel while drawing, not just in handleInput.
                        // Finish this frame under the synthetic hover before restoring the real cursor,
                        // or the same delta can scroll a different inventory on the next frame.
                        ObjectReference wheelFrame = make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Pre",
                            "(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
                        if (!((BooleanValue)call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",wheelFrame)).value()) {
                            call(screen,"drawScreen","(IIF)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
                            call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post",
                                "(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f)));
                        }
                        mouse.invokeMethod(thread,methods(mouse,"getDWheel","()I").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                    }
                } finally {
                    call(framebuffer,"unbindFramebuffer","()V");
                    for(var saved:previous.entrySet()) mouse.setValue(saved.getKey(),saved.getValue());
                }
                break;
            }
            case "chest-scroll": {
                ObjectReference screen = field(client, "currentScreen");
                if (!screen.referenceType().name().equals("tconstruct.tools.gui.CraftingStationGui"))
                    throw new AssertionError("Native crafting station required");
                ObjectReference slider = field(screen, "slider");
                call(slider, "setSliderValue", "(I)I", vm.mirrorOf(args[1].equals("bottom") ? Integer.MAX_VALUE : 0));
                call(screen, "updateChestSlots", "()V");
                System.out.println("chestScroll=" + call(slider, "getValue", "()I"));
                break;
            }
            case "scroll-info": {
                ObjectReference screen=field(client,"currentScreen");
                ObjectReference root;
                if(screen.referenceType().name().equals("com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui")) {
                    ObjectReference context=(ObjectReference)call(screen,"getContext",null);
                    root=(ObjectReference)call(context,"getMainWindow",null);
                } else {
                    ObjectReference modular=(ObjectReference)call(screen,"getScreen",null);
                    root=(ObjectReference)call(modular,"getMainPanel",null);
                }
                ArrayDeque<ObjectReference> pending=new ArrayDeque<>(); pending.add(root);
                while(!pending.isEmpty()) {
                    ObjectReference widget=pending.removeFirst();
                    String name=widget.referenceType().name();
                    if(name.equals("com.gtnewhorizons.modularui.common.widget.Scrollable") || name.equals("dev.gilbe.dynamicinventory.compat.mui1.InventoryViewport")) {
                        System.out.println("scroll="+call(widget,"getVerticalScrollOffset","()I")+" actual="+call(widget,"getActualHeight","()I")+" visible="+call(widget,"getVisibleHeight","()I"));
                    } else if(name.equals("dev.gilbe.dynamicinventory.compat.mui2.InventoryScrollWidget")) {
                        ObjectReference area=(ObjectReference)call(widget,"getScrollArea",null);
                        ObjectReference data=(ObjectReference)call(area,"getScrollY",null);
                        ObjectReference areaBounds=(ObjectReference)call(widget,"getArea",null);
                        System.out.println("scroll="+call(widget,"getScrollY","()I")+" actual="+call(data,"getScrollSize","()I")+" visible="+areaBounds.getValue(fieldInfo(areaBounds.referenceType(),"height")));
                    }
                    if(!methods(widget.referenceType(),"getChildren",null).isEmpty()) {
                        ObjectReference children=(ObjectReference)call(widget,"getChildren",null);
                        int count=((IntegerValue)call(children,"size","()I")).value();
                        for(int i=0;i<count;i++) pending.add((ObjectReference)call(children,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)));
                    }
                }
                break;
            }
            case "assert-transfer-probe": {
                // Read-only conservation check after native clicks/packets in a disposable world.
                act(new String[]{"require-qualification"});
                ObjectReference container = field(player, "openContainer"), inventory = field(player, "inventory");
                if (call(inventory, "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null)
                    throw new AssertionError("Transfer probe is still on cursor");
                ArrayReference slots = (ArrayReference) call(field(container, "inventorySlots"), "toArray", "()[Ljava/lang/Object;");
                int total = 0, stacks = 0;
                Set<String> expected = Set.of(args[3].split(","));
                for (int id = 0; id < slots.length(); id++) {
                    ObjectReference slot = (ObjectReference) slots.getValue(id);
                    ObjectReference stack = (ObjectReference) call(slot, "getStack", "()Lnet/minecraft/item/ItemStack;");
                    if (stack == null) continue;
                    ObjectReference tag = (ObjectReference) call(stack, "getTagCompound", "()Lnet/minecraft/nbt/NBTTagCompound;");
                    if (tag == null || !args[1].equals(((StringReference) call(tag, "getString", "(Ljava/lang/String;)Ljava/lang/String;", vm.mirrorOf("dinv_batch"))).value())) continue;
                    int count = ((IntegerValue) stack.getValue(fieldInfo(stack.referenceType(), "stackSize"))).value();
                    int raw = ((IntegerValue) call(slot, "getSlotIndex", "()I")).value();
                    String role = !inventory.equals(field(slot, "inventory")) ? "machine" : raw < 9 ? "hotbar" : raw > 36 ? "extra" : "storage";
                    if (!expected.contains(role)) throw new AssertionError("Unexpected probe destination: " + id + " " + role);
                    ObjectReference typed = (ObjectReference) call(tag, "getCompoundTag", "(Ljava/lang/String;)Lnet/minecraft/nbt/NBTTagCompound;", vm.mirrorOf("typed"));
                    if (((ByteValue) call(typed, "getByte", "(Ljava/lang/String;)B", vm.mirrorOf("byte"))).value() != 1
                        || ((LongValue) call(typed, "getLong", "(Ljava/lang/String;)J", vm.mirrorOf("long"))).value() != 17)
                        throw new AssertionError("Typed probe NBT changed");
                    total += count;
                    stacks++;
                    System.out.println("PROBE slot=" + id + " role=" + role + " count=" + count);
                }
                if (total != Integer.parseInt(args[2]) || stacks != (args.length > 4 ? Integer.parseInt(args[4]) : 1))
                    throw new AssertionError("Probe conservation failed: " + total + " items in " + stacks + " stacks");
                break;
            }
            case "registered-blocks": {
                // Read the real registry so smoke tests do not guess renamed/disabled pack blocks.
                ClassType blocks = type("net.minecraft.block.Block");
                ObjectReference registry = (ObjectReference) blocks.getValue(fieldInfo(blocks, "blockRegistry"));
                ObjectReference keys = (ObjectReference) call(registry, "getKeys", "()Ljava/util/Set;");
                ArrayReference entries = (ArrayReference) call(keys, "toArray", "()[Ljava/lang/Object;");
                for (Value entry : entries.getValues()) {
                    String name = ((StringReference) call((ObjectReference) entry, "toString", "()Ljava/lang/String;")).value();
                    for (int i = 1; i < args.length; i++) if (name.toLowerCase(Locale.ROOT).contains(args[i].toLowerCase(Locale.ROOT))) {
                        System.out.println(name);
                        break;
                    }
                }
                break;
            }
            case "paired-ui-state": {
                act(new String[]{"require-qualification"});
                act(new String[]{"gate-state", "ready"});
                act(new String[]{"ui-binding"});
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                ObjectReference manager = (ObjectReference) call(server, "getConfigurationManager", "()Lnet/minecraft/server/management/ServerConfigurationManager;");
                ObjectReference serverPlayer = (ObjectReference) call(manager, "func_152612_a", "(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;",
                    call(player, "getCommandSenderName", "()Ljava/lang/String;"));
                ObjectReference local = field(player, "openContainer"), remote = field(serverPlayer, "openContainer");
                if (!local.referenceType().name().equals(remote.referenceType().name())
                    || !local.getValue(fieldInfo(local.referenceType(), "windowId")).equals(remote.getValue(fieldInfo(remote.referenceType(), "windowId"))))
                    throw new AssertionError("Server/client host or window differs");
                int localCount = ((IntegerValue) call(field(local, "inventorySlots"), "size", "()I")).value();
                int remoteCount = ((IntegerValue) call(field(remote, "inventorySlots"), "size", "()I")).value();
                if (localCount != remoteCount) throw new AssertionError("Server/client slot count differs");
                if (call(field(player,"inventory"), "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null
                    || call(field(serverPlayer,"inventory"), "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null)
                    throw new AssertionError("Finish the cursor move before comparing peers");
                for (int i = 0; i < localCount; i++) {
                    String[] state = new String[2];
                    boolean occupied = false;
                    for (int side = 0; side < 2; side++) {
                        ObjectReference slot = (ObjectReference) call(side == 0 ? local : remote, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(i));
                        ObjectReference stack = (ObjectReference) call(slot, "getStack", "()Lnet/minecraft/item/ItemStack;");
                        occupied |= stack != null;
                        if (stack == null) {
                            state[side] = "null";
                            continue;
                        }
                        ObjectReference nbt = make("net.minecraft.nbt.NBTTagCompound", "()V");
                        call(stack, "writeToNBT", "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;", nbt);
                        state[side] = canonicalNbt(nbt);
                    }
                    if (!state[0].equals(state[1])) throw new AssertionError("Peer stack/NBT differs at slot " + i + ": " + Arrays.toString(state));
                    if (occupied) System.out.println("slot=" + i + " " + state[0]);
                }
                System.out.println("PASS paired host/window, all " + localCount + " slot stacks/NBT and empty cursors");
                break;
            }
            case "slot-info": {
                ObjectReference container = field(player, "openContainer");
                ObjectReference cursor = (ObjectReference) call(field(player,"inventory"), "getItemStack", "()Lnet/minecraft/item/ItemStack;");
                System.out.println("cursor=" + (cursor == null ? "null" : call(cursor,"toString","()Ljava/lang/String;")));
                for (int i = 1; i < args.length; i++) {
                    int id = Integer.parseInt(args[i]);
                    ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(id));
                    ObjectReference stack = (ObjectReference) call(slot, "getStack", "()Lnet/minecraft/item/ItemStack;");
                    System.out.println("enabled=" + call(slot,"func_111238_b","()Z") + " take=" + call(slot,"canTakeStack","(Lnet/minecraft/entity/player/EntityPlayer;)Z",player)
                        + " validCursor=" + (cursor == null ? "none" : call(slot,"isItemValid","(Lnet/minecraft/item/ItemStack;)Z",cursor)));
                    System.out.println("slot=" + id + " raw=" + call(slot, "getSlotIndex", "()I") + " x=" + slot.getValue(fieldInfo(slot.referenceType(), "xDisplayPosition"))
                        + " y=" + slot.getValue(fieldInfo(slot.referenceType(), "yDisplayPosition")) + " stack=" + (stack == null ? "null" : call(stack,"toString","()Ljava/lang/String;")));
                }
                break;
            }
            case "handshake-check": {
                if (player != null) throw new IllegalStateException("Handshake checks require the main menu");
                int target = Integer.parseInt(args[1]);
                ClassType loaderType = type("cpw.mods.fml.common.Loader");
                ObjectReference fml = (ObjectReference) loaderType.invokeMethod(thread, loaderType.methodsByName("instance").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference mods = (ObjectReference) call(fml, "getActiveModList", null);
                ObjectReference message = make("cpw.mods.fml.common.network.handshake.FMLHandshakeMessage$ModList", "(Ljava/util/List;)V", mods);
                ObjectReference map = (ObjectReference) call(message, "modList", null);
                String wire = ((StringReference) call(map, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", vm.mirrorOf("dynamicinventory"))).value();
                ClassType uiProtocol = type("dev.gilbe.dynamicinventory.api.InventoryUiProtocol");
                int protocolVersion = ((IntegerValue) uiProtocol.getValue(uiProtocol.fieldByName("WIRE_VERSION"))).value();
                if (!wire.matches("[^;]+;protocol=" + protocolVersion + ";storage=" + layoutInt("storageSlots") + ";adapters=[0-9a-f]{64}"))
                    throw new AssertionError("Outbound capacity and adapter protocol were not advertised: " + wire);
                String base = wire.substring(0, wire.indexOf(';'));
                String candidate = wire.replaceFirst(";storage=[0-9]+;", ";storage=" + target + ";");
                call(map, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", vm.mirrorOf("dynamicinventory"), vm.mirrorOf(candidate));
                ClassType network = type("cpw.mods.fml.common.network.internal.FMLNetworkHandler");
                ClassType side = type("cpw.mods.fml.relauncher.Side");
                Value server = side.getValue(side.fieldByName("SERVER"));
                int before = layoutInt("storageSlots");
                Value probe = network.invokeMethod(thread, network.methodsByName("checkModList", "(Ljava/util/Map;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;").getFirst(), List.of(map, server), ObjectReference.INVOKE_SINGLE_THREADED);
                if (probe != null || layoutInt("storageSlots") != before) throw new AssertionError("Server-list probe changed capacity or rejected supported capacity");
                Value accepted = network.invokeMethod(thread, network.methodsByName("checkModList", "(Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ModList;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;").getFirst(), List.of(message, server), ObjectReference.INVOKE_SINGLE_THREADED);
                if (accepted != null || layoutInt("storageSlots") != target) throw new AssertionError("Handshake failed to adopt capacity before player construction");
                String changedDigest = candidate.substring(0, candidate.length() - 1) + (candidate.endsWith("0") ? "1" : "0");
                call(map, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", vm.mirrorOf("dynamicinventory"), vm.mirrorOf(changedDigest));
                Value mismatch = network.invokeMethod(thread, network.methodsByName("checkModList", "(Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ModList;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;").getFirst(), List.of(message, server), ObjectReference.INVOKE_SINGLE_THREADED);
                if (mismatch == null || layoutInt("storageSlots") != target) throw new AssertionError("Mismatched adapters were accepted or changed capacity");
                call(map, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", vm.mirrorOf("dynamicinventory"), vm.mirrorOf(base));
                Value rejected = network.invokeMethod(thread, network.methodsByName("checkModList", "(Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ModList;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;").getFirst(), List.of(message, server), ObjectReference.INVOKE_SINGLE_THREADED);
                if (rejected == null || layoutInt("storageSlots") != target) throw new AssertionError("Missing capacity metadata was accepted or changed capacity");
                ClassType capacity = type("dev.gilbe.dynamicinventory.inventory.InventoryCapacity");
                capacity.invokeMethod(thread, capacity.methodsByName("useConfiguredCapacity").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("PASS outbound capacity and adapters, read-only server-list probe, pre-play adoption, mismatched adapters and missing metadata rejection, startup restoration");
                break;
            }
            case "machine-mode": {
                ClassType settings = type("dev.gilbe.dynamicinventory.client.InventoryViewSettings");
                System.out.println("expandMachineInventory=" + settings.getValue(settings.fieldByName("expandMachineInventory")));
                System.out.println("compactNativePanels=" + settings.getValue(settings.fieldByName("compactNativePanels")));
                System.out.println("mui2Presentation=" + settings.getValue(settings.fieldByName("mui2Presentation")));
                break;
            }
            case "presentation-check": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference presenter = screen;
                if (!screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))
                    presenter = (ObjectReference) call(screen, "dynamicinventory$panel", null);
                if (presenter == null) throw new AssertionError("No native inventory presentation owner");
                String status = ((StringReference) call(presenter, "presentationStatus", "()Ljava/lang/String;")).value();
                System.out.println(status);
                if (!status.contains("enabled=true") || !status.contains("canonicalContainer=true") || status.contains("drawCalls=0"))
                    throw new AssertionError("MUI2 presentation is not active on the canonical container");
                break;
            }
            case "capacity-info": {
                ClassType capacity = type("dev.gilbe.dynamicinventory.inventory.InventoryCapacity");
                Value configured = capacity.invokeMethod(thread, capacity.methodsByName("configuredStorageSlots").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("configuredStorage=" + configured + " storage=" + layoutInt("storageSlots") + " ordinary=" + layoutInt("mainSlots"));
                break;
            }
            case "foreign-check": {
                ObjectReference container = field(player, "openContainer");
                ObjectReference slots = field(container, "inventorySlots");
                ObjectReference inventory = field(player, "inventory");
                ArrayReference main = (ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), "mainInventory"));
                int count = ((IntegerValue) call(slots, "size", "()I")).value();
                int ordinary = ownedLayoutInt(inventory, "mainSlots");
                boolean offhand = main.length() == ordinary + 1;
                int[] ids = new int[ordinary]; Arrays.fill(ids, -1);
                for (int id = 0; id < count; id++) {
                    ObjectReference slot = (ObjectReference) call(slots, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(id));
                    int raw = ((IntegerValue) call(slot, "getSlotIndex", "()I")).value();
                    int logical = -1;
                    boolean mappedHandler = false;
                    if (!methods(slot.referenceType(), "getItemHandler", null).isEmpty()) {
                        ObjectReference handler = (ObjectReference) call(slot, "getItemHandler", null);
                        if (handler.referenceType().name().matches("dev\\.gilbe\\.dynamicinventory\\.compat\\.mui[12]\\.Mui[12]PlayerInventory")) {
                            mappedHandler = true;
                            boolean logicalHandler = handler.referenceType().name().contains(".mui1.");
                            logical = logicalHandler ? raw : raw - (offhand && raw > 36 ? 1 : 0);
                            if (!inventory.equals(call(handler, "getInventoryPlayer", "()Lnet/minecraft/entity/player/InventoryPlayer;")))
                                throw new AssertionError("Foreign GUI has a copied player inventory");
                            if (logicalHandler) raw += offhand && raw >= 36 ? 1 : 0;
                        }
                    }
                    if (!mappedHandler && inventory.equals(field(slot, "inventory"))) {
                        if (raw >= 0 && raw < main.length() && !(offhand && raw == 36))
                            logical = raw - (offhand && raw > 36 ? 1 : 0);
                    }
                    if (logical < 0 || logical >= ordinary) continue;
                    if (ids[logical] >= 0) throw new AssertionError("Duplicate ordinary slot " + logical);
                    ids[logical] = id;
                    Value expected = main.getValue(raw);
                    Value actual = call(slot, "getStack", "()Lnet/minecraft/item/ItemStack;");
                    if (!Objects.equals(expected, actual)) throw new AssertionError("Slot not backed by canonical inventory: " + logical);
                }
                for (int i = 0; i < ordinary; i++) if (ids[i] < 0) throw new AssertionError("Missing ordinary slot " + i);
                System.out.println("PASS foreign=" + container.referenceType().name() + " count=" + count + " ordinaryIds=" + Arrays.toString(ids));
                ObjectReference screen = field(client, "currentScreen");
                System.out.println("GUI=" + screen.referenceType().name());
                break;
            }
            case "mui-check": {
                ObjectReference container = field(player, "openContainer");
                ObjectReference slots = field(container, "inventorySlots");
                int count = ((IntegerValue) call(slots, "size", "()I")).value();
                if (count != ownedLayoutInt(field(player, "inventory"), "mainSlots")) throw new AssertionError("Unexpected MUI2 slot count " + count);
                for (int id = 0; id < count; id++) {
                    ObjectReference slot = (ObjectReference) call(slots, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(id));
                    int main = ((IntegerValue) call(slot, "getSlotIndex", "()I")).value();
                    ClassType layout = type("dev.gilbe.dynamicinventory.inventory.InventoryLayout");
                    int expected = ((IntegerValue) layout.invokeMethod(thread, layout.methodsByName("rawIndex").getFirst(), List.of(vm.mirrorOf(id)), ObjectReference.INVOKE_SINGLE_THREADED)).value();
                    if (main != expected) throw new AssertionError("MUI2 slot identity mismatch " + id + " -> " + main);
                    ObjectReference handler = (ObjectReference) call(slot, "getItemHandler", "()Lcom/cleanroommc/modularui/utils/item/IItemHandler;");
                    ObjectReference inventory = (ObjectReference) call(handler, "getInventoryPlayer", "()Lnet/minecraft/entity/player/InventoryPlayer;");
                    if (!inventory.equals(field(player, "inventory"))) throw new AssertionError("MUI2 inventory is not canonical");
                }
                System.out.println("PASS " + count + " MUI2 slots bind canonical player inventory in stable index order");
                break;
            }
            case "background": {
                ObjectReference settings = field(client, "gameSettings");
                settings.setValue(fieldInfo(settings.referenceType(), "pauseOnLostFocus"), vm.mirrorOf(false));
                break;
            }
            case "fixture-peaceful": {
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                if (server == null || !((StringReference) call(server, "getFolderName", "()Ljava/lang/String;")).value().startsWith("DI-Baseline-"))
                    throw new IllegalStateException("Disposable integrated world required");
                ClassType difficulty = type("net.minecraft.world.EnumDifficulty");
                ObjectReference settings = field(client, "gameSettings");
                settings.setValue(fieldInfo(settings.referenceType(), "difficulty"), difficulty.getValue(fieldInfo(difficulty, "PEACEFUL")));
                break;
            }
            case "native-layout-set":
            case "native-layout-clear": {
                ObjectReference screen = field(client, "currentScreen");
                ClassType patches = type("dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches");
                ObjectReference patch = null;
                if (args[0].equals("native-layout-set")) {
                    patch = (ObjectReference) patches.invokeMethod(thread, patches.methodsByName("defaults").getFirst(),
                        List.of(screen), ObjectReference.INVOKE_SINGLE_THREADED);
                    // Use a transient production editor draft, never overwrite a user's saved family layout.
                    for (int field = 0; field < 2; field++) {
                        int before = ((IntegerValue) call(patch, "value", "(I)I", vm.mirrorOf(field))).value();
                        patch = (ObjectReference) call(patch, "adjust", "(II)Ldev/gilbe/dynamicinventory/client/editor/NativeLayoutPatch;",
                            vm.mirrorOf(field), vm.mirrorOf(Integer.parseInt(args[field + 1]) - before));
                    }
                }
                patches.invokeMethod(thread, patches.methodsByName("draft").getFirst(), Arrays.asList(screen, patch),
                    ObjectReference.INVOKE_SINGLE_THREADED);
                int width = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "width"))).value();
                int height = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "height"))).value();
                call(screen, "setWorldAndResolution", "(Lnet/minecraft/client/Minecraft;II)V", client,
                    vm.mirrorOf(width), vm.mirrorOf(height));
                break;
            }
            case "layout-set": {
                ClassType settings = type("dev.gilbe.dynamicinventory.client.InventoryViewSettings");
                settings.invokeMethod(thread, settings.methodsByName("set", "(II)V").getFirst(),
                    List.of(vm.mirrorOf(Integer.parseInt(args[1])), vm.mirrorOf(Integer.parseInt(args[2]))), ObjectReference.INVOKE_SINGLE_THREADED);
                // Minecraft's resize path also dispatches Forge InitGui events. Calling
                // initGui directly silently drops integrations installed by those events.
                int width = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayWidth"))).value();
                int height = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayHeight"))).value();
                call(client, "resize", "(II)V", vm.mirrorOf(width), vm.mirrorOf(height));
                break;
            }
            case "connect": {
                if (player != null) throw new IllegalStateException("Disconnect first");
                ClassType fml = type("cpw.mods.fml.client.FMLClientHandler");
                ObjectReference handler = (ObjectReference) fml.invokeMethod(thread, fml.methodsByName("instance").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                call(handler, "setupServerList", "()V");
                ObjectReference data = make("net.minecraft.client.multiplayer.ServerData", "(Ljava/lang/String;Ljava/lang/String;)V",
                    vm.mirrorOf("Local qualification"), vm.mirrorOf("127.0.0.1:" + Integer.parseInt(args[1])));
                call(handler, "connectToServer", "(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/multiplayer/ServerData;)V",
                    make("net.minecraft.client.gui.GuiMainMenu", "()V"), data);
                break;
            }
            case "tick-begin": {
                ClassType measurements = type("dev.gilbe.dynamicinventory.qualification.TickMeasurements");
                measurements.invokeMethod(thread, measurements.methodsByName("begin").getFirst(), List.of(player, vm.mirrorOf(args[1])), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "tick-end": {
                ClassType measurements = type("dev.gilbe.dynamicinventory.qualification.TickMeasurements");
                Value report = measurements.invokeMethod(thread, measurements.methodsByName("finish").getFirst(), List.of(player), ObjectReference.INVOKE_SINGLE_THREADED);
                ClassType command = type("dev.gilbe.dynamicinventory.qualification.QualificationCommand");
                command.invokeMethod(thread, command.methodsByName("write").getFirst(), List.of(player, vm.mirrorOf("client-ticks-" + args[1]), report), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "require-dedicated": {
                if (player == null || call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;") != null)
                    throw new IllegalStateException("Requires a connected dedicated-server client");
                ObjectReference data = (ObjectReference) call(client, "func_147104_D", "()Lnet/minecraft/client/multiplayer/ServerData;");
                String address = ((StringReference) data.getValue(fieldInfo(data.referenceType(), "serverIP"))).value();
                if (!address.equals("127.0.0.1:25566")) throw new IllegalStateException("Local disposable server required");
                break;
            }
            case "require-qualification":
            case "require-step3": {
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                if (server == null) throw new IllegalStateException("Requires the isolated integrated test server");
                String folder = ((StringReference) call(server, "getFolderName", "()Ljava/lang/String;")).value();
                if (!folder.startsWith("DI-Baseline-") || !(args[0].equals("require-step3") ? folder.contains("Step3") : folder.endsWith("Qualification"))) throw new IllegalStateException("Requires the requested disposable fixture");
                if (args.length > 1 && !folder.equals(args[1])) throw new IllegalStateException("Fixture world differs from the requested reload target");
                break;
            }
            case "world": {
                if (player != null) throw new IllegalStateException("Refusing to replace an open world");
                if (!args[1].startsWith("DI-Baseline-")) throw new IllegalArgumentException("Disposable test world name required");
                ClassType gameType = type("net.minecraft.world.WorldSettings$GameType");
                ClassType worldType = type("net.minecraft.world.WorldType");
                Value survival = gameType.getValue(fieldInfo(gameType, "SURVIVAL"));
                Value flat = worldType.getValue(fieldInfo(worldType, "FLAT"));
                ObjectReference settings = make("net.minecraft.world.WorldSettings",
                    "(JLnet/minecraft/world/WorldSettings$GameType;ZZLnet/minecraft/world/WorldType;)V",
                    vm.mirrorOf(17017L), survival, vm.mirrorOf(false), vm.mirrorOf(false), flat);
                call(settings, "enableCommands", "()Lnet/minecraft/world/WorldSettings;");
                call(client, "launchIntegratedServer", "(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
                    vm.mirrorOf(args[1]), vm.mirrorOf(args[1]), settings);
                break;
            }
            case "register-storage-tests":
            case "register-container-tests": {
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                ObjectReference manager = (ObjectReference) call(server, "getCommandManager", "()Lnet/minecraft/command/ICommandManager;");
                call(manager, "registerCommand", "(Lnet/minecraft/command/ICommand;)Lnet/minecraft/command/ICommand;",
                    make(args[0].equals("register-storage-tests") ? "dev.gilbe.dynamicinventory.StorageTestCommand" : "dev.gilbe.dynamicinventory.ContainerTestCommand", "()V"));
                break;
            }
            case "command":
                if (player == null) throw new IllegalStateException("No player");
                call(player, "sendChatMessage", "(Ljava/lang/String;)V", vm.mirrorOf(args[1]));
                break;
            case "native-recipe-check": {
                ObjectReference gui = field(client, "currentScreen");
                boolean furnace = gui.referenceType().name().equals("net.minecraft.client.gui.inventory.GuiFurnace");
                String ident = furnace ? "smelting" : "crafting";
                ObjectReference container = field(player, "openContainer");
                ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(furnace ? 0 : 1));
                int x = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "xDisplayPosition"))).value();
                int y = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "yDisplayPosition"))).value();
                ClassType recipe = type("codechicken.nei.recipe.RecipeInfo");
                List<Value> key = List.of(gui, vm.mirrorOf(ident));
                ObjectReference handler = (ObjectReference) recipe.invokeMethod(thread, recipe.methodsByName("getOverlayHandler").getFirst(), key, ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference positioner = (ObjectReference) recipe.invokeMethod(thread, recipe.methodsByName("getStackPositioner").getFirst(), key, ObjectReference.INVOKE_SINGLE_THREADED);
                if ((!furnace && handler == null) || positioner == null) throw new IllegalStateException("Missing native registered recipe integration: " + ident);
                ClassType items = type("net.minecraft.init.Items");
                ObjectReference stack = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", items.getValue(fieldInfo(items, "diamond")), vm.mirrorOf(1));
                ObjectReference ingredient = make("codechicken.nei.PositionedStack", "(Ljava/lang/Object;II)V", stack, vm.mirrorOf(furnace ? 51 : 25), vm.mirrorOf(6));
                ObjectReference ingredients = make("java.util.ArrayList", "()V");
                call(ingredients, "add", "(Ljava/lang/Object;)Z", ingredient);
                ArrayReference mapped = handler == null ? null : (ArrayReference) call(handler, "mapIngredSlots", null, gui, ingredients);
                boolean found = furnace;
                if (mapped != null) for (Value row : mapped.getValues()) for (Value candidate : ((ArrayReference) row).getValues()) if (slot.equals(candidate)) found = true;
                if (!found) throw new IllegalStateException("Native recipe handler missed relocated input slot");
                ObjectReference positioned = (ObjectReference) call(positioner, "positionStacks", null, ingredients);
                ObjectReference ghost = (ObjectReference) call(positioned, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(0));
                int gx = ((IntegerValue) ghost.getValue(fieldInfo(ghost.referenceType(), "relx"))).value();
                int gy = ((IntegerValue) ghost.getValue(fieldInfo(ghost.referenceType(), "rely"))).value();
                if (gx != x || gy != y) throw new IllegalStateException("Recipe ghost " + gx + "," + gy + " differs from input " + x + "," + y);
                System.out.println("PASS registered native recipe input and ghost at " + x + "," + y);
                break;
            }
            case "pick-block": {
                // Invoke the real Minecraft/Forge path, including its outgoing creative
                // packet. Tests inspect server state afterward; local prediction alone
                // would also pass with the old, rejected packet ID.
                if (player == null) throw new IllegalStateException("No player");
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                if (server == null) throw new IllegalStateException("Disposable integrated qualification world required");
                String folder = ((StringReference) call(server, "getFolderName", "()Ljava/lang/String;")).value();
                if (!folder.startsWith("DI-Baseline-") || !folder.endsWith("Qualification"))
                    throw new IllegalStateException("Disposable qualification world required");
                ObjectReference capabilities = field(player, "capabilities");
                if (!((BooleanValue) capabilities.getValue(fieldInfo(capabilities.referenceType(), "isCreativeMode"))).value())
                    throw new IllegalStateException("Creative mode required");
                int selected = Integer.parseInt(args[4]);
                if (selected < 0 || selected >= 9) throw new IllegalArgumentException("Hotbar index must be 0..8");
                ObjectReference inventory = field(player, "inventory");
                inventory.setValue(fieldInfo(inventory.referenceType(), "currentItem"), vm.mirrorOf(selected));
                int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
                ObjectReference vec = (ObjectReference) type("net.minecraft.util.Vec3").invokeMethod(thread,
                    methods(type("net.minecraft.util.Vec3"), "createVectorHelper", "(DDD)Lnet/minecraft/util/Vec3;").getFirst(),
                    List.of(vm.mirrorOf(x + 0.5), vm.mirrorOf(y + 0.5), vm.mirrorOf(z + 0.5)), ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference target = make("net.minecraft.util.MovingObjectPosition", "(IIIILnet/minecraft/util/Vec3;)V",
                    vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(z), vm.mirrorOf(1), vec);
                Field mouseOver = fieldInfo(client.referenceType(), "objectMouseOver");
                Value previous = client.getValue(mouseOver);
                try {
                    client.setValue(mouseOver, target);
                    call(client, "func_147112_ai", "()V");
                } finally {
                    client.setValue(mouseOver, previous);
                }
                System.out.println("Invoked creative pick-block at " + x + "," + y + "," + z);
                break;
            }
            case "entity-open": {
                act(new String[]{"require-qualification"});
                ObjectReference entities = field(field(client, "theWorld"), "loadedEntityList");
                ArrayReference entries = (ArrayReference) call(entities, "toArray", "()[Ljava/lang/Object;");
                ObjectReference selected = null;
                for (Value entry : entries.getValues()) {
                    ObjectReference entity = (ObjectReference) entry;
                    StringReference name = (StringReference) call(entity, "getCommandSenderName", "()Ljava/lang/String;");
                    if ((name != null && name.value().equals(args[1])) || entity.referenceType().name().equals(args[1])) selected = entity;
                }
                if (selected == null) throw new AssertionError("Named fixture entity is not loaded: " + args[1]);
                // Send the same interaction packet as vanilla, leaving entity GUI selection to the server.
                ClassType action = type("net.minecraft.network.play.client.C02PacketUseEntity$Action");
                ObjectReference packet = make("net.minecraft.network.play.client.C02PacketUseEntity",
                    "(Lnet/minecraft/entity/Entity;Lnet/minecraft/network/play/client/C02PacketUseEntity$Action;)V",
                    selected, action.getValue(fieldInfo(action, "INTERACT")));
                call(field(player, "sendQueue"), "addToSendQueue", "(Lnet/minecraft/network/Packet;)V", packet);
                break;
            }
            case "use-held": {
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                if (server == null) throw new IllegalStateException("Disposable integrated fixture required");
                String folder = ((StringReference) call(server, "getFolderName", "()Ljava/lang/String;")).value();
                if (!folder.startsWith("DI-Baseline-") || !folder.endsWith("Qualification")) throw new IllegalStateException("Disposable qualification world required");
                Value held = call(player, "getHeldItem", "()Lnet/minecraft/item/ItemStack;");
                if (held == null) throw new IllegalStateException("No held fixture item");
                call(field(client, "playerController"), "sendUseItem",
                    "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z",
                    player, field(client, "theWorld"), held);
                break;
            }
            case "block-open": {
                if (player == null) throw new IllegalStateException("No player");
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                String folder = ((StringReference) call(server, "getFolderName", "()Ljava/lang/String;")).value();
                if (!folder.startsWith("DI-Baseline-") || !folder.endsWith("Qualification")) throw new IllegalStateException("Disposable qualification world required");
                int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
                ObjectReference vec = (ObjectReference) type("net.minecraft.util.Vec3").invokeMethod(thread,
                    methods(type("net.minecraft.util.Vec3"), "createVectorHelper", "(DDD)Lnet/minecraft/util/Vec3;").getFirst(),
                    List.of(vm.mirrorOf(x + 0.5), vm.mirrorOf(y + 0.5), vm.mirrorOf(z + 0.5)), ObjectReference.INVOKE_SINGLE_THREADED);
                Value held = call(player, "getHeldItem", "()Lnet/minecraft/item/ItemStack;");
                call(field(client, "playerController"), "onPlayerRightClick",
                    "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIILnet/minecraft/util/Vec3;)Z",
                    player, field(client, "theWorld"), held, vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(z), vm.mirrorOf(1), vec);
                break;
            }
            case "click": {
                ObjectReference controller = field(client, "playerController");
                ObjectReference container = field(player, "openContainer");
                int window = ((IntegerValue) container.getValue(fieldInfo(container.referenceType(), "windowId"))).value();
                call(controller, "windowClick", "(IIIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
                    vm.mirrorOf(window), vm.mirrorOf(Integer.parseInt(args[1])), vm.mirrorOf(Integer.parseInt(args[2])),
                    vm.mirrorOf(Integer.parseInt(args[3])), player);
                break;
            }
            case "capture": {
                if (player == null) throw new IllegalStateException("No player");
                ObjectReference current = field(client, "currentScreen");
                if (current == null || !(current.referenceType().name().equals("net.minecraft.client.gui.inventory.GuiInventory")
                    || current.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))) {
                    ObjectReference gui = make("net.minecraft.client.gui.inventory.GuiInventory", "(Lnet/minecraft/entity/player/EntityPlayer;)V", player);
                    call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", gui);
                }
                ClassType diagnostics = type("dev.gilbe.dynamicinventory.client.BaselineClientDiagnostics");
                diagnostics.invokeMethod(thread, diagnostics.methodsByName("capture").getFirst(),
                    List.of(client, vm.mirrorOf(args.length > 1 ? args[1] : "harness"), vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "snapshot": {
                ClassType diagnostics = type("dev.gilbe.dynamicinventory.client.BaselineClientDiagnostics");
                // A newly mirrored string has no game-side owner yet. Keep it alive until the
                // remote call accepts it; GC can otherwise interrupt a read-only snapshot.
                StringReference reason = vm.mirrorOf(args.length > 1 ? args[1] : "harness");
                reason.disableCollection();
                try {
                    diagnostics.invokeMethod(thread, diagnostics.methodsByName("capture").getFirst(),
                        List.of(client, reason, vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
                } finally {
                    reason.enableCollection();
                }
                break;
            }
            case "resize-state": {
                // Read the actual actor's shape, never the process-wide startup default.
                ObjectReference inventory = field(player, "inventory");
                ClassType layout = type("dev.gilbe.dynamicinventory.inventory.InventoryLayout");
                ObjectReference shape = (ObjectReference) layout.invokeMethod(thread,
                    layout.methodsByName("of", "(Lnet/minecraft/entity/player/InventoryPlayer;)Ldev/gilbe/dynamicinventory/inventory/InventoryShape;").getFirst(),
                    List.of(inventory), ObjectReference.INVOKE_SINGLE_THREADED);
                int storage = ((IntegerValue) call(shape, "storageSlots", "()I")).value();
                int raw = ((IntegerValue) call(shape, "rawMainSlots", "()I")).value();
                if (storage != Integer.parseInt(args[1]) || ((ArrayReference) field(inventory, "mainInventory")).length() != raw)
                    throw new AssertionError("Unexpected owned capacity: " + storage + "/" + raw);
                ObjectReference handler = field(player, "sendQueue");
                ObjectReference gate = (ObjectReference) call(handler, "dynamicinventory$resizeGate", "()Ldev/gilbe/dynamicinventory/client/ClientInventoryResizeGate;");
                if (!((BooleanValue) call(gate, "allowsInput", "(Lnet/minecraft/entity/player/EntityPlayer;)Z", player)).value())
                    throw new AssertionError("Capacity synchronization has not released input");
                ClassType protocol = type("dev.gilbe.dynamicinventory.network.InventoryResizeProtocol");
                protocol.invokeMethod(thread, protocol.methodsByName("describe").getFirst(), List.of(player), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("PASS live capacity " + storage + ", raw " + raw + ", revision " + call(shape, "revision", "()J"));
                break;
            }
            case "reload-test-settings": {
                // After restoring backed-up files, refresh their in-memory copies too.
                // Otherwise a later ordinary game shutdown would overwrite the restored files.
                if (player != null) act(new String[]{"resume"});
                call(field(client, "gameSettings"), "loadOptions", "()V");
                ObjectReference settingsFile = make("java.io.File", "(Ljava/io/File;Ljava/lang/String;)V",
                    field(client, "mcDataDir"), vm.mirrorOf("config/dynamicinventory-client.cfg"));
                ClassType settings = type("dev.gilbe.dynamicinventory.client.InventoryViewSettings");
                settings.invokeMethod(thread, settings.methodsByName("initialize").getFirst(),
                    List.of(settingsFile), ObjectReference.INVOKE_SINGLE_THREADED);
                int width = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayWidth"))).value();
                int height = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayHeight"))).value();
                call(client, "resize", "(II)V", vm.mirrorOf(width), vm.mirrorOf(height));
                break;
            }
            case "resume":
                call(player, "closeScreen", "()V");
                break;
            case "scale": {
                int scale = Integer.parseInt(args[1]);
                ObjectReference settings = field(client, "gameSettings");
                settings.setValue(fieldInfo(settings.referenceType(), "guiScale"), vm.mirrorOf(scale));
                int width = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayWidth"))).value();
                int height = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayHeight"))).value();
                call(client, "resize", "(II)V", vm.mirrorOf(width), vm.mirrorOf(height));
                ObjectReference screen = field(client, "currentScreen");
                int left = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value();
                int top = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value();
                int guiWidth = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "xSize"))).value();
                int guiHeight = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "ySize"))).value();
                int screenWidth = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "width"))).value();
                int screenHeight = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "height"))).value();
                if (left < 0 || top < 0 || left + guiWidth > screenWidth || top + guiHeight > screenHeight)
                    throw new AssertionError("Menu exceeds scaled window");
                System.out.println("PASS geometry scale=" + scale + " framebuffer=" + width + "x" + height + " scaled=" + screenWidth + "x" + screenHeight + " menu=" + guiWidth + "x" + guiHeight + " origin=" + left + "," + top);
                break;
            }
            case "clear-chat": {
                ObjectReference ingame = field(client, "ingameGUI");
                ObjectReference chat = (ObjectReference) call(ingame, "getChatGUI", "()Lnet/minecraft/client/gui/GuiNewChat;");
                call(chat, "clearChatMessages", "()V");
                break;
            }
            case "layout-math": {
                ClassType tests = type("dev.gilbe.dynamicinventory.LayoutMathTests");
                tests.invokeMethod(thread, tests.methodsByName("run", "()V").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "attached-layout-math": {
                ClassType tests = type("dev.gilbe.dynamicinventory.qualification.AttachedChestLayoutFixtures");
                Value cases = tests.invokeMethod(thread, tests.methodsByName("run", "()I").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("attachedChestGeometryCases=" + ((IntegerValue) cases).value());
                break;
            }
            case "tab-click":
            case "layout-control": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference buttons = field(screen, "buttonList");
                int wanted = args[0].equals("layout-control") ? Integer.parseInt(args[1]) : -1;
                int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                boolean found = false;
                for (int i = 0; i < count; i++) {
                    ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    if (args[0].equals("tab-click") ? !button.referenceType().name().equals(args[1])
                        : ((IntegerValue) button.getValue(fieldInfo(button.referenceType(), "id"))).value() != wanted) continue;
                    int x = ((IntegerValue) button.getValue(fieldInfo(button.referenceType(), "xPosition"))).value() + 5;
                    int y = ((IntegerValue) button.getValue(fieldInfo(button.referenceType(), "yPosition"))).value() + 5;
                    call(screen, "mouseClicked", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                    call(screen, "mouseMovedOrUp", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                    found = true;
                    break;
                }
                if (!found) throw new IllegalStateException("Missing layout button " + wanted);
                break;
            }
            case "tabs-check": {
                ObjectReference screen = field(client, "currentScreen");
                if (!screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))
                    throw new AssertionError("Expected expanded player screen");
                if (!field(player, "openContainer").equals(field(player, "inventoryContainer")))
                    throw new AssertionError("Returning from tabs left the wrong client container active");
                ObjectReference buttons = field(screen, "buttonList");
                int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                int top = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value();
                int tabSpace = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "tabSpace"))).value();
                int left = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value();
                int screenWidth = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "width"))).value();
                ClassType nativeTab = type("tconstruct.client.tabs.AbstractTab");
                ClassType registry = type("tconstruct.client.tabs.TabRegistry");
                ObjectReference registered = (ObjectReference) registry.invokeMethod(thread,
                    registry.methodsByName("getTabList").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                Set<ObjectReference> expectedNative = new HashSet<>(), actualNative = new HashSet<>();
                int registeredCount = ((IntegerValue) call(registered,"size","()I")).value();
                for (int i = 0; i < registeredCount; i++) {
                    ObjectReference tab = (ObjectReference) call(registered,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    if (((BooleanValue) call(tab,"shouldAddToList","()Z")).value()) expectedNative.add(tab);
                }
                int tabs = 0, selected = 0;
                for (int i = 0; i < count; i++) {
                    ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    ReferenceType clazz = button.referenceType();
                    boolean tab = clazz.name().startsWith("de.eydamos.backpack.gui.tab.");
                    for (ClassType parent = (ClassType) clazz; parent != null; parent = parent.superclass()) {
                        if (parent.equals(nativeTab)) { tab = true; actualNative.add(button); }
                    }
                    int id = ((IntegerValue) button.getValue(fieldInfo(clazz, "id"))).value();
                    int x = ((IntegerValue) button.getValue(fieldInfo(clazz, "xPosition"))).value();
                    int y = ((IntegerValue) button.getValue(fieldInfo(clazz, "yPosition"))).value();
                    if (id == 100 || id == 109 || id == 394658246 || id == 394658247) {
                        ObjectReference slots = field(field(player,"inventoryContainer"),"inventorySlots");
                        int slotCount = ((IntegerValue) call(slots,"size","()I")).value();
                        int buttonWidth = ((IntegerValue) button.getValue(fieldInfo(clazz,"width"))).value();
                        int buttonHeight = ((IntegerValue) button.getValue(fieldInfo(clazz,"height"))).value();
                        for (int s = 0; s < slotCount; s++) {
                            ObjectReference slot = (ObjectReference) call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(s));
                            int slotX = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(),"xDisplayPosition"))).value();
                            int slotY = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(),"yDisplayPosition"))).value();
                            if (slotX < 0) continue;
                            if (x < left + slotX + 17 && x + buttonWidth > left + slotX - 1
                                && y < top + slotY + 17 && y + buttonHeight > top + slotY - 1)
                                throw new AssertionError("Header control " + id + " overlaps slot " + s);
                        }
                    }
                    if (tab) {
                        if (x < 0 || y < 0 || x + 28 > screenWidth || y != top + tabSpace - 28)
                            throw new AssertionError("Native tab clipped or detached: " + clazz.name());
                        boolean enabled = ((BooleanValue) button.getValue(fieldInfo(clazz, "enabled"))).value();
                        if (!enabled) selected++;
                        if (clazz.name().equals("tconstruct.client.tabs.InventoryTabVanilla") && enabled)
                            throw new AssertionError("Player inventory tab must be selected");
                        System.out.println("TAB " + clazz.name() + " id=" + id + " enabled=" + enabled);
                        tabs++;
                    }
                    if (id == 394658246 || id == 394658247) {
                        int expectedX = left + ((IntegerValue) call(screen,"sortButtonX","()I")).value() + (id == 394658247 ? 10 : 0);
                        if (x != expectedX || y != top + ((IntegerValue) call(screen,"sortButtonY","()I")).value())
                            throw new AssertionError("Sort/config button detached from current header: " + x + "," + y);
                    }
                }
                if (!actualNative.equals(expectedNative) || tabs < 2 || selected != 1)
                    throw new AssertionError("Missing registered native tabs or incorrect selection: " + tabs);
                System.out.println("PASS native tabs=" + tabs + " active persistent player window and anchored sort controls");
                break;
            }
            case "page": {
                ObjectReference screen = field(client, "currentScreen");
                call(screen, "keyTyped", "(CI)V", vm.mirrorOf('\0'), vm.mirrorOf(args[1].equals("up") ? 201 : 209));
                break;
            }
            case "visible-click": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference container = field(player, "openContainer");
                ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(Integer.parseInt(args[1])));
                int x = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "xDisplayPosition"))).value();
                int y = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "yDisplayPosition"))).value();
                if (x < 0 || y < 0) throw new AssertionError("Cannot click a hidden slot");
                x += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value() + 7;
                y += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value() + 7;
                call(screen, "mouseClicked", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                call(screen, "mouseMovedOrUp", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                break;
            }
            case "scrollbar-bottom": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference view = field(screen, "view");
                int x = ((IntegerValue) view.getValue(fieldInfo(view.referenceType(), "gridX"))).value()
                    + ((IntegerValue) view.getValue(fieldInfo(view.referenceType(), "columns"))).value() * 18 + 5
                    + ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value();
                int y = ((IntegerValue) view.getValue(fieldInfo(view.referenceType(), "visibleRows"))).value() * 18 + 82
                    + ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "tabSpace"))).value()
                    + ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value();
                call(screen, "mouseClicked", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                call(screen, "mouseMovedOrUp", "(III)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0));
                break;
            }
            case "window": {
                int width = Integer.parseInt(args[1]);
                int height = Integer.parseInt(args[2]);
                if (width < 640 || width > 1920 || height < 480 || height > 1080) throw new IllegalArgumentException("Test window bounds");
                ObjectReference mode = make("org.lwjglx.opengl.DisplayMode", "(II)V", vm.mirrorOf(width), vm.mirrorOf(height));
                ClassType display = type("org.lwjglx.opengl.Display");
                display.invokeMethod(thread, display.methodsByName("setDisplayMode", "(Lorg/lwjglx/opengl/DisplayMode;)V").getFirst(), List.of(mode), ObjectReference.INVOKE_SINGLE_THREADED);
                call(client, "resize", "(II)V", vm.mirrorOf(width), vm.mirrorOf(height));
                break;
            }
            case "layout-check": {
                ObjectReference screen = field(client, "currentScreen");
                ObjectReference view = field(screen, "view");
                int screenWidth = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "width"))).value();
                int screenHeight = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "height"))).value();
                int left = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value();
                int top = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value();
                int columns = ((IntegerValue) view.getValue(fieldInfo(view.referenceType(), "columns"))).value();
                int rows = ((IntegerValue) view.getValue(fieldInfo(view.referenceType(), "visibleRows"))).value();
                if (args.length > 2 && (columns != Integer.parseInt(args[1]) || rows != Integer.parseInt(args[2]))) {
                    throw new AssertionError("Unexpected persisted layout: " + columns + "x" + rows);
                }
                int offset = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "scrollRow"))).value();
                int visible = 0;
                int ordinary = ownedLayoutInt(field(player, "inventory"), "mainSlots");
                ArrayReference main = (ArrayReference) field(player, "inventory").getValue(fieldInfo(field(player, "inventory").referenceType(), "mainInventory"));
                boolean backhand = main.length() == ordinary + 1;
                int ordinaryEnd = ordinary + (backhand ? 10 : 9);
                ObjectReference container = field(player, "inventoryContainer");
                ObjectReference slots = field(container, "inventorySlots");
                int count = ((IntegerValue) call(slots, "size", "()I")).value();
                for (int id = 0; id < count; id++) {
                    ObjectReference slot = (ObjectReference) call(slots, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(id));
                    int x = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "xDisplayPosition"))).value();
                    int y = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "yDisplayPosition"))).value();
                    if (x < 0) { if (id >= 36 && id < 45) throw new AssertionError("Hotbar hidden"); continue; }
                    if (left + x < 0 || top + y < 0 || left + x + 16 > screenWidth || top + y + 16 > screenHeight)
                        throw new AssertionError("Slot outside scaled window: " + id);
                    if (id >= 9 && id < ordinaryEnd && !(backhand && id == 45)) visible++;
                }
                if (visible < 9 || visible > ordinary) throw new AssertionError("Invalid visible slot count");
                System.out.println("PASS layout columns=" + columns + " visibleRows=" + rows + " scrollRow=" + offset + " visibleMain=" + visible + " scaled=" + screenWidth + "x" + screenHeight);
                break;
            }
            case "screenshot-hover":
            case "screenshot": {
                String filename = args[args.length - 1];
                if (!filename.matches("step3-[a-z0-9-]+\\.png")) throw new IllegalArgumentException("Step 3 screenshot filename required");
                ClassType helper = type("net.minecraft.util.ScreenShotHelper");
                int width = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayWidth"))).value();
                int height = ((IntegerValue) client.getValue(fieldInfo(client.referenceType(), "displayHeight"))).value();
                ObjectReference framebuffer = (ObjectReference) call(client, "getFramebuffer", "()Lnet/minecraft/client/shader/Framebuffer;");
                ClassType mouse = null;
                Value previousMouseX = null, previousMouseY = null;
                if (args[0].equals("screenshot-hover")) {
                    ObjectReference screen = field(client, "currentScreen");
                    ObjectReference container = field(player, "openContainer");
                    ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(Integer.parseInt(args[1])));
                    int x = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "xDisplayPosition"))).value();
                    int y = ((IntegerValue) slot.getValue(fieldInfo(slot.referenceType(), "yDisplayPosition"))).value();
                    if (x < 0 || y < 0) throw new AssertionError("Cannot hover a hidden slot");
                    x += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiLeft"))).value() + 7;
                    y += ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "guiTop"))).value() + 7;
                    // CCL/NEI's deferred tooltips read LWJGL's cached pointer instead
                    // of the draw-event coordinates. Supply the same pointer for this
                    // frame, then restore it without moving the operating-system cursor.
                    mouse = type("org.lwjglx.input.Mouse");
                    previousMouseX = mouse.getValue(mouse.fieldByName("x"));
                    previousMouseY = mouse.getValue(mouse.fieldByName("y"));
                    int scaledWidth = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "width"))).value();
                    int scaledHeight = ((IntegerValue) screen.getValue(fieldInfo(screen.referenceType(), "height"))).value();
                    mouse.setValue(mouse.fieldByName("x"), vm.mirrorOf(x * width / scaledWidth));
                    mouse.setValue(mouse.fieldByName("y"), vm.mirrorOf((scaledHeight - y - 1) * height / scaledHeight));
                    System.out.println("Rendered hover at scaled " + x + "," + y);
                    call(framebuffer, "bindFramebuffer", "(Z)V", vm.mirrorOf(true));
                    call(field(client, "entityRenderer"), "setupOverlayRendering", "()V");
                    ClassType forge = type("net.minecraftforge.common.MinecraftForge");
                    ObjectReference bus = (ObjectReference) forge.getValue(forge.fieldByName("EVENT_BUS"));
                    ObjectReference pre = make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Pre",
                        "(Lnet/minecraft/client/gui/GuiScreen;IIF)V", screen, vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0f));
                    if (!((BooleanValue) call(bus, "post", "(Lcpw/mods/fml/common/eventhandler/Event;)Z", pre)).value()) {
                        call(screen, "drawScreen", "(IIF)V", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0f));
                        ObjectReference post = make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post",
                            "(Lnet/minecraft/client/gui/GuiScreen;IIF)V", screen, vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(0f));
                        call(bus, "post", "(Lcpw/mods/fml/common/eventhandler/Event;)Z", post);
                    }
                }
                helper.invokeMethod(thread, methods(helper, "saveScreenshot", "(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;").getFirst(),
                    List.of(field(client, "mcDataDir"), vm.mirrorOf(filename), vm.mirrorOf(width), vm.mirrorOf(height), framebuffer), ObjectReference.INVOKE_SINGLE_THREADED);
                if (args[0].equals("screenshot-hover")) call(framebuffer, "unbindFramebuffer", "()V");
                if (mouse != null) {
                    mouse.setValue(mouse.fieldByName("x"), previousMouseX);
                    mouse.setValue(mouse.fieldByName("y"), previousMouseY);
                }
                break;
            }
            case "respawn":
                call(player, "respawnPlayer", "()V");
                break;
            case "disconnect-safe": {
                act(new String[]{"require-qualification", args[1]});
                if (call(field(player,"inventory"), "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null)
                    throw new IllegalStateException("Finish the current item move before restarting the test game");
                if (!field(player,"openContainer").equals(field(player,"inventoryContainer")))
                    throw new IllegalStateException("Return to the player inventory before restarting");
                ClassType diagnostics = type("dev.gilbe.dynamicinventory.client.BaselineClientDiagnostics");
                diagnostics.invokeMethod(thread, diagnostics.methodsByName("capture").getFirst(),
                    List.of(client, vm.mirrorOf("atomic-before-update"), vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
                call(player,"closeScreen","()V");
                ObjectReference leavingWorld=field(client,"theWorld");
                if (leavingWorld!=null) call(leavingWorld,"sendQuittingDisconnectingPacket","()V");
                call(client, "loadWorld", "(Lnet/minecraft/client/multiplayer/WorldClient;)V", new Value[]{null});
                call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", make("net.minecraft.client.gui.GuiMainMenu", "()V"));
                break;
            }
            case "disconnect":
                ObjectReference leavingWorld=field(client,"theWorld");
                if (leavingWorld!=null) call(leavingWorld,"sendQuittingDisconnectingPacket","()V");
                call(client, "loadWorld", "(Lnet/minecraft/client/multiplayer/WorldClient;)V", new Value[]{null});
                call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", make("net.minecraft.client.gui.GuiMainMenu", "()V"));
                break;
            case "quit":
                if (player != null) throw new IllegalStateException("Disconnect and save first");
                call(client, "shutdown", "()V");
                break;
            default: throw new IllegalArgumentException("Unknown action");
        }
        System.out.println("PASS action: " + String.join(" ", args));
    }
    public static void main(String[] args) throws Exception {
        loadMappings();
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("localhost");
        options.get("port").setValue(System.getProperty("dinv.port", "5005"));
        options.get("timeout").setValue("10000");
        long attachDeadline = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                vm = connector.attach(options);
                break;
            } catch (java.net.ConnectException starting) {
                if (System.currentTimeMillis() >= attachDeadline) throw starting;
                Thread.sleep(200);
            }
        }
        vm.resume();
        boolean armed = false;
        long deadline = System.currentTimeMillis() + Long.parseLong(System.getProperty("dinv.waitMs", "120000"));
        try {
            while (System.currentTimeMillis() < deadline) {
                EventSet events = vm.eventQueue().remove(250);
                if (events != null) {
                    try {
                        for (Event event : events) {
                            if (event instanceof BreakpointEvent hit) {
                                hit.request().disable();
                                thread = hit.thread();
                                loader = hit.location().declaringType().classLoader();
                                try {
                                    act(args);
                                    if (awaitingGraveSeed) continue;
                                } catch (InvocationException failure) {
                                    ObjectReference cause = failure.exception();
                                    System.err.println("Target failure: " + cause.referenceType().name() + ": " +
                                        cause.getValue(fieldInfo(cause.referenceType(), "detailMessage")));
                                    call(cause, "printStackTrace", "()V");
                                    throw failure;
                                }
                                return;
                            }
                        }
                    } finally { events.resume(); }
                }
                var clients = vm.classesByName("net.minecraft.client.Minecraft");
                if (!armed && !clients.isEmpty() && clients.getFirst().isInitialized()) {
                    ReferenceType clazz = clients.getFirst();
                    client = (ObjectReference) clazz.getValue(fieldInfo(clazz, "theMinecraft"));
                    if (client != null) {
                        ObjectReference screen = field(client, "currentScreen");
                        ObjectReference player = field(client, "thePlayer");
                        boolean disconnectedAction = args[0].equals("connect") || args[0].equals("disconnect") || args[0].equals("quit");
                        boolean wantedScreen = !args[0].equals("wait-screen") || (screen != null && screen.referenceType().name().equals(args[1]));
                        if (wantedScreen && (player != null || (screen != null && (args[0].equals("screen-status") || screen.referenceType().name().equals("net.minecraft.client.gui.GuiMainMenu")
                            || screen.referenceType().name().equals("lumien.custommainmenu.gui.GuiCustom")
                            || (disconnectedAction && screen.referenceType().name().equals("net.minecraft.client.gui.GuiDisconnected")))))) {
                            BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(methods(clazz, "runTick", "()V").getFirst().location());
                            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                            request.enable();
                            armed = true;
                        }
                    }
                }
            }
            throw new IllegalStateException("Timed out waiting for game");
        } finally { vm.dispose(); }
    }
}
