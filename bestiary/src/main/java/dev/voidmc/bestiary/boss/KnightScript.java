package dev.voidmc.bestiary.boss;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.anim.BestiaryAnim;
import dev.voidmc.bestiary.anim.GallowsKnight;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.Encounter;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.motion.MotionServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Gallows Knight's phase script (BESTIARY.md §8.3) on top of the AI framework: the moves come from its profile
 * (Tactics scores them; {@link KnightMoves} gates them by phase and the "one red in any three" rule), this adds:
 *
 * <ul>
 * <li>the transition at 55 %: once the current move is done, the knight tears the gibbet cage off its back (a scripted
 * move with no hit: a moment to breathe) and the cage becomes a rolling hazard ({@link GibbetCage});</li>
 * <li>the Break: when the meter fills it drops to one knee for the 60 Broken ticks (Fray staggers it; this plays the
 * kneel), and a parried cut (moves tagged {@code parry_stagger}) staggers it for 26 ticks; weak-point hits add to the
 * meter;</li>
 * <li>no cheese: a target it can't reach (up a pillar, across a gap) for 3 s gets the chain lash, in either phase.</li>
 * </ul>
 */
public final class KnightScript {
	/** Weak-point hits add this to the Break meter on top of their poise (BESTIARY §8.1 rule 5). */
	public static final float WEAK_POINT_BREAK = 0.04F;
	public static final int PARRY_STAGGER = 26;

	private final GallowsKnight knight;
	/** Tell colours of its last moves, newest last (the red rule). */
	final List<String> recent = new ArrayList<>();
	boolean tearing;
	long tearAsked;
	long unreachableSince = -1;
	/** Transitions played and Breaks taken (tests). */
	public int transitions, breaks;

	public KnightScript(GallowsKnight knight) {
		this.knight = knight;
	}

	public static void init() {
		CombatEvents.POISE_BROKEN.register((entity, by, ticks) -> {
			if (entity instanceof GallowsKnight k) {
				Encounter e = FrayApi.encounters().of(k);
				if (e != null && e.broken()) {
					// Fray's stagger clip just started; the Break is the long kneel.
					MotionServer.play(k, GallowsKnight.BROKEN, 1);
					k.script.breaks++;
					if (k.level() instanceof ServerLevel level) {
						level.playSound(null, k.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.5F, 0.5F);
					}
				}
			}
		});
		CombatEvents.GUARDED.register((defender, attacker, kind, amount) -> {
			if (kind == GuardOutcome.PARRIED && attacker instanceof GallowsKnight k) {
				Brain b = Brain.get(k);
				if (b != null && b.move != null && b.move.tagged("parry_stagger") && !FrayApi.statuses().incapacitated(k)) {
					FrayApi.statuses().apply(k, StatusType.STAGGER, PARRY_STAGGER, defender);
				}
			}
		});
		CombatEvents.DAMAGE_DEALT.register((attacker, target, hit, result) -> {
			if (target instanceof GallowsKnight k && hit.has(HitFlags.WEAK_POINT) && result.total() > 0) {
				FrayApi.encounters().addBreak(k, WEAK_POINT_BREAK);
				if (k.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.CRIT, k.getX(), k.getY() + 1.9, k.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
				}
			}
		});
	}

	public boolean transitioning() {
		return this.tearing;
	}

	public List<String> recent() {
		return this.recent;
	}

	void remember(String colour) {
		this.recent.add(colour);
		while (this.recent.size() > 3) {
			this.recent.removeFirst();
		}
	}

	public void fightStarted(ServerLevel level) {
		this.recent.clear();
		this.unreachableSince = -1;
	}

	public void phase(ServerLevel level, int phase) {
		Bestiary.LOGGER.info("The Gallows Knight enters phase {}", phase);
	}

	public void reset(ServerLevel level) {
		BossData d = BossData.get(this.knight);
		if (d != null) {
			d.caged = true;
		}
		this.knight.setCaged(true);
		this.tearing = false;
		this.recent.clear();
		for (GibbetCage c : this.knight.cages(level)) {
			c.discard();
		}
	}

	public void tick(ServerLevel level) {
		BossData d = BossData.get(this.knight);
		if (d == null) {
			return;
		}
		if (this.knight.caged() != d.caged) {
			this.knight.setCaged(d.caged);
		}
		long now = level.getGameTime();
		Brain b = Brain.get(this.knight);
		// The transition: wait for the current move to end, then tear the cage off (a forced, scripted move).
		if (d.active && d.phase >= 2 && d.caged) {
			boolean free = b == null || b.move == null && !FrayApi.statuses().incapacitated(this.knight);
			if (!this.tearing && free) {
				if (b != null && b.target != null && b.awareness == Brain.Awareness.ENGAGED) {
					b.force("tear_cage");
					b.nextThink = now;
					this.tearing = true;
					this.tearAsked = now;
				} else {
					this.tearOff(level);
				}
			} else if (this.tearing && now - this.tearAsked > 40 && (b == null || b.move == null || !b.move.id().equals("tear_cage"))) {
				// It never got going (no target, interrupted before the tear): tear it now so phase 2 is whole.
				this.tearOff(level);
			}
		}
		if (this.tearing && !d.caged && (b == null || b.move == null || !b.move.id().equals("tear_cage"))) {
			this.tearing = false;
		}
		// No cheese: a target out of reach for 3 s (above it, across a gap) is pulled down by the chain.
		if (b != null && b.target != null && b.awareness == Brain.Awareness.ENGAGED && b.move == null && d.active) {
			LivingEntity t = b.target;
			boolean above = t.getY() - this.knight.getY() > 2.2 && this.knight.distanceTo(t) < 14;
			boolean stuck = this.knight.getNavigation().isDone() && this.knight.distanceTo(t) > 4 && this.knight.distanceTo(t) < 14;
			if (above || stuck) {
				if (this.unreachableSince < 0) {
					this.unreachableSince = now;
				} else if (now - this.unreachableSince > 60 && b.ready("chain_lash", now)) {
					b.force("chain_lash");
					b.nextThink = now;
					this.unreachableSince = -1;
				}
			} else {
				this.unreachableSince = -1;
			}
		}
	}

	/** The cage comes off: no longer drawn on its back, and down it goes, rolling away behind the knight. */
	public void tearOff(ServerLevel level) {
		BossData d = BossData.get(this.knight);
		if (d == null || !d.caged) {
			return;
		}
		d.caged = false;
		this.knight.setCaged(false);
		this.transitions++;
		float yaw = this.knight.getYRot();
		double r = Math.toRadians(yaw + 90);
		Vec3 back = new Vec3(-Math.cos(r), 0, -Math.sin(r));
		GibbetCage cage = BestiaryAnim.GIBBET_CAGE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (cage != null) {
			Vec3 at = this.knight.position().add(back.scale(1.2)).add(0, 1.4, 0);
			cage.snapTo(at.x, at.y, at.z, yaw + 180, 0);
			cage.thrown(this.knight.getUUID(), yaw + 180);
			cage.setDeltaMovement(back.scale(0.25).add(0, 0.15, 0));
			level.addFreshEntity(cage);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.knight.getX(), this.knight.getY() + 1.8, this.knight.getZ(), 16, 0.4, 0.3, 0.4, 0.02);
		level.playSound(null, this.knight.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
	}
}
