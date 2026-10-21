package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.IntegrationProfile;

/** Run on a plain JDK classpath to prove early profile selection never links Forge or Minecraft. */
public final class IntegrationProfileFixtures {

    public static void main(String[] args) {
        IntegrationProfile selected = IntegrationProfile.selected();
        check(
            selected.profileName()
                .equals(args[0]),
            "Exact launch identity");
        check(
            selected.packTarget()
                .equals(args[1]),
            "Exact pack target");
        check(
            selected.version("Backhand")
                .equals(args[2]),
            "Profile-specific offhand version");
        check(selected.usesPackAdapters() == Boolean.parseBoolean(args[3]), "Pack mixin decision");
        check(selected.usesBackhand() == Boolean.parseBoolean(args[4]), "Early offhand decision");
        BackhandCompatibility.configure(true);
        check(BackhandCompatibility.enabled() == selected.usesBackhand(), "Early loader agrees");
        check(
            BackhandCompatibility.supportedVersion()
                .equals(args[2]),
            "Network version agrees");
        BackhandCompatibility.configure(false);
        check(!BackhandCompatibility.enabled(), "Absent offhand coremod stays disabled");
        System.setProperty("dynamicinventory.profile", "unknown-after-startup");
        check(IntegrationProfile.selected() == selected, "Launch selection cannot change mid-session");
        try {
            IntegrationProfile.named("daily");
            throw new AssertionError("Moving aliases must not select unqualified versions");
        } catch (IllegalArgumentException expected) {}
        try {
            selected.version("missing-module");
            throw new AssertionError("Missing pins must fail closed");
        } catch (IllegalStateException expected) {}
        System.out.println("PASS: 11 integration-profile checks for " + args[0]);
    }

    private static void check(boolean pass, String description) {
        if (!pass) throw new AssertionError(description);
    }
}
