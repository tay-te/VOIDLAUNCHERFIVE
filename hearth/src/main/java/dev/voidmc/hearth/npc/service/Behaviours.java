package dev.voidmc.hearth.npc.service;

import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every {@link FolkBehaviour} registered by any module, highest priority first. Registration happens during mod init
 * (and, for modules that load before the npc module, on server start), so the list is copied on write and read without
 * locks by every folk's {@link BehaviourRunner}.
 */
public final class Behaviours {
	private static volatile List<FolkBehaviour> all = List.of();

	private Behaviours() {
	}

	/** Adds a behaviour; a second registration with the same id replaces the first. */
	public static synchronized void register(FolkBehaviour behaviour) {
		List<FolkBehaviour> next = new ArrayList<>(all);
		next.removeIf(b -> b.id().equals(behaviour.id()));
		next.add(behaviour);
		// Stable: equal priorities keep registration order.
		next.sort(Comparator.comparingInt(FolkBehaviour::priority).reversed());
		all = List.copyOf(next);
	}

	/** Highest priority first. */
	public static List<FolkBehaviour> all() {
		return all;
	}

	/** For tests. */
	static synchronized void clear() {
		all = List.of();
	}
}
