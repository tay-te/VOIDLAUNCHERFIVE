package dev.voidmc.hearth.quest.world;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where the quest module finds settlements: the sim module's {@link HearthWorld} when it runs, plus the dev sandbox's
 * in-memory ones. Every lookup the module makes goes through here so the sandbox works with or without the sim.
 */
public final class QuestWorlds {
	public static final long TICKS_PER_DAY = 24000L;

	private QuestWorlds() {
	}

	public static @Nullable Settlement at(ServerLevel level, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		Settlement s = world == null ? null : world.settlementAt(pos);
		if (s == null && isSandboxLevel(level)) {
			s = SandboxSettlements.at(pos);
		}
		return s;
	}

	public static @Nullable Settlement nearest(ServerLevel level, BlockPos pos, int maxDistance) {
		HearthWorld world = HearthWorld.of(level);
		Settlement s = world == null ? null : world.nearest(pos, maxDistance);
		if (s == null && isSandboxLevel(level)) {
			s = SandboxSettlements.nearest(pos, maxDistance);
		}
		return s;
	}

	/** A settlement by id in any level. */
	public static @Nullable Settlement byId(MinecraftServer server, long id) {
		if (id < 0) {
			return SandboxSettlements.get(id);
		}
		for (ServerLevel level : server.getAllLevels()) {
			HearthWorld world = HearthWorld.of(level);
			if (world != null) {
				Settlement s = world.settlement(id);
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	/** Every live settlement of a level (the sim's and the sandbox's). */
	public static List<Settlement> all(ServerLevel level) {
		List<Settlement> out = new ArrayList<>();
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			out.addAll(world.settlements());
		}
		if (isSandboxLevel(level)) {
			out.addAll(SandboxSettlements.all());
		}
		return out;
	}

	/** The level a settlement is in, or null if that dimension is not loaded. */
	public static @Nullable ServerLevel level(MinecraftServer server, Settlement s) {
		return server.getLevel(s.dimension());
	}

	/** The sim day: the sim's calendar when it runs, else the overworld clock. */
	public static long day(ServerLevel level) {
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			return world.day();
		}
		return Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), TICKS_PER_DAY);
	}

	private static boolean isSandboxLevel(ServerLevel level) {
		return level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
	}
}
