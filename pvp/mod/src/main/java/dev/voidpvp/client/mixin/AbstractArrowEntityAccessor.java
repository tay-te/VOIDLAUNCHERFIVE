package dev.voidpvp.client.mixin;

import net.minecraft.entity.projectile.AbstractArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code AbstractArrowEntity.inGround} — whether an arrow has landed and stopped.
 *
 * <p>Private, with no getter, and it is the only field that separates the two things the Overlay
 * mod must treat differently: an arrow lying in a block is clutter, and an arrow in flight is a
 * shot coming at you. See {@link BaseArrowEntityRendererMixin}.</p>
 */
@Mixin(AbstractArrowEntity.class)
public interface AbstractArrowEntityAccessor {

    @Accessor("inGround")
    boolean void$inGround();
}
