package dev.gilbe.dynamicinventory.compat;

/** Early decision shared by storage and containers, before vanilla target classes load. */
public final class BackhandCompatibility {

    private static boolean enabled;

    private BackhandCompatibility() {}

    public static void configure(boolean present) {
        enabled = present && IntegrationProfile.selected()
            .usesBackhand();
    }

    public static String supportedVersion() {
        return IntegrationProfile.selected()
            .version("Backhand");
    }

    public static boolean enabled() {
        return enabled;
    }
}
