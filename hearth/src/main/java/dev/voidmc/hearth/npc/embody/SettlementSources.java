package dev.voidmc.hearth.npc.embody;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where the NPC module finds settlements: the sim's ({@link HearthWorld}), plus any extra source registered (the dev
 * sandbox, which runs without the sim). Server thread only.
 */
public final class SettlementSources {
	private static final List<Function<ServerLevel, Collection<? extends Settlement>>> EXTRA = new CopyOnWriteArrayList<>();

	private SettlementSources() {
	}

	public static void addExtra(Function<ServerLevel, Collection<? extends Settlement>> source) {
		EXTRA.add(source);
	}

	/** Every live settlement of the level. */
	public static List<Settlement> all(ServerLevel level) {
		List<Settlement> out = new ArrayList<>();
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			out.addAll(world.settlements());
		}
		for (Function<ServerLevel, Collection<? extends Settlement>> f : EXTRA) {
			out.addAll(f.apply(level));
		}
		return out;
	}

	/** The settlements a player is near enough to that their residents may walk about as entities. */
	public static List<Settlement> embodied(ServerLevel level) {
		List<Settlement> out = new ArrayList<>();
		for (Settlement s : all(level)) {
			if (s.embodied()) {
				out.add(s);
			}
		}
		return out;
	}

	public static @Nullable Settlement find(ServerLevel level, long id) {
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			Settlement s = world.settlement(id);
			if (s != null) {
				return s;
			}
		}
		for (Function<ServerLevel, Collection<? extends Settlement>> f : EXTRA) {
			for (Settlement s : f.apply(level)) {
				if (s.id() == id) {
					return s;
				}
			}
		}
		return null;
	}
}
