package dev.gilbe.dynamicinventory.compat;

/** Exposes Forestry's existing draw method without loading optional client classes on the server. */
public interface ForestryLedgerAccess {

    void drawLedgers();
}
