package dev.gilbe.dynamicinventory.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * One launch identity for early offhand setup, runtime guards and the build's dependency pins.
 * This class uses only the JDK: early mixins call it before Forge or Minecraft classes are safe to load.
 * Daily profiles remain experimental until their separate runtime qualification passes.
 */
public enum IntegrationProfile {

    ISOLATED("isolated", "rc2", false, false),
    RC2_MUI2("rc2-mui2", "rc2", false, false),
    RC2_BACKHAND("rc2-backhand", "rc2", true, false),
    RC2("rc2-integration", "rc2", true, true),
    DAILY_2026_10_19("daily-2026-10-19", "daily-2026-10-19", true, true);

    private static final Properties PINS = loadPins();
    // Freeze once, before inventory classes load. Changing a system property mid-session
    // must not silently switch the canonical offhand/storage layout.
    private static final IntegrationProfile CURRENT = named(System.getProperty("dynamicinventory.profile", "isolated"));
    private final String name;
    private final String prefix;
    private final boolean backhand;
    private final boolean pack;

    IntegrationProfile(String name, String prefix, boolean backhand, boolean pack) {
        this.name = name;
        this.prefix = prefix;
        this.backhand = backhand;
        this.pack = pack;
    }

    public static IntegrationProfile selected() {
        return CURRENT;
    }

    public static IntegrationProfile named(String name) {
        for (IntegrationProfile profile : values()) if (profile.name.equals(name)) return profile;
        throw new IllegalArgumentException("Unknown Dynamic Inventory integration profile: " + name);
    }

    public String profileName() {
        return name;
    }

    public boolean usesBackhand() {
        return backhand;
    }

    public boolean usesPackAdapters() {
        return pack;
    }

    /** Keys are Maven project names, matching the shared Gradle pins. No version ranges. */
    public String version(String project) {
        return pin(prefix + "." + project);
    }

    /** Optional native contracts share this table with the required profile bridges. */
    public static String reviewedVersion(String modId) {
        return pin("reviewed." + modId);
    }

    private static String pin(String key) {
        String value = PINS.getProperty(key);
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalStateException("Missing integration pin: " + key);
        return value;
    }

    public String packTarget() {
        return version("target");
    }

    private static Properties loadPins() {
        Properties pins = new Properties();
        try (InputStream input = IntegrationProfile.class.getResourceAsStream("/integration-profiles.properties")) {
            if (input == null) throw new IllegalStateException("Missing integration-profiles.properties");
            pins.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read exact integration pins", e);
        }
        return pins;
    }
}
