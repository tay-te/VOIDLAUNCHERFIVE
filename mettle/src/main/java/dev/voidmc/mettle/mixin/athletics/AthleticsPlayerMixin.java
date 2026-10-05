package dev.voidmc.mettle.mixin.athletics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.skill.athletics.AthleticsState;
import dev.voidmc.mettle.character.skill.athletics.Movement;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Athletics' state on the player, and the sprint gate for Marathon and Hauler's Stride. Burden hooks the same gate to
 * stop Overburdened players sprinting; the priority makes this one apply after it, so Hauler's Stride can lift that
 * refusal (and only that one, see AthleticsRules#canSprint).
 */
@Mixin(value = Player.class, priority = 1500)
abstract class AthleticsPlayerMixin implements AthleticsState.Holder {
	@Unique
	private AthleticsState mettle$athleticsState;

	@Override
	public AthleticsState mettle$athletics() {
		// Lazily: super constructors may already call into code that asks.
		if (mettle$athleticsState == null) {
			mettle$athleticsState = new AthleticsState();
		}
		return mettle$athleticsState;
	}

	@ModifyReturnValue(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"))
	private boolean mettle$canSprint(boolean original) {
		return Movement.canSprint((Player) (Object) this, original);
	}
}
