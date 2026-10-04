package dev.voidmc.hearth.npc;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.npc.routine.Schedule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The sim calendar as the NPC module reads it: the sim world's day when the sim module is running, otherwise the
 * overworld clock (which is what the sim follows too: a day is 24000 ticks).
 */
public final class NpcTime {
	private static volatile @Nullable MinecraftServer server;

	private NpcTime() {
	}

	static void setServer(@Nullable MinecraftServer s) {
		server = s;
	}

	/** Whole sim days since the world began (0 with no server running, as in tests). */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	public static long today() {
		MinecraftServer s = server;
		if (s == null) {
			return 0;
		}
		ServerLevel overworld = s.overworld();
		HearthWorld world = HearthWorld.of(overworld);
		return world != null ? world.day() : overworld.getOverworldClockTime() / 24000L;
	}

	/** The overworld clock's ticks: day count times 24000 plus the time of day. */
	public static long clock(ServerLevel level) {
		return level.getOverworldClockTime();
	}

	public static int minuteOfDay(ServerLevel level) {
		return Schedule.minuteOfDay(clock(level));
	}
}
