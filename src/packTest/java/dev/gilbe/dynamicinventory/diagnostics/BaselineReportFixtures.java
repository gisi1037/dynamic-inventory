package dev.gilbe.dynamicinventory.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

/** Automatic login capture is opt-in and retention only touches the chosen side's automatic reports. */
public final class BaselineReportFixtures {

    private BaselineReportFixtures() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get("."), "dinv-diagnostics");
        Path config = Files.createDirectories(root.resolve("config"));
        var home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, root.toFile());
        BaselineDiagnostics.initialize(config.toFile());
        BaselineDiagnostics.onJoin(null, null);
        if (Files.exists(root.resolve(Paths.get("diagnostics", "dynamicinventory"))))
            throw new AssertionError("Default login must not capture an inventory");
        String suffix = "-20261122-020000-000-1.json";
        for (String prefix : new String[] { "auto-server", "auto-client", "server" })
            Files.createFile(root.resolve(prefix + suffix));
        Path newest = Files.createFile(root.resolve("auto-server-20261122-020001-000-2.json"));
        Files.setLastModifiedTime(root.resolve("auto-server" + suffix), FileTime.fromMillis(1000));
        BaselineDiagnostics.pruneAutomaticReports(root.toFile(), "server", 1);
        if (Files.exists(root.resolve("auto-server" + suffix)) || !Files.exists(newest)
            || !Files.exists(root.resolve("auto-client" + suffix))
            || !Files.exists(root.resolve("server" + suffix)))
            throw new AssertionError("Retention must preserve the newest report, other side and manual reports");
        System.out.println("PASS opt-in diagnostics and automatic report retention");
    }
}
