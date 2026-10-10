package dev.voidmc.mettle.mixin.cards;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Piercing (Marksmanship trait) adds a pierce level to an arrow as it is loosed. */
@Mixin(AbstractArrow.class)
public interface ArrowAccess {
	@Invoker("setPierceLevel")
	void mettle$setPierceLevel(byte level);

	/** The Seeking Fletch stops steering an arrow that has landed. */
	@Invoker("isInGround")
	boolean mettle$isInGround();
}
