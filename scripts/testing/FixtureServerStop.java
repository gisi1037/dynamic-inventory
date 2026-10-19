import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Properties;

/** Clean shutdown for this workspace's headless Gradle fixture when its console stdin is closed. */
public final class FixtureServerStop {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("pid agentJar expectedServerDirectory");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            Properties properties = vm.getSystemProperties();
            Path actual = Path.of(properties.getProperty("user.dir")).toRealPath();
            Path expected = Path.of(args[2]).toRealPath();
            if (!actual.equals(expected) || !expected.getFileName().toString().equals("server")
                || !properties.getProperty("sun.java.command", "").contains("GradleStartServer"))
                throw new IllegalStateException("Target is not the exact disposable Gradle server directory");
            vm.loadAgent(Path.of(args[1]).toRealPath().toString(), "stop");
            System.out.println("Requested clean fixture server shutdown for PID " + args[0]);
        } finally { vm.detach(); }
    }

    public static void agentmain(String action, Instrumentation instrumentation) throws Exception {
        if (!"stop".equals(action)) throw new IllegalArgumentException("stop action required");
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (!type.getName().equals("net.minecraft.server.MinecraftServer")) continue;
            Object server = type.getMethod("getServer").invoke(null);
            if (server == null) continue;
            type.getMethod("initiateShutdown").invoke(server);
            return;
        }
        throw new IllegalStateException("No running MinecraftServer singleton in target");
    }
}
