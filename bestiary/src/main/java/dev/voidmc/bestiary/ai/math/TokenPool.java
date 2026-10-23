package dev.voidmc.bestiary.ai.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Attack tokens for one target (BESTIARY.md §5): at most {@code capacity} cost may be held at once; a creature without
 * a token positions instead of attacking. Tokens expire after {@code ttl} ticks so a creature that died or unloaded
 * never keeps one. Pure maths (JUnit: {@code AiMathTest}); one pool per target and kind (melee, ranged).
 */
public final class TokenPool {
	private record Hold(int holder, int cost, long until) {
	}

	private final List<Hold> holds = new ArrayList<>(4);

	/** Takes a token for {@code holder}; false when it would exceed {@code capacity}. Holding one already renews it. */
	public boolean acquire(int holder, int cost, int capacity, long now, int ttl) {
		this.expire(now);
		for (int i = 0; i < this.holds.size(); i++) {
			if (this.holds.get(i).holder == holder) {
				this.holds.set(i, new Hold(holder, this.holds.get(i).cost, now + ttl));
				return true;
			}
		}
		if (this.used() + cost > capacity) {
			return false;
		}
		this.holds.add(new Hold(holder, cost, now + ttl));
		return true;
	}

	public void release(int holder) {
		this.holds.removeIf(h -> h.holder == holder);
	}

	public boolean holds(int holder, long now) {
		this.expire(now);
		for (Hold h : this.holds) {
			if (h.holder == holder) {
				return true;
			}
		}
		return false;
	}

	public int used() {
		int u = 0;
		for (Hold h : this.holds) {
			u += h.cost;
		}
		return u;
	}

	public int holders(long now) {
		this.expire(now);
		return this.holds.size();
	}

	public boolean empty(long now) {
		this.expire(now);
		return this.holds.isEmpty();
	}

	private void expire(long now) {
		this.holds.removeIf(h -> h.until < now);
	}

	/** Melee tokens per target by danger (D1-2: 2, D3-4: 3, D5: 4) and game difficulty (easy -1, hard and Mythic +1). */
	public static int meleeCapacity(float danger, int difficultyStep) {
		int base = danger >= 4.5F ? 4 : danger >= 2.5F ? 3 : 2;
		return Math.max(1, base + difficultyStep);
	}

	/** Ranged tokens per target: 2 (difficulty as melee, at least 1). */
	public static int rangedCapacity(int difficultyStep) {
		return Math.max(1, 2 + difficultyStep);
	}
}
