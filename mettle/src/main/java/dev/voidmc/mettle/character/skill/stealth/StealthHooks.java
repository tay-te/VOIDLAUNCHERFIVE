package dev.voidmc.mettle.character.skill.stealth;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.Ceilings;
import dev.voidmc.mettle.character.draft.Cursed;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.StealthSkill;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.CardStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stealth's entry points for the {@code mixin.stealth} hooks (docs/BUILD_FEEL.md §14.1): how far hostiles notice a
 * player, sneak attacks (their damage, XP, slows, poison, Swift Exit and the alert they don't raise), vibrations, falls,
 * Darkness, a mob giving up the hunt, Lootfinger and Night Hunter. Server thread.
 */
public final class StealthHooks {
	/** A sneak attack seen at hurtServer's head, waiting for the hit to land (AFTER_DAMAGE) or kill (AFTER_DEATH). */
	private record Pending(UUID attacker, long tick, float healthBefore, boolean sneak) {
	}

	private static final Map<LivingEntity, Pending> PENDING = new WeakHashMap<>();
	/** Mobs each player has struck (Assassin's "first sneak attack"). */
	private static final Map<LivingEntity, Set<UUID>> STRUCK = new WeakHashMap<>();
	/** Mobs that raise no alert to their kind until this game time (Unseen Blade, Muffled). */
	private static final Map<LivingEntity, Long> SILENCED = new WeakHashMap<>();
	/** Timed slows on mobs (Hamstring, Hamstringing): modifier id → game time it ends. */
	private static final Map<LivingEntity, Map<Identifier, Long>> SLOWED = new WeakHashMap<>();
	/** Arrows loosed while sneaking, with Night Hunter. */
	private static final Map<Entity, Boolean> SNEAK_ARROWS = new WeakHashMap<>();
	/** Swift Exit's end per player. */
	private static final Map<UUID, Long> SWIFT_EXIT = new HashMap<>();

	static final Identifier HAMSTRING_ID = Mettle.id("stealth/hamstring");
	static final Identifier HAMSTRINGING_ID = Mettle.id("stealth/hamstringing");
	/** Counted by the movement ceiling (Ceilings.fromCards: {@code trait_timed/}). */
	static final Identifier SWIFT_EXIT_ID = Mettle.id("trait_timed/stealth_swift_exit");

	private StealthHooks() {
	}

	public static void clear() {
		PENDING.clear();
		STRUCK.clear();
		SILENCED.clear();
		SLOWED.clear();
		SNEAK_ARROWS.clear();
		SWIFT_EXIT.clear();
		STEPS.clear();
	}

	/** A percent trait's active total as a fraction (both sides: the synced card cache). */
	public static double pct(Player player, String trait) {
		return CardStore.get(player).trait(Mettle.id(trait)) / 100.0;
	}

	// ---------------------------------------------------------------- being noticed

	/** LivingEntity#getVisibilityPercent: how far a hostile notices this player (Shadow, Shadowed, Cloak of Dusk, Skulker, Ghost, Footpad). */
	public static double visibility(LivingEntity self, @Nullable Entity looking, double percent) {
		if (!(self instanceof Player player) || !(looking instanceof Enemy) || percent <= 0) {
			return percent;
		}
		int shadow = Perks.rank(player, StealthSkill.SHADOW);
		boolean cloak = Perks.has(player, StealthSkill.CLOAK_OF_DUSK);
		boolean skulker = Perks.has(player, StealthSkill.SKULKER);
		double skulkerCost = skulker ? Cursed.down(StealthRules.SKULKER_COST, Cursed.hex(player)) : 1;
		boolean ghost = Perks.has(player, StealthSkill.GHOST);
		double shadowed = pct(player, "stealth/shadowed");
		if (shadow == 0 && !cloak && !skulker && !ghost && shadowed <= 0 && !Perks.has(player, StealthSkill.FOOTPAD)) {
			return percent;
		}
		boolean sneaking = Sneaking.sneaking(player);
		boolean footpad = !sneaking && Sneaking.footpadCover(player);
		int light = Sneaking.light(player);
		boolean ghostHolds = ghost && sneaking && light <= StealthRules.DARK && !Sneaking.attacking(player);
		double f = StealthRules.sight(shadow, sneaking || footpad, footpad, shadowed, cloak, skulkerCost, light, ghostHolds, looking.distanceTo(player));
		if (f == 0 && player instanceof ServerPlayer sp) {
			StealthSkill.chipThrottled(sp, StealthSkill.GHOST, 200, "ghost");
		}
		return percent * f;
	}

	// ---------------------------------------------------------------- sneak attacks

	/** A sneak attack: a player hits a mob that isn't targeting them, sneaking (or with an arrow loosed while sneaking). */
	public static boolean sneakAttack(ServerPlayer attacker, LivingEntity target, DamageSource source) {
		if (!(target instanceof Mob mob) || mob.getTarget() == attacker) {
			return false;
		}
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			return SNEAK_ARROWS.containsKey(arrow) || Sneaking.sneaking(attacker);
		}
		return source.getDirectEntity() == attacker && Sneaking.sneaking(attacker);
	}

	private static boolean boss(LivingEntity e) {
		return e instanceof Warden || e instanceof WitherBoss || e instanceof EnderDragon || e instanceof ElderGuardian || e.getMaxHealth() >= 150;
	}

	/**
	 * LivingEntity#hurtServer's incoming damage, Stealth's part outside the cards' ceilings: Ambusher, Coward's Edge and
	 * Assassin on a sneak attack, Night Hunter's sure crit; on the player hit, Coward's Edge's cost, Silent Fall and
	 * Catfooted. Remembers the sneak attack for {@link #afterDamage}.
	 */
	public static float hurt(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0) {
			return damage;
		}
		double m = 1;
		float add = 0;
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
			boolean sneak = sneakAttack(attacker, target, source);
			long now = target.level().getGameTime();
			PENDING.put(target, new Pending(attacker.getUUID(), now, target.getHealth(), sneak));
			Set<UUID> struck = STRUCK.computeIfAbsent(target, t -> new HashSet<>());
			boolean first = struck.add(attacker.getUUID()) && target.getLastHurtByMob() != attacker;
			if (sneak) {
				boolean ambusher = Perks.has(attacker, StealthSkill.AMBUSHER);
				double coward = Perks.has(attacker, StealthSkill.COWARDS_EDGE) ? Cursed.up(1 + StealthRules.COWARD, Cursed.hex(attacker)) - 1 : 0;
				boolean assassin = first && Perks.has(attacker, StealthSkill.ASSASSIN);
				m *= StealthRules.sneakAttack(ambusher, coward, assassin, boss(target));
				if (assassin) {
					StealthSkill.chip(attacker, StealthSkill.ASSASSIN, "assassin");
				}
				if (source.getDirectEntity() instanceof AbstractArrow arrow && SNEAK_ARROWS.containsKey(arrow) && !arrow.isCritArrow()
					&& Perks.has(attacker, StealthSkill.NIGHT_HUNTER)) {
					add = (float) (StealthRules.nightHunterCrit(damage) - damage);
					StealthSkill.chipThrottled(attacker, StealthSkill.NIGHT_HUNTER, 100, "night_hunter");
				}
			}
		}
		if (target instanceof ServerPlayer victim) {
			boolean sneaking = Sneaking.sneaking(victim);
			if (!sneaking && Perks.has(victim, StealthSkill.COWARDS_EDGE) && source.getEntity() != null) {
				m *= Cursed.down(1 + StealthRules.COWARD_TAKEN, Cursed.hex(victim));
			}
			if (sneaking && source.is(DamageTypes.FALL)) {
				m *= 1 - pct(victim, "stealth/catfooted");
				if (Perks.has(victim, StealthSkill.SILENT_FALL)) {
					return (float) Math.max(0, (damage + add) * m - StealthRules.SILENT_FALL);
				}
			}
		}
		return (float) ((damage + add) * m);
	}

	/**
	 * The cards' part of a sneak attack (CardHooks.dealt, inside the melee ceiling): Backbiting on a sneak attack, Patient
	 * on the first hit after sneaking still for 2 s.
	 */
	public static double traitHit(ServerPlayer attacker, LivingEntity target, DamageSource source) {
		double m = 1;
		if (sneakAttack(attacker, target, source)) {
			m *= 1 + pct(attacker, "stealth/backbiting");
		}
		if (Sneaking.patientReady(attacker)) {
			m *= 1 + pct(attacker, "stealth/patient");
		}
		return m;
	}

	/** After a hit landed: Ghost's "attacking", a sneak attack's XP and what it does besides its damage. */
	public static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == target) {
			return;
		}
		Sneaking.attacked(attacker);
		Pending p = PENDING.get(target);
		long now = target.level().getGameTime();
		if (p == null || !p.sneak() || p.tick() != now || !p.attacker().equals(attacker.getUUID()) || taken <= 0) {
			return;
		}
		double xp = StealthRules.sneakAttackXp(taken, p.healthBefore()) * Combat.targetFactor(target);
		StealthSkill.award(attacker, "attack", xp);
		if (Perks.has(attacker, StealthSkill.HAMSTRING)) {
			slow(target, HAMSTRING_ID, StealthRules.HAMSTRING, StealthRules.HAMSTRING_TICKS, now);
		}
		double hamstringing = pct(attacker, "stealth/hamstringing");
		if (hamstringing > 0) {
			slow(target, HAMSTRINGING_ID, hamstringing, StealthRules.HAMSTRINGING_TICKS, now);
		}
		if (Perks.has(attacker, StealthSkill.POISONER) && Combat.weapon(source, attacker) == Combat.Weapon.BLADE && target.isAlive()) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, StealthRules.POISON_TICKS, 0), attacker);
		}
		double exit = pct(attacker, "stealth/swift_exit");
		if (exit > 0) {
			swiftExit(attacker, exit, now);
		}
		// Unseen Blade: no other mob within 8 blocks is hunting the attacker, so this one raises no alert. Muffled: a chance of the same.
		boolean seen = !attacker.level().getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(StealthRules.UNSEEN_BLADE_RANGE),
			m -> m != target && m.getTarget() == attacker).isEmpty();
		boolean silent = !seen && Perks.has(attacker, StealthSkill.UNSEEN_BLADE);
		double muffled = pct(attacker, "stealth/muffled");
		if (!silent && muffled > 0 && attacker.getRandom().nextDouble() < muffled) {
			silent = true;
		}
		if (silent) {
			SILENCED.put(target, now + 40);
			if (Perks.has(attacker, StealthSkill.UNSEEN_BLADE) && !seen) {
				StealthSkill.chipThrottled(attacker, StealthSkill.UNSEEN_BLADE, 200, "unseen_blade");
			}
		}
	}

	/**
	 * A mob died (AFTER_DAMAGE doesn't fire for a killing blow): a sneak kill's XP (the health it had left) and kill bonus,
	 * Swift Exit, and Never Seen's tally for a kill it never noticed (without Hearth).
	 */
	public static void died(LivingEntity killed, DamageSource source) {
		Pending p = PENDING.remove(killed);
		if (source.getEntity() instanceof ServerPlayer hitter && hitter != killed) {
			Sneaking.attacked(hitter);
		}
		if (p == null || !p.sneak() || p.tick() != killed.level().getGameTime() || !(source.getEntity() instanceof ServerPlayer killer)
			|| !p.attacker().equals(killer.getUUID())) {
			return;
		}
		double xp = StealthRules.sneakAttackXp(p.healthBefore(), p.healthBefore()) + StealthRules.killBonus(killed.getMaxHealth());
		StealthSkill.award(killer, "attack", xp * Combat.targetFactor(killed));
		double exit = pct(killer, "stealth/swift_exit");
		if (exit > 0) {
			swiftExit(killer, exit, killed.level().getGameTime());
		}
		if (!HearthLink.hearth() && killed instanceof Enemy && !killer.isCreative()) {
			FeatCounters.add(killer, StealthSkill.NEVER_SEEN_COUNTER, StealthRules.NEVER_SEEN_KILL);
		}
	}

	/** Lootfinger: a mob killed unseen (by a sneak attack) drops one more of its loot this share of the time. Read by the drop hook. */
	public static double lootfinger(ServerPlayer player, LivingEntity killed) {
		double pct = pct(player, "stealth/lootfinger");
		if (pct <= 0) {
			return 0;
		}
		Pending p = PENDING.get(killed);
		return p != null && p.sneak() && p.attacker().equals(player.getUUID()) && p.tick() == killed.level().getGameTime() ? pct : 0;
	}

	private static void slow(LivingEntity target, Identifier id, double share, int ticks, long now) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(id);
		speed.addTransientModifier(new AttributeModifier(id, -Math.clamp(share, 0, 0.9), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		SLOWED.computeIfAbsent(target, t -> new HashMap<>()).put(id, now + ticks);
	}

	private static void swiftExit(ServerPlayer player, double share, long now) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SWIFT_EXIT_ID);
		speed.addTransientModifier(new AttributeModifier(SWIFT_EXIT_ID, share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		Ceilings.movement(player);
		SWIFT_EXIT.put(player.getUUID(), now + StealthRules.SWIFT_EXIT_TICKS);
	}

	/** Each tick: slows and Swift Exit run out. */
	public static void tickMobs(MinecraftServer server) {
		steps(server);
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<LivingEntity, Map<Identifier, Long>>> it = SLOWED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Map<Identifier, Long>> e = it.next();
			LivingEntity mob = e.getKey();
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			e.getValue().entrySet().removeIf(x -> {
				if (now >= x.getValue() || !mob.isAlive() || mob.level().getGameTime() >= x.getValue()) {
					if (speed != null) {
						speed.removeModifier(x.getKey());
					}
					return true;
				}
				return false;
			});
			if (e.getValue().isEmpty()) {
				it.remove();
			}
		}
		if (!SWIFT_EXIT.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = SWIFT_EXIT.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Long> e = it.next();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || p.level().getGameTime() >= e.getValue()) {
					if (p != null) {
						AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
						if (speed != null) {
							speed.removeModifier(SWIFT_EXIT_ID);
							Ceilings.movement(p);
						}
					}
					it.remove();
				}
			}
		}
	}

	/** An arrow entered the world: loosed while sneaking, it is a sneak attack wherever it lands (Night Hunter's sure crit). */
	public static void arrowLoaded(Entity entity) {
		if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer p && Sneaking.sneaking(p) && arrow.tickCount == 0) {
			SNEAK_ARROWS.put(arrow, Boolean.TRUE);
		}
	}

	// ---------------------------------------------------------------- alerts, giving up, Darkness

	/** HurtByTargetGoal#alertOthers: a mob silenced by Unseen Blade or Muffled raises no alert. */
	public static boolean silenced(Mob mob) {
		Long until = SILENCED.get(mob);
		return until != null && mob.level().getGameTime() <= until;
	}

	/** TargetGoal's unseen memory: Vanishing and Never Seen make a mob that lost the player give up sooner. */
	public static int unseenMemory(Mob mob, int ticks) {
		if (!(mob.getTarget() instanceof Player player)) {
			return ticks;
		}
		double vanishing = pct(player, "stealth/vanishing");
		boolean neverSeen = Perks.has(player, StealthSkill.NEVER_SEEN);
		return vanishing <= 0 && !neverSeen ? ticks : StealthRules.unseenMemory(ticks, vanishing, neverSeen);
	}

	/** LivingEntity#addEffect: Lurking shortens Darkness and Blindness, Under Its Nose halves Darkness. */
	public static MobEffectInstance effect(LivingEntity entity, MobEffectInstance effect) {
		if (!(entity instanceof Player player) || effect.isInfiniteDuration() || effect.getDuration() <= 1) {
			return effect;
		}
		Holder<MobEffect> kind = effect.getEffect();
		boolean darkness = kind.equals(MobEffects.DARKNESS);
		if (!darkness && !kind.equals(MobEffects.BLINDNESS)) {
			return effect;
		}
		double lurking = pct(player, "stealth/lurking");
		boolean nose = Perks.has(player, StealthSkill.UNDER_ITS_NOSE);
		if (lurking <= 0 && !(darkness && nose)) {
			return effect;
		}
		return new MobEffectInstance(kind, StealthRules.darkDuration(effect.getDuration(), darkness, lurking, nose), effect.getAmplifier(), effect.isAmbient(),
			effect.isVisible(), effect.showIcon());
	}

	// ---------------------------------------------------------------- vibrations

	/** ServerLevel#gameEvent: Quiet Feet, Silent Fall and Footpad make no vibration at all. */
	public static boolean silent(Holder<GameEvent> event, @Nullable Entity source) {
		if (!(source instanceof Player player)) {
			return false;
		}
		boolean step = event.is(GameEvent.STEP);
		boolean land = event.is(GameEvent.HIT_GROUND);
		boolean sneaking = Sneaking.sneaking(player);
		if (sneaking && (step || land) && Perks.has(player, StealthSkill.QUIET_FEET)) {
			return true;
		}
		if (sneaking && land && Perks.has(player, StealthSkill.SILENT_FALL)) {
			return true;
		}
		return (step || land || event.is(GameEvent.SWIM) || event.is(GameEvent.SPLASH)) && Sneaking.footpadCover(player);
	}

	/** VibrationSystem.Listener#handleGameEvent: Hushed, a listener beyond its radius × (1 − X) never hears the player. */
	public static boolean hushed(@Nullable Entity source, Vec3 from, Vec3 listener, int radius) {
		if (!(source instanceof Player player)) {
			return false;
		}
		double hushed = pct(player, "stealth/hushed");
		if (hushed <= 0) {
			return false;
		}
		double r = StealthRules.hushedRadius(radius, hushed);
		return from.distanceToSqr(listener) > r * r;
	}

	// ---------------------------------------------------------------- Shadowstep

	/** ServerPlayer#jumpFromGround: a sneak-jump at an unaware mob within 8 blocks steps behind it. */
	public static void jumped(ServerPlayer player) {
		if (!Sneaking.sneaking(player) || !Perks.has(player, StealthSkill.SHADOWSTEP)) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < Sneaking.shadowstepReady(player)) {
			return;
		}
		Mob mob = Shadowstep.target(player);
		if (mob != null) {
			// Stepped at the end of this tick: the jump arrives inside the movement packet, which mustn't be moved under.
			STEPS.put(player.getUUID(), mob);
		}
	}

	/** Shadowsteps waiting for the end of the tick they were jumped in. */
	private static final Map<UUID, Mob> STEPS = new HashMap<>();

	static void steps(MinecraftServer server) {
		if (STEPS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Mob> e : STEPS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Mob mob = e.getValue();
			if (player == null || !mob.isAlive() || mob.level() != player.level()) {
				continue;
			}
			if (Shadowstep.step(player, mob)) {
				long now = player.level().getGameTime();
				Sneaking.shadowstepUsed(player, now + Math.round(StealthRules.SHADOWSTEP_COOLDOWN * CardHooks.cooldownScale(player)));
				StealthSkill.chip(player, StealthSkill.SHADOWSTEP, "shadowstep");
			}
		}
		STEPS.clear();
	}

	/** The light-fingered fallback (no bonus keys): a share added to Stalking's "animals notice you closer". */
	public static double animalsCloser(Player player) {
		return HearthLink.bonus() ? 0 : pct(player, "stealth/light_fingered");
	}
}
