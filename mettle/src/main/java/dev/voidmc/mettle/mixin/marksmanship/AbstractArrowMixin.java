package dev.voidmc.mettle.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.skill.marksmanship.Hits;
import dev.voidmc.mettle.character.skill.marksmanship.ShotArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arrows remember how they were loosed ({@link ShotArrow}) and, when one of a player's arrows strikes something
 * living, {@link Hits} works out the strike before vanilla deals its damage and acts on it after.
 */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin implements ShotArrow {
	@Unique
	private @Nullable Vec3 mettle$origin;
	@Unique
	private boolean mettle$fullDraw;
	@Unique
	private float mettle$speed = 1f;
	@Unique
	private Hits.@Nullable Hit mettle$hit;

	@Override
	public @Nullable Vec3 mettle$origin() {
		return this.mettle$origin;
	}

	@Override
	public boolean mettle$fullDraw() {
		return this.mettle$fullDraw;
	}

	@Override
	public float mettle$speed() {
		return this.mettle$speed;
	}

	@Override
	public void mettle$loosed(Vec3 origin, boolean fullDraw, float speed) {
		this.mettle$origin = origin;
		this.mettle$fullDraw = fullDraw;
		this.mettle$speed = speed;
	}

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void mettle$planHit(EntityHitResult result, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		this.mettle$hit = self.level().isClientSide() ? null : Hits.plan(self, result);
	}

	@ModifyExpressionValue(method = "onHitEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;isCritArrow()Z"))
	private boolean mettle$deadeye(boolean crit) {
		return this.mettle$hit == null ? crit : Hits.crit(this.mettle$hit, crit);
	}

	@WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean mettle$strike(Entity entity, DamageSource source, float damage, Operation<Boolean> original) {
		Hits.Hit hit = this.mettle$hit;
		if (hit == null) {
			return original.call(entity, source, damage);
		}
		AbstractArrow self = (AbstractArrow) (Object) this;
		Hits.beforeHurt(self, hit);
		boolean hurt = original.call(entity, source, Hits.damage(hit, damage));
		Hits.afterHurt(self, hit, hurt);
		return hurt;
	}

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void mettle$forgetHit(EntityHitResult result, CallbackInfo ci) {
		this.mettle$hit = null;
	}
}
