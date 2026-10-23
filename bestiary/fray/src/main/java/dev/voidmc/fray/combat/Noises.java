package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.PerceptionService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** {@link PerceptionService}: the last few seconds of noise per level (at most 256 sounds, 100 ticks). */
public final class Noises implements PerceptionService {
	public static final Noises INSTANCE = new Noises();
	private static final int MAX = 256;
	private static final int KEEP_TICKS = 100;
	private final Map<ServerLevel, ArrayDeque<Noise>> levels = new WeakHashMap<>();
	public static long made;

	private Noises() {
	}

	@Override
	public void noise(ServerLevel level, Vec3 pos, float radius, Identifier kind, @Nullable Entity source) {
		if (radius <= 0) {
			return;
		}
		ArrayDeque<Noise> q = this.levels.computeIfAbsent(level, l -> new ArrayDeque<>());
		long now = level.getGameTime();
		while (!q.isEmpty() && (q.size() >= MAX || now - q.peekFirst().tick() > KEEP_TICKS)) {
			q.pollFirst();
		}
		q.addLast(new Noise(pos, radius, kind, source == null ? -1 : source.getId(), now));
		made++;
	}

	@Override
	public List<Noise> heard(ServerLevel level, Vec3 at, long since) {
		ArrayDeque<Noise> q = this.levels.get(level);
		if (q == null || q.isEmpty()) {
			return List.of();
		}
		List<Noise> out = null;
		for (Iterator<Noise> it = q.descendingIterator(); it.hasNext(); ) {
			Noise n = it.next();
			if (n.tick() < since) {
				break;
			}
			if (n.pos().distanceToSqr(at) <= n.radius() * n.radius()) {
				(out == null ? out = new ArrayList<>(4) : out).add(n);
			}
		}
		return out == null ? List.of() : out;
	}

	public void clear() {
		this.levels.clear();
	}
}
