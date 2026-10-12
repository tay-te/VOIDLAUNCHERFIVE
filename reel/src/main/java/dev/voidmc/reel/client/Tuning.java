package dev.voidmc.reel.client;

import dev.voidmc.reel.shot.Shot;
import java.util.Locale;

/**
 * Settings that can be changed on a running studio (POST /set?key=value) to compare ways of filming without a
 * restart: whether the spectator body follows the camera every frame, and overrides for the shot's own jitter and
 * supersampling ("shot" goes back to the shot's).
 */
public final class Tuning {
	/** Keep the player's body (and its interpolated position) exactly at the camera on every frame, not only on ticks. */
	static volatile boolean syncBody = true;
	/** VOID LOD's frame timers (fades, rebuild and swap gates) run on the recording's clock, not the wall's. */
	static volatile boolean lodClock = true;
	/** VOID LOD's drawn tiles stay as they are across the samples of one frame, so no frame blends two tile sets. */
	static volatile boolean lodFreeze = true;
	static volatile Boolean jitter;
	static volatile Integer supersample;

	private Tuning() {
	}

	public static boolean lodClock() {
		return lodClock;
	}

	public static boolean lodFreeze() {
		return lodFreeze;
	}

	static boolean jitter(Shot shot) {
		Boolean set = jitter;
		return set != null ? set : shot.render().jitter();
	}

	static int supersample(Shot shot) {
		Integer set = supersample;
		return set != null ? set : shot.render().supersample();
	}

	static String set(String key, String value) {
		switch (key) {
			case "syncBody" -> syncBody = Boolean.parseBoolean(value);
			case "lodClock" -> lodClock = Boolean.parseBoolean(value);
			case "lodFreeze" -> lodFreeze = Boolean.parseBoolean(value);
			case "jitter" -> jitter = "shot".equals(value) ? null : Boolean.valueOf(value);
			case "supersample" -> {
				Integer s = "shot".equals(value) ? null : Integer.valueOf(value);
				if (s != null && (s < 1 || s > 4)) {
					throw new IllegalArgumentException("supersample must be 1-4");
				}
				supersample = s;
			}
			default -> throw new IllegalArgumentException("unknown setting " + key + " (syncBody, lodClock, lodFreeze, jitter, supersample)");
		}
		return describe();
	}

	static String describe() {
		return String.format(Locale.ROOT, "{\"syncBody\":%s,\"lodClock\":%s,\"lodFreeze\":%s,\"jitter\":\"%s\",\"supersample\":\"%s\"}",
			syncBody, lodClock, lodFreeze, jitter == null ? "shot" : jitter, supersample == null ? "shot" : supersample);
	}
}
