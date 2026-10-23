package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.elite.Affix;
import dev.voidmc.bestiary.elite.Elites;
import dev.voidmc.fray.api.CreatureGuard;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.StatusType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Creatures with shields (the revenant soldier, Shielded elites) through Fray's {@link CreatureGuard}: while its guard is
 * up (it raises the shield whenever it isn't attacking) a hit from the front 120° is blocked, and a light may be parried
 * (the attacker is Staggered and the creature answers with its riposte). Heavies get partly through and wear the guard
 * down (a charged hit or an axe's heavy most of all); when the guard's four points run out it breaks: Staggered 25 ticks.
 * Red and purple attacks and anything from the flank or behind go through: heavies, flanking and axes are the counters.
 */
public final class Guarding {
	public static long blocks, parries, breaks;

	private Guarding() {
	}

	public static CreatureGuard.Result guard(LivingEntity defender, @Nullable LivingEntity attacker, HitContext hit, int flags) {
		if (!(defender instanceof Mob mob) || attacker == null || !(mob.level() instanceof ServerLevel level)) {
			return CreatureGuard.Result.NONE;
		}
		Brain b = Brain.get(mob);
		if (b == null || !(b.profile.guards() || Elites.affixed(mob, Affix.SHIELDED)) || !mob.getOffhandItem().is(Items.SHIELD)) {
			return CreatureGuard.Result.NONE;
		}
		if (!b.guarding || b.busy() || FrayApi.statuses().incapacitated(mob) || HitFlags.has(flags, HitFlags.UNBLOCKABLE | HitFlags.GRAB)) {
			return CreatureGuard.Result.NONE;
		}
		Vec3 d = attacker.position().subtract(mob.position());
		float to = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
		if (Math.abs(Mth.wrapDegrees(to - mob.yBodyRot)) > 60) {
			return CreatureGuard.Result.NONE;
		}
		long now = level.getGameTime();
		while (b.guardPoints < 4 && now >= b.guardRegenAt) {
			b.guardPoints++;
			b.guardRegenAt = now + 40;
		}
		boolean charged = HitFlags.has(flags, HitFlags.CHARGED);
		boolean heavy = charged || HitFlags.has(flags, HitFlags.HEAVY | HitFlags.EXECUTE);
		boolean axe = heavy && attacker.getMainHandItem().is(ItemTags.AXES);
		if (!heavy && !HitFlags.has(flags, HitFlags.PROJECTILE)) {
			float chance = (b.profile.guards() ? 0.35F : 0.25F) + 0.15F * Math.min(1, b.seenAttack / 5F);
			if (mob.getRandom().nextFloat() < chance) {
				parries++;
				level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1, 1.4F);
				if (b.profile.move("riposte") != null) {
					b.ready.remove("riposte");
					b.force("riposte");
					b.nextThink = now;
				}
				return new CreatureGuard.Result(GuardOutcome.PARRIED, 0);
			}
		}
		int cost = axe ? 4 : charged ? 3 : heavy ? 2 : 1;
		b.guardPoints -= cost;
		b.guardRegenAt = now + 40;
		if (b.guardPoints <= 0) {
			b.guardPoints = 0;
			breaks++;
			Executor.setGuard(b, false);
			level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1, 1);
			FrayApi.statuses().apply(mob, StatusType.STAGGER, 25, attacker);
			return new CreatureGuard.Result(GuardOutcome.GUARD_BREAK, 0.7F);
		}
		blocks++;
		level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1, 0.9F);
		return new CreatureGuard.Result(GuardOutcome.BLOCKED, heavy ? 0.35F : 0F);
	}
}
