package dev.voidmc.hub.model;

/**
 * Paces the hovered-item card.
 *
 * <p>The wells' hover ring follows the pointer every frame (a ~2 ms repaint of two small boxes).
 * The card is a bigger repaint (5–10 ms of Ultralight's rasteriser), and a pointer sweeping across
 * the grid at 30 wells a second would have it repaint 30 times a second for text nobody can read
 * at that speed. So the card updates at once when it has been still for a while, and otherwise at
 * most every {@link #MIN_INTERVAL_NS} — or as soon as the pointer rests for
 * {@link #SETTLE_NS} on a new item.
 */
public final class CardPacer {

	public static final long SETTLE_NS = 60_000_000L;
	public static final long MIN_INTERVAL_NS = 150_000_000L;

	private String shown;
	private String pending;
	private long pendingSince;
	private long lastSent = Long.MIN_VALUE / 2;

	/** Whether the card for {@code key} should be sent now (and, if so, it counts as shown). */
	public boolean offer(String key, long now) {
		if (key.equals(this.shown)) {
			this.pending = null;
			return false;
		}
		if (!key.equals(this.pending)) {
			this.pending = key;
			this.pendingSince = now;
		}
		if (now - this.pendingSince >= SETTLE_NS || now - this.lastSent >= MIN_INTERVAL_NS) {
			this.shown = key;
			this.pending = null;
			this.lastSent = now;
			return true;
		}
		return false;
	}

	/** Forgets what is shown: the next offer is sent at once (a screen just opened). */
	public void reset() {
		this.shown = null;
		this.pending = null;
		this.lastSent = Long.MIN_VALUE / 2;
	}
}
