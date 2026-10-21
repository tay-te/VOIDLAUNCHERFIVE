package dev.voidmc.fray.combat.math;

/**
 * One creature's threat table (CONTRACTS.md §8): at most {@link #MAX} entries keyed by entity id. A new source past
 * the limit replaces the lowest entry if it outweighs it. Threat decays 5 % a second after 5 s without new threat from
 * that source. A taunt puts the taunter at top + 20 % and holds it there for its ticks.
 */
public final class ThreatTable {
	public static final int MAX = 8;
	public static final int DECAY_DELAY = 100;
	public static final float DECAY_PER_TICK = 0.05F / 20F;

	public final int[] ids = new int[MAX];
	public final float[] threat = new float[MAX];
	private final long[] last = new long[MAX];
	public int size;
	private int tauntId = -1;
	private long tauntUntil;

	public void add(int id, float amount, long now) {
		if (amount <= 0) {
			return;
		}
		int i = this.indexOf(id);
		if (i >= 0) {
			this.threat[i] += amount;
			this.last[i] = now;
			return;
		}
		if (this.size < MAX) {
			i = this.size++;
		} else {
			int low = 0;
			for (int j = 1; j < MAX; j++) {
				if (this.threat[j] < this.threat[low]) {
					low = j;
				}
			}
			if (this.threat[low] >= amount) {
				return;
			}
			i = low;
		}
		this.ids[i] = id;
		this.threat[i] = amount;
		this.last[i] = now;
	}

	public void taunt(int id, int ticks, long now) {
		float top = 0;
		for (int i = 0; i < this.size; i++) {
			top = Math.max(top, this.threat[i]);
		}
		int i = this.indexOf(id);
		float want = Math.max(1, top * 1.2F);
		if (i < 0) {
			this.add(id, want, now);
		} else {
			this.threat[i] = Math.max(this.threat[i], want);
			this.last[i] = now;
		}
		this.tauntId = id;
		this.tauntUntil = now + ticks;
	}

	public int indexOf(int id) {
		for (int i = 0; i < this.size; i++) {
			if (this.ids[i] == id) {
				return i;
			}
		}
		return -1;
	}

	public float of(int id) {
		int i = this.indexOf(id);
		return i < 0 ? 0 : this.threat[i];
	}

	/** The id with the most threat (a live taunt wins), or -1. */
	public int top(long now) {
		if (this.tauntId >= 0 && now < this.tauntUntil && this.indexOf(this.tauntId) >= 0) {
			return this.tauntId;
		}
		int best = -1;
		float bestThreat = 0;
		for (int i = 0; i < this.size; i++) {
			if (this.threat[i] > bestThreat) {
				bestThreat = this.threat[i];
				best = this.ids[i];
			}
		}
		return best;
	}

	public void remove(int id) {
		int i = this.indexOf(id);
		if (i < 0) {
			return;
		}
		this.size--;
		this.ids[i] = this.ids[this.size];
		this.threat[i] = this.threat[this.size];
		this.last[i] = this.last[this.size];
	}

	/** Decays stale entries; drops those under 0.1. */
	public void tick(long now) {
		for (int i = this.size - 1; i >= 0; i--) {
			if (now - this.last[i] >= DECAY_DELAY) {
				this.threat[i] -= this.threat[i] * DECAY_PER_TICK + 0.001F;
				if (this.threat[i] < 0.1F) {
					this.remove(this.ids[i]);
				}
			}
		}
	}

	public void clear() {
		this.size = 0;
		this.tauntId = -1;
	}
}
