package dev.voidmc.bestiary.elite;

import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.StatusType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The affixes' combat rules, all through Fray's events: Ironbound's plate refuses Staggers until three blunt hits break
 * it; Venomous adds poison build-up to its hits and bursts a poison cloud when hit hard. Frenzied, Regenerating, Swift
 * and the drips run in {@link Elites#tick}; Shielded is {@code ai.Guarding}; health, damage, armour and speed are
 * {@code ai.Scaling}.
 */
public final class EliteRules {
	/** Counters for tests. */
	public static long PLATE_REFUSALS, VENOM_BUILDUPS, VENOM_CLOUDS, SIDESTEPS, PLATE_BREAKS;

	private EliteRules() {
	}

	public static boolean plateIntact(Mob m) {
		Brain b = Brain.get(m);
		return b != null && b.plateHits < 3;
	}

	public static void init() {
		CombatEvents.ALLOW_STATUS.register((target, status, ticks, source) -> {
			if (status == StatusType.STAGGER && target instanceof Mob m && Elites.affixed(m, Affix.IRONBOUND) && plateIntact(m)) {
				PLATE_REFUSALS++;
				m.level().playSound(null, m.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.25F, 1.6F);
				return false;
			}
			return true;
		});
		CombatEvents.MODIFY_DAMAGE.register((target, hit, builder) -> {
			if (builder.stage() != CombatEvents.Stage.DEFENCE || !(target instanceof Mob m) || !Elites.affixed(m, Affix.IRONBOUND)) {
				return;
			}
			Brain b = Brain.get(m);
			if (b != null && b.plateHits < 3 && Elites.bluntShare(hit.spec().amounts()) >= 0.5F) {
				b.plateHits++;
				if (b.plateHits == 3) {
					PLATE_BREAKS++;
					m.level().playSound(null, m.blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.HOSTILE, 0.6F, 0.9F);
				}
			}
		});
		CombatEvents.DAMAGE_DEALT.register((attacker, target, hit, result) -> {
			if (result.total() <= 0) {
				return;
			}
			if (attacker instanceof Mob am && Elites.affixed(am, Affix.VENOMOUS)) {
				VENOM_BUILDUPS++;
				FrayApi.statuses().addBuildUp(target, StatusType.POISON, 30, am);
			}
			if (target instanceof Mob tm) {
				hurtHard(tm, result.total());
			}
		});
	}

	/** Venomous bursts a poison cloud when one hit takes a fifth of its health (at most every 5 s). */
	public static void hurtHard(Mob m, float amount) {
		if (!Elites.affixed(m, Affix.VENOMOUS) || amount < m.getMaxHealth() * 0.2F || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		Brain b = Brain.get(m);
		long now = level.getGameTime();
		if (b != null && now - b.lastVenomCloud < 100) {
			return;
		}
		if (b != null) {
			b.lastVenomCloud = now;
		}
		VENOM_CLOUDS++;
		Elites.venomCloud(m, level);
	}
}
