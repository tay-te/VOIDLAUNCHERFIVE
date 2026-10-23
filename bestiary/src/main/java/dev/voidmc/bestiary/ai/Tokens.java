package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.math.TokenPool;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.danger.DangerConfig;
import dev.voidmc.bestiary.danger.DangerMath;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;

/**
 * Attack tokens per target (BESTIARY.md §5): melee 2/3/4 by danger, ranged 2, ±1 by game difficulty and Mythic; an
 * elite takes two; a creature whose profile ignores tokens (bosses) never asks. A token is held from the move's start to
 * its end (and expires on its own after 5 s).
 */
public final class Tokens {
	private static final Int2ObjectOpenHashMap<TokenPool[]> POOLS = new Int2ObjectOpenHashMap<>();
	public static final int TTL = 100;
	/** Tests: how often a request was refused because the pool was full. */
	public static long refusals;
	public static long grants;

	private Tokens() {
	}

	private static TokenPool pool(int target, MoveDef.Tokens kind) {
		TokenPool[] p = POOLS.computeIfAbsent(target, t -> new TokenPool[] {new TokenPool(), new TokenPool()});
		return p[kind == MoveDef.Tokens.RANGED ? 1 : 0];
	}

	static int difficultyStep(LivingEntity e) {
		Difficulty d = e.level().getDifficulty();
		return DangerMath.difficultyTokens(d.getId(), DangerConfig.get().mythic);
	}

	public static int capacity(Brain b, MoveDef.Tokens kind) {
		int step = difficultyStep(b.mob);
		return kind == MoveDef.Tokens.RANGED ? TokenPool.rangedCapacity(step) : TokenPool.meleeCapacity(CreatureData.of(b.mob).danger(), step);
	}

	/** Takes (or keeps) a token on {@code target} for this creature; true when it may attack. */
	public static boolean acquire(Brain b, LivingEntity target, MoveDef.Tokens kind, long now) {
		if (kind == MoveDef.Tokens.NONE || b.profile.ignoresTokens()) {
			return true;
		}
		if (b.tokenTarget >= 0 && (b.tokenTarget != target.getId() || b.tokenKind != kind)) {
			release(b);
		}
		int cost = CreatureData.of(b.mob).elite() ? 2 : 1;
		boolean ok = pool(target.getId(), kind).acquire(b.mob.getId(), cost, capacity(b, kind), now, TTL);
		if (ok) {
			b.tokenTarget = target.getId();
			b.tokenKind = kind;
			grants++;
		} else {
			refusals++;
		}
		return ok;
	}

	/** Whether a token of this kind would be granted now (no side effects beyond expiring old ones). */
	public static boolean available(Brain b, LivingEntity target, MoveDef.Tokens kind, long now) {
		if (kind == MoveDef.Tokens.NONE || b.profile.ignoresTokens()) {
			return true;
		}
		TokenPool p = pool(target.getId(), kind);
		if (p.holds(b.mob.getId(), now)) {
			return true;
		}
		int cost = CreatureData.of(b.mob).elite() ? 2 : 1;
		return p.used() + cost <= capacity(b, kind);
	}

	public static void release(Brain b) {
		if (b.tokenTarget >= 0) {
			TokenPool[] p = POOLS.get(b.tokenTarget);
			if (p != null) {
				p[b.tokenKind == MoveDef.Tokens.RANGED ? 1 : 0].release(b.mob.getId());
			}
		}
		b.tokenTarget = -1;
		b.tokenKind = MoveDef.Tokens.NONE;
	}

	/** Melee token holders on a target now (tests). */
	public static int holders(LivingEntity target, MoveDef.Tokens kind, long now) {
		TokenPool[] p = POOLS.get(target.getId());
		return p == null ? 0 : p[kind == MoveDef.Tokens.RANGED ? 1 : 0].holders(now);
	}

	public static void clear() {
		POOLS.clear();
	}
}
