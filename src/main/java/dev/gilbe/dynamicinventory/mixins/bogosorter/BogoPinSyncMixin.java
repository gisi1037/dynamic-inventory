package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.network.PacketBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.common.network.SPlayerPins;

import dev.gilbe.dynamicinventory.compat.BogoExpandedPins;

@Mixin(value = SPlayerPins.class, remap = false)
public abstract class BogoPinSyncMixin implements BogoExpandedPins.Sync {

    @Unique
    private static final int DYNAMICINVENTORY_PIN_TRAILER = 0x44495031;
    @Unique
    private int[] dynamicinventory$expandedPins = new int[0];

    @Override
    public void dynamicinventory$setExpandedPins(int[] mask) {
        dynamicinventory$expandedPins = mask;
    }

    @Override
    public int[] dynamicinventory$getExpandedPins() {
        return dynamicinventory$expandedPins;
    }

    @Inject(method = "encode", at = @At("RETURN"))
    private void dynamicinventory$writeExpandedPins(PacketBuffer buffer, CallbackInfo ci) {
        int count = Math.min(dynamicinventory$expandedPins.length, BogoExpandedPins.MAX_WORDS);
        if (count == 0) return;
        // Older Bogo clients decode their normal mask and ignore the optional trailer.
        buffer.writeInt(DYNAMICINVENTORY_PIN_TRAILER);
        buffer.writeByte(count);
        for (int i = 0; i < count; i++) buffer.writeInt(dynamicinventory$expandedPins[i]);
    }

    @Inject(method = "decode", at = @At("RETURN"))
    private void dynamicinventory$readExpandedPins(PacketBuffer buffer, CallbackInfo ci) {
        if (buffer.readableBytes() < 5 || buffer.getInt(buffer.readerIndex()) != DYNAMICINVENTORY_PIN_TRAILER) return;
        buffer.skipBytes(4);
        int count = buffer.readUnsignedByte();
        if (count > BogoExpandedPins.MAX_WORDS || buffer.readableBytes() < count * Integer.BYTES) return;
        dynamicinventory$expandedPins = new int[count];
        for (int i = 0; i < count; i++) dynamicinventory$expandedPins[i] = buffer.readInt();
    }

}
