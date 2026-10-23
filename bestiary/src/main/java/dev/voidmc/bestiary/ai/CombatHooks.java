package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.bestiary.elite.EliteRules;
import dev.voidmc.bestiary.elite.Elites;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.PerceptionService;
import dev.voidmc.fray.combat.FrayDamageSource;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Bestiary on Fray's events: its creatures' damage (danger, difficulty, elite, leader and the move's multiplier, in
 * the attacker stage of the pipeline), shields ({@link Guarding}), the codex's fought and felled, when a creature was
 * last hurt (Regenerating), and the noise of broken blocks for hearing.
 */
public final class CombatHooks {
	private CombatHooks() {
	}

	public static void init() {
		CombatEvents.MODIFY_DAMAGE.register((target, hit, builder) -> {
			if (builder.stage() != CombatEvents.Stage.ATTACKER || !(hit.attacker() instanceof Mob mob)) {
				return;
			}
			Brain b = Brain.get(mob);
			if (b == null) {
				return;
			}
			float f = Scaling.damage(mob);
			MoveDef m = b.profile.moveForClip(hit.action());
			if (m != null) {
				f *= m.damage();
			}
			builder.more(null, f);
		});
		CombatEvents.CREATURE_GUARD.register(Guarding::guard);
		CombatEvents.DAMAGE_DEALT.register((attacker, target, hit, result) -> {
			if (result.total() > 0) {
				hurt(target, attacker);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0 && !(source instanceof FrayDamageSource)) {
				hurt(entity, source.getEntity());
				if (entity instanceof Mob m) {
					EliteRules.hurtHard(m, taken);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Mob m)) {
				return;
			}
			Brain b = Brain.get(m);
			if (b == null) {
				return;
			}
			Tokens.release(b);
			Grabs.releaseBy(m);
			Elites.forget(m);
			if (source.getEntity() instanceof ServerPlayer p) {
				Codex.felled(p, b.profile);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel sl) {
				FrayApi.perception().noise(sl, Vec3.atCenterOf(pos), 8, PerceptionService.BLOCK_BROKEN, player);
			}
		});
	}

	static void hurt(LivingEntity target, Entity attacker) {
		if (target instanceof Mob m) {
			Brain b = Brain.get(m);
			if (b != null) {
				b.lastHurt = m.level().getGameTime();
				if (attacker instanceof ServerPlayer p) {
					Codex.fought(p, b.profile);
				}
			}
		}
		if (target instanceof ServerPlayer p && attacker instanceof Mob am) {
			Brain b = Brain.get(am);
			if (b != null) {
				Codex.fought(p, b.profile);
			}
		}
	}
}
