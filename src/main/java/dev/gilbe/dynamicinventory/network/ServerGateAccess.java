package dev.gilbe.dynamicinventory.network;

/** One gate per play connection: dropping the connection also drops all pending window state. */
public interface ServerGateAccess {

    ServerInventoryGate dynamicinventory$openingGate();
}
