package dev.voidmc.mettle.mixin.cards;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Surefoot (Athletics trait): soul sand, honey, cobwebs, powder snow and berry bushes slow a player less. Both sides. */
@Mixin(Entity.class)
abstract class CardsEntityMixin {
	@ModifyReturnValue(method = "getBlockSpeedFactor", at = @At("RETURN"))
	private float mettle$cardsSurefoot(float factor) {
		return CardHooks.blockSpeed((Entity) (Object) this, factor);
	}

	@ModifyVariable(method = "makeStuckInBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 mettle$cardsStuck(Vec3 multiplier) {
		return CardHooks.stuck((Entity) (Object) this, multiplier);
	}
}
