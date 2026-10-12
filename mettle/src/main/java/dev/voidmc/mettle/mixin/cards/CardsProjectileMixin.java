package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The Gale set (4 cards): thrown things fly 10 % faster (docs/CARDS.md §6.3). */
@Mixin(Projectile.class)
abstract class CardsProjectileMixin {
	@ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float mettle$cardsThrown(float power, Entity source) {
		return CardHooks.thrown((Projectile) (Object) this, source, power);
	}
}
