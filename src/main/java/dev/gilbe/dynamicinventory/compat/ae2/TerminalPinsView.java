package dev.gilbe.dynamicinventory.compat.ae2;

import appeng.api.storage.data.IAEStack;

/** Client bridge to the terminal's existing pin data and interaction path. */
public interface TerminalPinsView {

    boolean dynamicinventory$hasPins();

    IAEStack<?> dynamicinventory$pin(int index);

    int dynamicinventory$pinSlots(boolean crafting);

    boolean dynamicinventory$clickPin(int index, int mouseButton);

    void dynamicinventory$scrollPin(int index, int steps);

    void dynamicinventory$rebuildPins();
}
