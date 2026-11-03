import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.util.*;

/** Read-only rejection capture in an already running client. No screen actions, packet sends or inventory writes. */
public final class OpeningWatch {
    static Value field(ObjectReference object,String... names) {
        if(object==null)return null;
        for(String name:names) {
            Field f=object.referenceType().fieldByName(name);
            if(f!=null)return object.getValue(f);
        }
        return null;
    }
    static String text(Value value) {
        return value instanceof StringReference s?s.value():String.valueOf(value);
    }
    static List<Value> entries(ObjectReference list) {
        ArrayReference array=(ArrayReference)field(list,"elementData");
        IntegerValue size=(IntegerValue)field(list,"size");
        if(array==null||size==null)throw new IllegalStateException("Unexpected slot list "+list.referenceType().name());
        return array.getValues(0,size.value());
    }
    static String hex(ArrayReference bytes) {
        if(bytes==null)return "null";
        StringBuilder value=new StringBuilder();
        for(Value b:bytes.getValues())value.append(String.format("%02x",((ByteValue)b).value()&255));
        return value.toString();
    }
    static String type(Value value) {
        return value instanceof ObjectReference object?object.referenceType().name():text(value);
    }
    static String item(Value value) {
        if(!(value instanceof ObjectReference stack))return type(value);
        return type(field(stack,"item","field_151002_e"))+" count="+field(stack,"stackSize","field_77994_a")
            +" damage="+field(stack,"itemDamage","field_77991_e");
    }
    static void capture(VirtualMachine vm,ThreadReference thread,ObjectReference gate,String reason) throws Exception {
        ObjectReference container=(ObjectReference)field(gate,"container");
        ObjectReference offer=(ObjectReference)field(gate,"offer");
        System.out.println("REJECTION "+java.time.Instant.now()+" reason="+reason);
        System.out.println("window="+field(gate,"window")+" nonce="+field(offer,"nonce"));
        System.out.println("serverDigest="+hex((ArrayReference)field(offer,"digest")));
        System.out.println("container="+(container==null?"null":container.referenceType().name()));
        if(container==null)return;
        ObjectReference region=(ObjectReference)field(container,"dynamicinventory$region");
        ObjectReference owner=(ObjectReference)field(region,"inventory");
        System.out.println("nativeSlots="+field(region,"nativeSlotCount")+" shape="+field(region,"shape"));
        System.out.println("targetSlotIndex="+field(container,"targetSlotIndex"));
        ObjectReference locked=(ObjectReference)field(container,"locked");
        if(locked!=null) {
            Method toString=locked.referenceType().methodsByName("toString","()Ljava/lang/String;").getFirst();
            System.out.println("locked="+text(locked.invokeMethod(thread,toString,List.of(),ObjectReference.INVOKE_SINGLE_THREADED)));
        }
        if(owner!=null) {
            ClassType protocol=(ClassType)vm.classesByName("dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol").getFirst();
            Method describe=protocol.methodsByName("describe").getFirst();
            System.out.println("clientDigest="+hex((ArrayReference)protocol.invokeMethod(thread,describe,List.of(container,owner),ObjectReference.INVOKE_SINGLE_THREADED)));
        }
        Map<Long,Integer> groups=new HashMap<>();
        if(owner!=null)groups.put(owner.uniqueID(),0);
        List<Value> slots=entries((ObjectReference)field(container,"inventorySlots","field_75151_b"));
        System.out.println("totalSlots="+slots.size());
        for(Value value:slots) {
            ObjectReference slot=(ObjectReference)value,inventory=(ObjectReference)field(slot,"inventory","field_75224_c");
            long key=inventory==null?-1:inventory.uniqueID();
            int group=groups.computeIfAbsent(key,ignored->groups.size());
            System.out.println("SLOT id="+field(slot,"slotNumber","field_75222_d")+" raw="+field(slot,"slotIndex","field_75225_a")
                +" group="+group+" type="+slot.referenceType().name()+" inventory="+(inventory==null?"null":inventory.referenceType().name()));
        }
        System.out.println("END_REJECTION");
        System.out.flush();
    }
    public static void main(String[] args) throws Exception {
        AttachingConnector connector=Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c->c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String,Connector.Argument> connection=connector.defaultArguments();
        connection.get("hostname").setValue("localhost");
        connection.get("port").setValue(args.length>0?args[0]:"5010");
        VirtualMachine vm=connector.attach(connection);
        try {
            List<ReferenceType> types=vm.classesByName("dev.gilbe.dynamicinventory.client.ClientInventoryGate");
            if(types.isEmpty())throw new IllegalStateException("Opening gate is not loaded in this client");
            BreakpointRequest request=vm.eventRequestManager().createBreakpointRequest(types.getFirst().methodsByName("refuse").getFirst().location());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
            MethodExitRequest nativeExit=null;
            Map<BreakpointRequest,String> nativeEntries=new HashMap<>();
            List<ReferenceType> bridges=vm.classesByName("appeng.core.sync.GuiBridge");
            // Native method-exit tracing can perturb a live JVM; keep it opt-in for disposable fixtures only.
            if(args.length>2&&args[2].equals("trace-native")&&!bridges.isEmpty()) {
                nativeExit=vm.eventRequestManager().createMethodExitRequest();
                nativeExit.addClassFilter(bridges.getFirst());
                nativeExit.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                for(String name:List.of("getClientGuiElement","getGuiObject")) {
                    Method method=bridges.getFirst().methodsByName(name).getFirst();
                    BreakpointRequest entry=vm.eventRequestManager().createBreakpointRequest(method.location());
                    entry.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                    nativeEntries.put(entry,name);
                    entry.enable();
                }
            }
            int minutes=args.length>1?Integer.parseInt(args[1]):60;
            System.out.println("WATCHING client inventory refusals; no navigation or item changes. Timeout "+minutes+" minutes.");
            System.out.flush();
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(minutes);
            while(System.nanoTime()<end) {
                EventSet events=vm.eventQueue().remove(1000);
                if(events==null)continue;
                try {
                    for(Event event:events)if(event instanceof BreakpointEvent hit) {
                        StackFrame frame=hit.thread().frame(0);
                        String nativeName=nativeEntries.get(hit.request());
                        if(nativeName!=null) {
                            List<Value> arguments=frame.getArgumentValues();
                            if(nativeName.equals("getClientGuiElement")) {
                                System.out.println("AE_OPEN "+java.time.Instant.now()+" ordinal="+arguments.get(0)
                                    +" x="+arguments.get(3)+" y="+arguments.get(4)+" z="+arguments.get(5));
                                if(nativeExit!=null)nativeExit.enable();
                            } else System.out.println("AE_ITEM "+item(arguments.get(0)));
                            System.out.flush();
                            continue;
                        }
                        String reason=text(frame.getArgumentValues().get(1));
                        request.disable();
                        try {capture(vm,hit.thread(),frame.thisObject(),reason);}finally{request.enable();}
                    } else if(event instanceof MethodExitEvent exit) {
                        String name=exit.method().name();
                        if(List.of("getGuiObject","CorrectTileOrPart","ConstructGui","getClientGuiElement").contains(name)) {
                            System.out.println("AE_RETURN "+name+" "+type(exit.returnValue()));
                            System.out.flush();
                        }
                        if(name.equals("getClientGuiElement")&&nativeExit!=null)nativeExit.disable();
                    } else if(event instanceof VMDisconnectEvent)return;
                } finally {events.resume();}
            }
            System.out.println("WATCH_TIMEOUT");
        } finally {try{vm.dispose();}catch(VMDisconnectedException ignored){}}
    }
}
