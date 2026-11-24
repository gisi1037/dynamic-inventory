package dev.gilbe.dynamicinventory.qualification;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.mixins.EarlyMixinsLoader;
import dev.gilbe.dynamicinventory.mixins.minecraft.HotbarPacketOrderMixin;

/** Checks that the hook exercised by HotbarBankFixtures actually targets the ordered play queue. */
public final class HotbarPacketOrderFixtures {

    private static final String MANAGER = "net/minecraft/network/NetworkManager";
    private static final String PACKET = "net/minecraft/network/Packet";
    private static final String DISPATCH = "(Lnet/minecraft/network/INetHandler;)V";

    private HotbarPacketOrderFixtures() {}

    public static void main(String[] args) throws Exception {
        Field side = FMLLaunchHandler.class.getDeclaredField("side");
        side.setAccessible(true);
        Object previous = side.get(null);
        try {
            side.set(null, Side.SERVER);
            if (!new EarlyMixinsLoader().getMixins(Collections.emptySet())
                .contains("minecraft.HotbarPacketOrderMixin"))
                throw new AssertionError("Dedicated server does not register the hotbar packet hook");
        } finally {
            side.set(null, previous);
        }
        Method hook = Arrays.stream(HotbarPacketOrderMixin.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(WrapOperation.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing hotbar packet wrapper"));
        WrapOperation wrap = hook.getAnnotation(WrapOperation.class);
        At at = wrap.at()[0];
        if (!Arrays.equals(wrap.method(), new String[] { "processReceivedPackets" }) || wrap.require() != 1
            || !wrap.remap()
            || !"INVOKE".equals(at.value())
            || !("L" + PACKET + ";processPacket" + DISPATCH).equals(at.target()))
            throw new AssertionError("Hotbar wrapper must target the required, remapped Forge packet dispatch");
        try (InputStream input = HotbarPacketOrderFixtures.class.getResourceAsStream("/" + MANAGER + ".class")) {
            ClassNode manager = read(input);
            MethodNode queue = method(manager, "processReceivedPackets");
            MethodInsnNode poll = findCall(queue.instructions.getFirst(), "java/util/Queue", "poll");
            MethodInsnNode dispatch = findCall(poll.getNext(), PACKET, "processPacket");
            if (!DISPATCH.equals(dispatch.desc)) throw new AssertionError("Forge packet dispatch descriptor changed");
        }
        System.out.println("PASS dedicated hotbar registration and ordered Forge packet dispatch target");

        String export = System.getenv("DYNAMICINVENTORY_NETWORK_EXPORT");
        if (export == null || export.isEmpty()) return;
        try (InputStream input = Files.newInputStream(Paths.get(export))) {
            ClassNode manager = read(input);
            // Mixin exports the production (SRG) queue and an installation-specific wrapper prefix.
            MethodNode queue = method(manager, "func_74428_b");
            MethodInsnNode poll = findCall(queue.instructions.getFirst(), "java/util/Queue", "poll");
            MethodInsnNode wrapper = findCall(poll.getNext(), MANAGER, "$" + hook.getName());
            MethodNode wrapped = method(manager, wrapper.name);
            MethodInsnNode nativeDispatch = findCall(
                wrapped.instructions.getFirst(),
                "com/llamalad7/mixinextras/injector/wrapoperation/Operation",
                "call");
            findCall(nativeDispatch.getNext(), "dev/gilbe/dynamicinventory/network/HotbarRequest", "applyPending");
        }
        System.out.println("PASS stock server export dispatches the hotbar request before polling the next packet");
    }

    private static ClassNode read(InputStream input) throws Exception {
        ClassNode result = new ClassNode();
        new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!MANAGER.equals(result.name)) throw new AssertionError("Expected Minecraft NetworkManager");
        return result;
    }

    private static MethodNode method(ClassNode owner, String name) {
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode) value;
            if (name.equals(method.name)) return method;
        }
        throw new AssertionError("Missing " + owner.name + "." + name);
    }

    private static MethodInsnNode findCall(AbstractInsnNode first, String owner, String name) {
        for (AbstractInsnNode insn = first; insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && owner.equals(call.owner)
                && (call.name.equals(name) || (name.startsWith("$") && call.name.endsWith(name)))) return call;
        }
        throw new AssertionError("Missing ordered call to " + owner + "." + name);
    }
}
