package dev.gilbe.dynamicinventory.compat.mui2;

/** Optional native hosts whose machine footprint stays fixed while extra player storage scrolls. */
public final class Mui2CompactMachineHosts {

    private Mui2CompactMachineHosts() {}

    /** Exact framework identifiers keep optional mod classes out of both dependencies and class loading. */
    public static boolean keepsCompactViewport(String screenOwner, String panelName) {
        return "superfactorymanager".equals(screenOwner) && "sfm_manager".equals(panelName);
    }
}
