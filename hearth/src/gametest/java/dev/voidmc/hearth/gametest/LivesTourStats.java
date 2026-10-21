package dev.voidmc.hearth.gametest;

/** The lives' embodied timings since a point in the tour (the talk pass a player, the behaviours a step). */
final class LivesTourStats {
	private LivesTourStats() {
	}

	static void reset() {
		dev.voidmc.hearth.social.lives.LivesFolkAccess.resetTimings();
	}

	static String line() {
		return dev.voidmc.hearth.social.lives.LivesFolkAccess.timings();
	}
}
