package dev.gilbe.dynamicinventory.client;

/** For native GUIs which cache absolute header origins instead of using GuiContainer's origin. */
public interface NativeHeaderAccess extends dev.gilbe.dynamicinventory.api.client.NativeUiAnchors {

    int[] dynamicinventory$headerOrigins();

    void dynamicinventory$headerOrigins(int[] values);

    @Override
    default int[] captureHeaderOrigins() {
        return dynamicinventory$headerOrigins();
    }

    @Override
    default void positionHeaderOrigins(int[] origins) {
        dynamicinventory$headerOrigins(origins);
    }
}
