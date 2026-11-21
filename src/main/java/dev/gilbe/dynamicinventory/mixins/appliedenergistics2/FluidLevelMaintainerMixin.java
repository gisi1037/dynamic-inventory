package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.glodblock.github.client.gui.FCGuiTextField;

import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.compat.ae2.FluidTextFieldPosition;

@Mixin(targets = "com.glodblock.github.client.gui.GuiLevelMaintainer", remap = false)
public abstract class FluidLevelMaintainerMixin implements NativeUiAnchors {

    @Unique
    private final List<FluidTextFieldPosition> dynamicinventory$fields = new ArrayList<>();

    @Override
    public int[] captureHeaderOrigins() {
        dynamicinventory$fields.clear();
        try {
            // Component and Widget are private native types. Resolve them once per screen build,
            // then retain the real text fields for drawing, input and movement.
            for (Object component : (Object[]) dynamicinventory$field(this, "component")) {
                for (String name : new String[] { "qty", "batch", "line" }) {
                    Object field = dynamicinventory$field(component, name);
                    if (!(field instanceof FCGuiTextField)) field = dynamicinventory$field(field, "textField");
                    dynamicinventory$fields.add((FluidTextFieldPosition) field);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to capture the Level Maintainer controls", failure);
        }
        int[] origins = new int[dynamicinventory$fields.size() * 2];
        int index = 0;
        for (FluidTextFieldPosition field : dynamicinventory$fields) {
            int[] origin = field.dynamicinventory$origin();
            origins[index++] = origin[0];
            origins[index++] = origin[1];
        }
        return origins;
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        int index = 0;
        for (FluidTextFieldPosition field : dynamicinventory$fields) {
            field.dynamicinventory$position(origins[index], origins[index + 1]);
            index += 2;
        }
    }

    @Unique
    private static Object dynamicinventory$field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
