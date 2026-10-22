package dev.voidmc.fray.combat.math;

/**
 * A key that fires only when pressed and released by itself (the user's call on Alt, 2026-10-21): any other key or
 * mouse button pressed while it is held spoils it, and so does holding it longer than {@code maxHoldMs} (Alt held for
 * a menu, Alt + Tab). Fray's dodge on Left Alt uses it so Alt + middle mouse stays pick block; C uses the same gate in
 * creative so C + a hotbar number stays the save/load toolbar. Pure: the client feeds it key events.
 */
public final class LoneKey {
	private final long maxHoldMs;
	private boolean held;
	private boolean spoiled;
	private long downAt;

	public LoneKey(long maxHoldMs) {
		this.maxHoldMs = maxHoldMs;
	}

	/** The key went down; {@code spoiledAlready} when a key it must not chord with is already held. */
	public void press(long nowMs, boolean spoiledAlready) {
		this.held = true;
		this.spoiled = spoiledAlready;
		this.downAt = nowMs;
	}

	/** Another key or button went down. */
	public void other() {
		if (this.held) {
			this.spoiled = true;
		}
	}

	/** The key came up: true when it fires (held alone, not too long). */
	public boolean release(long nowMs) {
		boolean fire = this.held && !this.spoiled && nowMs - this.downAt <= this.maxHoldMs;
		this.held = false;
		this.spoiled = false;
		return fire;
	}

	/** Focus lost or a screen opened: the press is forgotten. */
	public void cancel() {
		this.held = false;
		this.spoiled = false;
	}

	public boolean held() {
		return this.held;
	}

	public boolean spoiled() {
		return this.spoiled;
	}
}
