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

    static void panelInputInfo() throws Exception {
        ObjectReference host=field(client,"currentScreen"), player=field(client,"thePlayer"), inventory=field(player,"inventory");
        if(host==null) { System.out.println("SCREEN=none"); panelOverlayInfo(); return; }
        System.out.println("SCREEN="+host.referenceType().name());
        ObjectReference held=(ObjectReference)call(inventory,"getItemStack","()Lnet/minecraft/item/ItemStack;");
        System.out.println("CURSOR="+(held==null?"empty":call(held,"toString","()Ljava/lang/String;")));
        System.out.println("SAME_CONTAINER="+Objects.equals(field(player,"openContainer"),field(host,"inventorySlots")));
        System.out.println("DRAGGING="+call(host,"dynamicinventory$draggingItems",null));
        ObjectReference panel=floatingPanel(host);
        System.out.println("DETACHED="+call(panel,"detached",null)+" BLOCKED="+call(panel,"blocked",null));
        recipeFields("PANEL",panel,"releaseOwned","captured");
        recipeFields("PLAYER",host,"sectionReleaseOwned","sectionDragging","controlClick","settingsOpen");
        ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
        ObjectReference modal=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
        System.out.println("OVERLAY="+modal);
        if(modal!=null) {
            ObjectReference modalHost=field(modal,"host");
            System.out.println("OVERLAY_HOST="+modalHost.referenceType().name()+" SAME="+modalHost.equals(host));
            recipeFields("OVERLAY",modal,"closing");
        }
        ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
        recipeFields("CONTROL",control,"x","y","width","height");
        recipeFields("GUI",host,"width","height","guiLeft","guiTop");
        recipeFields("DISPLAY",client,"displayWidth","displayHeight");
        ClassType itemPanels=type("codechicken.nei.ItemPanels");
        for(String name:List.of("itemPanel","bookmarkPanel")) {
            ObjectReference nei=(ObjectReference)itemPanels.getValue(itemPanels.fieldByName(name));
            recipeFields("NEI."+name,nei,"mouseDownSlot","draggedStack");
        }
        ClassType mouse=type("org.lwjglx.input.Mouse");
        for(String name:List.of("x","y","lastEventX","lastEventY","lastEventButton")) {
            Field f=mouse.fieldByName(name); if(f!=null)System.out.println("MOUSE."+name+"="+mouse.getValue(f));
        }
    }

    static void panelOverlayInfo() throws Exception {
        ObjectReference host=field(client,"currentScreen");
        ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
        ObjectReference modal=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
        System.out.println("OVERLAY="+modal);
        if(modal!=null) {
            ObjectReference modalHost=field(modal,"host");
            System.out.println("OVERLAY_HOST="+modalHost.referenceType().name()+" SAME="+modalHost.equals(host));
            recipeFields("OVERLAY",modal,"closing");
        }
    }

    // Read-only: capture the host behind NEI without returning to it or rebuilding its decorator.
    static void terminalRecipeInfo() throws Exception {
        System.out.println("GAME_DIR="+call(field(client,"mcDataDir"),"getAbsolutePath","()Ljava/lang/String;"));
        ObjectReference screen=field(client,"currentScreen");
        if(screen==null)throw new AssertionError("No screen is open");
        System.out.println("SCREEN="+screen.referenceType().name());
        ObjectReference host=screen;
        if(screen.referenceType().name().startsWith("codechicken.nei.recipe.Gui"))host=field(screen,"firstGui");
        if(host==null||!host.referenceType().name().startsWith("appeng.client.gui."))
            throw new AssertionError("Leave an AE2 terminal or its NEI recipe open");
        System.out.println("HOST="+host.referenceType().name());
        recipeFields("HOST",host,"width","height","xSize","ySize","guiLeft","guiTop","rows","perRow","reservedSpace","craftingMode");
        ObjectReference panel=(ObjectReference)call(host,"dynamicinventory$panel",null);
        if(panel!=null) {
            recipeFields("DECORATOR",panel,"nativeWidth","nativeHeight","viewportWidth","viewportHeight","nativeStorageTop","nativeClipLeft","nativeClipRight","nativeClipTop","nativeClipBottom","headerX","headerY");
            ObjectReference floating=field(panel,"floating");
            if(floating!=null) {
                System.out.println("DETACHED="+call(floating,"detached","()Z"));
                recipeFields("MACHINE",field(floating,"machine"),"x","y","width","height");
            }
        }
        ObjectReference container=field(host,"inventorySlots"), slots=field(container,"inventorySlots");
        System.out.println("CONTAINER="+container.referenceType().name());
        recipeFields("WINDOW",container,"windowId");
        recipeFields("ANCHOR",container,"targetSlotIndex");
        Field locks=recipeField(container.referenceType(),"locked");
        if(locks!=null)System.out.println("LOCKED="+call((ObjectReference)container.getValue(locks),"toString","()Ljava/lang/String;"));
        ObjectReference net=(ObjectReference)call(client,"getNetHandler",null);
        ObjectReference gate=(ObjectReference)call(net,"dynamicinventory$openingGate",null);
        recipeFields("GATE",gate,"window","accepted","committed","ready","lastNonce");
        ObjectReference offer=field(gate,"offer");
        if(offer!=null) {
            ArrayReference digest=(ArrayReference)offer.getValue(fieldInfo(offer.referenceType(),"digest"));
            StringBuilder hex=new StringBuilder();
            for(Value value:digest.getValues())hex.append(String.format("%02x",((ByteValue)value).value()&255));
            System.out.println("SERVER_OFFER_DIGEST="+hex);
        }
        int count=((IntegerValue)call(slots,"size","()I")).value();
        for(int i=0;i<count;i++) {
            ObjectReference slot=(ObjectReference)call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
            System.out.println("REAL_SLOT="+i+" "+slot.referenceType().name()+" raw="+call(slot,"getSlotIndex","()I")+" x="+intField(slot,"xDisplayPosition")+" y="+intField(slot,"yDisplayPosition"));
            ObjectReference stack=(ObjectReference)call(slot,"getStack","()Lnet/minecraft/item/ItemStack;");
            System.out.println("REAL_STACK="+i+" "+(stack==null?"empty":call(stack,"toString","()Ljava/lang/String;")));
        }
        for(String name:List.of("craftingSlots","outputSlots")) {
            Field f=recipeField(host.referenceType(),name);
            ArrayReference virtual=f==null?null:(ArrayReference)host.getValue(f);
            if(virtual==null)continue;
            for(int i=0;i<virtual.length();i++) {
                ObjectReference slot=(ObjectReference)virtual.getValue(i);
                if(slot==null)continue;
                recipeFields(name+"["+i+"]",slot,"xPos","yPos","isHidden","slotIndex");
                ObjectReference stack=(ObjectReference)call(slot,"getAEStack",null);
                System.out.println("STACK="+name+"["+i+"] "+(stack==null?"empty":call(stack,"toString","()Ljava/lang/String;")));
            }
        }
        ClassType monitor=type("appeng.client.gui.implementations.GuiMEMonitorable");
        for(String name:List.of("craftingGridOffsetX","craftingGridOffsetY"))System.out.println(name+"="+monitor.getValue(monitor.fieldByName(name)));
    }

    static void recipeFields(String label,ObjectReference object,String... names) {
        if(object==null)return;
        for(String name:names) {
            Field f=recipeField(object.referenceType(),name);
            if(f!=null)System.out.println(label+"."+name+"="+object.getValue(f));
        }
    }

    static Field recipeField(ReferenceType type,String name) {
        try {return fieldInfo(type,name);}catch(IllegalStateException absent){return null;}
    }

    static void nativeBatchClassCheck() throws Exception {
        System.out.println("GAME_DIR="+call(field(client,"mcDataDir"),"getAbsolutePath","()Ljava/lang/String;"));
        for(String cls:List.of("dev.gilbe.dynamicinventory.compat.BuiltinInventoryUiAdapters","dev.gilbe.dynamicinventory.compat.IntegrationProfile")) {
            ClassType loaded=type(cls);
            ObjectReference domain=(ObjectReference)call(loaded.classObject(),"getProtectionDomain","()Ljava/security/ProtectionDomain;");
            ObjectReference code=(ObjectReference)call(domain,"getCodeSource","()Ljava/security/CodeSource;");
            System.out.println("SOURCE="+cls+" "+call(code,"getLocation","()Ljava/net/URL;"));
        }
        ClassType sys=type("java.lang.System");
        System.out.println("PROFILE="+sys.invokeMethod(thread,sys.methodsByName("getProperty","(Ljava/lang/String;)Ljava/lang/String;").getFirst(),List.of(vm.mirrorOf("dynamicinventory.profile")),ObjectReference.INVOKE_SINGLE_THREADED));
        ClassType api = type("dev.gilbe.dynamicinventory.api.InventoryUi");
        ObjectReference adapters = (ObjectReference) api.invokeMethod(thread, api.methodsByName("getRegisteredAdapters").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
        int count = ((IntegerValue)call(adapters,"size","()I")).value(), checked=0;
        for(int i=0;i<count;i++) {
            ObjectReference adapter=(ObjectReference)call(adapters,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
            String id=((StringReference)call(adapter,"getId",null)).value();
            if(!id.startsWith("dynamicinventory:enderio/")&&!id.startsWith("dynamicinventory:extrautils/")&&!id.startsWith("dynamicinventory:etfuturum/"))continue;
            String container=((StringReference)call(adapter,"getContainerClassName",null)).value();
            String gui=((StringReference)call(adapter,"getGuiClassName",null)).value();
            type(container);type(gui);
            System.out.println("HOST\t"+id+"\t"+container+"\t"+gui); checked++;
        }
        for(String name:List.of("ganymedes01.etfuturum.client.gui.inventory.GuiShulkerBox","crazypants.enderio.teleport.telepad.GuiAugmentedTravelAccessible","com.cleanroommc.bogosorter.common.sort.SortHandler"))type(name);
        if(checked!=45)throw new AssertionError("Expected 45 registered container families, got "+checked);
        System.out.println("PASS installed adapter/mixin class loading, with no UIs opened");
    }
    static void registeredItems(String[] args) throws Exception {
        ClassType items=type("net.minecraft.item.Item");
        ObjectReference registry=(ObjectReference)items.getValue(fieldInfo(items,"itemRegistry"));
        ObjectReference keys=(ObjectReference)call(registry,"getKeys","()Ljava/util/Set;");
        ArrayReference entries=(ArrayReference)call(keys,"toArray","()[Ljava/lang/Object;");
        for(Value entry:entries.getValues()) {
            String name=entry instanceof StringReference text?text.value():((StringReference)call((ObjectReference)entry,"toString","()Ljava/lang/String;")).value();
            for(int i=1;i<args.length;i++)if(name.toLowerCase(Locale.ROOT).contains(args[i].toLowerCase(Locale.ROOT))){System.out.println(name);break;}
        }
    }
    static ObjectReference batchLabWorld(String worldName) throws Exception {
        act(new String[]{"require-qualification",worldName});
        if(!worldName.equals("DI-Baseline-Storage-Panel-Lab-Qualification")||!thread.name().equals("Server thread"))throw new AssertionError("Separate lab and server thread required");
        ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;");
        return (ObjectReference)call(server,"worldServerForDimension","(I)Lnet/minecraft/world/WorldServer;",vm.mirrorOf(0));
    }
    static ObjectReference registeredBlock(String name) throws Exception {
        ClassType blocks=type("net.minecraft.block.Block");
        ObjectReference registry=(ObjectReference)blocks.getValue(fieldInfo(blocks,"blockRegistry"));
        ObjectReference block=(ObjectReference)call(registry,"getObject","(Ljava/lang/Object;)Ljava/lang/Object;",vm.mirrorOf(name));
        if(block==null)throw new AssertionError("Missing block "+name);
        return block;
    }
    static void batchLabInstall(String[] args) throws Exception {
        ObjectReference world=batchLabWorld(args[1]);
        List<String[]> rows=new ArrayList<>();
        for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[2])))if(!line.isBlank()&&!line.startsWith("#"))rows.add(line.split("\t"));
        Set<String> targets=new HashSet<>();
        for(String[] row:rows){
            int x=Integer.parseInt(row[4]),y=4,z=Integer.parseInt(row[5]);
            if(x < -24||x>24||z<112||z>400)throw new AssertionError("Station outside designated lab extension");
            for(int dz:new int[]{0,-2,1}){
                String key=x+","+(z+dz);
                if(!targets.add(key))throw new AssertionError("Duplicate station target "+key);
                if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z+dz))).value())throw new AssertionError("Occupied station target "+key);
            }
        }
        ObjectReference sign=registeredBlock("minecraft:standing_sign");
        for(String[] row:rows){
            int x=Integer.parseInt(row[4]),z=Integer.parseInt(row[5]),meta=Integer.parseInt(row[3]);
            if(!row[2].equals("-")){
                ObjectReference block=registeredBlock(row[2]);
                if(!((BooleanValue)call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z),block,vm.mirrorOf(meta),vm.mirrorOf(3))).value())throw new AssertionError("Placement rejected "+row[1]);
                if(row[2].equals("EnderIO:blockConduitBundle")){
                    call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z+1),registeredBlock("minecraft:chest"),vm.mirrorOf(0),vm.mirrorOf(3));
                    ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z));
                    ClassType eio=type("crazypants.enderio.EnderIO");
                    for(String fieldName:List.of("itemItemConduit","itemPowerConduit","itemLiquidConduit","itemRedstoneConduit")){
                        ObjectReference item=(ObjectReference)eio.getValue(fieldInfo(eio,fieldName));
                        if(item==null)continue;
                        ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;II)V",item,vm.mirrorOf(1),vm.mirrorOf(0));
                        ObjectReference conduit=(ObjectReference)call(item,"createConduit","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;)Lcrazypants/enderio/conduit/IConduit;",stack,serverPlayer(field(client,"thePlayer")));
                        call(tile,"addConduit","(Lcrazypants/enderio/conduit/IConduit;)V",conduit);
                    }
                    call(world,"markBlockForUpdate","(III)V",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z));
                }
            }
            call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z-2),sign,vm.mirrorOf(8),vm.mirrorOf(3));
            ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z-2));
            ArrayReference text=(ArrayReference)tile.getValue(fieldInfo(tile.referenceType(),"signText"));
            text.setValues(List.of(vm.mirrorOf(row[0]),vm.mirrorOf(row[1]),vm.mirrorOf(row[2].equals("-")?"Pack disabled":"Right-click UI"),vm.mirrorOf("New UI batch")));
            call(tile,"markDirty","()V");call(world,"markBlockForUpdate","(III)V",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z-2));
            System.out.println("PLACED\t"+String.join("\t",row));
        }
        System.out.println("PASS station blocks/signs installed; player inventory untouched; no UIs opened");
    }
    static void batchLabSupplies(String[] args) throws Exception {
        ObjectReference world=batchLabWorld(args[1]);int x=24,y=4,z=104;
        if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z))).value())throw new AssertionError("Supply target occupied");
        call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z),registeredBlock("minecraft:chest"),vm.mirrorOf(0),vm.mirrorOf(3));
        ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z));
        ClassType items=type("net.minecraft.item.Item");ObjectReference registry=(ObjectReference)items.getValue(fieldInfo(items,"itemRegistry"));
        int slot=0;
        for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[2]))) {
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] spec=line.split("\t");
            ObjectReference item=(ObjectReference)call(registry,"getObject","(Ljava/lang/Object;)Ljava/lang/Object;",vm.mirrorOf(spec[0]));
            if(item==null)throw new AssertionError("Missing supply "+spec[0]);
            ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;II)V",item,vm.mirrorOf(1),vm.mirrorOf(Integer.parseInt(spec[1])));
            if(slot>=27)throw new AssertionError("Supply chest full");
            call(tile,"setInventorySlotContents","(ILnet/minecraft/item/ItemStack;)V",vm.mirrorOf(slot++),stack);
        }
        call(tile,"markDirty","()V");
        System.out.println("PASS supplied "+slot+" tools/filters at 24,4,104; player inventory untouched");
    }

    static ObjectReference routingStack(int value) throws Exception {
        if(value==0)return null;
        ClassType items=type("net.minecraft.init.Items");
        ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;I)V",
            items.getValue(fieldInfo(items,value==-64 ? "stick" : "emerald")),vm.mirrorOf(value==-64 ? 64 : value));
        if(value!=-64) {
            ObjectReference tag=make("net.minecraft.nbt.NBTTagCompound","()V");
            call(tag,"setInteger","(Ljava/lang/String;I)V",vm.mirrorOf("dinvRoutingProbe"),vm.mirrorOf(7));
            call(stack,"setTagCompound","(Lnet/minecraft/nbt/NBTTagCompound;)V",tag);
        }
        return stack;
    }
    static int[] routingCounts(String mode, int size, boolean after) {
        int[] counts=new int[size];
        if(!mode.equals("hotbar-to-main")&&!mode.equals("main-to-hotbar")&&!mode.startsWith("container-"))Arrays.fill(counts,0,9,-64);
        switch(mode) {
            case "hotbar-to-main":counts[after ? 18 : 0]=7;break;
            case "main-to-hotbar":counts[after ? 0 : 18]=7;break;
            case "blocked-main":counts[18]=7;break;
            case "satellite":counts[9]=after ? 0 : 7;counts[18]=after ? 64 : 60;counts[19]=after ? 3 : 0;break;
            case "spawn":counts[9]=60;counts[18]=after ? 7 : 0;break;
            case "spawn-merge":counts[9]=60;counts[18]=after ? 64 : 60;counts[19]=after ? 3 : 0;break;
            case "spawn-extra":counts[9]=60;Arrays.fill(counts,18,36,-64);counts[36]=after ? 7 : 0;break;
            case "spawn-full":Arrays.fill(counts,18,size,-64);break;
            case "spawn-infinite":counts[9]=60;counts[18]=after ? -1 : 0;break;
            case "container-main":if(!after)counts[18]=7;break;
            case "container-satellite":if(!after)counts[9]=7;break;
            case "container-hotbar":if(!after)counts[0]=7;break;
            default:throw new IllegalArgumentException(mode);
        }
        return counts;
    }

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
    static int intField(ObjectReference target, String name) {
        return ((IntegerValue) target.getValue(fieldInfo(target.referenceType(), name))).value();
    }
    static String rectInfo(ObjectReference rect) throws Exception {
        if (rect == null) return "null";
        List<Integer> values = new ArrayList<>();
        for (String name : List.of("x", "y", "width", "height")) values.add(intField(rect, name));
        return values.toString();
    }
    static void muiLayoutInfo() throws Exception {
        ObjectReference host = field(client, "currentScreen");
        for (int depth = 0; depth < 5 && host != null && host.referenceType().methodsByName("getScreen").isEmpty(); depth++) {
            Field previous = host.referenceType().fieldByName("firstGui");
            if (previous == null) break;
            host = (ObjectReference) host.getValue(previous);
        }
        if (host == null || host.referenceType().methodsByName("getScreen").isEmpty()) throw new IllegalStateException("No open MUI2 host");
        ObjectReference screen = (ObjectReference) call(host, "getScreen", null);
        ObjectReference bridge = (ObjectReference) call(screen, "dynamicinventory$detachedPanels", null);
        ObjectReference controller = field(bridge, "controller"), owner = field(bridge, "owner"), group = field(bridge, "group");
        System.out.println("host=" + host.referenceType().name() + " screen=" + intField(host,"width") + "x" + intField(host,"height"));
        if (controller == null) { System.out.println("No current detached controller"); return; }
        System.out.println("key=" + call(controller, "key", null) + " inherited=" + controller.getValue(fieldInfo(controller.referenceType(),"inherited")));
        for (String name : List.of("machine", "attached", "viewport")) System.out.println(name + "=" + rectInfo(field(controller,name)));
        System.out.println("panels=" + panelRectangles(controller));
        System.out.println("owner=" + rectInfo((ObjectReference)call(owner,"getArea",null)) + " group=" + rectInfo((ObjectReference)call(group,"getArea",null)));
        ObjectReference originals = field(bridge,"originalAreas");
        System.out.println("nativeOwner=" + rectInfo((ObjectReference)call(originals,"get","(Ljava/lang/Object;)Ljava/lang/Object;",owner)));
        ObjectReference states=field(controller,"panels"), main=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(0));
        ObjectReference profile=field(main,"profile");
        System.out.println("profile=" + call(profile,"toJson",null));
        ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
        Value request=profiles.invokeMethod(thread, profiles.methodsByName("requestedBounds").getFirst(), List.of(call(controller,"key",null), profile, call(controller,"profileMachine",null), field(controller,"viewport")), ObjectReference.INVOKE_SINGLE_THREADED);
        System.out.println("requested=" + rectInfo((ObjectReference)request));
    }
    static void rebuildScreen(ObjectReference screen) throws Exception {
        call(screen, "setWorldAndResolution", "(Lnet/minecraft/client/Minecraft;II)V", client,
            vm.mirrorOf(intField(screen, "width")), vm.mirrorOf(intField(screen, "height")));
    }
    static void playerReopenCheck(String[] args) throws Exception {
        act(new String[]{"require-qualification", args[1]});
        ObjectReference player = field(client, "thePlayer");
        ObjectReference screen = field(client, "currentScreen");
        if (!screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))
            throw new IllegalStateException("Open the player inventory first");
        String inventoryBefore = inventoryNbt(field(player, "inventory"));
        if (args.length > 2 && args[2].equals("roundtrip")) {
            for (int toggle = 0; toggle < 2; toggle++) {
                ObjectReference panel = floatingPanel(screen);
                ObjectReference control = (ObjectReference) call(panel, "control", null, call(panel, "playerBounds", null));
                rawClick(screen, intField(control, "x") + 2, intField(control, "y") + 2, 0);
                renderScreen(screen, -1000, -1000);
                boolean detached = ((BooleanValue) call(panel, "detached", null)).value();
                if (detached != (toggle == 1)) throw new AssertionError("Attach/detach toggle failed");
            }
        }
        List<Integer> initialBounds = null;
        boolean observe = args.length > 2 && args[2].equals("observe");
        for (int reopen = 0; reopen < (observe ? 1 : 4); reopen++) {
            if (reopen > 0) {
                call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", (Value) null);
                call(client, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V",
                    make("net.minecraft.client.gui.inventory.GuiInventory", "(Lnet/minecraft/entity/player/EntityPlayer;)V", player));
                screen = field(client, "currentScreen");
            }
            renderScreen(screen, -1000, -1000);
            ObjectReference panel = floatingPanel(screen);
            if (!((BooleanValue) call(panel, "detached", null)).value())
                throw new AssertionError("Detached layout required");
            ObjectReference bounds = (ObjectReference) call(panel, "playerBounds", null);
            int bottom = intField(screen, "guiTop") + intField(screen, "tabSpace") + 90;
            System.out.println("reopen=" + reopen + " characterBottom=" + bottom + " panels=" + panelRectangles(panel));
            List<Integer> currentBounds = panelRectangles(panel);
            if (initialBounds == null) initialBounds = currentBounds;
            else if (!initialBounds.equals(currentBounds)) throw new AssertionError("Panel bounds changed on reopen");
            if (!observe && intField(bounds, "y") <= bottom)
                throw new AssertionError("Main jumped above the character section after reopen");
        }
        if (!inventoryBefore.equals(inventoryNbt(field(player, "inventory"))))
            throw new AssertionError("Inventory contents changed during reopen");
        System.out.println("PASS player inventory reopen " + (observe ? "observation" : "placement and item preservation"));
    }
    static ObjectReference floatingPanel(ObjectReference screen) throws Exception {
        ClassType controller = type("dev.gilbe.dynamicinventory.client.DetachedChestPanel");
        return (ObjectReference) controller.invokeMethod(thread, controller.methodsByName("of").getFirst(), List.of(screen), ObjectReference.INVOKE_SINGLE_THREADED);
    }
    static List<Integer> panelRectangles(ObjectReference panel) throws Exception {
        ObjectReference states=field(panel,"panels");
        List<Integer> result=new ArrayList<>();
        for(int i=0;i<((IntegerValue)call(states,"size","()I")).value();i++) {
            ObjectReference state=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)),layout=field(state,"layout");
            if(layout!=null)for(String name:List.of("x","y","width","height"))result.add(intField(field(layout,"bounds"),name));
        }
        return result;
    }
    static void panelPointer(ObjectReference panel, int x, int y, int button, boolean down) throws Exception {
        if (!((BooleanValue) call(panel, "pointer", "(IIIZ)Z", vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(button), vm.mirrorOf(down))).value())
            throw new AssertionError("Panel chrome did not own gesture");
    }
    static void rawClick(ObjectReference screen, int x, int y, int button) throws Exception {
        int sw=intField(screen,"width"),sh=intField(screen,"height");
        int dw=intField(client,"displayWidth"),dh=intField(client,"displayHeight");
        ClassType mouse=type("org.lwjglx.input.Mouse");
        Map<Field,Value> previous=new LinkedHashMap<>();
        for(String name:List.of("x","y","latestX","latestY","lastEventX","lastEventY")) {
            Field f=mouse.fieldByName(name); previous.put(f,mouse.getValue(f));
            mouse.setValue(f,vm.mirrorOf(name.endsWith("X")||name.equals("x") ? x*dw/sw : (sh-y-1)*dh/sh));
        }
        try {
            for(boolean down:new boolean[]{true,false}) mouse.invokeMethod(thread,methods(mouse,"addButtonEvent","(IZ)V").getFirst(),List.of(vm.mirrorOf(button),vm.mirrorOf(down)),ObjectReference.INVOKE_SINGLE_THREADED);
            call(screen,"handleInput","()V");
        } finally { for(var saved:previous.entrySet())mouse.setValue(saved.getKey(),saved.getValue()); }
    }
    static void renderScreen(ObjectReference screen, int x, int y) throws Exception {
        call(field(client,"entityRenderer"),"setupOverlayRendering","()V");
        ObjectReference bus=(ObjectReference)type("net.minecraftforge.common.MinecraftForge").getValue(type("net.minecraftforge.common.MinecraftForge").fieldByName("EVENT_BUS"));
        ObjectReference pre=make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Pre","(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
        if(((BooleanValue)call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",pre)).value())throw new AssertionError("Screen frame canceled");
        call(screen,"drawScreen","(IIF)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f));
        call(bus,"post","(Lcpw/mods/fml/common/eventhandler/Event;)Z",make("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post","(Lnet/minecraft/client/gui/GuiScreen;IIF)V",screen,vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0f)));
    }
    static void rawSectionDrag(ObjectReference screen,int x,int y,int dx,int dy) throws Exception {
        ClassType mouse=type("org.lwjglx.input.Mouse"),display=type("org.lwjglx.opengl.Display");
        Map<Field,Value> previous=new LinkedHashMap<>();
        for(String f:List.of("x","y","latestX","latestY","lastEventX","lastEventY","sdlMouseButtonFlags"))previous.put(mouse.fieldByName(f),mouse.getValue(mouse.fieldByName(f)));
        Value focus=display.getValue(display.fieldByName("displayFocused"));
        int sw=intField(screen,"width"),sh=intField(screen,"height"),dw=intField(client,"displayWidth"),dh=intField(client,"displayHeight");
        try {
            // Synthetic hardware state is scoped to this paused render-thread invocation.
            display.setValue(display.fieldByName("displayFocused"),vm.mirrorOf(true));
            mouse.setValue(mouse.fieldByName("sdlMouseButtonFlags"),vm.mirrorOf(1));
            int left=intField(screen,"guiLeft"),top=intField(screen,"guiTop");
            for(int phase=0;phase<3;phase++) {
                int px=x+(phase==0?0:dx),py=y+(phase==0?0:dy);
                for(String f:List.of("x","y","latestX","latestY","lastEventX","lastEventY"))mouse.setValue(mouse.fieldByName(f),vm.mirrorOf(f.endsWith("X")||f.equals("x")?px*dw/sw:(sh-py-1)*dh/sh));
                if(phase!=1) {
                    if(phase==2)mouse.setValue(mouse.fieldByName("sdlMouseButtonFlags"),vm.mirrorOf(0));
                    mouse.invokeMethod(thread,methods(mouse,"addButtonEvent","(IZ)V").getFirst(),List.of(vm.mirrorOf(0),vm.mirrorOf(phase==0)),ObjectReference.INVOKE_SINGLE_THREADED);
                    call(screen,"handleInput","()V");
                }
                renderScreen(screen,px,py);
                if(phase==0&&!((BooleanValue)screen.getValue(fieldInfo(screen.referenceType(),"sectionDragging"))).value())throw new AssertionError("Raw button-down did not reach the player-section handle");
                if(phase==1&&left==intField(screen,"guiLeft")&&top==intField(screen,"guiTop"))throw new AssertionError("Rendered frame did not move the held section");
            }
            if(((BooleanValue)screen.getValue(fieldInfo(screen.referenceType(),"sectionReleaseOwned"))).value())throw new AssertionError("Raw release did not finish the header gesture");
            int movedLeft=intField(screen,"guiLeft"),movedTop=intField(screen,"guiTop");
            for(int i=0;i<3;i++)renderScreen(screen,-1000,-1000);
            if(movedLeft!=intField(screen,"guiLeft")||movedTop!=intField(screen,"guiTop"))throw new AssertionError("Post-release frames recentered the player section");
        } finally {
            for(var saved:previous.entrySet())mouse.setValue(saved.getKey(),saved.getValue());
            display.setValue(display.fieldByName("displayFocused"),focus);
        }
    }
    static void muiDrag(ObjectReference screen,int x,int y,int dx,int dy) throws Exception {
        ClassType mouse=type("org.lwjglx.input.Mouse"),display=type("org.lwjglx.opengl.Display");
        Map<Field,Value> previous=new LinkedHashMap<>();
        for(String f:List.of("x","y","latestX","latestY","lastEventX","lastEventY","sdlMouseButtonFlags"))previous.put(mouse.fieldByName(f),mouse.getValue(mouse.fieldByName(f)));
        Value focus=display.getValue(display.fieldByName("displayFocused"));
        int sw=intField(screen,"width"),sh=intField(screen,"height"),dw=intField(client,"displayWidth"),dh=intField(client,"displayHeight");
        try {
            // Synthetic hardware state is scoped to this paused render-thread invocation.
            display.setValue(display.fieldByName("displayFocused"),vm.mirrorOf(true));
            mouse.setValue(mouse.fieldByName("sdlMouseButtonFlags"),vm.mirrorOf(1));
            int left=intField(screen,"guiLeft"),top=intField(screen,"guiTop");
            for(int phase=0;phase<3;phase++) {
                int px=x+(phase==0?0:dx),py=y+(phase==0?0:dy);
                for(String f:List.of("x","y","latestX","latestY","lastEventX","lastEventY"))mouse.setValue(mouse.fieldByName(f),vm.mirrorOf(f.endsWith("X")||f.equals("x")?px*dw/sw:(sh-py-1)*dh/sh));
                if(phase!=1) {
                    if(phase==2)mouse.setValue(mouse.fieldByName("sdlMouseButtonFlags"),vm.mirrorOf(0));
                    mouse.invokeMethod(thread,methods(mouse,"addButtonEvent","(IZ)V").getFirst(),List.of(vm.mirrorOf(0),vm.mirrorOf(phase==0)),ObjectReference.INVOKE_SINGLE_THREADED);
                    call(screen,"handleInput","()V");
                }
                muiFrame(screen);
            }
            int movedLeft=intField(screen,"guiLeft"),movedTop=intField(screen,"guiTop");
            for(int i=0;i<3;i++)muiFrame(screen);
        } finally {
            for(var saved:previous.entrySet())mouse.setValue(saved.getKey(),saved.getValue());
            display.setValue(display.fieldByName("displayFocused"),focus);
        }
    }
    static void rawGuiButton(ObjectReference gui,int id) throws Exception {
        ObjectReference buttons=field(gui,"buttonList");
        for(int i=0;i<((IntegerValue)call(buttons,"size","()I")).value();i++) {
            ObjectReference b=(ObjectReference)call(buttons,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
            if(intField(b,"id")==id) {
                rawClick(gui,intField(b,"xPosition")+5,intField(b,"yPosition")+5,0);
                renderScreen(gui,-1000,-1000);
                return;
            }
        }
        throw new AssertionError("Missing raw-input menu button "+id);
    }
    static void ae2WorkbenchSideCheck(ObjectReference player) throws Exception {
        String before=inventoryNbt(field(player,"inventory"));
        ObjectReference active=field(player,"openContainer");
        ObjectReference tile=make("appeng.tile.misc.TileCellWorkbench","()V");
        ObjectReference cellInventory=(ObjectReference)call(tile,"getInventoryByName","(Ljava/lang/String;)Lnet/minecraft/inventory/IInventory;",vm.mirrorOf("cell"));
        ClassType registry=type("cpw.mods.fml.common.registry.GameRegistry");
        ObjectReference item=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findItem").getFirst(),List.of(vm.mirrorOf("appliedenergistics2"),vm.mirrorOf("item.ItemBasicStorageCell.1k")),ObjectReference.INVOKE_SINGLE_THREADED);
        ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;I)V",item,vm.mirrorOf(1));
        call(cellInventory,"setInventorySlotContents","(ILnet/minecraft/item/ItemStack;)V",vm.mirrorOf(0),stack);
        ObjectReference gui=make("appeng.client.gui.implementations.GuiCellWorkbench","(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/implementations/tiles/ICellWorkbench;)V",field(player,"inventory"),tile);
        ObjectReference resolution=make("net.minecraft.client.gui.ScaledResolution","(Lnet/minecraft/client/Minecraft;II)V",client,vm.mirrorOf(intField(client,"displayWidth")),vm.mirrorOf(intField(client,"displayHeight")));
        try {
        call(gui,"setWorldAndResolution","(Lnet/minecraft/client/Minecraft;II)V",client,call(resolution,"getScaledWidth","()I"),call(resolution,"getScaledHeight","()I"));
        ObjectReference panel=(ObjectReference)call(gui,"dynamicinventory$panel","()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;"),sides=field(panel,"aeSidePanels"),container=field(gui,"inventorySlots");
        for(int expected:new int[]{5,0,5}) {
            call(cellInventory,"setInventorySlotContents","(ILnet/minecraft/item/ItemStack;)V",vm.mirrorOf(0),expected==0?null:stack);
            call(panel,"position","()V");
            int nativeCount=((IntegerValue)call(container,"availableUpgrades","()I")).value();
            ObjectReference bounds=(ObjectReference)call(sides,"bounds",null,vm.mirrorOf(0),vm.mirrorOf(0));
            int count=((IntegerValue)call(bounds,"size","()I")).value();
            if(nativeCount!=expected||count!=(expected==0?0:1))throw new AssertionError("Workbench native capacity/frame mismatch: "+nativeCount+" / "+count);
            if(expected>0) {
                ObjectReference frame=(ObjectReference)call(bounds,"get","(I)Ljava/lang/Object;",vm.mirrorOf(0));
                if(intField(frame,"width")!=28||intField(frame,"height")!=100)throw new AssertionError("Five upgrade slots did not form one compact column");
            }
        }
        } finally {
            // Vanilla GuiContainer.initGui selects its container even for an undisplayed fixture.
            player.setValue(fieldInfo(player.referenceType(),"openContainer"),active);
        }
        if(!before.equals(inventoryNbt(field(player,"inventory")))||!active.equals(field(player,"openContainer")))throw new AssertionError("Read-only cell fixture changed player inventory/window");
        System.out.println("PASS native standard-cell capacity 5, single-column side frame, live 5/0/5 cell changes, unchanged player items/window; fixture tile never placed in world");
    }
    static String inventoryNbt(ObjectReference inventory) throws Exception {
        return canonicalNbt((ObjectReference)call(inventory,"writeToNBT","(Lnet/minecraft/nbt/NBTTagList;)Lnet/minecraft/nbt/NBTTagList;",make("net.minecraft.nbt.NBTTagList","()V")));
    }
    static ObjectReference serverPlayer(ObjectReference player) throws Exception {
        ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;");
        return (ObjectReference)call((ObjectReference)call(server,"getConfigurationManager","()Lnet/minecraft/server/management/ServerConfigurationManager;"),"func_152612_a","(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;",call(player,"getCommandSenderName","()Ljava/lang/String;"));
    }
    static int ownedRawIndex(ObjectReference inventory, int logical) throws Exception {
        ClassType layout=type("dev.gilbe.dynamicinventory.inventory.InventoryLayout");
        ObjectReference shape=(ObjectReference)layout.invokeMethod(thread,layout.methodsByName("of").getFirst(),List.of(inventory),ObjectReference.INVOKE_SINGLE_THREADED);
        return ((IntegerValue)call(shape,"rawIndex","(I)I",vm.mirrorOf(logical))).value();
    }
    static void pressGuiButton(ObjectReference gui, int id) throws Exception {
        ObjectReference buttons = field(gui,"buttonList");
        for(int i=0;i<((IntegerValue)call(buttons,"size","()I")).value();i++) {
            ObjectReference button=(ObjectReference)call(buttons,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
            if(intField(button,"id")!=id) continue;
            int x=intField(button,"xPosition")+5,y=intField(button,"yPosition")+5;
            call(gui,"mouseClicked","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
            call(gui,"mouseMovedOrUp","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
            return;
        }
        throw new AssertionError("Missing menu button "+id);
    }
    static void panelMenuField(ObjectReference view, String fields, String key, String text) throws Exception {
        ObjectReference input=(ObjectReference)call(field(view,fields),"get","(Ljava/lang/Object;)Ljava/lang/Object;",vm.mirrorOf(key));
        call(input,"setText","(Ljava/lang/String;)V",vm.mirrorOf(text));
    }
    static void checkPlayerPanelRoundTrip(ObjectReference host, ObjectReference panel) throws Exception {
        if(!host.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen")) return;
        ObjectReference view=field(host,"view"), container=field(host,"inventorySlots");
        ArrayReference ids=(ArrayReference)field(panel,"slotIds");
        for(int cycle=0;cycle<2;cycle++) {
            ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
            int x=intField(control,"x")+2,y=intField(control,"y")+2;
            panelPointer(panel,x,y,0,true); panelPointer(panel,x,y,0,false);
            if(((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Reattach failed");
            for(int main=0;main<ids.length();main++) {
                ObjectReference slot=(ObjectReference)call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",ids.getValue(main));
                int columns=intField(view,"columns"),row=(main-9)/columns-intField(host,"scrollRow");
                boolean visible=main<9 || row>=0 && row<intField(view,"visibleRows");
                ClassType viewSettings=type("dev.gilbe.dynamicinventory.client.InventoryViewSettings");
                boolean centered=((BooleanValue)viewSettings.getValue(viewSettings.fieldByName("centerLastRow"))).value();
                int inset=main<9 || !centered ? 0 : (columns-Math.min(columns,ids.length()-9-(main-9)/columns*columns))*9;
                int expectedX=!visible ? -10000 : main<9 ? (intField(view,"width")-162)/2+main*18 : intField(view,"gridX")+inset+(main-9)%columns*18;
                int expectedY=!visible ? -10000 : (main<9 ? intField(view,"hotbarY") : 84+row*18)+intField(host,"tabSpace");
                if(intField(slot,"xDisplayPosition")!=expectedX || intField(slot,"yDisplayPosition")!=expectedY)
                    throw new AssertionError("Reattach retained floating coordinates for ordinary slot "+main);
            }
            control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
            x=intField(control,"x")+2; y=intField(control,"y")+2;
            panelPointer(panel,x,y,0,true); panelPointer(panel,x,y,0,false);
            if(!((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Detach failed");
            List<Integer> before=new ArrayList<>();
            for(Value id:ids.getValues()) {
                ObjectReference slot=(ObjectReference)call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",id);
                before.add(intField(slot,"yDisplayPosition"));
            }
            call(host,"positionSlots",null);
            for(int main=0;main<ids.length();main++) {
                ObjectReference slot=(ObjectReference)call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",ids.getValue(main));
                if(intField(slot,"yDisplayPosition")!=before.get(main)) throw new AssertionError("Tab offset applied twice to floating slot "+main);
            }
        }
    }
    static void checkPanelMenu(ObjectReference host, ObjectReference panel) throws Exception {
        ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
        ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
        ObjectReference container=field(host,"inventorySlots");
        ObjectReference before=(ObjectReference)call(panel,"preference",null);
        ObjectReference beforeJson=(ObjectReference)call(before,"toJson",null);
        String original=((StringReference)call(beforeJson,"toString",null)).value();
        for(boolean apply:new boolean[]{false,true}) {
            ObjectReference bounds=(ObjectReference)call(panel,"playerBounds",null);
            panelPointer(panel,intField(bounds,"x")+16,intField(bounds,"y")+6,1,true);
            ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
            if(view==null || !field(client,"currentScreen").equals(host) || !field(field(client,"thePlayer"),"openContainer").equals(container))
                throw new AssertionError("Panel menu replaced the live host");
            for(int key:new int[]{2,16,18}) call(view,"keyTyped","(CI)V",vm.mirrorOf('x'),vm.mirrorOf(key));
            if(apply) pressGuiButton(view,20);
            panelMenuField(view,"geometryFields","width",Integer.toString(intField(before,"width")+1));
            panelMenuField(view,"geometryFields","x","51");
            pressGuiButton(view,32); pressGuiButton(view,11); pressGuiButton(view,11); pressGuiButton(view,13); pressGuiButton(view,17);
            pressGuiButton(view,apply ? 50 : 0);
            overlay.invokeMethod(thread,overlay.methodsByName("handleInput").getFirst(),List.of(host),ObjectReference.INVOKE_SINGLE_THREADED);
            if(overlay.getValue(overlay.fieldByName("active"))!=null) throw new AssertionError("Menu did not finish its release");
            ObjectReference after=(ObjectReference)call((ObjectReference)call(panel,"preference",null),"toJson",null);
            if(!apply && !original.equals(((StringReference)call(after,"toString",null)).value())) throw new AssertionError("Cancel changed interface override");
            if(apply) {
                ObjectReference global=(ObjectReference)profiles.invokeMethod(thread,profiles.methodsByName("global").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                if(intField(global,"frameStyle")<0 || intField(global,"borderWidth")!=3) throw new AssertionError("Global style settings were not saved");
                ObjectReference globalJson=(ObjectReference)call(global,"toJson",null);
                if(!((StringReference)call(globalJson,"toString",null)).value().equals(((StringReference)call(after,"toString",null)).value())) throw new AssertionError("Current interface did not inherit saved global");
            }
        }
        ObjectReference bounds=(ObjectReference)call(panel,"playerBounds",null);
        panelPointer(panel,intField(bounds,"x")+16,intField(bounds,"y")+6,1,true);
        ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
        pressGuiButton(view,33); pressGuiButton(view,40);
        overlay.invokeMethod(thread,overlay.methodsByName("handleInput").getFirst(),List.of(host),ObjectReference.INVOKE_SINGLE_THREADED);
        if(((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Use global did not restore attached mode");
        System.out.println("PASS in-container panel menu: pages, guarded keys, Cancel, global/interface scope and Use global");
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
    static void muiFrame(ObjectReference host) throws Exception {
        ObjectReference screen=(ObjectReference)call(host,"getScreen",null);
        call(screen,"onFrameUpdate","()V");
        call(field(client,"entityRenderer"),"setupOverlayRendering","()V");
        call(screen,"drawScreen","()V");
        call(screen,"drawForeground","()V");
    }
    static void muiDetachedCheck() throws Exception {
        ObjectReference host=field(client,"currentScreen"),player=field(client,"thePlayer");
        ObjectReference container=field(player,"openContainer"),slots=field(container,"inventorySlots");
        ObjectReference screen=(ObjectReference)call(host,"getScreen",null);
        ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
        String fixture=System.getProperty("dinv.panelFixtureFile"),original=System.getProperty("dinv.panelOriginalFile");
        if(fixture==null||original==null)throw new IllegalArgumentException("Explicit fixture and original profile paths required");
        String before=inventoryNbt(field(player,"inventory"));
        List<Value> identities=new ArrayList<>(((ArrayReference)call(slots,"toArray","()[Ljava/lang/Object;")).getValues());
        try {
            profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(fixture))),ObjectReference.INVOKE_SINGLE_THREADED);
            profiles.invokeMethod(thread,profiles.methodsByName("resetGlobal").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
            call((ObjectReference)call(screen,"dynamicinventory$detachedPanels",null),"close","()V");
            rebuildScreen(host);muiFrame(host);
            ObjectReference panel=floatingPanel(host);
            if(panel==null)throw new AssertionError("MUI2 controller missing");
            call(panel,"transientDetached","(Z)V",vm.mirrorOf(false));muiFrame(host);
            int attachedHeight=intField((ObjectReference)call((ObjectReference)call(screen,"getMainPanel",null),"getArea",null),"height");
            ObjectReference attachedControl=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
            ObjectReference nativeFrame=(ObjectReference)call((ObjectReference)call(screen,"getMainPanel",null),"getArea",null);
            if(intField(attachedControl,"x")<intField(nativeFrame,"x")+intField(nativeFrame,"width")&&intField(attachedControl,"x")+12>intField(nativeFrame,"x")&&intField(attachedControl,"y")<intField(nativeFrame,"y")+intField(nativeFrame,"height")&&intField(attachedControl,"y")+12>intField(nativeFrame,"y"))throw new AssertionError("MUI split icon overlaps the machine frame");
            for(int pass=0;pass<3;pass++) {
                ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                System.out.println("MUI pass="+pass+" detached="+call(panel,"detached",null)+" control="+intField(control,"x")+","+intField(control,"y")+" release="+panel.getValue(fieldInfo(panel.referenceType(),"releaseOwned")));
                rawClick(host,intField(control,"x")+3,intField(control,"y")+3,0);muiFrame(host);
                if(!((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Raw MUI2 detach input failed");
                int detachedHeight=intField((ObjectReference)call((ObjectReference)call(screen,"getMainPanel",null),"getArea",null),"height");
                if(detachedHeight>=attachedHeight)throw new AssertionError("Detached machine retained expanded inventory height: "+detachedHeight+" attached="+attachedHeight);
                System.out.println("MUI machine height attached="+attachedHeight+" detached="+detachedHeight);
                ObjectReference bridge=(ObjectReference)call(screen,"dynamicinventory$detachedPanels",null);
                ObjectReference widgets=field(bridge,"widgets");
                int count=((IntegerValue)call(widgets,"size","()I")).value();
                for(int i=0;i<count;i++) {
                    ObjectReference widget=(ObjectReference)call(widgets,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    ObjectReference slot=(ObjectReference)call(widget,"getSlot",null),area=(ObjectReference)call(widget,"getArea",null);
                    if(intField(area,"x")!=intField(host,"guiLeft")+intField(slot,"xDisplayPosition")-1 || intField(area,"y")!=intField(host,"guiTop")+intField(slot,"yDisplayPosition")-1)throw new AssertionError("Widget and native slot coordinates disagree");
                    if(!((BooleanValue)call(slot,"func_111238_b","()Z")).value())throw new AssertionError("Projection disabled a synced slot");
                }
                if(pass==0) {
                    ObjectReference bounds=(ObjectReference)call(panel,"playerBounds",null);
                    int bx=intField(bounds,"x"),by=intField(bounds,"y");
                    muiDrag(host,bx+60,by+7,-44,8);
                    bounds=(ObjectReference)call(panel,"playerBounds",null);
                    if(bx==intField(bounds,"x")&&by==intField(bounds,"y"))throw new AssertionError("MUI2 raw drag did not move panel");
                    int width=intField(bounds,"width"),height=intField(bounds,"height");
                    muiDrag(host,intField(bounds,"x")+width-2,intField(bounds,"y")+height-2,19,19);
                    bounds=(ObjectReference)call(panel,"playerBounds",null);
                    if(width==intField(bounds,"width")&&height==intField(bounds,"height"))throw new AssertionError("MUI2 raw corner resize did not change bounds");
                }
                control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                rawClick(host,intField(control,"x")+3,intField(control,"y")+3,1);muiFrame(host);
                ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
                if(overlay.getValue(overlay.fieldByName("active"))==null)throw new AssertionError("MUI2 panel menu missing");
                rawGuiButton((ObjectReference)overlay.getValue(overlay.fieldByName("active")),0);
                call(host,"handleInput","()V");
                control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                rawClick(host,intField(control,"x")+3,intField(control,"y")+3,0);muiFrame(host);
                if(((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Raw MUI2 attach input failed");
                if(attachedHeight!=intField((ObjectReference)call((ObjectReference)call(screen,"getMainPanel",null),"getArea",null),"height"))throw new AssertionError("Attached machine height changed");
            }
            if(!identities.equals(((ArrayReference)call(slots,"toArray","()[Ljava/lang/Object;")).getValues())||!container.equals(field(player,"openContainer"))||!before.equals(inventoryNbt(field(player,"inventory"))))throw new AssertionError("MUI2 lifecycle changed slots, window or contents");
            System.out.println("PASS MUI2 raw drag, corner resize, detach/attach and right-click menu x3; native widget projection, enabled slots, slot identities, window and inventory unchanged");
        } finally {
            profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(original))),ObjectReference.INVOKE_SINGLE_THREADED);
            call((ObjectReference)call(screen,"dynamicinventory$detachedPanels",null),"close","()V");
            rebuildScreen(host);muiFrame(host);
        }
    }
    static void wirelessCheck(String requested) throws Exception {
        ObjectReference host=field(client,"currentScreen"),list=field(host,"buttonList"),panel=floatingPanel(host);
        if(panel==null) {
            if(!host.referenceType().name().equals("com.glodblock.github.client.gui.GuiLevelWireless")||requested==null)throw new AssertionError("Terminal has no panel controller");
            for(int i=0;i<((IntegerValue)call(list,"size","()I")).value();i++) {
                ObjectReference button=(ObjectReference)call(list,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                if(button.referenceType().name().equals("com.glodblock.github.client.gui.GuiFCImgButton")&&((BooleanValue)button.getValue(fieldInfo(button.referenceType(),"visible"))).value()&&requested.equals(((StringReference)call(button,"getSetting",null)).value())) {
                    rawClick(host,intField(button,"xPosition")+5,intField(button,"yPosition")+5,0);
                    System.out.println("PASS native Level Terminal navigation to "+requested+" (layout remains native)");return;
                }
            }
            throw new AssertionError("Missing native level navigation "+requested);
        }
        ObjectReference machine=(ObjectReference)call(panel,"machineBounds",null);
        int bottom=intField(machine,"y")+intField(machine,"height");
        List<ObjectReference> visible=new ArrayList<>();
        ObjectReference selected=null;
        Set<String> modes=new LinkedHashSet<>();
        int controls=0;
        for(int i=0;i<((IntegerValue)call(list,"size","()I")).value();i++) {
            ObjectReference button=(ObjectReference)call(list,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
            if(!button.referenceType().name().equals("com.glodblock.github.client.gui.GuiFCImgButton")||!((BooleanValue)button.getValue(fieldInfo(button.referenceType(),"visible"))).value())continue;
            String setting=((StringReference)call(button,"getSetting",null)).value();
            System.out.println(setting+" x="+intField(button,"xPosition")+" y="+intField(button,"yPosition"));
            if(setting.endsWith("_TEM"))modes.add(setting);else if(setting.equals("MAGNET_CARD")||setting.equals("RESTOCK"))controls++;
            if(setting.equals(requested))selected=button;
            if(intField(button,"xPosition")>=intField(machine,"x"))throw new AssertionError("Wireless control overlaps inventory side");
            if(intField(button,"yPosition")+intField(button,"height")>bottom)throw new AssertionError("Wireless control extends below machine");
            for(ObjectReference other:visible)if(intField(button,"xPosition")<intField(other,"xPosition")+intField(other,"width")&&intField(button,"xPosition")+intField(button,"width")>intField(other,"xPosition")&&intField(button,"yPosition")<intField(other,"yPosition")+intField(other,"height")&&intField(button,"yPosition")+intField(button,"height")>intField(other,"yPosition"))throw new AssertionError("Wireless mode buttons overlap");
            visible.add(button);
        }
        if(modes.size()!=4||controls!=3)throw new AssertionError("Expected four alternate modes and three wireless controls: "+modes+" controls="+controls);
        String active=switch(host.referenceType().name()) {
            case "appeng.client.gui.implementations.GuiCraftingTerm" -> "CRAFT_TEM";
            case "appeng.client.gui.implementations.GuiPatternTerm" -> "PATTERN_TEM";
            case "appeng.client.gui.implementations.GuiPatternTermEx" -> "PATTERN_EX_TEM";
            case "appeng.client.gui.implementations.GuiInterfaceTerminal" -> "INTERFACE_TEM";
            default -> "";
        };
        List<String> expected=new ArrayList<>(List.of("CRAFT_TEM","PATTERN_TEM","PATTERN_EX_TEM","INTERFACE_TEM","LEVEL_TEM"));
        expected.remove(active);
        if(!new ArrayList<>(modes).equals(expected))throw new AssertionError("Wrong alternate destinations/order: "+modes+" active="+active);
        if(host.referenceType().name().equals("appeng.client.gui.implementations.GuiInterfaceTerminal")&&intField(host,"height")>=351) {
            int previous=-1,first=-1,primary=intField(machine,"x")-18;
            for(ObjectReference button:visible) {
                String setting=((StringReference)call(button,"getSetting",null)).value();
                if(!setting.endsWith("_TEM"))continue;
                int y=intField(button,"yPosition");
                if(intField(button,"xPosition")!=primary||previous>=0&&y!=previous+20)throw new AssertionError("Interface navigation is not one primary-column group");
                if(first<0)first=y;previous=y;
                for(int i=0;i<((IntegerValue)call(list,"size","()I")).value();i++) {
                    ObjectReference other=(ObjectReference)call(list,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    if(other.equals(button)||!((BooleanValue)other.getValue(fieldInfo(other.referenceType(),"visible"))).value())continue;
                    if(intField(button,"xPosition")<intField(other,"xPosition")+intField(other,"width")&&intField(button,"xPosition")+intField(button,"width")>intField(other,"xPosition")&&y<intField(other,"yPosition")+intField(other,"height")&&y+intField(button,"height")>intField(other,"yPosition"))throw new AssertionError("Navigation overlaps native control "+other.referenceType().name());
                }
            }
            for(int i=0;i<((IntegerValue)call(list,"size","()I")).value();i++) {
                ObjectReference other=(ObjectReference)call(list,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                if(!visible.contains(other)&&((BooleanValue)other.getValue(fieldInfo(other.referenceType(),"visible"))).value()&&intField(other,"xPosition")==primary&&intField(other,"yPosition")+intField(other,"height")>first)throw new AssertionError("Navigation is not below native controls");
            }
            System.out.println("PASS bottom four navigation widgets in the primary left column; native controls do not overlap");
        }
        System.out.println("PASS four visible alternate modes and three wireless controls; bounded, separate native hitboxes");
        if(requested!=null) {
            if(selected==null)throw new AssertionError("Missing requested mode "+requested);
            rawClick(host,intField(selected,"xPosition")+5,intField(selected,"yPosition")+5,0);
        }
    }
    static void panelModeCheck(String action, String expected) throws Exception {
        ObjectReference host=field(client,"currentScreen"),panel=floatingPanel(host);
        ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
        String fixture=System.getProperty("dinv.panelFixtureFile"),original=System.getProperty("dinv.panelOriginalFile");
        if(fixture==null||original==null)throw new IllegalArgumentException("Explicit isolated profiles required");
        ObjectReference key=(ObjectReference)call(panel,"key",null);
        if(action.equals("setup")||action.equals("restore")) {
            profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(action.equals("restore")?original:fixture))),ObjectReference.INVOKE_SINGLE_THREADED);
            if(action.equals("setup")) {
                profiles.invokeMethod(thread,profiles.methodsByName("resetGlobal").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                profiles.invokeMethod(thread,profiles.methodsByName("reset").getFirst(),Arrays.asList((Value)null),ObjectReference.INVOKE_SINGLE_THREADED);
            }
            call(panel,"reload","()V");
        } else if(action.equals("custom")) {
            ObjectReference profile=(ObjectReference)call(panel,"preference",null);
            profile=(ObjectReference)call(profile,"withBounds","(IIII)Ldev/gilbe/dynamicinventory/client/PlayerPanelProfile;",vm.mirrorOf(196),vm.mirrorOf(190),vm.mirrorOf(4000),vm.mirrorOf(5000));
            profiles.invokeMethod(thread,profiles.methodsByName("save").getFirst(),List.of(key,profile),ObjectReference.INVOKE_SINGLE_THREADED);
            call(panel,"reload","()V");
        }
        boolean mui=!methods(host.referenceType(),"getScreen",null).isEmpty();
        if(mui)muiFrame(host);else renderScreen(host,-1000,-1000);
        if(action.equals("menu")) {
            ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
            rawClick(host,intField(control,"x")+3,intField(control,"y")+3,1);
            ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
            ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
            if(view==null)throw new AssertionError("Mode menu did not open");
            rawGuiButton(view,1);rawGuiButton(view,50);call(host,"handleInput","()V");
            if(mui)muiFrame(host);else renderScreen(host,-1000,-1000);
        }
        if(action.equals("toggle")||action.equals("custom")) {
            boolean globalBefore=((BooleanValue)((ObjectReference)profiles.invokeMethod(thread,profiles.methodsByName("global").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED)).getValue(fieldInfo(type("dev.gilbe.dynamicinventory.client.PlayerPanelProfile"),"detached"))).value();
            ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
            rawClick(host,intField(control,"x")+3,intField(control,"y")+3,0);
            if(mui)muiFrame(host);else renderScreen(host,-1000,-1000);
            if(action.equals("custom")) {
                boolean after=((BooleanValue)((ObjectReference)profiles.invokeMethod(thread,profiles.methodsByName("global").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED)).getValue(fieldInfo(type("dev.gilbe.dynamicinventory.client.PlayerPanelProfile"),"detached"))).value();
                if(after!=globalBefore||!((BooleanValue)profiles.invokeMethod(thread,profiles.methodsByName("hasOverride").getFirst(),List.of(key),ObjectReference.INVOKE_SINGLE_THREADED)).value())throw new AssertionError("Custom mode leaked into global mode");
                System.out.println("PASS custom toggle preserves global mode and override");return;
            }
        }
        if(!action.equals("restore")) {
            ObjectReference global=(ObjectReference)profiles.invokeMethod(thread,profiles.methodsByName("global").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
            boolean wanted=Boolean.parseBoolean(expected);
            if(((BooleanValue)global.getValue(fieldInfo(global.referenceType(),"detached"))).value()!=wanted||((BooleanValue)call(panel,"detached",null)).value()!=wanted||!((BooleanValue)call(panel,"isInherited",null)).value())throw new AssertionError("Global mode did not propagate to inherited interface");
            if(((BooleanValue)profiles.invokeMethod(thread,profiles.methodsByName("hasOverride").getFirst(),List.of(key),ObjectReference.INVOKE_SINGLE_THREADED)).value())throw new AssertionError("Global toggle created interface override");
            profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(fixture))),ObjectReference.INVOKE_SINGLE_THREADED);
            global=(ObjectReference)profiles.invokeMethod(thread,profiles.methodsByName("global").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
            if(((BooleanValue)global.getValue(fieldInfo(global.referenceType(),"detached"))).value()!=wanted)throw new AssertionError("Global toggle not saved on disk");
            System.out.println("PASS persisted global mode="+wanted+" inherited="+host.referenceType().name());
        }
    }
    static void act(String[] args) throws Exception {
        ObjectReference player = field(client, "thePlayer");
        if (Boolean.getBoolean("dinv.packInput") && Set.of("click", "visible-click", "layout-control", "nei-fill-recipe").contains(args[0])) {
            ClassType events = type("com.cleanroommc.bogosorter.ClientEventHandler");
            events.invokeMethod(thread, events.methodsByName("handleInput").getFirst(),
                List.of(field(client, "currentScreen"), vm.mirrorOf(false)), ObjectReference.INVOKE_SINGLE_THREADED);
        }
        switch (args[0]) {
            case "panel-input-info": panelInputInfo(); break;
            case "terminal-recipe-info": terminalRecipeInfo(); break;
            case "panel-mode-check": panelModeCheck(args[1],args.length>2?args[2]:"false"); break;
            case "player-reopen-check": playerReopenCheck(args); break;
            case "mui-layout-info": muiLayoutInfo(); break;
            case "wireless-check": wirelessCheck(args.length>1?args[1]:null); break;
            case "mui-detached-check": muiDetachedCheck(); break;
            case "mui-toggle": {
                ObjectReference host=field(client,"currentScreen"), panel=floatingPanel(host);
                ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                rawClick(host,intField(control,"x")+3,intField(control,"y")+3,0);muiFrame(host);
                System.out.println("detached="+call(panel,"detached",null));break;
            }
            case "panel-menu-outside-check": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference host=field(client,"currentScreen"),container=field(player,"openContainer"),inventory=field(player,"inventory");
                ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
                if(overlay.getValue(overlay.fieldByName("active"))!=null)throw new IllegalStateException("Close existing draft first");
                String before=inventoryNbt(inventory);
                ObjectReference panel=floatingPanel(host);
                String profile=((StringReference)call((ObjectReference)call((ObjectReference)call(panel,"preference",null),"toJson",null),"toString","()Ljava/lang/String;")).value();
                for(int button:new int[]{0,1}) {
                    if(!((BooleanValue)overlay.invokeMethod(thread,overlay.methodsByName("open").getFirst(),List.of(host,panel),ObjectReference.INVOKE_SINGLE_THREADED)).value())throw new AssertionError("Menu did not open");
                    ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
                    call(field(view,"nameField"),"setText","(Ljava/lang/String;)V",vm.mirrorOf("Unsaved outside-click fixture"));
                    // Blank interior clicks preserve the draft; either outside mouse button cancels it.
                    rawClick(host,intField(view,"left")+3,intField(view,"top")+3,button);
                    if(overlay.getValue(overlay.fieldByName("active"))==null)throw new AssertionError("Inside click closed menu");
                    rawClick(host,2,2,button);
                    if(overlay.getValue(overlay.fieldByName("active"))!=null)throw new AssertionError("Outside click failed to close menu");
                    if(!field(client,"currentScreen").equals(host)||!field(player,"openContainer").equals(container)||call(inventory,"getItemStack","()Lnet/minecraft/item/ItemStack;")!=null||!before.equals(inventoryNbt(inventory)))throw new AssertionError("Closing clicked through into inventory");
                    if(!profile.equals(((StringReference)call((ObjectReference)call((ObjectReference)call(panel,"preference",null),"toJson",null),"toString","()Ljava/lang/String;")).value()))throw new AssertionError("Outside click saved draft");
                }
                System.out.println("PASS raw left/right outside clicks cancel draft, preserve inside clicks, host, container and inventory");
                return;
            }
            case "hotbar-seed": {
                if(!args[1].equals("DI-Baseline-Hotbar-20261027-Qualification"))throw new IllegalArgumentException("Dedicated hotbar fixture world required");
                act(new String[]{"require-qualification",args[1]});
                if(!thread.name().equals("Server thread")) {
                    ClassType handler=type("net.minecraft.network.NetHandlerPlayServer");
                    BreakpointRequest seed=vm.eventRequestManager().createBreakpointRequest(methods(handler,"processChatMessage","(Lnet/minecraft/network/play/client/C01PacketChatMessage;)V").getFirst().location());
                    seed.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);seed.enable();awaitingGraveSeed=true;
                    call(player,"sendChatMessage","(Ljava/lang/String;)V",vm.mirrorOf("/dinvbaseline"));break;
                }
                awaitingGraveSeed=false;
                ObjectReference actor=field(thread.frame(0).thisObject(),"playerEntity"),inventory=field(actor,"inventory");
                if(!field(actor,"openContainer").equals(field(actor,"inventoryContainer"))||call(inventory,"getItemStack","()Lnet/minecraft/item/ItemStack;")!=null)throw new IllegalStateException("Empty cursor and player container required");
                int total=ownedLayoutInt(inventory,"mainSlots");
                boolean banks=args[2].equals("banks")||args[2].equals("pickup");
                boolean sweep=args[2].equals("sweep");
                if(!banks&&!args[2].equals("transfer")&&!sweep)throw new IllegalArgumentException("banks, pickup, sweep or transfer");
                if(total<36)throw new IllegalStateException("Fixture requires at least 27 storage slots");
                ArrayReference main=(ArrayReference)field(inventory,"mainInventory");
                ClassType items=type("net.minecraft.init.Items");
                for(int logical=0;logical<total;logical++) {
                    ObjectReference stack=null;
                    if(banks || logical<9 || logical==9 || logical==18 || sweep&&logical<18) {
                        stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;I)V",items.getValue(fieldInfo(items,!banks&&logical<9 ? "stick" : "diamond")),vm.mirrorOf(banks ? 1 : logical<9 ? 64 : logical<18 ? 8 : 61));
                        ObjectReference tag=make("net.minecraft.nbt.NBTTagCompound","()V");
                        call(tag,"setInteger","(Ljava/lang/String;I)V",vm.mirrorOf("dinvHotbarFixture"),vm.mirrorOf(1));
                        if(banks)call(tag,"setInteger","(Ljava/lang/String;I)V",vm.mirrorOf("identity"),vm.mirrorOf(logical));
                        call(stack,"setTagCompound","(Lnet/minecraft/nbt/NBTTagCompound;)V",tag);
                    }
                    main.setValue(ownedRawIndex(inventory,logical),stack);
                }
                if(args[2].equals("pickup")) {
                    for(int logical=10;logical<27;logical++)main.setValue(ownedRawIndex(inventory,logical),null);
                    int empty=((IntegerValue)call(inventory,"getFirstEmptyStack","()I")).value();
                    if(empty>=9&&empty<27)throw new AssertionError("Pickup selected an empty reserved cell");
                    ObjectReference probe=(ObjectReference)call((ObjectReference)main.getValue(9),"copy","()Lnet/minecraft/item/ItemStack;");
                    if(((IntegerValue)call(inventory,"storeItemStack","(Lnet/minecraft/item/ItemStack;)I",probe)).value()!=-1)throw new AssertionError("Pickup selected matching reserved stack");
                    call(inventory,"addItemStackToInventory","(Lnet/minecraft/item/ItemStack;)Z",probe);
                    if(intField((ObjectReference)main.getValue(9),"stackSize")!=1)throw new AssertionError("Pickup changed reserved stack");
                    for(int logical=10;logical<27;logical++)if(main.getValue(ownedRawIndex(inventory,logical))!=null)throw new AssertionError("Pickup filled hidden hotbar");
                    System.out.println("PASS actual transformed pickup skips empty and matching reserved slots");
                }
                call(inventory,"markDirty","()V");call(field(actor,"inventoryContainer"),"detectAndSendChanges","()V");
                System.out.println("PASS authoritative fixture seed "+args[2]);return;
            }
            case "hotbar-configure":
            case "hotbar-swap": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ClassType controls=type("dev.gilbe.dynamicinventory.client.HotbarControls");
                controls.invokeMethod(thread,controls.methodsByName(args[0].equals("hotbar-configure")?"configure":"swap").getFirst(),List.of(vm.mirrorOf(Integer.parseInt(args[1]))),ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "hotbar-key": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                if(field(client,"currentScreen")!=null)throw new IllegalStateException("Close GUI for gameplay hotkeys");
                ClassType controls=type("dev.gilbe.dynamicinventory.client.HotbarControls");
                ObjectReference binding=args[1].equals("cycle")?(ObjectReference)controls.getValue(controls.fieldByName("CYCLE")):(ObjectReference)((ArrayReference)controls.getValue(controls.fieldByName("ROWS"))).getValue(Integer.parseInt(args[1]));
                binding.setValue(fieldInfo(binding.referenceType(),"pressTime"),vm.mirrorOf(1));
                call(make("dev.gilbe.dynamicinventory.client.HotbarControls","()V"),"keys",null,make("cpw.mods.fml.common.gameevent.InputEvent$KeyInputEvent","()V"));
                break;
            }
            case "hotbar-cell": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                for(ObjectReference actor:new ObjectReference[]{player,serverPlayer(player)}) {
                    ObjectReference inventory=field(actor,"inventory");
                    ObjectReference stack=(ObjectReference)((ArrayReference)field(inventory,"mainInventory")).getValue(ownedRawIndex(inventory,Integer.parseInt(args[1])));
                    if(stack==null||((IntegerValue)call((ObjectReference)call(stack,"getTagCompound","()Lnet/minecraft/nbt/NBTTagCompound;"),"getInteger","(Ljava/lang/String;)I",vm.mirrorOf("identity"))).value()!=Integer.parseInt(args[2]))throw new AssertionError("Unexpected cell identity");
                }
                System.out.println("PASS client/server cell "+args[1]+" = "+args[2]);return;
            }
            case "hotbar-editor-check": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ObjectReference gui=field(client,"currentScreen");
                ClassType editors=type("dev.gilbe.dynamicinventory.client.HotbarBankPanel");
                ObjectReference editor=(ObjectReference)call((ObjectReference)editors.getValue(editors.fieldByName("PANELS")),"get","(Ljava/lang/Object;)Ljava/lang/Object;",gui);
                if(editor==null||field(editor,"bounds")==null)throw new AssertionError("Editor did not fit");
                ObjectReference bounds=field(editor,"bounds");
                ObjectReference occupied=(ObjectReference)call(editor,"obstacles",null,gui);
                for(int i=0;i<((IntegerValue)call(occupied,"size","()I")).value();i++)if(((BooleanValue)call(bounds,"intersects",null,call(occupied,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)))).value())throw new AssertionError("Editor overlaps host/panel");
                int left=intField(gui,"guiLeft"),top=intField(gui,"guiTop"),visible=0;
                ObjectReference inventory=field(player,"inventory"),container=field(player,"openContainer");
                ClassType banks=type("dev.gilbe.dynamicinventory.inventory.HotbarBanks");
                ObjectReference slots=field(container,"inventorySlots");
                for(int i=0;i<((IntegerValue)call(slots,"size","()I")).value();i++) {
                    ObjectReference slot=(ObjectReference)call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    if(!field(slot,"inventory").equals(inventory)||!((BooleanValue)banks.invokeMethod(thread,banks.methodsByName("isReserved").getFirst(),List.of(inventory,call(slot,"getSlotIndex","()I")),ObjectReference.INVOKE_SINGLE_THREADED)).value())continue;
                    int x=intField(slot,"xDisplayPosition"),y=intField(slot,"yDisplayPosition");
                    if(x==-10000||y==-10000)continue;
                    if(!((BooleanValue)call(bounds,"contains","(II)Z",vm.mirrorOf(x+left),vm.mirrorOf(y+top))).value()||!((BooleanValue)call(bounds,"contains","(II)Z",vm.mirrorOf(x+left+16),vm.mirrorOf(y+top+16))).value())throw new AssertionError("Bank cell clipped");
                    visible++;
                }
                if(visible!=intField(editor,"rows")*9)throw new AssertionError("Missing visible bank cells");
                System.out.println("PASS fitted editor columns="+intField(editor,"columns")+" visible cells="+visible);return;
            }
            case "hotbar-state": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ObjectReference inventory=field(player,"inventory"),remote=field(serverPlayer(player),"inventory");
                String snapshot=inventoryNbt(inventory);
                if(!snapshot.equals(inventoryNbt(remote)))throw new AssertionError("Client/server inventory differs");
                ClassType banks=type("dev.gilbe.dynamicinventory.inventory.HotbarBanks");
                for(ObjectReference actor:new ObjectReference[]{player,serverPlayer(player)}) {
                    int count=((IntegerValue)banks.invokeMethod(thread,banks.methodsByName("count").getFirst(),List.of(actor),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                    if(count!=Integer.parseInt(args[1]))throw new AssertionError("Unexpected reservation "+count);
                }
                ArrayReference main=(ArrayReference)field(inventory,"mainInventory");
                if(args[2].equals("sweep")) {
                    for(int logical=9;logical<18;logical++)if(main.getValue(ownedRawIndex(inventory,logical))!=null)throw new AssertionError("Sweep refilled a subpanel cell");
                    int[] expected={64,64,5};
                    for(int i=0;i<3;i++)if(intField((ObjectReference)main.getValue(ownedRawIndex(inventory,18+i)),"stackSize")!=expected[i])throw new AssertionError("Incorrect Main sweep totals");
                } else if(args[2].equals("transfer")) {
                    if(main.getValue(ownedRawIndex(inventory,9))!=null || intField((ObjectReference)main.getValue(ownedRawIndex(inventory,18)),"stackSize")!=64 || intField((ObjectReference)main.getValue(ownedRawIndex(inventory,19)),"stackSize")!=5)throw new AssertionError("Panel-to-Main fallback failed");
                } else {
                    boolean mapped=args[2].startsWith("bank:");
                    int expected=mapped?Integer.parseInt(args[2].substring(5))*9:Integer.parseInt(args[2]);
                    ArrayReference reserved=mapped?(ArrayReference)banks.invokeMethod(thread,banks.methodsByName("slots").getFirst(),List.of(player),ObjectReference.INVOKE_SINGLE_THREADED):null;
                    Set<Integer> identities=new HashSet<>();
                    java.util.regex.Matcher ids=java.util.regex.Pattern.compile("\"identity\":\\{\"type\":3,\"value\":\"([0-9]+)\"\\}").matcher(snapshot);
                    while(ids.find())if(!identities.add(Integer.parseInt(ids.group(1))))throw new AssertionError("Duplicate identity");
                    int total=ownedLayoutInt(inventory,"mainSlots");
                    if(identities.size()!=total)throw new AssertionError("Lost fixture identities: "+identities.size()+"/"+total);
                    for(int logical=0;logical<total;logical++)if(!identities.contains(logical))throw new AssertionError("Missing identity "+logical);
                    for(int logical=0;logical<9;logical++) {
                        ObjectReference stack=(ObjectReference)main.getValue(logical);
                        if(stack==null||intField(stack,"stackSize")!=1)throw new AssertionError("Lost fixture stack");
                        int identity=((IntegerValue)call((ObjectReference)call(stack,"getTagCompound","()Lnet/minecraft/nbt/NBTTagCompound;"),"getInteger","(Ljava/lang/String;)I",vm.mirrorOf("identity"))).value();
                        int wanted=mapped?9+((IntegerValue)reserved.getValue(expected+logical)).value():expected+logical;
                        if(identity!=wanted)throw new AssertionError("Incorrect hotbar: "+logical+" -> "+identity+" wanted="+wanted);
                    }
                }
                System.out.println("PASS synchronized hotbar state "+args[1]+" "+args[2]);return;
            }
            case "panel-ui-shift": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ObjectReference host=field(client,"currentScreen"),container=field(player,"openContainer");
                int id=Integer.parseInt(args[1]);
                call(host,"handleMouseClick","(Lnet/minecraft/inventory/Slot;III)V",call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(id)),vm.mirrorOf(id),vm.mirrorOf(0),vm.mirrorOf(1));
                break;
            }
            case "panel-mouse-tweaks-shift": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ObjectReference host=field(client,"currentScreen"),container=field(player,"openContainer");
                ObjectReference remote=field(serverPlayer(player),"openContainer");
                if(host==null || !Set.of("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen","net.minecraft.client.gui.inventory.GuiChest").contains(host.referenceType().name())
                    || !container.referenceType().name().equals(remote.referenceType().name()) || intField(container,"windowId")!=intField(remote,"windowId"))throw new AssertionError("Server has not opened the same test container");
                ObjectReference context=make("yalter.mousetweaks.handlers.ContainerContext","()V");
                if(!((BooleanValue)call(context,"refresh","(Lnet/minecraft/client/gui/GuiScreen;Z)Z",host,vm.mirrorOf(false))).value())throw new AssertionError("Mouse Tweaks refused fixture screen");
                for(int i=1;i<args.length;i++)call(context,"clickSlot","(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/inventory/Slot;IZ)V",host,call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(Integer.parseInt(args[i]))),vm.mirrorOf(0),vm.mirrorOf(true));
                break;
            }
            case "panel-reserve-check": {
                act(new String[]{"require-qualification","DI-Baseline-Hotbar-20261027-Qualification"});
                ObjectReference panel=floatingPanel(field(client,"currentScreen")),groups=field(panel,"panels");
                for(int i=0;i<((IntegerValue)call(groups,"size","()I")).value();i++) {
                    ObjectReference group=(ObjectReference)call(groups,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    int size=((IntegerValue)call(field(group,"members"),"size","()I")).value();
                    if(size!=Integer.parseInt(args[i+1]))throw new AssertionError("Panel "+i+" has "+size+" cells");
                }
                System.out.println("PASS Main-only reservation panel sizes="+String.join(",",Arrays.copyOfRange(args,1,args.length)));return;
            }
            case "world-status": {
                ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;");
                System.out.println("World="+(server==null ? "none" : call(server,"getFolderName","()Ljava/lang/String;")));
                break;
            }
            case "panel-menu-control": {
                ClassType overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
                ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
                if(view==null) throw new IllegalStateException("Open panel settings first");
                pressGuiButton(view,Integer.parseInt(args[1]));
                overlay.invokeMethod(thread,overlay.methodsByName("handleInput").getFirst(),List.of(field(client,"currentScreen")),ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "attached-frame-info": {
                ObjectReference host=field(client,"currentScreen");
                ObjectReference panel=(ObjectReference)call(host,"dynamicinventory$panel","()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                ArrayReference bounds=(ArrayReference)call(panel,"editorSectionBounds","(I)[I",vm.mirrorOf(1));
                System.out.println("Attached player bounds="+bounds.getValues()+" columns="+call(panel,"visibleColumns","()I")+" rows="+call(panel,"visibleRows","()I")+" maxScroll="+call(panel,"maxScroll","()I"));
                if(args.length>1 && ((IntegerValue)bounds.getValue(2)).value()!=Integer.parseInt(args[1]))throw new AssertionError("Unexpected attached player width");
                break;
            }
            case "storage-frame-check": {
                ObjectReference host=field(client,"currentScreen"), panel=(ObjectReference)call(host,"dynamicinventory$panel",null);
                ObjectReference geometry=field(panel,"storageGeometry"), floating=floatingPanel(host);
                if(geometry==null)throw new AssertionError("Reviewed storage geometry required");
                call(panel,"position",null);
                ObjectReference frame=(ObjectReference)call(floating,"machineBounds",null);
                ObjectReference slots=field(panel,"machineSlots");
                int size=((IntegerValue)call(slots,"size","()I")).value();
                int minX=Integer.MAX_VALUE,maxX=0,maxY=0;
                for(int i=0;i<size;i++) {
                    ObjectReference slot=(ObjectReference)call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                    int x=intField(host,"guiLeft")+intField(slot,"xDisplayPosition"), y=intField(host,"guiTop")+intField(slot,"yDisplayPosition");
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x+16);maxY=Math.max(maxY,y+16);
                }
                if(host.referenceType().name().equals("cpw.mods.ironchest.client.GUIChest") && size>1) {
                    if(minX-intField(frame,"x")!=8 || intField(frame,"x")+intField(frame,"width")-maxX!=8
                        || intField(frame,"y")+intField(frame,"height")-maxY!=8)
                        throw new AssertionError("Unequal actual Iron Chest slot padding");
                }
                System.out.println("PASS storage frame "+host.referenceType().name()+" detached="+call(floating,"detached",null)
                    +" nativeSlots="+size+" frame="+intField(frame,"width")+"x"+intField(frame,"height")
                    +" cropBottom="+intField(geometry,"cropBottom")+" fill="+geometry.getValue(geometry.referenceType().fieldByName("fillBackground")));
                break;
            }
            case "panel-preview": {
                act(new String[]{"require-qualification", args[1]});
                ClassType profiles = type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
                String location = args[2].equals("restore") ? System.getProperty("dinv.panelOriginalFile") : System.getProperty("dinv.panelFixtureFile");
                if(location==null) throw new IllegalArgumentException("Explicit profile path required");
                profiles.invokeMethod(thread, profiles.methodsByName("initialize").getFirst(), List.of(make("java.io.File", "(Ljava/lang/String;)V", vm.mirrorOf(location))), ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference screen=field(client,"currentScreen");
                rebuildScreen(screen);
                ObjectReference panel=floatingPanel(screen), bounds=(ObjectReference)call(panel,"playerBounds",null);
                System.out.println("detached="+call(panel,"detached",null)+" bounds="+intField(bounds,"x")+","+intField(bounds,"y")+","+intField(bounds,"width")+","+intField(bounds,"height"));
                break;
            }
            case "panel-baubles": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference host=field(client,"currentScreen"), container=field(player,"openContainer");
                if(!host.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen")) throw new AssertionError("Player screen required");
                for(int toggle=0;toggle<2;toggle++) {
                    int beforeLeft=intField(host,"guiLeft"),beforeTop=intField(host,"guiTop"),beforeWidth=intField(field(host,"view"),"width");
                    ObjectReference button=field(host,"baublesButton");
                    int x=intField(button,"xPosition")+5,y=intField(button,"yPosition")+5;
                    call(host,"mouseClicked","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
                    call(host,"mouseMovedOrUp","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
                    ObjectReference panel=floatingPanel(host);
                    if(intField(host,"guiLeft")!=beforeLeft||intField(host,"guiTop")!=beforeTop||intField(field(host,"view"),"width")!=beforeWidth)throw new AssertionError("Baubles recentered or resized player inventory");
                    if(panel==null || !((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Baubles toggle lost detached panels");
                    ObjectReference panes=(ObjectReference)call(panel,"bounds",null), fixed=field(panel,"fixed");
                    for(int i=0;i<((IntegerValue)call(panes,"size","()I")).value();i++) {
                        ObjectReference rect=(ObjectReference)call(panes,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        for(int j=0;j<((IntegerValue)call(fixed,"size","()I")).value();j++)
                            if(((BooleanValue)call(rect,"intersects",null,(ObjectReference)call(fixed,"get","(I)Ljava/lang/Object;",vm.mirrorOf(j)))).value()) throw new AssertionError("Panel overlaps static equipment");
                    }
                    if(!field(player,"openContainer").equals(container)) throw new AssertionError("Baubles toggle replaced container");
                }
                ObjectReference settings=field(host,"layoutButton");
                int x=intField(settings,"xPosition")+5,y=intField(settings,"yPosition")+5;
                call(host,"mouseClicked","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
                call(host,"mouseMovedOrUp","(III)V",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(0));
                if(!((BooleanValue)host.getValue(fieldInfo(host.referenceType(),"settingsOpen"))).value()) throw new AssertionError("Player settings failed to open");
                pressGuiButton(host,108);
                if(!((BooleanValue)call(floatingPanel(host),"detached",null)).value()) throw new AssertionError("Player settings lost saved split layout");
                System.out.println("PASS split player panels survive Baubles open/close and player settings without overlapping equipment or replacing the container"); return;
            }
            case "panel-clearance-check": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference host=field(client,"currentScreen");
                int original=intField(host,"guiLeft");
                try {
                    host.setValue(fieldInfo(host.referenceType(),"guiLeft"),vm.mirrorOf(original+6));
                    call(host,"positionSlots",null);
                    ObjectReference panel=floatingPanel(host),machine=(ObjectReference)call(panel,"machineBounds",null);
                    if(intField(machine,"x")!=original+6)throw new AssertionError("Cached host obstacle did not follow actual header");
                    ObjectReference bounds=(ObjectReference)call(panel,"bounds",null),fixed=field(panel,"fixed");
                    if(((IntegerValue)call(bounds,"size","()I")).value()!=3)throw new AssertionError("Crowded fixture unexpectedly fell back");
                    for(int i=0;i<3;i++) {
                        ObjectReference rect=(ObjectReference)call(bounds,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        for(int j=0;j<((IntegerValue)call(fixed,"size","()I")).value();j++)if(((BooleanValue)call(rect,"intersects",null,call(fixed,"get","(I)Ljava/lang/Object;",vm.mirrorOf(j)))).value())throw new AssertionError("Live header clips a panel");
                    }
                } finally { host.setValue(fieldInfo(host.referenceType(),"guiLeft"),vm.mirrorOf(original));rebuildScreen(host); }
                System.out.println("PASS live header movement refits all panels before slot drawing");return;
            }
            case "native-layout-path": {
                act(new String[]{"require-qualification",args[1]});
                String path=args[2].equals("restore") ? null : System.getProperty("dinv.nativeLayouts");
                ObjectReference file;
                if(path==null) file=make("java.io.File","(Ljava/io/File;Ljava/lang/String;)V",field(client,"mcDataDir"),vm.mirrorOf("config/dynamicinventory/ui-layouts"));
                else {
                    if(!path.replace('\\','/').endsWith("/.setup/panels-belts-suite/core/native-layouts")) throw new IllegalArgumentException("Isolated native layout directory required");
                    file=make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(path));
                }
                ClassType layouts=type("dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches");
                layouts.invokeMethod(thread,layouts.methodsByName("initialize").getFirst(),List.of(file),ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("PASS native layout scope "+args[2]); return;
            }
            case "nei-status": {
                ClassType config=type("codechicken.nei.NEIClientConfig");
                for(String method:new String[]{"isHidden","isBookmarkPanelHidden"}) System.out.println(method+"="+config.invokeMethod(thread,config.methodsByName(method).getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED));
                ObjectReference host=field(client,"currentScreen");
                System.out.println("guiLeft="+intField(host,"guiLeft")+" width="+intField(host,"width")+" settingsOpen="+host.getValue(fieldInfo(host.referenceType(),"settingsOpen")));
                return;
            }
            case "panel-bookmarks": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference host=field(client,"currentScreen"), panel=floatingPanel(host);
                ObjectReference rect=(ObjectReference)call(panel,"playerBounds",null);
                int x=intField(rect,"x")+25,y=intField(rect,"y")+7;
                panelPointer(panel,x,y,0,true); panelPointer(panel,x-1000,y,-1,false); panelPointer(panel,x-1000,y,0,false);
                ClassType items=type("codechicken.nei.ItemPanels"), layouts=type("codechicken.nei.LayoutManager");
                ObjectReference bookmarks=(ObjectReference)items.getValue(items.fieldByName("bookmarkPanel"));
                ObjectReference diamond=(ObjectReference)type("net.minecraft.init.Items").getValue(fieldInfo(type("net.minecraft.init.Items"),"diamond"));
                ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;I)V",diamond,vm.mirrorOf(1));
                boolean added=((BooleanValue)call(bookmarks,"addItem","(Lnet/minecraft/item/ItemStack;)Z",stack)).value();
                try {
                    layouts.invokeMethod(thread,methods(layouts,"layout","(Lnet/minecraft/client/gui/inventory/GuiContainer;)V").getFirst(),List.of(host),ObjectReference.INVOKE_SINGLE_THREADED);
                    ObjectReference bounds=(ObjectReference)call(bookmarks,"calculateBounds",null,host);
                    ObjectReference areas=(ObjectReference)call(panel,"bounds",null);
                    for(int i=0;i<((IntegerValue)call(areas,"size","()I")).value();i++) {
                        ObjectReference area=(ObjectReference)call(areas,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        int ax=intField(area,"x"),ay=intField(area,"y"),bx=intField(bounds,"x"),by=intField(bounds,"y");
                        if(ax<bx+intField(bounds,"w") && ax+intField(area,"width")>bx && ay<by+intField(bounds,"h") && ay+intField(area,"height")>by)
                            throw new AssertionError("Bookmark region still overlaps a floating panel");
                    }
                    if(((IntegerValue)call(field(bookmarks,"grid"),"getPerPage",null)).value()<1) throw new AssertionError("Bookmark capacity disappeared");
                    ObjectReference widgets=(ObjectReference)layouts.getValue(layouts.fieldByName("drawWidgets"));
                    // NEI's Z-order comparator returns nonzero even for the same Widget, so TreeSet.contains is invalid here.
                    ObjectReference iterator=(ObjectReference)call(widgets,"iterator","()Ljava/util/Iterator;");
                    boolean visible=false;
                    while(((BooleanValue)call(iterator,"hasNext","()Z")).value())
                        visible |= bookmarks.equals(call(iterator,"next","()Ljava/lang/Object;"));
                    if(!visible) throw new AssertionError("Bookmark widget hidden");
                    System.out.println("PASS bookmark panel remains visible with native grid capacity at "+intField(bounds,"x")+","+intField(bounds,"y")+","+intField(bounds,"w")+","+intField(bounds,"h"));
                } finally { if(added) call(bookmarks,"removeItem",null,stack,null,vm.mirrorOf(0)); }
                return;
            }
            case "panel-transfer-info": {
                ObjectReference screen=field(client,"currentScreen"), container=field(player,"openContainer");
                System.out.println("container="+container.referenceType().name());
                ObjectReference panel=floatingPanel(screen);
                if(panel!=null){ObjectReference groups=field(panel,"panels");int count=((IntegerValue)call(groups,"size","()I")).value();
                    for(int i=0;i<count;i++){ObjectReference group=(ObjectReference)call(groups,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        System.out.println("panel="+((StringReference)group.getValue(group.referenceType().fieldByName("id"))).value()+" members="+call(field(group,"members"),"toString","()Ljava/lang/String;"));}}
                for(int i=0;i<((IntegerValue)call(field(container,"inventorySlots"),"size","()I")).value();i++){
                    ObjectReference slot=(ObjectReference)call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(i));
                    if(!field(slot,"inventory").equals(field(player,"inventory")))continue;
                    ObjectReference stack=(ObjectReference)call(slot,"getStack","()Lnet/minecraft/item/ItemStack;");
                    System.out.println("slot="+i+" raw="+call(slot,"getSlotIndex","()I")+" count="+(stack==null?0:intField(stack,"stackSize")));}
                break;
            }
            case "belt-registry-check": {
                act(new String[]{"require-qualification",args[1]});
                ClassType registry=type("cpw.mods.fml.common.registry.GameRegistry");
                ObjectReference legacy=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findItem").getFirst(),
                    List.of(vm.mirrorOf("dynamicinventory"),vm.mirrorOf("creative_inventory_controller")),ObjectReference.INVOKE_SINGLE_THREADED);
                ClassType upgrades=type("dev.gilbe.dynamicinventory.upgrades.InventoryUpgrades");
                ObjectReference belt=(ObjectReference)upgrades.invokeMethod(thread,upgrades.methodsByName("creativeBelt").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                if(legacy==null||belt==null||legacy.equals(belt))throw new AssertionError("Saved registry identities were merged or removed");
                ClassType item=type("net.minecraft.item.Item");
                Method id=methods(item,"getIdFromItem","(Lnet/minecraft/item/Item;)I").getFirst();
                int legacyId=((IntegerValue)item.invokeMethod(thread,id,List.of(legacy),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                int beltId=((IntegerValue)item.invokeMethod(thread,id,List.of(belt),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                if(legacyId<0||beltId<0||legacyId==beltId)throw new AssertionError("Invalid saved item IDs");
                ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;II)V",legacy,vm.mirrorOf(1),vm.mirrorOf(7));
                ClassType info=type("codechicken.nei.api.ItemInfo");
                boolean hidden=((BooleanValue)info.invokeMethod(thread,info.methodsByName("isHidden","(Lnet/minecraft/item/ItemStack;)Z").getFirst(),List.of(stack),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                if(!hidden)throw new AssertionError("Legacy compatibility entry is visible in NEI");
                ObjectReference tag=make("net.minecraft.nbt.NBTTagCompound","()V");
                call(tag,"setString","(Ljava/lang/String;Ljava/lang/String;)V",vm.mirrorOf("RegistryFixture"),vm.mirrorOf("preserved"));
                call(stack,"setTagCompound","(Lnet/minecraft/nbt/NBTTagCompound;)V",tag);
                call(legacy,"onUpdate","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;IZ)V",
                    stack,field(client,"theWorld"),player,vm.mirrorOf(0),vm.mirrorOf(false));
                if(!call(stack,"getItem","()Lnet/minecraft/item/Item;").equals(belt)||intField(stack,"stackSize")!=1
                    ||((IntegerValue)call(stack,"getItemDamage","()I")).value()!=7||!call(stack,"getTagCompound","()Lnet/minecraft/nbt/NBTTagCompound;").equals(tag))
                    throw new AssertionError("Legacy conversion did not preserve its item contents");
                System.out.println("PASS distinct saved registry IDs, hidden legacy NEI entry, and stack/NBT-preserving Creative Belt conversion");return;
            }
            case "upgrade-recipe-check": {
                act(new String[]{"require-qualification",args[1]});
                String helper=System.getProperty("dinv.helperClasses");
                if(helper==null || !helper.replace('\\','/').endsWith("/.setup/panel-runtime-helper")) throw new IllegalArgumentException("Explicit fixture helper path required");
                ObjectReference url=(ObjectReference)call((ObjectReference)call(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(helper)),"toURI",null),"toURL",null);
                call(loader,"addURL","(Ljava/net/URL;)V",url);
                ClassType audit=type("dev.gilbe.dynamicinventory.PanelRecipeAudit");
                System.out.println(((StringReference)audit.invokeMethod(thread,audit.methodsByName("run").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED)).value()); return;
            }
            case "controller-resume": {
                ObjectReference screen=field(client,"currentScreen");
                if(screen==null || !screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.InventoryControllerScreen")) throw new IllegalStateException("Open the inventory controller first");
                pressGuiButton(screen,2); System.out.println("PASS controller UI resumed belt bonuses"); return;
            }
            case "controller-control": {
                ObjectReference screen = field(client,"currentScreen");
                if (!screen.referenceType().name().equals("dev.gilbe.dynamicinventory.client.InventoryControllerScreen"))
                    throw new AssertionError("Creative Belt size editor did not open");
                call(field(screen,"size"),"setText","(Ljava/lang/String;)V",vm.mirrorOf(args[1]));
                pressGuiButton(screen,1); System.out.println("PASS controller UI submitted target "+args[1]); return;
            }
            case "controller-request": {
                ClassType request=type("dev.gilbe.dynamicinventory.upgrades.CreativeSizeRequest");
                request.invokeMethod(thread,request.methodsByName("send").getFirst(),List.of(vm.mirrorOf(Integer.parseInt(args[1]))),ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("Queued controller request "+args[1]); return;
            }
            case "panel-multi": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference host=field(client,"currentScreen"), container=field(player,"openContainer");
                ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles"), overlay=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
                ObjectReference original=(ObjectReference)call((ObjectReference)profiles.getValue(profiles.fieldByName("file")),"toFile",null);
                String fixture=System.getProperty("dinv.panelFixtureFile");
                if(fixture==null || !fixture.endsWith("panel-client-fixture.json")) throw new IllegalArgumentException("Isolated file required");
                List<ObjectReference> identities=new ArrayList<>(); ObjectReference slots=field(container,"inventorySlots");
                int count=((IntegerValue)call(slots,"size","()I")).value();
                for(int i=0;i<count;i++) identities.add((ObjectReference)call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)));
                try {
                    profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(fixture))),ObjectReference.INVOKE_SINGLE_THREADED);
                    // Start from a truly empty panel collection on every fixture invocation.
                    ClassType sets=type("dev.gilbe.dynamicinventory.client.PlayerPanelSet");
                    profiles.invokeMethod(thread,profiles.methodsByName("applyPanels").getFirst(),Arrays.asList(sets.getValue(sets.fieldByName("DEFAULT")),(Value)null,type("dev.gilbe.dynamicinventory.client.PlayerPanelProfile").getValue(type("dev.gilbe.dynamicinventory.client.PlayerPanelProfile").fieldByName("DEFAULT"))),ObjectReference.INVOKE_SINGLE_THREADED);
                    profiles.invokeMethod(thread,profiles.methodsByName("reset").getFirst(),Arrays.asList((Value)null),ObjectReference.INVOKE_SINGLE_THREADED);
                    rebuildScreen(host); ObjectReference panel=floatingPanel(host);
                    overlay.invokeMethod(thread,overlay.methodsByName("open").getFirst(),List.of(host,panel),ObjectReference.INVOKE_SINGLE_THREADED);
                    ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
                    pressGuiButton(view,60);
                    if(((IntegerValue)call(field(field(view,"panelDraft"),"entries"),"size","()I")).value()!=2)
                        throw new AssertionError("One Add click must create exactly one side panel");
                    call(field(view,"nameField"),"setText","(Ljava/lang/String;)V",vm.mirrorOf("Tools"));
                    panelMenuField(view,"slotFields",((StringReference)field(view,"selected")).value(),"9");
                    pressGuiButton(view,50);
                    overlay.invokeMethod(thread,overlay.methodsByName("handleInput").getFirst(),List.of(host),ObjectReference.INVOKE_SINGLE_THREADED);
                    if(overlay.getValue(overlay.fieldByName("active"))!=null) throw new AssertionError("Panel apply failed");
                    if(!((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Multi-panel layout did not activate");
                    call(panel,"position",null);
                    ObjectReference bounds=(ObjectReference)call(panel,"bounds",null);
                    if(((IntegerValue)call(bounds,"size","()I")).value()!=2) throw new AssertionError("Missing satellite");
                    checkPlayerPanelRoundTrip(host,panel);
                    ObjectReference states=field(panel,"panels");
                    Set<Integer> visited=new HashSet<>();
                    for(int i=0;i<2;i++) {
                        ObjectReference state=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i));
                        ObjectReference members=field(state,"members");
                        for(int j=0;j<((IntegerValue)call(members,"size","()I")).value();j++) {
                            ObjectReference index=(ObjectReference)call(members,"get","(I)Ljava/lang/Object;",vm.mirrorOf(j));
                            if(!visited.add(((IntegerValue)call(index,"intValue","()I")).value())) throw new AssertionError("Duplicate membership");
                        }
                    }
                    if(visited.size()!=((IntegerValue)call(panel,"capacity",null)).value()) throw new AssertionError("Missing active members");
                    // Negative local coordinates are valid when a panel moves left of the host.
                    ObjectReference satellite=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(1));
                    ObjectReference frame=field(field(satellite,"layout"),"bounds");
                    int x=intField(frame,"x")+25,y=intField(frame,"y")+7;
                    panelPointer(panel,x,y,0,true); panelPointer(panel,x-20,y,-1,false);
                    if(!((BooleanValue)call(panel,"cancel",null)).value()) throw new AssertionError("Satellite cancel failed");
                    ObjectReference restored=field(field(satellite,"layout"),"bounds");
                    if(intField(restored,"x")!=intField(frame,"x") || intField(restored,"y")!=intField(frame,"y")
                        || intField(restored,"width")!=intField(frame,"width") || intField(restored,"height")!=intField(frame,"height")) throw new AssertionError("Canceled satellite drag changed effective bounds");
                    panelPointer(panel,x-20,y,0,false);
                    panelPointer(panel,x,y,0,true); panelPointer(panel,x-20,y,-1,false); panelPointer(panel,x-20,y,0,false);
                    if(!field(player,"openContainer").equals(container)) throw new AssertionError("Panel actions replaced container");
                    for(int i=0;i<count;i++) if(!identities.get(i).equals(call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)))) throw new AssertionError("Panel actions replaced Slot "+i);
                    call(panel,"reload",null); call(panel,"position",null);
                    if(((IntegerValue)call((ObjectReference)call(panel,"bounds",null),"size","()I")).value()!=2) throw new AssertionError("Panel reload lost allocation");
                    System.out.println("PASS named multi-panel menu, exact allocation, drag, persistence and live Slot/container identity on "+host.referenceType().name());
                } finally {
                    ObjectReference view=(ObjectReference)overlay.getValue(overlay.fieldByName("active"));
                    if(view!=null) { pressGuiButton(view,0); overlay.invokeMethod(thread,overlay.methodsByName("handleInput").getFirst(),List.of(host),ObjectReference.INVOKE_SINGLE_THREADED); }
                    profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(original),ObjectReference.INVOKE_SINGLE_THREADED); rebuildScreen(host);
                }
                return;
            }
            case "panel-fixtures": {
                act(new String[]{"require-qualification", args[1]});
                ObjectReference screen = field(client, "currentScreen"), container = field(player, "openContainer");
                if (screen == null || !screen.referenceType().name().equals("net.minecraft.client.gui.inventory.GuiChest"))
                    throw new IllegalStateException("Open a single vanilla chest first");
                ClassType profiles = type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
                ObjectReference originalFile = (ObjectReference) call((ObjectReference) profiles.getValue(profiles.fieldByName("file")), "toFile", null);
                String fixtureFile = System.getProperty("dinv.panelFixtureFile");
                if (fixtureFile == null || !fixtureFile.endsWith("panel-client-fixture.json")) throw new IllegalArgumentException("Isolated profile file required");
                ObjectReference slots = field(container, "inventorySlots");
                List<ObjectReference> identities = new ArrayList<>();
                int count = ((IntegerValue) call(slots, "size", "()I")).value(), window = intField(container, "windowId");
                for (int i=0;i<count;i++) identities.add((ObjectReference) call(slots, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i)));
                try {
                    profiles.invokeMethod(thread, profiles.methodsByName("initialize").getFirst(), List.of(make("java.io.File", "(Ljava/lang/String;)V", vm.mirrorOf(fixtureFile))), ObjectReference.INVOKE_SINGLE_THREADED);
                    profiles.invokeMethod(thread, profiles.methodsByName("resetGlobal").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                    profiles.invokeMethod(thread, profiles.methodsByName("reset").getFirst(), Arrays.asList((Value)null), ObjectReference.INVOKE_SINGLE_THREADED);
                    ClassType panelSets=type("dev.gilbe.dynamicinventory.client.PlayerPanelSet");
                    ClassType panelProfile=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfile");
                    profiles.invokeMethod(thread,profiles.methodsByName("applyPanels").getFirst(),Arrays.asList(panelSets.getValue(panelSets.fieldByName("DEFAULT")),(Value)null,panelProfile.getValue(panelProfile.fieldByName("DEFAULT"))),ObjectReference.INVOKE_SINGLE_THREADED);
                    rebuildScreen(screen);
                    ObjectReference panel = floatingPanel(screen);
                    if (panel == null || ((BooleanValue) call(panel,"detached",null)).value()) throw new AssertionError("Attached default");
                    ObjectReference bounds = (ObjectReference) call(panel,"playerBounds",null);
                    int ix=intField(bounds,"x")+16, iy=intField(bounds,"y")+6;
                    // Exercise the actual native press/release hooks, not only the controller API.
                    call(screen,"mouseClicked","(III)V",vm.mirrorOf(ix),vm.mirrorOf(iy),vm.mirrorOf(0));
                    call(screen,"mouseMovedOrUp","(III)V",vm.mirrorOf(ix),vm.mirrorOf(iy),vm.mirrorOf(0));
                    if (!((BooleanValue) call(panel,"detached",null)).value()) throw new AssertionError("Detach icon");
                    ObjectReference originalBounds = (ObjectReference) call(panel,"playerBounds",null);
                    int bx=intField(originalBounds,"x"), by=intField(originalBounds,"y"), bw=intField(originalBounds,"width"), bh=intField(originalBounds,"height");
                    panelPointer(panel,bx+25,by+7,0,true);
                    panelPointer(panel,bx+50,by+25,-1,false);
                    if (!((BooleanValue)call(panel,"cancel",null)).value()) throw new AssertionError("Escape cancels drag");
                    panelPointer(panel,bx+50,by+25,0,false);
                    bounds=(ObjectReference)call(panel,"playerBounds",null);
                    if (intField(bounds,"x")!=bx || intField(bounds,"y")!=by) throw new AssertionError("Canceled drag restored geometry");
                    for (int corner=0;corner<4;corner++) {
                        bounds=(ObjectReference)call(panel,"playerBounds",null);
                        int x=intField(bounds,"x")+(corner%2==0 ? 1 : intField(bounds,"width")-2);
                        int y=intField(bounds,"y")+(corner<2 ? 1 : intField(bounds,"height")-2);
                        panelPointer(panel,x,y,0,true);
                        panelPointer(panel,x+(corner%2==0 ? -18 : 18),y+(corner<2 ? -18 : 18),-1,false);
                        panelPointer(panel,x+(corner%2==0 ? -18 : 18),y+(corner<2 ? -18 : 18),0,false);
                    }
                    ClassType itemPanels = type("codechicken.nei.ItemPanels");
                    bounds=(ObjectReference)call(panel,"playerBounds",null);
                    int titleX=intField(bounds,"x")+25, titleY=intField(bounds,"y")+7;
                    panelPointer(panel,titleX,titleY,0,true);
                    panelPointer(panel,titleX+intField(screen,"width"),titleY,-1,false);
                    panelPointer(panel,titleX+intField(screen,"width"),titleY,0,false);
                    ObjectReference search = (ObjectReference) itemPanels.getValue(itemPanels.fieldByName("itemPanel"));
                    ObjectReference grid = (ObjectReference) call(search,"getGrid",null);
                    call(grid,"refresh","(Lnet/minecraft/client/gui/inventory/GuiContainer;)V",screen);
                    ArrayReference invalid = (ArrayReference) grid.getValue(fieldInfo(grid.referenceType(),"invalidSlotMap"));
                    int columns=intField(grid,"columns"), checked=0;
                    for(int i=0;i<invalid.length();i++) {
                        ObjectReference cell=(ObjectReference)call(grid,"getSlotRect","(II)Lcodechicken/lib/vec/Rectangle4i;",vm.mirrorOf(i/columns),vm.mirrorOf(i%columns));
                        boolean intersects=((BooleanValue)call(panel,"excludes","(IIII)Z",vm.mirrorOf(intField(cell,"x")),vm.mirrorOf(intField(cell,"y")),vm.mirrorOf(intField(cell,"w")),vm.mirrorOf(intField(cell,"h")))).value();
                        if(intersects) { checked++; if(!((BooleanValue)invalid.getValue(i)).value()) throw new AssertionError("NEI rendered over detached panel"); }
                    }
                    if(checked==0) throw new AssertionError("Fixture did not intersect the search-result grid");
                    rebuildScreen(screen);
                    panel=floatingPanel(screen);
                    if (!((BooleanValue)call(panel,"detached",null)).value()) throw new AssertionError("Saved profile survives reopen");
                    checkPanelMenu(screen,panel);
                    if (!container.equals(field(player,"openContainer")) || intField(container,"windowId")!=window) throw new AssertionError("Gesture replaced live container");
                    for(int i=0;i<count;i++) if(!identities.get(i).equals(call(slots,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)))) throw new AssertionError("Gesture replaced slot identity");
                    if(call(field(player,"inventory"),"getItemStack","()Lnet/minecraft/item/ItemStack;")!=null) throw new AssertionError("Gesture changed cursor");
                    System.out.println("PASS attached default, detach, canceled drag, four corners, persistence, global-default menu, live slot/window identity; NEI masked cells="+checked+"; initial bounds="+bx+","+by+","+bw+","+bh);
                } finally {
                    profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(originalFile),ObjectReference.INVOKE_SINGLE_THREADED);
                    rebuildScreen(screen);
                }
                break;
            }
            case "seed-large-grave": {
                act(new String[]{"require-qualification"});
                ObjectReference fixtureServer = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                String fixtureWorld = ((StringReference) call(fixtureServer, "getFolderName", "()Ljava/lang/String;")).value();
                if (!fixtureWorld.equals("DI-Baseline-Large-Grave-Smoke-2-Qualification") &&
                    !fixtureWorld.startsWith("DI-Baseline-Upgrades-Graves-"))
                    throw new IllegalStateException("Disposable grave qualification world required");
                int probes = args.length > 1 ? Integer.parseInt(args[1]) : 130;
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
                ObjectReference inventory = field(serverPlayer, "inventory");
                int ordinary = ownedLayoutInt(inventory, "mainSlots");
                if (probes < 1 || probes > ordinary) throw new IllegalArgumentException("Probe count exceeds owned capacity");
                for (String array : List.of("mainInventory", "armorInventory"))
                    for (Value stack : ((ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), array))).getValues())
                        if (stack != null) throw new IllegalStateException("Seed requires empty inventory and armor");
                if (call(inventory, "getItemStack", "()Lnet/minecraft/item/ItemStack;") != null)
                    throw new IllegalStateException("Seed requires empty cursor");
                ArrayReference main = (ArrayReference) inventory.getValue(fieldInfo(inventory.referenceType(), "mainInventory"));
                ClassType items = type("net.minecraft.init.Items"), json = type("net.minecraft.nbt.JsonToNBT");
                for (int i = 0; i < probes; i++) {
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
                System.out.println("Seeded " + probes + " unique fixture stacks on the server thread; normal death/reclaim remain native");
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
            case "mui-lifecycle-fixtures": {
                if (player == null || field(client, "currentScreen") == null)
                    throw new IllegalStateException("Open the disposable player inventory first");
                String fixture = switch (args[0]) {
                    case "screen-fixtures" -> "InventoryScreenFixtures";
                    case "editor-fixtures" -> "NativeEditorClientFixtures";
                    case "mui-lifecycle-fixtures" -> "Mui2LifecycleClientFixtures";
                    default -> throw new IllegalArgumentException(args[0]);
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
            case "input-panel":
            case "input-sort":
            case "input-wheel": {
                ObjectReference screen = field(client,"currentScreen");
                ObjectReference container = field(player,"openContainer");
                int x, y;
                if (args[0].equals("input-panel")) {
                    ObjectReference panel=floatingPanel(screen), bounds=(ObjectReference)call(panel,"playerBounds",null);
                    x=intField(bounds,"x")+16; y=intField(bounds,"y")+6;
                } else if (args[0].equals("input-sort")) {
                    if (!Set.of("player", "chest").contains(args[1])) throw new IllegalArgumentException("Sort role must be player or chest");
                    ObjectReference buttons = field(screen, "buttonList");
                    int count = ((IntegerValue) call(buttons, "size", "()I")).value();
                    ObjectReference selected = null;
                    for (int i = 0; i < count; i++) {
                        ObjectReference button = (ObjectReference) call(buttons, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                        if (!button.referenceType().name().equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton")) continue;
                        ObjectReference group = field(button, "slotGroup");
                        if (((BooleanValue) call(group, "isPlayerInventory", "()Z")).value() != args[1].equals("player")
                            || ((BooleanValue) button.getValue(fieldInfo(button.referenceType(), "sort"))).value() == (args.length > 2 && args[2].equals("config"))) continue;
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
                    if (x == -10000 || y == -10000) throw new AssertionError("Hidden input slot");
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
                    if(args[0].equals("input-click") || args[0].equals("input-sort") || args[0].equals("input-panel")) {
                        for(boolean down:new boolean[]{true,false}) mouse.invokeMethod(thread,methods(mouse,"addButtonEvent","(IZ)V").getFirst(),List.of(vm.mirrorOf(args[0].equals("input-panel") ? 1 : 0),vm.mirrorOf(down)),ObjectReference.INVOKE_SINGLE_THREADED);
                    } else mouse.invokeMethod(thread,methods(mouse,"addWheelEvent","(D)V").getFirst(),List.of(vm.mirrorOf(Double.parseDouble(args[2]))),ObjectReference.INVOKE_SINGLE_THREADED);
                    call(screen,"handleInput","()V");
                    if(args[0].equals("input-panel")) {
                        ClassType modal=type("dev.gilbe.dynamicinventory.client.PanelSettingsOverlay");
                        if(modal.getValue(modal.fieldByName("active"))==null || !field(client,"currentScreen").equals(screen)
                            || !field(player,"openContainer").equals(container)) throw new AssertionError("Raw right-click failed modal ownership");
                    }
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
                    String name = entry instanceof StringReference text ? text.value()
                        : ((StringReference) call((ObjectReference) entry, "toString", "()Ljava/lang/String;")).value();
                    for (int i = 1; i < args.length; i++) if (name.toLowerCase(Locale.ROOT).contains(args[i].toLowerCase(Locale.ROOT))) {
                        System.out.println(name);
                        break;
                    }
                }
                break;
            }
            case "routing-seed":
            case "routing-assert": {
                act(new String[]{"require-qualification","DI-Baseline-Panel-Routing-Qualification"});
                boolean verify=args[0].equals("routing-assert");
                ObjectReference routingPanel=floatingPanel(field(client,"currentScreen"));
                if(routingPanel==null || !((BooleanValue)call(routingPanel,"detached",null)).value())
                    throw new AssertionError("Routing fixture must have usable detached panels");
                for(ObjectReference actor:new ObjectReference[]{player,serverPlayer(player)}) {
                    ObjectReference inventory=field(actor,"inventory");
                    int[] expected=routingCounts(args[1],ownedLayoutInt(inventory,"mainSlots"),verify);
                    if(args[1].startsWith("container-")) {
                        ObjectReference chest=field(actor,"openContainer");
                        if(!chest.referenceType().name().equals("net.minecraft.inventory.ContainerChest"))throw new AssertionError("Native chest fixture required");
                        for(int i=0;i<27;i++) {
                            ObjectReference slot=(ObjectReference)call(chest,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(i));
                            if(!verify)call(slot,"putStack","(Lnet/minecraft/item/ItemStack;)V",new Value[]{null});
                            else {
                                ObjectReference actual=(ObjectReference)call(slot,"getStack","()Lnet/minecraft/item/ItemStack;");
                                if(i==0) {
                                    if(!((BooleanValue)type("net.minecraft.item.ItemStack").invokeMethod(thread,methods(type("net.minecraft.item.ItemStack"),"areItemStacksEqual","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z").getFirst(),Arrays.asList(actual,routingStack(7)),ObjectReference.INVOKE_SINGLE_THREADED)).value())throw new AssertionError("Shift-click missed native chest on "+actor.referenceType().name());
                                } else if(actual!=null)throw new AssertionError("Unexpected native chest contents");
                            }
                        }
                    }
                    ArrayReference stacks=(ArrayReference)field(inventory,"mainInventory");
                    List<Value> values=new ArrayList<>(stacks.getValues());
                    boolean offhand=stacks.length()==expected.length+1;
                    Map<Integer,ObjectReference> probes=new HashMap<>();
                    for(int logical=0;logical<expected.length;logical++) {
                        int raw=logical+(offhand&&logical>=36 ? 1 : 0);
                        int amount=expected[logical];
                        if(amount==0) {
                            if(verify&&values.get(raw)!=null)throw new AssertionError(args[1]+" unexpected item at logical "+logical);
                            if(!verify)values.set(raw,null);
                            continue;
                        }
                        if(!probes.containsKey(amount))probes.put(amount,routingStack(amount));
                        ObjectReference wanted=probes.get(amount);
                        if(!verify)values.set(raw,call(wanted,"copy","()Lnet/minecraft/item/ItemStack;"));
                        else {
                            ObjectReference actual=(ObjectReference)values.get(raw);
                            if(!((BooleanValue)type("net.minecraft.item.ItemStack").invokeMethod(thread,
                                methods(type("net.minecraft.item.ItemStack"),"areItemStacksEqual","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z").getFirst(),
                                Arrays.asList(actual,wanted),ObjectReference.INVOKE_SINGLE_THREADED)).value())
                                throw new AssertionError(args[1]+" logical "+logical+" on "+actor.referenceType().name()+": "+actual);
                        }
                    }
                    if(!verify){stacks.setValues(values);call(inventory,"markDirty","()V");call(field(actor,"openContainer"),"detectAndSendChanges","()V");}
                }
                System.out.println("PASS "+args[0]+" "+args[1]+" canonical inventory, counts and typed NBT on both peers");
                break;
            }
            case "routing-shift": {
                act(new String[]{"require-qualification","DI-Baseline-Panel-Routing-Qualification"});
                ObjectReference host=field(client,"currentScreen"), panel=floatingPanel(host), container=field(player,"openContainer");
                int id=((IntegerValue)((ArrayReference)field(panel,"slotIds")).getValue(Integer.parseInt(args[1]))).value();
                if(args.length>2 && args[2].equals("gui"))call(host,"handleMouseClick","(Lnet/minecraft/inventory/Slot;III)V",
                    call(container,"getSlot","(I)Lnet/minecraft/inventory/Slot;",vm.mirrorOf(id)),vm.mirrorOf(id),vm.mirrorOf(0),vm.mirrorOf(1));
                else act(new String[]{"click",Integer.toString(id),"0","1"});
                break;
            }
            case "routing-spawn": {
                act(new String[]{"require-qualification","DI-Baseline-Panel-Routing-Qualification"});
                ObjectReference stack=routingStack(args.length>1 && args[1].equals("infinite") ? -1 : 7);
                ClassType config=type("codechicken.nei.NEIClientConfig");
                Value mode=config.invokeMethod(thread,config.methodsByName("getCheatMode").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                try {
                    // Only enable the disposable fixture's client mode temporarily; server permissions remain authoritative.
                    config.invokeMethod(thread,config.methodsByName("setIntSetting").getFirst(),List.of(vm.mirrorOf("inventory.cheatmode"),vm.mirrorOf(2)),ObjectReference.INVOKE_SINGLE_THREADED);
                    if(!((BooleanValue)config.invokeMethod(thread,config.methodsByName("canCheatItem").getFirst(),List.of(stack),ObjectReference.INVOKE_SINGLE_THREADED)).value())
                        throw new AssertionError("NEI server spawn permission unavailable in disposable routing world");
                    ClassType utils=type("codechicken.nei.NEIClientUtils");
                    utils.invokeMethod(thread,methods(utils,"giveStack","(Lnet/minecraft/item/ItemStack;IZ)V").getFirst(),
                        List.of(stack,vm.mirrorOf(intField(stack,"stackSize")),vm.mirrorOf(args.length>1&&args[1].equals("infinite"))),ObjectReference.INVOKE_SINGLE_THREADED);
                } finally {
                    config.invokeMethod(thread,config.methodsByName("setIntSetting").getFirst(),List.of(vm.mirrorOf("inventory.cheatmode"),mode),ObjectReference.INVOKE_SINGLE_THREADED);
                }
                System.out.println("PASS NEI giveStack entry point invoked");
                break;
            }
            case "panel-scale-save": {
                act(new String[]{"require-qualification","DI-Baseline-Panel-Routing-Qualification"});
                ObjectReference panel=floatingPanel(field(client,"currentScreen")), viewport=field(panel,"viewport");
                ObjectReference machine=(ObjectReference)call(panel,"machineBounds",null);
                ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
                ObjectReference key=(ObjectReference)call(panel,"key",null);
                ObjectReference set=(ObjectReference)profiles.invokeMethod(thread,methods(profiles,"panels","(Ljava/lang/String;)Ldev/gilbe/dynamicinventory/client/PlayerPanelSet;").getFirst(),List.of(key),ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference states=field(panel,"panels"), mainProfile=null;
                for(int i=0;i<2;i++) {
                    ObjectReference state=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)), id=field(state,"id");
                    ObjectReference profile=(ObjectReference)call(panel,"displayedProfile",null,id);
                    ObjectReference bounds=make("dev.gilbe.dynamicinventory.client.InventoryPanelLayout$Rect","(IIII)V",
                        vm.mirrorOf(intField(machine,"x")+(i==0 ? 0 : 179)),vm.mirrorOf(intField(machine,"y")+intField(machine,"height")+(args[1].equals("custom") ? 2 : 1)),
                        vm.mirrorOf(i==0 ? 178 : 70),vm.mirrorOf(i==0 ? 140 : 116));
                    profile=(ObjectReference)call(profile,"atBounds",null,bounds,viewport);
                    ObjectReference entry=(ObjectReference)call(set,"get",null,id);
                    entry=(ObjectReference)call(entry,"profile",null,key,profile);
                    set=(ObjectReference)call(set,"replace",null,entry);
                    if(i==0)mainProfile=profile;
                }
                call(panel,"apply","(Ldev/gilbe/dynamicinventory/client/PlayerPanelSet;Ldev/gilbe/dynamicinventory/client/PlayerPanelProfile;Z)V",
                    set,mainProfile,vm.mirrorOf(args[1].equals("global")));
                rebuildScreen(field(client,"currentScreen"));
                System.out.println("PASS authored "+args[1]+" scale fixture");
                break;
            }
            case "panel-toggle-roundtrip": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                ObjectReference host=field(client,"currentScreen"), panel=floatingPanel(host);
                ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
                String fixture=System.getProperty("dinv.panelFixtureFile");
                if(fixture==null || !fixture.replace('\\','/').endsWith("/.setup/panel-attach-regression/panel-client-fixture.json"))
                    throw new IllegalArgumentException("Isolated attach regression profile required");
                String activeFile=((StringReference)call((ObjectReference)profiles.getValue(profiles.fieldByName("file")),"toString","()Ljava/lang/String;")).value();
                if(!java.nio.file.Path.of(fixture).equals(java.nio.file.Path.of(activeFile)))throw new AssertionError("Fixture is not active");
                profiles.invokeMethod(thread,profiles.methodsByName("inherit").getFirst(),List.of(call(panel,"key",null)),ObjectReference.INVOKE_SINGLE_THREADED);
                call(panel,"reload",null);
                if(!((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Detached global fixture required");
                byte[] globalFile=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(fixture));
                for(int cycle=0;cycle<4;cycle++) {
                    boolean expectedCustom=cycle>=2;
                    if(cycle==2) {
                        call(panel,"select",null,vm.mirrorOf("main"));
                        ObjectReference profile=(ObjectReference)call(panel,"displayedProfile",null,vm.mirrorOf("main"));
                        profile=(ObjectReference)call(profile,"withAppearance",null,vm.mirrorOf(intField(profile,"frameStyle")==1 ? 2 : 1),
                            vm.mirrorOf(intField(profile,"borderWidth")),vm.mirrorOf(intField(profile,"frameCrop")),vm.mirrorOf(intField(profile,"cornerCrop")));
                        call(panel,"apply","(Ldev/gilbe/dynamicinventory/client/PlayerPanelProfile;)V",profile);
                    }
                    ObjectReference states=field(panel,"panels");
                    List<Integer> before=new ArrayList<>();
                    for(int i=0;i<((IntegerValue)call(states,"size","()I")).value();i++) {
                        ObjectReference state=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)), layout=field(state,"layout");
                        if(layout!=null)for(String name:List.of("x","y","width","height"))before.add(intField(field(layout,"bounds"),name));
                    }
                    for(int toggle=0;toggle<2;toggle++) {
                        ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                        int x=intField(control,"x")+2,y=intField(control,"y")+2;
                        panelPointer(panel,x,y,0,true);panelPointer(panel,x,y,0,false);
                        boolean custom=((BooleanValue)profiles.invokeMethod(thread,profiles.methodsByName("hasOverride").getFirst(),List.of(call(panel,"key",null)),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                        if(custom!=expectedCustom)throw new AssertionError("Mode toggle changed layout scope cycle="+cycle+" toggle="+toggle);
                        if(!expectedCustom && !Arrays.equals(globalFile,java.nio.file.Files.readAllBytes(java.nio.file.Path.of(fixture))))
                            throw new AssertionError("Global mode toggle rewrote profile file");
                    }
                    for(int reload=0;reload<2;reload++) {
                        List<Integer> after=new ArrayList<>();
                        states=field(panel,"panels");
                        for(int i=0;i<((IntegerValue)call(states,"size","()I")).value();i++) {
                            ObjectReference state=(ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(i)), layout=field(state,"layout");
                            if(layout!=null)for(String name:List.of("x","y","width","height"))after.add(intField(field(layout,"bounds"),name));
                        }
                        if(!before.equals(after))throw new AssertionError("Attach/detach changed geometry cycle="+cycle+" reload="+reload+" before="+before+" after="+after);
                        call(panel,"reload",null);
                    }
                }
                System.out.println("PASS global/custom attach-detach preserves scope and all bounds; global profile file unchanged");
                break;
            }
            case "player-section-check": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                String fixture=System.getProperty("dinv.panelFixtureFile"), settingsFile=System.getProperty("dinv.sectionFixtureFile");
                if(fixture==null || settingsFile==null || !settingsFile.replace('\\','/').endsWith("/.setup/container-shift-routing/player-section-client.cfg"))throw new IllegalArgumentException("Isolated section settings required");
                ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles"), settings=type("dev.gilbe.dynamicinventory.client.InventoryViewSettings");
                ObjectReference host=field(client,"currentScreen");
                if(!host.referenceType().name().equals("dev.gilbe.dynamicinventory.client.PlayerInventoryScreen"))throw new AssertionError("Player inventory screen required");
                String beforeNbt=inventoryNbt(field(player,"inventory"));
                ObjectReference container=field(player,"openContainer"),slots=field(container,"inventorySlots");
                List<Value> identities=new ArrayList<>(((ArrayReference)call(slots,"toArray","()[Ljava/lang/Object;")).getValues());
                try {
                    settings.invokeMethod(thread,settings.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(settingsFile))),ObjectReference.INVOKE_SINGLE_THREADED);
                    profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(fixture))),ObjectReference.INVOKE_SINGLE_THREADED);
                    settings.invokeMethod(thread,settings.methodsByName("setPlayerSectionPosition").getFirst(),List.of(vm.mirrorOf(-1),vm.mirrorOf(-1)),ObjectReference.INVOKE_SINGLE_THREADED);
                    int top=-1;
                    rebuildScreen(host);
                    for(int columns:new int[]{9,18,9}) {
                        rawGuiButton(host,100); rawGuiButton(host,columns==9?102:101); rawGuiButton(host,108);
                        for(int frame=0;frame<3;frame++)renderScreen(host,-1000,-1000);
                        if(top<0)top=intField(host,"guiTop");
                        if(top!=intField(host,"guiTop"))throw new AssertionError("Wide/Compact moved the player section vertically");
                    }
                    ObjectReference panel=floatingPanel(host);
                    if(!((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Detached panel fixture failed to fit");
                    List<Integer> bounds=panelRectangles(panel);
                    int left=intField(host,"guiLeft");
                    ObjectReference handle=(ObjectReference)call(host,"playerSectionHandle",null);
                    int x=intField(handle,"x")+6,y=intField(handle,"y")+6;
                    rawSectionDrag(host,x,y,-24,-12);
                    int movedLeft=intField(host,"guiLeft"),movedTop=intField(host,"guiTop");
                    if(left==movedLeft && top==movedTop)throw new AssertionError("Drag handle did not move the section");
                    if(!bounds.equals(panelRectangles(panel)))throw new AssertionError("Moving the player section translated inventory panels");
                    rebuildScreen(host);
                    renderScreen(host,-1000,-1000);
                    if(movedLeft!=intField(host,"guiLeft")||movedTop!=intField(host,"guiTop"))throw new AssertionError("Dragged position did not survive reopen");
                    panel=floatingPanel(host);
                    ObjectReference control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                    panelPointer(panel,intField(control,"x")+2,intField(control,"y")+2,0,true);panelPointer(panel,intField(control,"x")+2,intField(control,"y")+2,0,false);
                    if(((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Attach failed after header drag");
                    if(intField(host,"guiTop")+intField(field(host,"view"),"height")+intField(host,"tabSpace")>intField(host,"height"))throw new AssertionError("Attached inventory moved below the screen");
                    control=(ObjectReference)call(panel,"control",null,call(panel,"playerBounds",null));
                    panelPointer(panel,intField(control,"x")+2,intField(control,"y")+2,0,true);panelPointer(panel,intField(control,"x")+2,intField(control,"y")+2,0,false);
                    if(!((BooleanValue)call(panel,"detached",null)).value()||movedLeft!=intField(host,"guiLeft")||movedTop!=intField(host,"guiTop"))throw new AssertionError("Re-detach lost the saved header position");
                    handle=(ObjectReference)call(host,"playerSectionHandle",null);
                    rawClick(host,intField(handle,"x")+6,intField(handle,"y")+6,1);
                    renderScreen(host,-1000,-1000);
                    if(intField(host,"guiTop")!=top)throw new AssertionError("Reset did not restore default vertical position");
                    rawGuiButton(host,100);rawGuiButton(host,101);rawGuiButton(host,108);
                    if(intField(host,"guiTop")!=top)throw new AssertionError("Wide moved vertically after a completed drag/reset");
                    panel=floatingPanel(host);
                    bounds=panelRectangles(panel);
                    handle=(ObjectReference)call(host,"playerSectionHandle",null);
                    rawSectionDrag(host,intField(handle,"x")+6,intField(handle,"y")+6,-20,-10);
                    if(!bounds.equals(panelRectangles(panel)))throw new AssertionError("Wide header drag moved inventory panels");
                    handle=(ObjectReference)call(host,"playerSectionHandle",null);
                    rawClick(host,intField(handle,"x")+6,intField(handle,"y")+6,1);
                    renderScreen(host,-1000,-1000);
                    if(intField(host,"guiTop")!=top)throw new AssertionError("Wide reset was overwritten during rendering");
                    if(!beforeNbt.equals(inventoryNbt(field(player,"inventory"))) || !container.equals(field(player,"openContainer"))
                        || !identities.equals(((ArrayReference)call(slots,"toArray","()[Ljava/lang/Object;")).getValues()))throw new AssertionError("Section movement changed inventory ownership/items");
                    if(((BooleanValue)profiles.invokeMethod(thread,profiles.methodsByName("hasOverride").getFirst(),List.of(call(panel,"key",null)),ObjectReference.INVOKE_SINGLE_THREADED)).value())throw new AssertionError("Header movement created a panel override");
                    System.out.println("PASS raw-input Wide/Compact alignment across rendered frames, raw handle drag with per-frame motion and stable release, independent inventory panels, saved position, attach/detach, raw right-click reset and unchanged items/slots");
                } finally {
                    settings.invokeMethod(thread,settings.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(System.getProperty("dinv.sectionOriginalFile")))),ObjectReference.INVOKE_SINGLE_THREADED);
                    profiles.invokeMethod(thread,profiles.methodsByName("initialize").getFirst(),List.of(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(System.getProperty("dinv.panelOriginalFile")))),ObjectReference.INVOKE_SINGLE_THREADED);
                    rebuildScreen(host);
                }
                break;
            }
            case "panel-scale-check": {
                ObjectReference panel=floatingPanel(field(client,"currentScreen")), states=field(panel,"panels");
                if(!((BooleanValue)call(panel,"detached",null)).value())throw new AssertionError("Scale fixture failed to fit");
                ObjectReference machine=(ObjectReference)call(panel,"machineBounds",null);
                ObjectReference main=field(field((ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(0)),"layout"),"bounds");
                ObjectReference side=field(field((ObjectReference)call(states,"get","(I)Ljava/lang/Object;",vm.mirrorOf(1)),"layout"),"bounds");
                int[] measured={intField(main,"x")-intField(machine,"x"),intField(main,"y")-intField(machine,"y")-intField(machine,"height"),
                    intField(side,"x")-intField(main,"x")-intField(main,"width"),intField(side,"y")-intField(main,"y"),
                    intField(main,"width"),intField(main,"height"),intField(side,"width"),intField(side,"height")};
                boolean expectedCustom=args.length>1&&args[1].equals("custom");
                if(!Arrays.equals(measured,new int[]{0,expectedCustom ? 2 : 1,1,0,178,140,70,116}))throw new AssertionError("Scale changed authored group: "+Arrays.toString(measured));
                ClassType profiles=type("dev.gilbe.dynamicinventory.client.PlayerPanelProfiles");
                boolean custom=((BooleanValue)profiles.invokeMethod(thread,profiles.methodsByName("hasOverride").getFirst(),List.of(call(panel,"key",null)),ObjectReference.INVOKE_SINGLE_THREADED)).value();
                if(custom!=expectedCustom)throw new AssertionError("Scale fixture used the wrong layout scope");
                System.out.println("PASS live scale group "+Arrays.toString(measured)+" custom="+custom);
                break;
            }
            case "harvest-lab-empty": {
                act(new String[]{"require-qualification",args[1]});
                ObjectReference world=field(client,"theWorld");
                for(int i=0;i<8;i++) {
                    int x=-12+(i%4)*8,z=28+(i/4)*8;
                    if(!((BooleanValue)call(world,"blockExists","(III)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z))).value())throw new AssertionError("Lab chunks not loaded");
                    for(int dx=-2;dx<=2;dx++)for(int dz=-3;dz<=2;dz++) {
                        if(i!=4 && (dx!=0 || dz!=0 && dz!=-2))continue;
                        if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x+dx),vm.mirrorOf(4),vm.mirrorOf(z+dz))).value())throw new AssertionError("HarvestCraft lab area already occupied");
                    }
                }
                System.out.println("PASS HarvestCraft lab area loaded and empty");
                break;
            }
            case "ae2-lab-definitions": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                ClassType registry=type("cpw.mods.fml.common.registry.GameRegistry"), items=type("net.minecraft.item.Item");
                for(String name:List.of("item.ItemMultiPart","item.ToolPortableCell","item.ToolWirelessTerminal","item.ItemBasicStorageCell.1k","item.ToolNetworkTool")) {
                    ObjectReference item=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findItem").getFirst(),List.of(vm.mirrorOf("appliedenergistics2"),vm.mirrorOf(name)),ObjectReference.INVOKE_SINGLE_THREADED);
                    if(item!=null)System.out.println("item="+name+":"+items.invokeMethod(thread,methods(items,"getIdFromItem","(Lnet/minecraft/item/Item;)I").getFirst(),List.of(item),ObjectReference.INVOKE_SINGLE_THREADED));
                }
                ClassType parts=type("appeng.items.parts.PartType");
                for(String name:List.of("CableGlass","Terminal","CraftingTerminal","PatternTerminal","PatternTerminalEx","InterfaceTerminal","CraftingDiagnosticTerminal")) {
                    ObjectReference part=(ObjectReference)parts.getValue(parts.fieldByName(name));
                    System.out.println("part="+name+":"+intField(part,"baseDamage"));
                }
                break;
            }
            case "ae2-lab-empty": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                ObjectReference world=field(client,"theWorld");
                for(int x:new int[]{-16,-8,0,8,16})for(int z:new int[]{48,56,64,72,80}) {
                    for(int[] offset:new int[][]{{0,0},{0,-2},{0,1},{1,1},{2,1}})
                        if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x+offset[0]),vm.mirrorOf(4),vm.mirrorOf(z+offset[1]))).value())throw new AssertionError("AE2 lab area occupied at "+x+","+z);
                }
                for(int x=-2;x<=2;x+=2)if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(88))).value())throw new AssertionError("AE2 supply area occupied");
                System.out.println("PASS AE2 lab targets are empty");
                break;
            }
            case "ae2-lab-verify": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                java.nio.file.Path path=java.nio.file.Path.of(args[1]);
                if(!path.toString().replace('\\','/').endsWith("/.setup/ae2-panel-lab/commands.txt"))throw new IllegalArgumentException("AE2 lab command file required");
                ObjectReference world=field(client,"theWorld");
                ClassType blocks=type("net.minecraft.block.Block");
                ObjectReference registry=(ObjectReference)blocks.getValue(fieldInfo(blocks,"blockRegistry"));
                ClassType direction=type("net.minecraftforge.common.util.ForgeDirection");
                int checked=0,terminals=0;
                for(String command:java.nio.file.Files.readAllLines(path)) {
                    if(!command.startsWith("/setblock "))continue;
                    String[] tokens=command.split(" ",8);
                    int x=Integer.parseInt(tokens[1]),y=Integer.parseInt(tokens[2]),z=Integer.parseInt(tokens[3]);
                    ObjectReference actual=(ObjectReference)call(world,"getBlock","(III)Lnet/minecraft/block/Block;",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z));
                    ObjectReference expected=(ObjectReference)call(registry,"getObject","(Ljava/lang/Object;)Ljava/lang/Object;",vm.mirrorOf(tokens[4]));
                    if(!actual.equals(expected))throw new AssertionError("Station placement failed: "+command+" actual="+call(registry,"getNameForObject","(Ljava/lang/Object;)Ljava/lang/String;",actual));
                    if(tokens[4].equals("appliedenergistics2:tile.BlockCableBus")) {
                        ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(x),vm.mirrorOf(y),vm.mirrorOf(z)),bus=(ObjectReference)call(tile,"getCableBus",null);
                        for(String side:List.of("NORTH","UNKNOWN"))if(call(bus,"getPart","(Lnet/minecraftforge/common/util/ForgeDirection;)Lappeng/api/parts/IPart;",direction.getValue(direction.fieldByName(side)))==null)
                            throw new AssertionError("Missing terminal/cable part at "+x+","+z);
                        terminals++;
                    }
                    checked++;
                }
                System.out.println("PASS placed blocks="+checked+" terminal cable hosts="+terminals+"; no UIs opened");
                break;
            }
            case "ae2-lab-install-parts": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                if(!thread.name().equals("Server thread"))throw new AssertionError("Native part placement must run on the server thread");
                ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;"), world=(ObjectReference)call(server,"worldServerForDimension","(I)Lnet/minecraft/world/WorldServer;",vm.mirrorOf(0));
                ClassType registry=type("cpw.mods.fml.common.registry.GameRegistry");
                ObjectReference block=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findBlock").getFirst(),List.of(vm.mirrorOf("appliedenergistics2"),vm.mirrorOf("tile.BlockCableBus")),ObjectReference.INVOKE_SINGLE_THREADED);
                ObjectReference item=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findItem").getFirst(),List.of(vm.mirrorOf("appliedenergistics2"),vm.mirrorOf("item.ItemMultiPart")),ObjectReference.INVOKE_SINGLE_THREADED);
                ClassType directions=type("net.minecraftforge.common.util.ForgeDirection"),types=type("appeng.items.parts.PartType");
                String[] names={"Terminal","CraftingTerminal","PatternTerminal","PatternTerminalEx","InterfaceTerminal","CraftingDiagnosticTerminal"};
                for(int i=0;i<6;i++) {
                    int station=i+18,x=-16+(station%5)*8,z=48+(station/5)*8;
                    ObjectReference actual=(ObjectReference)call(world,"getBlock","(III)Lnet/minecraft/block/Block;",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z));
                    if(!actual.equals(block)) {
                        if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z))).value())throw new AssertionError("Terminal target occupied");
                        call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z),block,vm.mirrorOf(0),vm.mirrorOf(3));
                    }
                    ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z)),bus=(ObjectReference)call(tile,"getCableBus",null);
                    for(String side:List.of("UNKNOWN","NORTH")) {
                        Value direction=directions.getValue(directions.fieldByName(side));
                        if(call(bus,"getPart","(Lnet/minecraftforge/common/util/ForgeDirection;)Lappeng/api/parts/IPart;",direction)!=null)throw new AssertionError("Refusing to replace an existing AE2 part");
                        int damage=side.equals("UNKNOWN") ? 16 : intField((ObjectReference)types.getValue(types.fieldByName(names[i])),"baseDamage");
                        ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;II)V",item,vm.mirrorOf(1),vm.mirrorOf(damage));
                        if(call(bus,"addPart","(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraftforge/common/util/ForgeDirection;",stack,direction,serverPlayer(player))==null)throw new AssertionError("AE2 refused part placement");
                    }
                    call(tile,"markDirty","()V");
                    call(world,"markBlockForUpdate","(III)V",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z));
                }
                System.out.println("PASS placed six terminal parts and cables through native AE2 API; player inventory unchanged");
                break;
            }
            case "ae2-lab-install-supplies": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                if(!thread.name().equals("Server thread"))throw new AssertionError("Supply placement requires server thread");
                ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;"), world=(ObjectReference)call(server,"worldServerForDimension","(I)Lnet/minecraft/world/WorldServer;",vm.mirrorOf(0));
                ClassType blocks=type("net.minecraft.init.Blocks"),registry=type("cpw.mods.fml.common.registry.GameRegistry");
                Value chest=blocks.getValue(fieldInfo(blocks,"chest"));
                if(!((BooleanValue)call(world,"isAirBlock","(III)Z",vm.mirrorOf(16),vm.mirrorOf(4),vm.mirrorOf(80))).value()) {
                    if(!chest.equals(call(world,"getBlock","(III)Lnet/minecraft/block/Block;",vm.mirrorOf(16),vm.mirrorOf(4),vm.mirrorOf(80))))throw new AssertionError("Supply target occupied");
                } else call(world,"setBlock","(IIILnet/minecraft/block/Block;II)Z",vm.mirrorOf(16),vm.mirrorOf(4),vm.mirrorOf(80),chest,vm.mirrorOf(0),vm.mirrorOf(3));
                ObjectReference tile=(ObjectReference)call(world,"getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",vm.mirrorOf(16),vm.mirrorOf(4),vm.mirrorOf(80));
                for(int i=0;i<27;i++)if(call(tile,"getStackInSlot","(I)Lnet/minecraft/item/ItemStack;",vm.mirrorOf(i))!=null)throw new AssertionError("Supply chest already contains items");
                int slot=0;
                for(String name:List.of("item.ToolPortableCell","item.ToolWirelessTerminal","item.ItemBasicStorageCell.1k","item.ToolNetworkTool")) {
                    ObjectReference item=(ObjectReference)registry.invokeMethod(thread,registry.methodsByName("findItem").getFirst(),List.of(vm.mirrorOf("appliedenergistics2"),vm.mirrorOf(name)),ObjectReference.INVOKE_SINGLE_THREADED);
                    if(item==null)throw new AssertionError("Missing AE2 supply "+name);
                    ObjectReference stack=make("net.minecraft.item.ItemStack","(Lnet/minecraft/item/Item;II)V",item,vm.mirrorOf(name.contains("StorageCell") ? 16 : 1),vm.mirrorOf(0));
                    if(name.contains("ToolPortableCell")||name.contains("ToolWirelessTerminal")) {
                        ObjectReference tag=make("net.minecraft.nbt.NBTTagCompound","()V");
                        call(tag,"setDouble","(Ljava/lang/String;D)V",vm.mirrorOf("internalCurrentPower"),vm.mirrorOf(1600000.0));
                        call(stack,"setTagCompound","(Lnet/minecraft/nbt/NBTTagCompound;)V",tag);
                    }
                    call(tile,"setInventorySlotContents","(ILnet/minecraft/item/ItemStack;)V",vm.mirrorOf(slot++),stack);
                }
                call(tile,"markDirty","()V");
                System.out.println("PASS AE2 supply chest created; no player items changed");
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
            case "capacity-info": {
                ClassType capacity = type("dev.gilbe.dynamicinventory.inventory.InventoryCapacity");
                Value configured = capacity.invokeMethod(thread, capacity.methodsByName("configuredStorageSlots").getFirst(), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
                System.out.println("configuredStorage=" + configured + " storage=" + layoutInt("storageSlots") + " ordinary=" + layoutInt("mainSlots"));
                break;
            }
            case "forestry-class-check": {
                for (String group : List.of("core:Analyzer", "apiculture:BeeHousing,Alveary,AlvearyHygroregulator,AlvearySieve,AlvearySwarmer",
                        "energy:EngineBiogas,EngineElectric,EnginePeat,Generator",
                        "factory:Bottler,Carpenter,Centrifuge,Fabricator,Fermenter,Moistener,Raintank,Squeezer,Still,Worktable", "farming:Farm")) {
                    String[] parts = group.split(":");
                    for (String suffix : parts[1].split(",")) {
                        type("forestry." + parts[0] + ".gui.Container" + suffix);
                        type("forestry." + parts[0] + ".gui.Gui" + suffix);
                        System.out.println("PASS installed Forestry class/mixin loading: " + suffix);
                    }
                }
                type("forestry.core.utils.SlotUtil");
                type("forestry.core.gui.ledgers.LedgerManager");
                type("forestry.core.network.PacketHandler");
                break;
            }
            case "forestry-ledger-check": {
                ObjectReference screen = field(client, "currentScreen");
                if (!screen.referenceType().name().startsWith("forestry.")) throw new IllegalStateException("Forestry GUI required");
                ObjectReference panel = (ObjectReference) call(screen, "dynamicinventory$panel", "()Ldev/gilbe/dynamicinventory/client/NativeInventoryPanel;");
                if (panel == null) throw new AssertionError("Forestry panel missing");
                int left = ((IntegerValue) call(panel, "nativeHeaderLeft", "()I")).value();
                int top = ((IntegerValue) call(panel, "nativeHeaderTop", "()I")).value();
                int nativeWidth = ((IntegerValue) call(panel, "nativeUiWidth", "()I")).value();
                if (((IntegerValue) call(screen, "getGuiLeft", "()I")).value() != left || ((IntegerValue) call(screen, "getGuiTop", "()I")).value() != top)
                    throw new AssertionError("Forestry widget origin mismatch");
                ObjectReference manager = field(screen, "ledgerManager"), ledgers = field(manager, "ledgers");
                int count = ((IntegerValue) call(ledgers, "size", "()I")).value(), y = top + 8, checked = 0;
                for (int i = 0; i < count; i++) {
                    ObjectReference ledger = (ObjectReference) call(ledgers, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(i));
                    if (!((BooleanValue) call(ledger, "isVisible", "()Z")).value()) continue;
                    ObjectReference hit = (ObjectReference) call(manager, "getAtPosition", "(II)Lforestry/core/gui/ledgers/Ledger;", vm.mirrorOf(left + nativeWidth + 4), vm.mirrorOf(y + 4));
                    if (!ledger.equals(hit)) throw new AssertionError("Forestry ledger hit/draw origin mismatch: " + ledger.referenceType().name());
                    y += ((IntegerValue) call(ledger, "getHeight", "()I")).value();
                    checked++;
                }
                if (checked == 0) throw new AssertionError("No visible ledgers tested");
                System.out.println("PASS installed Forestry widget origin and native ledger hit testing: " + checked + " visible side tabs");
                break;
            }
            case "forestry-transfer-check": {
                // Detached client fixture; authoritative world/player inventories are never changed.
                act(new String[]{"require-qualification"});
                ObjectReference originalInventory = field(player, "inventory"), originalContainer = field(player, "openContainer");
                ObjectReference detached = make("net.minecraft.entity.player.InventoryPlayer", "(Lnet/minecraft/entity/player/EntityPlayer;)V", player);
                ObjectReference tile = make("forestry.factory.tiles.TileCarpenter", "()V");
                ObjectReference container = make("forestry.factory.gui.ContainerCarpenter", "(Lnet/minecraft/entity/player/InventoryPlayer;Lforestry/factory/tiles/TileCarpenter;)V", detached, tile);
                ClassType api = type("dev.gilbe.dynamicinventory.api.InventoryUi");
                ObjectReference binding = (ObjectReference) api.invokeMethod(thread, methods(api, "attach", "(Lnet/minecraft/inventory/Container;Lnet/minecraft/entity/player/InventoryPlayer;)Ldev/gilbe/dynamicinventory/api/PlayerInventoryBinding;").getFirst(), List.of(container, detached), ObjectReference.INVOKE_SINGLE_THREADED);
                int size = ((IntegerValue) call(binding, "getOrdinarySlotCount", "()I")).value();
                if (size <= 36) throw new AssertionError("Expanded inventory required");
                int lastId = ((IntegerValue) call(binding, "getContainerSlotId", "(I)I", vm.mirrorOf(size - 1))).value();
                ObjectReference lastSlot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(lastId));
                ClassType items = type("net.minecraft.init.Items");
                ObjectReference full = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", items.getValue(fieldInfo(items, "stick")), vm.mirrorOf(64));
                ObjectReference probe = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", items.getValue(fieldInfo(items, "diamond")), vm.mirrorOf(7));
                ObjectReference tag = make("net.minecraft.nbt.NBTTagCompound", "()V");
                call(tag, "setByte", "(Ljava/lang/String;B)V", vm.mirrorOf("typed"), vm.mirrorOf((byte) 1));
                call(tag, "setLong", "(Ljava/lang/String;J)V", vm.mirrorOf("serial"), vm.mirrorOf(17L));
                call(probe, "setTagCompound", "(Lnet/minecraft/nbt/NBTTagCompound;)V", tag);
                String expectedTag = canonicalNbt(tag);
                try {
                    player.setValue(fieldInfo(player.referenceType(), "inventory"), detached);
                    player.setValue(fieldInfo(player.referenceType(), "openContainer"), container);
                    call(lastSlot, "putStack", "(Lnet/minecraft/item/ItemStack;)V", probe);
                    if (call(container, "transferStackInSlot", "(Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;", player, vm.mirrorOf(lastId)) == null)
                        throw new AssertionError("Extra player slot did not reach machine");
                    ObjectReference firstInput = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(36));
                    ObjectReference moved = (ObjectReference) call(firstInput, "getStack", "()Lnet/minecraft/item/ItemStack;");
                    if (moved != null) moved.disableCollection(); // Root it before clearing the detached source slot.
                    if (call(lastSlot, "getStack", "()Lnet/minecraft/item/ItemStack;") != null || moved == null || !canonicalNbt((ObjectReference) call(moved, "getTagCompound", "()Lnet/minecraft/nbt/NBTTagCompound;")).equals(expectedTag))
                        throw new AssertionError("Player to machine conservation/NBT");
                    call(firstInput, "putStack", "(Lnet/minecraft/item/ItemStack;)V", (Value) null);
                    for (int logical = 0; logical < size - 1; logical++) {
                        int id = ((IntegerValue) call(binding, "getContainerSlotId", "(I)I", vm.mirrorOf(logical))).value();
                        ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(id));
                        call(slot, "putStack", "(Lnet/minecraft/item/ItemStack;)V", call(full, "copy", "()Lnet/minecraft/item/ItemStack;"));
                    }
                    ObjectReference output = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(56));
                    call(output, "putStack", "(Lnet/minecraft/item/ItemStack;)V", moved);
                    if (call(container, "transferStackInSlot", "(Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;", player, vm.mirrorOf(56)) == null)
                        throw new AssertionError("Machine output did not reach final extra slot");
                    ObjectReference returned = (ObjectReference) call(lastSlot, "getStack", "()Lnet/minecraft/item/ItemStack;");
                    if (returned == null || ((IntegerValue) returned.getValue(fieldInfo(returned.referenceType(), "stackSize"))).value() != 7 || !canonicalNbt((ObjectReference) call(returned, "getTagCompound", "()Lnet/minecraft/nbt/NBTTagCompound;")).equals(expectedTag))
                        throw new AssertionError("Machine to player conservation/NBT");
                    for (int id = 36; id < 54; id++) {
                        ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(id));
                        call(slot, "putStack", "(Lnet/minecraft/item/ItemStack;)V", call(full, "copy", "()Lnet/minecraft/item/ItemStack;"));
                    }
                    if (call(container, "transferStackInSlot", "(Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;", player, vm.mirrorOf(lastId)) != null)
                        throw new AssertionError("Full/filtered/phantom destinations accepted diamond");
                    for (int id = 56; id < 67; id++) {
                        ObjectReference slot = (ObjectReference) call(container, "getSlot", "(I)Lnet/minecraft/inventory/Slot;", vm.mirrorOf(id));
                        if (call(slot, "getStack", "()Lnet/minecraft/item/ItemStack;") != null) throw new AssertionError("Output/preview/phantom slot filled: " + id);
                    }
                    System.out.println("PASS detached native Forestry transfers: both directions through final extra slot, typed NBT/count, full and filtered destinations, phantom/output exclusion; slots=" + size);
                } finally {
                    player.setValue(fieldInfo(player.referenceType(), "openContainer"), originalContainer);
                    player.setValue(fieldInfo(player.referenceType(), "inventory"), originalInventory);
                }
                break;
            }
            case "ae2-workbench-side-check": {
                act(new String[]{"require-qualification","DI-Baseline-Storage-Panel-Lab-Qualification"});
                ae2WorkbenchSideCheck(player);
                break;
            }
            case "native-batch-class-check": nativeBatchClassCheck(); break;
            case "registered-items": registeredItems(args); break;
            case "batch-lab-install-stations": batchLabInstall(args); break;
            case "batch-lab-install-supplies": batchLabSupplies(args); break;
            case "ae2-class-check": {
                // No world or inventory mutation: force the new client mixins through the installed
                // production class loader before asking the user to review the actual machines.
                for (String suffix : List.of("Wireless", "Security", "QNB", "SpatialIOPort",
                        "SpatialLinkChamber", "Interface", "CellWorkbench", "MEMonitorable", "CraftingTerm",
                        "PatternTerm", "PatternTermEx", "MEPortableCell", "InterfaceTerminal", "CraftingDiagnosticTerminal")) {
                    type("appeng.container.implementations.Container" + suffix);
                    type("appeng.client.gui.implementations.Gui" + suffix);
                    System.out.println("PASS installed AE2 class/mixin loading: " + suffix);
                }
                break;
            }
            case "ae2-slot-check": {
                act(new String[]{"require-qualification"});
                ObjectReference container = field(player, "openContainer");
                if (!container.referenceType().name().startsWith("appeng.container.implementations.Container"))
                    throw new IllegalStateException("Open an AE2 container first");
                ObjectReference slots = field(container, "inventorySlots");
                ObjectReference inventory = field(player, "inventory");
                ObjectReference locked = field(container, "locked");
                int count = ((IntegerValue) call(slots, "size", "()I")).value();
                int checked = 0, disabled = 0;
                for (int id = 0; id < count; id++) {
                    ObjectReference slot = (ObjectReference) call(slots, "get", "(I)Ljava/lang/Object;", vm.mirrorOf(id));
                    if (!inventory.equals(field(slot, "inventory"))) continue;
                    int raw = ((IntegerValue) call(slot, "getSlotIndex", "()I")).value();
                    Value boxed = type("java.lang.Integer").invokeMethod(thread,
                        type("java.lang.Integer").methodsByName("valueOf", "(I)Ljava/lang/Integer;").getFirst(),
                        List.of(vm.mirrorOf(raw)), ObjectReference.INVOKE_SINGLE_THREADED);
                    boolean isLocked = ((BooleanValue) call(locked, "contains", "(Ljava/lang/Object;)Z", boxed)).value();
                    boolean isDisabled = slot.referenceType().name().equals("appeng.container.slot.SlotDisabled");
                    if (isLocked != isDisabled) throw new AssertionError("AE2 lock mismatch at raw slot " + raw);
                    if (!((BooleanValue) call(slot, "isPlayerSide", "()Z")).value())
                        throw new AssertionError("AE2 player-side flag missing at raw slot " + raw);
                    if (!container.equals(call(slot, "getContainer", "()Lappeng/container/AEBaseContainer;")))
                        throw new AssertionError("AE2 container callback owner missing at raw slot " + raw);
                    if (isDisabled) disabled++;
                    checked++;
                }
                if (checked != ownedLayoutInt(inventory, "mainSlots")) throw new AssertionError("AE2 player slot count mismatch");
                System.out.println("AE2 slots=" + checked + " disabled=" + disabled + " native flags, locks and callback owners preserved");
                break;
            }
            case "ae2-adaptor-check": {
                if (player == null) throw new IllegalStateException("A player is required for a detached inventory fixture");
                ClassType items = type("net.minecraft.init.Items");
                for (boolean swap : new boolean[]{false, true}) {
                    ObjectReference detached = make("net.minecraft.entity.player.InventoryPlayer", "(Lnet/minecraft/entity/player/EntityPlayer;)V", player);
                    ArrayReference stacks = (ArrayReference) field(detached, "mainInventory");
                    ObjectReference shape = (ObjectReference) type("dev.gilbe.dynamicinventory.inventory.InventoryLayout").invokeMethod(thread,
                        methods(type("dev.gilbe.dynamicinventory.inventory.InventoryLayout"), "of", "(Lnet/minecraft/entity/player/InventoryPlayer;)Ldev/gilbe/dynamicinventory/inventory/InventoryShape;").getFirst(), List.of(detached), ObjectReference.INVOKE_SINGLE_THREADED);
                    int size = ((IntegerValue) call(shape, "mainSlots", "()I")).value();
                    int lastRaw = ((IntegerValue) call(shape, "rawIndex", "(I)I", vm.mirrorOf(size - 1))).value();
                    ObjectReference full = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", items.getValue(fieldInfo(items, "stick")), vm.mirrorOf(64));
                    for (int logical = 0; logical < size - 1; logical++) {
                        int raw = ((IntegerValue) call(shape, "rawIndex", "(I)I", vm.mirrorOf(logical))).value();
                        stacks.setValue(raw, full);
                    }
                    ObjectReference wrapper = make("appeng.util.inv.AdaptorPlayerInventory", "(Lnet/minecraft/inventory/IInventory;Z)V", detached, vm.mirrorOf(swap));
                    if (((IntegerValue) call(wrapper, "getSizeInventory", "()I")).value() != size) throw new AssertionError("AE2 wrapper capacity");
                    ObjectReference adaptor = make("appeng.util.inv.AdaptorIInventory", "(Lnet/minecraft/inventory/IInventory;)V", wrapper);
                    ObjectReference input = make("net.minecraft.item.ItemStack", "(Lnet/minecraft/item/Item;I)V", items.getValue(fieldInfo(items, "diamond")), vm.mirrorOf(64));
                    if (call(adaptor, "simulateAdd", "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", input) != null || stacks.getValue(lastRaw) != null) throw new AssertionError("AE2 final-slot simulation");
                    if (call(adaptor, "addItems", "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", input) != null || stacks.getValue(lastRaw) == null) throw new AssertionError("AE2 final-slot insertion");
                    if (stacks.length() > size && stacks.getValue(36) != null) throw new AssertionError("AE2 inserted into offhand");
                    if (call(adaptor, "simulateAdd", "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", input) == null) throw new AssertionError("AE2 full destination overflow");
                    int lastLogical = swap ? size - 10 : size - 1;
                    Value removed = call(wrapper, "getStackInSlotOnClosing", "(I)Lnet/minecraft/item/ItemStack;", vm.mirrorOf(lastLogical));
                    if (removed == null || stacks.getValue(lastRaw) != null) throw new AssertionError("AE2 closing-slot mapping");
                    call(wrapper, "setInventorySlotContents", "(ILnet/minecraft/item/ItemStack;)V", vm.mirrorOf(lastLogical), removed);
                    if (call(wrapper, "decrStackSize", "(II)Lnet/minecraft/item/ItemStack;", vm.mirrorOf(lastLogical), vm.mirrorOf(1)) == null) throw new AssertionError("AE2 decrement mapping");
                    System.out.println("PASS detached AE2 adaptor: size=" + size + " swap=" + swap + " final-slot simulate/insert/full/closing/decrement; offhand excluded");
                }
                break;
            }
            case "bauble-use-state": {
                act(new String[]{"require-qualification"});
                ObjectReference server = (ObjectReference) call(client, "getIntegratedServer", "()Lnet/minecraft/server/integrated/IntegratedServer;");
                ObjectReference manager = (ObjectReference) call(server, "getConfigurationManager", "()Lnet/minecraft/server/management/ServerConfigurationManager;");
                ObjectReference serverPlayer = (ObjectReference) call(manager, "func_152612_a", "(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;", call(player, "getCommandSenderName", "()Ljava/lang/String;"));
                for (ObjectReference actor : new ObjectReference[]{player, serverPlayer}) {
                    ClassType api = type("baubles.api.BaublesApi");
                    ObjectReference equipment = (ObjectReference) api.invokeMethod(thread, methods(api, "getBaubles", "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/IInventory;").getFirst(), List.of(actor), ObjectReference.INVOKE_SINGLE_THREADED);
                    int belts = 0;
                    int size = ((IntegerValue) call(equipment, "getSizeInventory", "()I")).value();
                    for (int index = 0; index < size; index++) {
                        ObjectReference stack = (ObjectReference) call(equipment, "getStackInSlot", "(I)Lnet/minecraft/item/ItemStack;", vm.mirrorOf(index));
                        if (stack != null && ((ObjectReference) call(stack, "getItem", "()Lnet/minecraft/item/Item;")).referenceType().name().endsWith("BaubleUpgradeSupport")) belts++;
                    }
                    ObjectReference held = (ObjectReference) call(actor, "getHeldItem", "()Lnet/minecraft/item/ItemStack;");
                    ObjectReference registry = (ObjectReference) type("net.minecraft.item.Item").getValue(fieldInfo(type("net.minecraft.item.Item"), "itemRegistry"));
                    String name = held == null ? "empty" : ((StringReference) call(registry, "getNameForObject", "(Ljava/lang/Object;)Ljava/lang/String;", call(held, "getItem", "()Lnet/minecraft/item/Item;"))).value();
                    if (belts != Integer.parseInt(args[1]) || !name.equals(args[2])) throw new AssertionError("Baubles use state: belts=" + belts + " held=" + name);
                    System.out.println("PASS " + (actor.equals(player) ? "client" : "server") + " equipment belts=" + belts + " held=" + name);
                }
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
            case "look": {
                act(new String[]{"require-qualification"});
                float yaw = Float.parseFloat(args[1]), pitch = Float.parseFloat(args[2]);
                for (String name : new String[]{"rotationYaw", "prevRotationYaw"})
                    player.setValue(fieldInfo(player.referenceType(), name), vm.mirrorOf(yaw));
                for (String name : new String[]{"rotationPitch", "prevRotationPitch"})
                    player.setValue(fieldInfo(player.referenceType(), name), vm.mirrorOf(pitch));
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
            case "vanilla-panel-check": {
                act(new String[]{"require-qualification", args[1]});
                String fixtures=System.getProperty("dinv.vanillaFixtures");
                if (fixtures != null) {
                    if (!fixtures.replace('\\','/').endsWith("/build/classes/java/packTest")) throw new IllegalArgumentException("Explicit pack fixture directory required");
                    ObjectReference url=(ObjectReference)call((ObjectReference)call(make("java.io.File","(Ljava/lang/String;)V",vm.mirrorOf(fixtures)),"toURI",null),"toURL",null);
                    call(loader,"addURL","(Ljava/net/URL;)V",url);
                    call(field(loader,"invalidClasses"),"remove","(Ljava/lang/Object;)Z",vm.mirrorOf("dev.gilbe.dynamicinventory.qualification.VanillaPanelClientFixtures"));
                }
                ClassType fixture=type("dev.gilbe.dynamicinventory.qualification.VanillaPanelClientFixtures");
                fixture.invokeMethod(thread,fixture.methodsByName("run").getFirst(),List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                break;
            }
            case "commands-file": {
                act(new String[]{"require-qualification", args[1]});
                for (String command : java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[2]))) {
                    if (command.isBlank() || command.startsWith("#")) continue;
                    if (!command.startsWith("/") || command.length() > 32767) throw new IllegalArgumentException("Invalid fixture command");
                    call(player,"sendChatMessage","(Ljava/lang/String;)V",vm.mirrorOf(command));
                }
                break;
            }
            case "lab-chunks-ready": {
                act(new String[]{"require-qualification", args[1]});
                ObjectReference server=(ObjectReference)call(client,"getIntegratedServer","()Lnet/minecraft/server/integrated/IntegratedServer;");
                ObjectReference world=(ObjectReference)call(server,"worldServerForDimension","(I)Lnet/minecraft/world/WorldServer;",vm.mirrorOf(0));
                for (int x : new int[]{-16,16}) for (int z : new int[]{-16,48}) {
                    if (!((BooleanValue)call(world,"blockExists","(III)Z",vm.mirrorOf(x),vm.mirrorOf(4),vm.mirrorOf(z))).value())
                        throw new IllegalStateException("Vanilla lab chunks are still loading");
                }
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
            case "lab-entities": {
                act(new String[]{"require-qualification", args[1]});
                System.out.println("player x="+player.getValue(fieldInfo(player.referenceType(),"posX"))+" y="+player.getValue(fieldInfo(player.referenceType(),"posY"))+" z="+player.getValue(fieldInfo(player.referenceType(),"posZ")));
                ArrayReference entries=(ArrayReference)call(field(field(client,"theWorld"),"loadedEntityList"),"toArray","()[Ljava/lang/Object;");
                for(Value entry:entries.getValues()) {
                    ObjectReference entity=(ObjectReference)entry;
                    String name=((StringReference)call(entity,"getCommandSenderName","()Ljava/lang/String;")).value();
                    if(name.startsWith("Lab ") || entity.referenceType().name().contains("Cart") || entity.referenceType().name().contains("Minecart"))
                        System.out.println(entity.referenceType().name()+" name="+name+" x="+entity.getValue(fieldInfo(entity.referenceType(),"posX"))+" z="+entity.getValue(fieldInfo(entity.referenceType(),"posZ")));
                }
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
                    List<String> selectors=Arrays.asList(args[1].split("\\|"));
                    if ((name != null && selectors.contains(name.value())) || selectors.contains(entity.referenceType().name())) selected = entity;
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
                int side=args.length>4?Integer.parseInt(args[4]):1;
                if(side<0||side>5)throw new IllegalArgumentException("Block face 0..5 required");
                double hitX=.5,hitY=.5,hitZ=.5;
                if(args.length>4)switch(side) {
                    case 0 -> hitY=.01; case 1 -> hitY=.99; case 2 -> hitZ=.01;
                    case 3 -> hitZ=.99; case 4 -> hitX=.01; case 5 -> hitX=.99;
                }
                ObjectReference vec = (ObjectReference) type("net.minecraft.util.Vec3").invokeMethod(thread,
                    methods(type("net.minecraft.util.Vec3"), "createVectorHelper", "(DDD)Lnet/minecraft/util/Vec3;").getFirst(),
                    List.of(vm.mirrorOf(x + hitX), vm.mirrorOf(y + hitY), vm.mirrorOf(z + hitZ)), ObjectReference.INVOKE_SINGLE_THREADED);
                Value held = call(player, "getHeldItem", "()Lnet/minecraft/item/ItemStack;");
                call(field(client, "playerController"), "onPlayerRightClick",
                    "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIILnet/minecraft/util/Vec3;)Z",
                    player, field(client, "theWorld"), held, vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(z), vm.mirrorOf(side), vec);
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
                            ReferenceType target=clazz;
                            String tick="runTick";
                            if(args[0].startsWith("ae2-lab-install-") || args[0].startsWith("batch-lab-install-")) {
                                target=vm.classesByName("net.minecraft.server.MinecraftServer").getFirst();
                                tick="tick";
                            }
                            BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(methods(target, tick, "()V").getFirst().location());
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
