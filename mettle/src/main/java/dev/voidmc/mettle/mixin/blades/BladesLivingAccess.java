package dev.voidmc.mettle.mixin.blades;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The swing charge (Cut and Run, Sword and Board) and the hurt memory a bleed tick must leave as it found it. */
@Mixin(LivingEntity.class)
public interface BladesLivingAccess {
	@Accessor("attackStrengthTicker")
	int mettle$attackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void mettle$setAttackStrengthTicker(int ticks);

	@Accessor("lastHurt")
	float mettle$lastHurt();

	@Accessor("lastHurt")
	void mettle$setLastHurt(float damage);
}
