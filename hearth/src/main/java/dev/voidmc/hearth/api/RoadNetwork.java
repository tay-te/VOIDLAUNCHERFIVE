package dev.voidmc.hearth.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The roads between settlements and posts (docs/LIVING_WORLD_2.md §1.2): {@link HearthWorld#roads()}. Implemented by
 * the sim module's roads (lane A1); {@link #NONE} until then. Server thread only; every call is a cheap lookup.
 */
public interface RoadNetwork {
	/** Roads with a point within {@code radius} blocks of {@code at}, nearest first. */
	List<RoadView> near(ResourceKey<Level> dimension, BlockPos at, int radius);

	/** The roads out of a settlement (or post). */
	List<RoadView> from(long settlementId);

	/** The road joining two settlements directly, or null. */
	@Nullable RoadView between(long a, long b);

	/** Blocks by road from one settlement to another (over several roads), or -1 when they are not joined. */
	double roadDistance(long a, long b);

	/** How dangerous the road is {@code along} blocks from its {@code from} end: 0 safe, 1 bandit country. */
	float danger(long roadId, double along);

	/** No roads: nothing is near, nothing is joined. */
	RoadNetwork NONE = new RoadNetwork() {
		@Override
		public List<RoadView> near(ResourceKey<Level> dimension, BlockPos at, int radius) {
			return List.of();
		}

		@Override
		public List<RoadView> from(long settlementId) {
			return List.of();
		}

		@Override
		public @Nullable RoadView between(long a, long b) {
			return null;
		}

		@Override
		public double roadDistance(long a, long b) {
			return -1;
		}

		@Override
		public float danger(long roadId, double along) {
			return 0;
		}
	};

	enum RoadClass {
		KINGS_ROAD, TRACK
	}

	/**
	 * A road.
	 *
	 * @param points the route, packed {@code x, y, z} triples from the {@code from} end to the {@code to} end
	 * @param built  0 (planned) to 1 (all its segments built)
	 * @param length blocks along the route
	 */
	record RoadView(long id, long from, long to, RoadClass kind, int[] points, float built, double length) {
		/** The point {@code along} blocks from the {@code from} end (clamped to the ends). */
		public BlockPos at(double along) {
			int n = this.points.length / 3;
			if (n == 0) {
				return BlockPos.ZERO;
			}
			double left = Math.max(0, along);
			for (int i = 0; i + 1 < n; i++) {
				int a = i * 3;
				int b = a + 3;
				double dx = this.points[b] - this.points[a];
				double dy = this.points[b + 1] - this.points[a + 1];
				double dz = this.points[b + 2] - this.points[a + 2];
				double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (left <= len && len > 0) {
					double t = left / len;
					return BlockPos.containing(this.points[a] + dx * t, this.points[a + 1] + dy * t, this.points[a + 2] + dz * t);
				}
				left -= len;
			}
			int last = (n - 1) * 3;
			return new BlockPos(this.points[last], this.points[last + 1], this.points[last + 2]);
		}
	}
}
