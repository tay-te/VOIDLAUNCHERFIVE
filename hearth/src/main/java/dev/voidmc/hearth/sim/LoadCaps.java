package dev.voidmc.hearth.sim;

import net.minecraft.server.MinecraftServer;

/**
 * Entity and work caps that shrink as the server slows (docs/LIVING_WORLD_2.md §9.2), on the same curve as the sim
 * clock's budget: full up to 60 % of {@code high_mspt}, nothing new above it, and above {@code high_mspt} itself the
 * farthest are shed. Every living-world lane sizes its caps through here so the server slows them all alike. Pure.
 */
public final class LoadCaps {
	private LoadCaps() {
	}

	/** The server's average tick time, in milliseconds. */
	public static double mspt(MinecraftServer server) {
		return server.getAverageTickTimeNanos() / 1e6;
	}

	/** The fraction of a cap allowed at this tick time: 1 up to 60 % of {@code highMspt}, falling to 0 at it. */
	public static double scale(double mspt, double highMspt) {
		double comfortable = highMspt * 0.6;
		if (mspt <= comfortable) {
			return 1;
		}
		if (mspt >= highMspt) {
			return 0;
		}
		return 1 - (mspt - comfortable) / (highMspt - comfortable);
	}

	/** {@code cap} at this tick time (never below {@code floor}, never above {@code cap}). */
	public static int cap(int cap, int floor, double mspt, double highMspt) {
		int f = Math.min(floor, cap);
		return Math.max(f, (int) Math.floor(cap * scale(mspt, highMspt)));
	}

	/** Whether new things (parties, flocks, reactions) may start: false above 60 % of {@code highMspt}. */
	public static boolean admitting(double mspt, double highMspt) {
		return mspt <= highMspt * 0.6;
	}

	/** Whether the farthest should be folded away now: above {@code highMspt}. */
	public static boolean shedding(double mspt, double highMspt) {
		return mspt > highMspt;
	}

	/** The sim clock's {@code high_mspt}, which these caps follow unless a lane's config sets its own. */
	public static double highMspt() {
		return SimModule.rules().config.clock().highMspt();
	}
}
