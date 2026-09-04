package dev.voidpvp.client.net;

/**
 * Reconnect backoff for the WS client (§6.9): exponential with a ceiling and a
 * little jitter, so a launcher that is restarting does not meet a tight loop
 * and a stopped launcher costs one attempt every few seconds forever.
 */
public final class Backoff {

    private final long baseMs;
    private final long maxMs;
    private int attempt;

    public Backoff(long baseMs, long maxMs) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
    }

    public static Backoff defaults() {
        return new Backoff(500, 15000);
    }

    /** Delay before the next attempt, in milliseconds. */
    public long nextDelayMs() {
        long delay = baseMs << Math.min(attempt, 20);
        if (delay > maxMs || delay <= 0) {
            delay = maxMs;
        }
        attempt++;
        long jitter = (long) (delay * 0.2 * Math.random());
        return delay + jitter;
    }

    /** Delay without jitter, for tests. */
    public long peekDelayMs() {
        long delay = baseMs << Math.min(attempt, 20);
        return delay > maxMs || delay <= 0 ? maxMs : delay;
    }

    public int attempts() {
        return attempt;
    }

    public void reset() {
        attempt = 0;
    }
}
