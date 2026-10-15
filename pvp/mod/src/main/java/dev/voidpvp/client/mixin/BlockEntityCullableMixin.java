package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.Cullable;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the culling thread's answer on every block entity. See {@code perf/Cullable}. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityCullableMixin implements Cullable {

    @Unique
    private volatile boolean void$occluded;
    @Unique
    private volatile int void$pass;

    @Override
    public boolean void$isOccluded() {
        return void$occluded;
    }

    @Override
    public int void$cullPass() {
        return void$pass;
    }

    @Override
    public void void$setOccluded(boolean occluded, int pass) {
        void$occluded = occluded;
        void$pass = pass;
    }
}
