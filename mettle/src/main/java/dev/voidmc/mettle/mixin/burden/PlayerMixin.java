package dev.voidmc.mettle.mixin.burden;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.burden.Bands;
import dev.voidmc.mettle.burden.LoadState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Band effects that are not attributes (BURDEN.md §3): hunger drains ×1.5 from Burdened on; from Overburdened the elytra
 * won't open, you sink slowly in water and you cannot sprint. All read the synced band, so the client (which moves the player) and the
 * server agree.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float mettle$burdenHunger(float amount) {
		Player self = (Player) (Object) this;
		LoadBand band = LoadState.band(self);
		return band == LoadBand.LIGHT ? amount : amount * (1f + Bands.scaled(Bands.HUNGER, band, Bands.rules(self)));
	}

	/**
	 * The sprint gate: LocalPlayer will not start or keep a sprint when this says no, and the server asks it too
	 * ({@link dev.voidmc.mettle.burden.Sprint}). Default priority, so later hooks (Athletics' Hauler's Stride, 1500)
	 * see burden's answer and may overrule it.
	 */
	@ModifyReturnValue(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"))
	private boolean mettle$burdenSprint(boolean enough) {
		return enough && !Bands.atLeast(LoadState.band((Player) (Object) this), LoadBand.OVERBURDENED);
	}

	@ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
	private boolean mettle$burdenGlide(boolean canGlide) {
		return canGlide && !Bands.atLeast(LoadState.band((Player) (Object) this), LoadBand.OVERBURDENED);
	}

	@Inject(method = "travel", at = @At("HEAD"))
	private void mettle$burdenSink(Vec3 input, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!self.isInWater() || self.getAbilities().flying || self.isPassenger()) {
			return;
		}
		LoadBand band = LoadState.band(self);
		if (Bands.atLeast(band, LoadBand.OVERBURDENED)) {
			self.setDeltaMovement(self.getDeltaMovement().add(0.0, -Bands.scaled(Bands.SINK, band, Bands.rules(self)), 0.0));
		}
	}
}
