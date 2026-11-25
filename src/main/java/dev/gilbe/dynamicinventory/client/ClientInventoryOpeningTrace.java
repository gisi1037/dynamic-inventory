package dev.gilbe.dynamicinventory.client;

import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Correlates a refusal with the Forge open for that window, including factories that return an empty GUI. */
public final class ClientInventoryOpeningTrace {

    private static final long RECENT = TimeUnit.SECONDS.toNanos(5);
    private static final ThreadLocal<NativeOpen> DISPATCH = new ThreadLocal<>();

    private volatile NativeOpen latest;
    private volatile PendingRefusal pending;

    public void open(Minecraft client, int window, String mod, int gui, Runnable dispatch) {
        NativeOpen previous = DISPATCH.get();
        NativeOpen attempt = new NativeOpen(window, mod, gui);
        latest = attempt;
        DISPATCH.set(attempt);
        try {
            dispatch.run();
        } finally {
            if (previous == null) DISPATCH.remove();
            else DISPATCH.set(previous);
            attempt.result = clientWindow(client);
            PendingRefusal refusal = pending;
            if (refusal != null && refusal.window == window && System.nanoTime() - refusal.at < RECENT) {
                pending = null;
                DynamicInventory.LOG.warn(
                    "Native inventory open completed after refusal: expectedWindow={} {}",
                    window,
                    attempt.describe());
            }
        }
    }

    public static boolean isObserving(String mod) {
        NativeOpen active = DISPATCH.get();
        return active != null && mod.equals(active.mod);
    }

    public static void terminal(String mod, String details, boolean hasExactSlot) {
        NativeOpen active = DISPATCH.get();
        if (active != null && mod.equals(active.mod) && (hasExactSlot || active.terminal == null))
            active.terminal = details;
    }

    void reject(Minecraft client, int expectedWindow, String reason) {
        NativeOpen attempt = latest;
        long now = System.nanoTime();
        boolean matching = attempt != null && attempt.window == expectedWindow && now - attempt.started < RECENT;
        if (!matching || attempt.result == null) pending = new PendingRefusal(expectedWindow, now);
        DynamicInventory.LOG.warn(
            "Rejected client inventory window: {} expectedWindow={} {} {}",
            reason,
            expectedWindow,
            clientWindow(client),
            matching ? attempt.describe() : "forgeOpen=unobserved");
    }

    private static String clientWindow(Minecraft client) {
        Container open = client.thePlayer == null ? null : client.thePlayer.openContainer;
        return "clientWindow=" + (open == null ? "none" : open.windowId)
            + " container="
            + className(open)
            + " screen="
            + className(client.currentScreen);
    }

    private static String className(Object object) {
        return object == null ? "none"
            : object.getClass()
                .getName();
    }

    private static final class PendingRefusal {

        final int window;
        final long at;

        PendingRefusal(int window, long at) {
            this.window = window;
            this.at = at;
        }
    }

    private static final class NativeOpen {

        final long started = System.nanoTime();
        final int window;
        final String mod;
        final int gui;
        volatile String terminal;
        volatile String result;

        NativeOpen(int window, String mod, int gui) {
            this.window = window;
            this.mod = mod;
            this.gui = gui;
        }

        String describe() {
            String observedResult = result;
            return "forgeOpen=" + (observedResult == null ? "in-progress" : "complete")
                + " mod="
                + mod
                + " guiId="
                + gui
                + " terminal={"
                + (terminal == null ? "unobserved" : terminal)
                + "}"
                + (observedResult == null ? "" : " nativeResult={" + observedResult + "}");
        }
    }
}
