package dev.gilbe.dynamicinventory.qualification;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.compat.mui2.NativeInventoryPresentation;

/** Run on the render thread: preserve one depth pixel while drawing private presentation trees. */
public final class PresentationClientFixtures {

    private PresentationClientFixtures() {}

    public static void run() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiContainer owner = (GuiContainer) minecraft.currentScreen;
        if (GL11.glGetInteger(GL11.GL_DEPTH_BITS) <= 0)
            throw new IllegalStateException("Fixture requires a depth buffer");
        float originalDepth = readDepth();
        NativeInventoryPresentation renderer = new NativeInventoryPresentation(owner);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // Change only one depth pixel, and suppress color output from the test draw calls.
            GL11.glColorMask(false, false, false, false);
            clearDepthPixel(0.25f);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            renderer.frame(0, 0, 40, 40, true);
            checkDepth("frame draw");
            List<Slot> actual = owner.inventorySlots.inventorySlots;
            renderer.slots(actual);
            checkDepth("slot background draw");
            if (!actual.isEmpty()) {
                List<Slot> replacement = new ArrayList<>(actual);
                Slot original = actual.get(0);
                Slot changed = new Slot(
                    original.inventory,
                    original.getSlotIndex(),
                    original.xDisplayPosition,
                    original.yDisplayPosition);
                changed.slotNumber = original.slotNumber;
                replacement.set(0, changed);
                renderer.slots(replacement);
                renderer.slots(actual);
                checkDepth("same-count slot identity replacement");
            }
            if (GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) != depthMask
                || GL11.glIsEnabled(GL11.GL_DEPTH_TEST) != depthTest)
                throw new AssertionError("Presentation did not restore caller depth state");
            JsonObject report = new JsonObject();
            report.addProperty("success", true);
            report.addProperty("depthSentinel", readDepth());
            report.addProperty("slotIdentityReplacement", !actual.isEmpty());
            QualificationCommand.write(minecraft.thePlayer, "client-presentation-depth", report);
        } finally {
            try {
                try {
                    renderer.dispose();
                } finally {
                    clearDepthPixel(originalDepth);
                }
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    private static void checkDepth(String operation) {
        if (Math.abs(readDepth() - 0.25f) > 0.00001f)
            throw new AssertionError(operation + " changed the caller's depth buffer");
    }

    private static float readDepth() {
        FloatBuffer depth = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(1, 1, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        return depth.get(0);
    }

    private static void clearDepthPixel(float depth) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(1, 1, 1, 1);
        GL11.glDepthMask(true);
        GL11.glClearDepth(depth);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }
}
