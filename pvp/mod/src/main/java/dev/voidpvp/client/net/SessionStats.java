package dev.voidpvp.client.net;

/**
 * The numbers behind the {@code session} message (§7): mean FPS and played
 * time for this game session, cumulative rather than deltas, reported every 60
 * seconds and once more on exit.
 */
public final class SessionStats {

    /** {@code msg_session} cadence, in milliseconds. */
    public static final long REPORT_INTERVAL_MS = 60000L;

    private final long startedAtMs;
    private long fpsSum;
    private long fpsSamples;
    private long pingSum;
    private long pingSamples;
    private long lastReportMs;

    public SessionStats(long nowMs) {
        this.startedAtMs = nowMs;
        this.lastReportMs = nowMs;
    }

    /** One FPS sample; the client tick calls this 20 times a second. */
    public void sample(int fps) {
        if (fps <= 0) {
            return;
        }
        fpsSum += fps;
        fpsSamples++;
    }

    public double fpsAverage() {
        return fpsSamples == 0 ? 0 : (double) fpsSum / (double) fpsSamples;
    }

    /**
     * One ping reading, in milliseconds; the client tick calls this alongside {@link #sample}.
     * Unknown readings (-1 in singleplayer, 0 before the player list arrives) are not pings and
     * are skipped, so the mean is only ever over real round trips.
     */
    public void samplePing(int ping) {
        if (ping <= 0) {
            return;
        }
        pingSum += ping;
        pingSamples++;
    }

    /** Mean ping over the session, or -1 when there has not been a reading to average. */
    public double pingAverage() {
        return pingSamples == 0 ? -1 : (double) pingSum / (double) pingSamples;
    }

    public long playedMs(long nowMs) {
        return Math.max(0, nowMs - startedAtMs);
    }

    /** True when a periodic report is due; resets the timer when it is. */
    public boolean shouldReport(long nowMs) {
        if (nowMs - lastReportMs < REPORT_INTERVAL_MS) {
            return false;
        }
        lastReportMs = nowMs;
        return true;
    }
}
