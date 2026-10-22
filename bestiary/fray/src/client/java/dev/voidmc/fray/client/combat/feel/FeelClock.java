package dev.voidmc.fray.client.combat.feel;

import java.util.function.LongSupplier;

/**
 * The clock hit-stop and camera shake run on: wall time (nanoseconds). Tests that film a tick at a time (one screenshot
 * a tick, slower than real time) switch it to game time so a 4-tick freeze lasts 4 filmed ticks.
 */
public final class FeelClock {
	public static volatile LongSupplier source = System::nanoTime;

	private FeelClock() {
	}

	public static long now() {
		return source.getAsLong();
	}
}
