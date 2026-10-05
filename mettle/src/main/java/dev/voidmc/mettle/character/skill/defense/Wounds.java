package dev.voidmc.mettle.character.skill.defense;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import dev.voidmc.mettle.mixin.defense.LivingEntityInvoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Roll with the Blow (teaching): a quarter of every hit, after armour, is held back and dealt in four equal parts over
 * the next four seconds. The held-back part can still kill, through the same death checks as a normal hit (so Last
 * Stand, other mods' ALLOW_DEATH and a totem all get their say), and it is credited to the original hit.
 */
public final class Wounds {
	private record Wound(float perSecond, int secondsLeft, DamageSource source) {
	}

	private static final Map<UUID, List<Wound>> PENDING = new HashMap<>();

	private Wounds() {
	}

	/** Player#actuallyHurt's damage after armour and effects: the part dealt now. */
	public static float hold(Player player, DamageSource source, float damage) {
		if (!(player instanceof ServerPlayer sp) || damage <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| !Perks.has(sp, DefenseSkill.ROLL_WITH_THE_BLOW)) {
			return damage;
		}
		float deferred = DefenseRules.rollDeferred(damage);
		if (deferred < 0.05f) {
			return damage;
		}
		PENDING.computeIfAbsent(sp.getUUID(), u -> new ArrayList<>()).add(new Wound(deferred / DefenseRules.ROLL_SECONDS, DefenseRules.ROLL_SECONDS, source));
		DefenseSkill.feedback(sp, DefenseSkill.ROLL_WITH_THE_BLOW, Component.translatable(DefenseSkill.chip(DefenseSkill.ROLL_WITH_THE_BLOW),
			String.format(java.util.Locale.ROOT, "%.1f", deferred)), 60);
		return damage - deferred;
	}

	/** Held-back damage still to come, for status lines and tests. */
	public static float pending(ServerPlayer player) {
		float total = 0;
		for (Wound w : PENDING.getOrDefault(player.getUUID(), List.of())) {
			total += w.perSecond() * w.secondsLeft();
		}
		return total;
	}

	/** Once a second: one part of every held-back hit comes due. */
	public static void tick(ServerPlayer player) {
		List<Wound> wounds = PENDING.get(player.getUUID());
		if (wounds == null) {
			return;
		}
		if (!player.isAlive()) {
			PENDING.remove(player.getUUID());
			return;
		}
		float due = 0;
		DamageSource source = null;
		List<Wound> next = new ArrayList<>();
		for (Wound w : wounds) {
			due += w.perSecond();
			source = w.source();
			if (w.secondsLeft() > 1) {
				next.add(new Wound(w.perSecond(), w.secondsLeft() - 1, w.source()));
			}
		}
		if (next.isEmpty()) {
			PENDING.remove(player.getUUID());
		} else {
			PENDING.put(player.getUUID(), next);
		}
		if (source != null && !player.isCreative() && !player.isSpectator()) {
			deal(player, source, due);
		}
	}

	private static void deal(ServerPlayer player, DamageSource source, float due) {
		float absorbed = Math.min(player.getAbsorptionAmount(), due);
		player.setAbsorptionAmount(player.getAbsorptionAmount() - absorbed);
		due -= absorbed;
		if (due <= 0) {
			return;
		}
		player.getCombatTracker().recordDamage(source, due);
		player.setHealth(player.getHealth() - due);
		if (!player.isDeadOrDying()) {
			return;
		}
		// The same order as LivingEntity#hurtServer: ALLOW_DEATH (Last Stand), then a totem, then death.
		if (!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, source, due)) {
			return;
		}
		if (!((LivingEntityInvoker) player).mettle$checkTotemDeathProtection(source)) {
			player.die(source);
		}
	}

	public static void forget(UUID player) {
		PENDING.remove(player);
	}
}
