package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A creature's AI state (not saved: a fight doesn't survive a restart, the creature just perceives again): awareness,
 * what it remembers of its target, cooldowns, the option it is carrying out, its token, role and squad, and what it has
 * seen the target do (the adaptation of BESTIARY.md §2.1).
 */
public final class Brain {
	public static final AttachmentType<Brain> TYPE = AttachmentRegistry.create(Bestiary.id("brain"));

	public enum Awareness {
		UNAWARE, SUSPICIOUS, SEARCHING, ENGAGED, FLEEING
	}

	/** A positioning option (BESTIARY.md §2.1 executor): what to do when not attacking. */
	public enum Position {
		CLOSE, CIRCLE, FLANK, RETREAT, KITE, GUARD, STRAFE, HOLD
	}

	public final Mob mob;
	public CreatureProfile profile;
	public Awareness awareness = Awareness.UNAWARE;
	public long awarenessSince;
	public @Nullable LivingEntity target;
	public @Nullable Vec3 lastKnown;
	public long lastSeen = Long.MIN_VALUE / 2;
	public long lastHeard = Long.MIN_VALUE / 2;
	public @Nullable Vec3 heardAt;
	public final Map<String, Long> ready = new HashMap<>();
	public @Nullable String lastMove;
	public int repeats;
	/** The move running now (its clip is on the action layer), or null. */
	public @Nullable MoveDef move;
	public long moveStart;
	public float moveSpeed = 1;
	public @Nullable Object moveState;
	public @Nullable Position position;
	public long positionUntil;
	public long nextThink;
	public long lastPath = Long.MIN_VALUE / 2;
	public @Nullable Vec3 pathGoal;
	public int ringSlot;
	public String role = "bruiser";
	/** A token held on this target id (-1 none). */
	public int tokenTarget = -1;
	public MoveDef.Tokens tokenKind = MoveDef.Tokens.NONE;
	public @Nullable String forced;
	public long lastHurt = Long.MIN_VALUE / 2;
	public long guardUntil;
	public boolean guarding;
	public long nextSidestep;
	public boolean enraged;
	/** Ironbound: blunt hits its plate has taken (3 break it). */
	public int plateHits;
	public long lastVenomCloud = Long.MIN_VALUE / 2;
	/** A shield's guard: points left before it breaks (4; one back every 2 s). */
	public int guardPoints = 4;
	public long guardRegenAt;
	/** Adaptation: decaying counts of what the target was seen doing (guarding, kiting away, drinking, attacking). */
	public float seenGuard;
	public float seenKite;
	public float seenDrink;
	public float seenAttack;
	public double lastTargetDistance = -1;
	public boolean takenOver;
	/** Ticks of BESTIARY.md's per-creature work (perception, tactics, executor) for the perf gate. */
	public static long perceptionNanos, tacticsNanos, executorNanos, perceptionRuns, tacticsRuns, executorRuns;

	public Brain(Mob mob, CreatureProfile profile) {
		this.mob = mob;
		this.profile = profile;
		this.ringSlot = Math.floorMod(mob.getUUID().hashCode(), 12);
	}

	public static @Nullable Brain get(Mob mob) {
		return mob.getAttached(TYPE);
	}

	public void setAwareness(Awareness a, long now) {
		if (this.awareness != a) {
			this.awareness = a;
			this.awarenessSince = now;
		}
	}

	public boolean ready(String move, long now) {
		return this.ready.getOrDefault(move, Long.MIN_VALUE) <= now;
	}

	/** The next move this creature should use, whatever the scores say (a boss's phase script, tests). */
	public void force(String moveId) {
		this.forced = moveId;
	}

	public boolean busy() {
		return this.move != null;
	}
}
