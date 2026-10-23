package dev.voidmc.bestiary.boss;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.Perception;
import dev.voidmc.bestiary.api.BestiaryApi;
import dev.voidmc.bestiary.api.BossMusic;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.Encounter;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.combat.FrayDamageSource;
import dev.voidmc.fray.motion.MotionServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bestiary's encounter framework (BESTIARY.md §8.2) on top of Fray's {@code EncounterService} (the bar, Break, banners):
 *
 * <ul>
 * <li><b>Arena</b>: a cylinder saved with the boss. The boss never leaves it (vanilla's home restriction, a walk home, a
 * teleport home when it is far out or stuck) and drops any target that steps out.</li>
 * <li><b>Start</b>: a player (or a test bot) inside the arena whom the boss sees, or who hurts it from inside.</li>
 * <li><b>Reset</b>: nobody in the arena for {@link #resetTicks} (60 s; everyone left or died): full health, back to its
 * spot, the boss's own state put back ({@link Boss#onReset}), the bar ended as a reset.</li>
 * <li><b>No cheese from outside</b>: damage from anyone standing outside the arena, and environmental damage with no
 * attacker (lava, falls, walls, cacti), does nothing.</li>
 * <li><b>Multiplayer</b>: health × (1 + 0.6 per extra participant in the arena, the most seen this fight), the share
 * kept; participants (damage dealt, time in the arena) are saved with the boss.</li>
 * <li><b>Personal loot</b>: each participant with 10 % of the damage or 30 s in the arena rolls the boss's table for
 * themselves ({@link BossLoot}).</li>
 * <li><b>Music</b>: {@link BossMusic} cues, a vanilla placeholder until Gloam answers them.</li>
 * </ul>
 *
 * Everything that must survive a relog or a restart lives in {@link BossData}; a fight that was on when the boss
 * unloaded resumes when it loads (the bar comes back without a new banner).
 */
public final class BossEncounter {
	public static final Identifier PARTY_HEALTH = Bestiary.id("boss_party");
	/** Ticks the arena must be empty before a reset (BESTIARY §8.2: 60 s). Tests lower it. */
	public static int resetTicks = 60 * 20;
	/** Probes: server time spent in boss ticks, unstick moves, resets. */
	public static long tickNanos, ticks, unsticks, resetsDone;

	private static final class Watch {
		Vec3 at = Vec3.ZERO;
		long since;
	}

	private static final Map<Mob, Watch> WATCH = new WeakHashMap<>();

	/** A boss was felled in a fight (lairs record it for the respawn). */
	public interface Felled {
		void felled(Mob boss, BossData data, ServerLevel level);
	}

	public static final net.fabricmc.fabric.api.event.Event<Felled> FELLED = net.fabricmc.fabric.api.event.EventFactory.createArrayBacked(Felled.class,
		ls -> (boss, data, level) -> {
			for (Felled l : ls) {
				l.felled(boss, data, level);
			}
		});

	private BossEncounter() {
	}

	public static void init() {
		// No cheese from outside the arena: Fray hits from outside deal nothing (attacker stage), and so does anything else.
		CombatEvents.MODIFY_DAMAGE.register((target, hit, builder) -> {
			if (builder.stage() == CombatEvents.Stage.ATTACKER && target instanceof Mob m && hit.attacker() instanceof LivingEntity a) {
				BossData d = BossData.get(m);
				if (d != null) {
					builder.more(null, d.arena.contains(a) ? 1 / toughness(m, d) : 0);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof Mob m) || BossData.get(m) == null
			|| allowed(BossData.get(m), source));
		CombatEvents.DAMAGE_DEALT.register((attacker, target, hit, result) -> {
			if (target instanceof Mob m && attacker instanceof LivingEntity a && result.total() > 0) {
				credit(m, a, result.total());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob m && taken > 0 && !(source instanceof FrayDamageSource) && source.getEntity() instanceof LivingEntity a) {
				credit(m, a, taken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob m && entity.level() instanceof ServerLevel level && BossData.get(m) != null) {
				felled(m, BossData.get(m), source, level);
			}
		});
	}

	static boolean allowed(BossData d, DamageSource source) {
		Entity e = source.getEntity();
		if (e instanceof LivingEntity a) {
			return d.arena.contains(a);
		}
		// No attacker: Fray's own procs (Bleed, Burn) count; lava, falls, walls, cacti and the like don't (/kill still works).
		return source instanceof FrayDamageSource || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** Gives a boss its arena (the summon command, a lair): saved, home restriction set, never despawns. */
	public static BossData setup(Mob boss, Arena arena) {
		BossData d = BossData.fresh(arena);
		BossData.set(boss, d);
		boss.setHomeTo(arena.centre(), arena.radius());
		boss.setPersistenceRequired();
		return d;
	}

	/** Players (survival or adventure, alive) and test bots in the level that could fight. */
	public static List<LivingEntity> fighters(ServerLevel level) {
		List<LivingEntity> out = new ArrayList<>(4);
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative()) {
				out.add(p);
			}
		}
		for (LivingEntity bot : BestiaryApi.bots()) {
			if (bot.level() == level && bot.isAlive()) {
				out.add(bot);
			}
		}
		return out;
	}

	public static List<LivingEntity> inside(BossData d, ServerLevel level) {
		List<LivingEntity> out = new ArrayList<>(4);
		for (LivingEntity e : fighters(level)) {
			if (d.arena.contains(e)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Players among {@code es}. */
	static List<ServerPlayer> players(List<LivingEntity> es) {
		List<ServerPlayer> out = new ArrayList<>();
		for (LivingEntity e : es) {
			if (e instanceof ServerPlayer p) {
				out.add(p);
			}
		}
		return out;
	}

	/** The boss's server tick (from its {@code customServerAiStep}). */
	public static void tick(Mob boss, ServerLevel level) {
		long t0 = System.nanoTime();
		try {
			run(boss, level);
		} finally {
			tickNanos += System.nanoTime() - t0;
			ticks++;
		}
	}

	private static void run(Mob boss, ServerLevel level) {
		BossData d = BossData.get(boss);
		if (d == null || !(boss instanceof Boss b) || !boss.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		Arena arena = d.arena;
		if (!boss.hasHome()) {
			boss.setHomeTo(arena.centre(), arena.radius());
		}
		Brain brain = Brain.get(boss);
		if (brain != null && brain.leash == null) {
			brain.leash = arena::contains;
		}
		List<LivingEntity> inside = inside(d, level);
		// The boss fights only what stands in its arena.
		if (brain != null && brain.target != null && (!arena.contains(brain.target) || !brain.target.isAlive())) {
			forget(brain, boss, now);
		}
		if (!d.active) {
			for (LivingEntity e : inside) {
				boolean sees = brain == null ? boss.hasLineOfSight(e) : Perception.sees(brain, e, level);
				if (sees || boss.distanceTo(e) < 6 || boss.getLastHurtByMob() == e) {
					start(boss, b, d, level, e, now, true);
					break;
				}
			}
		}
		if (d.active) {
			Encounter enc = FrayApi.encounters().of(boss);
			if (enc == null) {
				// Resumed after an unload, a relog or a restart: the bar comes back, no new banner; the Break meter as saved.
				FrayApi.encounters().start(boss, b.bossName(), b.bossSubtitle(), b.phaseThresholds(), false);
				if (d.breakMeter > 0 && d.breakMeter < 1) {
					FrayApi.encounters().addBreak(boss, d.breakMeter);
				}
			} else if (!enc.broken()) {
				d.breakMeter = enc.breakMeter();
			}
			for (LivingEntity e : inside) {
				d.participant(e.getUUID(), e.getName().getString()).present++;
			}
			if (inside.size() > d.party) {
				scaleParty(boss, d, inside.size());
			}
			float[] thresholds = b.phaseThresholds();
			float health = boss.getHealth() / Math.max(1, boss.getMaxHealth());
			while (d.phase - 1 < thresholds.length && health <= thresholds[d.phase - 1]) {
				d.phase++;
				b.onPhase(level, d.phase);
				music(boss, "phase_" + d.phase, players(inside));
			}
			if (inside.isEmpty()) {
				if (d.emptySince == 0) {
					d.emptySince = Math.max(1, now);
				} else if (BossMath.resetDue(d.emptySince, now, resetTicks)) {
					reset(boss, b, d, level);
					return;
				}
			} else {
				d.emptySince = 0;
			}
		}
		keepHome(boss, b, d, brain, level, now);
	}

	static void start(Mob boss, Boss b, BossData d, ServerLevel level, LivingEntity first, long now, boolean announce) {
		d.active = true;
		d.started = now;
		d.emptySince = 0;
		d.participant(first.getUUID(), first.getName().getString());
		FrayApi.encounters().start(boss, b.bossName(), b.bossSubtitle(), b.phaseThresholds(), announce);
		Brain brain = Brain.get(boss);
		if (brain != null) {
			brain.target = first;
			brain.lastSeen = now;
			brain.lastKnown = first.position();
			brain.setAwareness(Brain.Awareness.ENGAGED, now);
		}
		if (first instanceof ServerPlayer p && brain != null) {
			Codex.seen(p, brain.profile);
		}
		b.onFightStart(level, first);
		music(boss, "start", players(inside(d, level)));
		Bestiary.LOGGER.info("{} fight started by {}", b.bossName().getString(), first.getName().getString());
	}

	/** Damage on a boss from {@code attacker}: credit, and a fight starts if it came from inside the arena. */
	static void credit(Mob boss, LivingEntity attacker, float amount) {
		BossData d = BossData.get(boss);
		if (d == null || !(boss instanceof Boss b) || !(boss.level() instanceof ServerLevel level) || !d.arena.contains(attacker)) {
			return;
		}
		if (!d.active && (attacker instanceof ServerPlayer || BestiaryApi.bots().contains(attacker))) {
			start(boss, b, d, level, attacker, level.getGameTime(), true);
		}
		if (d.active) {
			d.participant(attacker.getUUID(), attacker.getName().getString()).damage += amount;
		}
	}

	/** More players in the arena: the boss toughens (the most seen this fight). */
	static void scaleParty(Mob boss, BossData d, int players) {
		d.party = players;
	}

	/**
	 * What a boss's damage taken is divided by: the party (×1 + 0.6 per extra player) and the game difficulty's health
	 * factor. Vanilla caps max health at 1024, so this stands in for the extra hit points (the bar shows the same share).
	 */
	public static float toughness(Mob boss, BossData d) {
		return BossMath.toughness(d.active ? d.party : 1, dev.voidmc.bestiary.danger.DangerMath.difficultyHealth(boss.level().getDifficulty().getId()));
	}

	/** Damage outside Fray's pipeline on a boss (the mixin on {@code hurtServer}): divided by its toughness. */
	public static float foreignDamage(LivingEntity e, DamageSource source, float amount) {
		if (!(e instanceof Mob m) || source instanceof FrayDamageSource || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		BossData d = BossData.get(m);
		return d == null ? amount : amount / toughness(m, d);
	}

	/** Nobody in the arena for a minute: full health, back home, the bar ended as a reset. */
	public static void reset(Mob boss, Boss b, BossData d, ServerLevel level) {
		if (FrayApi.encounters().of(boss) != null) {
			FrayApi.encounters().end(boss, Encounter.Outcome.RESET);
		}
		d.active = false;
		d.participants.clear();
		d.emptySince = 0;
		d.phase = 1;
		d.breakMeter = 0;
		d.resets++;
		resetsDone++;
		AttributeInstance inst = boss.getAttribute(Attributes.MAX_HEALTH);
		if (inst != null) {
			inst.removeModifier(PARTY_HEALTH);
		}
		d.party = 1;
		MotionServer.stop(boss, 0);
		Brain brain = Brain.get(boss);
		if (brain != null) {
			forget(brain, boss, level.getGameTime());
			brain.ready.clear();
			brain.forced = null;
		}
		FrayApi.threat().clear(boss);
		boss.clearFire();
		boss.removeAllEffects();
		boss.setHealth(boss.getMaxHealth());
		goHome(boss, d.arena);
		b.onReset(level);
		music(boss, "reset", List.of());
		Bestiary.LOGGER.info("{} reset (arena empty {} s)", b.bossName().getString(), resetTicks / 20);
	}

	static void felled(Mob boss, BossData d, DamageSource source, ServerLevel level) {
		if (!d.active || !(boss instanceof Boss b)) {
			return;
		}
		List<ServerPlayer> heard = new ArrayList<>();
		Brain brain = Brain.get(boss);
		float total = d.totalDamage();
		for (Map.Entry<UUID, BossData.Participant> e : d.participants.entrySet()) {
			BossData.Participant p = e.getValue();
			if (!BossMath.earnsLoot(p.damage, total, p.present)) {
				continue;
			}
			Entity who = level.getEntity(e.getKey());
			if (who == null) {
				who = level.getServer().getPlayerList().getPlayer(e.getKey());
			}
			BossLoot.award(boss, b.personalLoot(), e.getKey(), who, source, level);
			if (who instanceof ServerPlayer sp) {
				heard.add(sp);
				if (brain != null) {
					Codex.felled(sp, brain.profile);
				}
			}
		}
		d.active = false;
		FELLED.invoker().felled(boss, d, level);
		music(boss, "victory", heard);
	}

	static void forget(Brain brain, Mob boss, long now) {
		brain.target = null;
		brain.lastKnown = null;
		brain.heardAt = null;
		brain.setAwareness(Brain.Awareness.UNAWARE, now);
		boss.setTarget(null);
	}

	static void goHome(Mob boss, Arena arena) {
		Vec3 h = arena.homeVec();
		boss.getNavigation().stop();
		boss.snapTo(h.x, h.y, h.z, arena.homeYaw(), 0);
		boss.setYHeadRot(arena.homeYaw());
		boss.setYBodyRot(arena.homeYaw());
		boss.setDeltaMovement(Vec3.ZERO);
		boss.resetFallDistance();
	}

	/**
	 * Keeps the boss in its arena and unstuck: out of the arena it walks home (and is put back when far out, fallen
	 * below it or in a wall); idle it returns to its spot; a boss that wants to move but hasn't for 5 s is nudged along a
	 * fresh path, and after 10 s put back home.
	 */
	static void keepHome(Mob boss, Boss b, BossData d, @Nullable Brain brain, ServerLevel level, long now) {
		Arena arena = d.arena;
		double out = arena.distance(boss.position()) - arena.radius();
		boolean acting = MotionServer.running(boss) != null;
		if (out > 6 || boss.getY() < arena.centre().getY() - arena.below() - 3 || boss.isInWall()) {
			unstick(boss, arena, level, "out of its arena");
			return;
		}
		boolean engaged = brain != null && brain.awareness == Brain.Awareness.ENGAGED && brain.target != null;
		if (out > 0.5 && !acting) {
			Vec3 h = arena.homeVec();
			boss.getNavigation().moveTo(h.x, h.y, h.z, 1.0);
		} else if (!engaged && !acting && !d.active && boss.position().distanceToSqr(arena.homeVec()) > 2.5 * 2.5 && boss.getNavigation().isDone()) {
			Vec3 h = arena.homeVec();
			boss.getNavigation().moveTo(h.x, h.y, h.z, 0.8);
		}
		// The watchdog: wants to move (a path, or a target out of reach) and doesn't.
		Watch w = WATCH.computeIfAbsent(boss, k -> new Watch());
		boolean wants = !acting && !b.busyScripted() && (!boss.getNavigation().isDone() || engaged && boss.distanceTo(brain.target) > 4.5
			|| !engaged && boss.position().distanceToSqr(arena.homeVec()) > 3 * 3);
		if (!wants || boss.position().distanceToSqr(w.at) > 1.0) {
			w.at = boss.position();
			w.since = now;
			return;
		}
		if (BossMath.stuck(boss.position().distanceTo(w.at), w.since, now, 200, 1.0)) {
			unstick(boss, arena, level, "stuck for 10 s");
			w.at = boss.position();
			w.since = now;
		} else if (now - w.since == 100) {
			// First try: a fresh path (to the target, or home), and a hop.
			Vec3 goal = engaged ? brain.target.position() : arena.homeVec();
			boss.getNavigation().stop();
			boss.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
			if (boss.onGround()) {
				boss.getJumpControl().jump();
			}
		}
	}

	static void unstick(Mob boss, Arena arena, ServerLevel level, String why) {
		unsticks++;
		BlockPos at = boss.blockPosition();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 1, boss.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		goHome(boss, arena);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 1, boss.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		Bestiary.LOGGER.info("{} at {} {}: back to its spot", boss.getName().getString(), at.toShortString(), why);
	}

	/** Fires a music cue; plays the placeholder when nobody handled it. */
	static void music(Mob boss, String cue, List<ServerPlayer> players) {
		if (BossMusic.CUE.invoker().cue(boss, cue, players)) {
			return;
		}
		SoundEvent s;
		float pitch;
		switch (cue) {
			case "start" -> {
				s = SoundEvents.BELL_BLOCK;
				pitch = 0.5F;
			}
			case "victory" -> {
				s = SoundEvents.BELL_RESONATE;
				pitch = 0.8F;
			}
			case "reset" -> {
				return;
			}
			default -> {
				s = SoundEvents.BELL_BLOCK;
				pitch = 0.4F;
			}
		}
		for (ServerPlayer p : players) {
			p.level().playSound(null, p.blockPosition(), s, SoundSource.HOSTILE, 1.2F, pitch);
		}
	}
}
