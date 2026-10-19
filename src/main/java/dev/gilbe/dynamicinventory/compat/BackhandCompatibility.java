package dev.gilbe.dynamicinventory.compat;

/** Early decision shared by storage and containers, before vanilla target classes load. */
public final class BackhandCompatibility {

    public static final String SUPPORTED_VERSION = "1.8.16";
    private static boolean enabled;

    private BackhandCompatibility() {}

    public static void configure(boolean present) {
        String profile = System.getProperty("dynamicinventory.profile", "isolated");
        enabled = present && ("rc2-backhand".equals(profile) || "rc2-integration".equals(profile));
    }

    public static boolean enabled() {
        return enabled;
    }
}
