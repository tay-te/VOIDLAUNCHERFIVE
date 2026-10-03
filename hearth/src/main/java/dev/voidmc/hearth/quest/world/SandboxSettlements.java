package dev.voidmc.hearth.quest.world;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * In-memory settlements made by {@code /hearth quest sandbox}, so the quest module can be exercised in game before the
 * sim module exists (and on a dev server alongside it). They live until the server stops; their ids are negative so
 * they never collide with the sim's.
 */
public final class SandboxSettlements {
	/** A sandbox settlement counts as "at" a position within this many blocks of its centre. */
	public static final int RADIUS = 64;
	private static final Map<Long, SimpleSettlement> SETTLEMENTS = new LinkedHashMap<>();
	private static long nextId = -1000;

	private SandboxSettlements() {
	}

	public static SimpleSettlement add(java.util.function.LongFunction<SimpleSettlement> factory) {
		SimpleSettlement s = factory.apply(nextId--);
		SETTLEMENTS.put(s.id(), s);
		return s;
	}

	public static Collection<SimpleSettlement> all() {
		return Collections.unmodifiableCollection(SETTLEMENTS.values());
	}

	public static @Nullable SimpleSettlement get(long id) {
		return SETTLEMENTS.get(id);
	}

	public static @Nullable SimpleSettlement at(BlockPos pos) {
		SimpleSettlement best = null;
		double bestSq = (double) RADIUS * RADIUS;
		for (SimpleSettlement s : SETTLEMENTS.values()) {
			double d = s.center().distSqr(new BlockPos(pos.getX(), s.center().getY(), pos.getZ()));
			if (d <= bestSq) {
				bestSq = d;
				best = s;
			}
		}
		return best;
	}

	public static @Nullable Settlement nearest(BlockPos pos, int maxDistance) {
		Settlement best = null;
		double bestSq = (double) maxDistance * maxDistance;
		for (SimpleSettlement s : SETTLEMENTS.values()) {
			double d = s.center().distSqr(new BlockPos(pos.getX(), s.center().getY(), pos.getZ()));
			if (d <= bestSq) {
				bestSq = d;
				best = s;
			}
		}
		return best;
	}

	public static boolean remove(long id) {
		return SETTLEMENTS.remove(id) != null;
	}

	public static void clear() {
		SETTLEMENTS.clear();
		nextId = -1000;
	}
}
