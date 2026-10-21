package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.RoadNetwork;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One road as the network knows it: the two places it joins, its class, the points where it leaves each of them, its
 * way across the land and the plots that build it. Immutable once made (the trim marks are a cache of a deterministic
 * answer, so a race only works one out twice).
 *
 * @param route    null where no way could be found (the road is never built; travellers go across country)
 * @param live     a road begun after day 0 (kept in the world's road book), not one of the historic network
 * @param spur     a short live road from a historic road's end to its town's own streets (not travelled as a road)
 * @param length   blocks along the road (its route; across country, a quarter longer than the straight line)
 */
public record Way(RoadGraph.Edge edge, int ax, int az, int bx, int bz, String nameA, String nameB, @Nullable Route route,
	List<RoadSegments.Segment> segments, byte[] trim, boolean live, boolean spur, double length) {
	/** A way, its length measured once. */
	public static Way of(RoadGraph.Edge edge, int ax, int az, int bx, int bz, String nameA, String nameB, @Nullable Route route,
		List<RoadSegments.Segment> segments, boolean live) {
		return of(edge, ax, az, bx, bz, nameA, nameB, route, segments, live, false);
	}

	public static Way of(RoadGraph.Edge edge, int ax, int az, int bx, int bz, String nameA, String nameB, @Nullable Route route,
		List<RoadSegments.Segment> segments, boolean live, boolean spur) {
		double len = route != null ? route.length() : Math.hypot(bx - ax, bz - az) * 1.25;
		return new Way(edge, ax, az, bx, bz, nameA, nameB, route, segments, new byte[segments.size()], live, spur, len);
	}

	/** Trim marks: not yet worked out, kept, dropped (it would run over a settlement's planned ground). */
	static final byte UNKNOWN = 0;
	static final byte KEEP = 1;
	static final byte DROP = 2;

	public long id() {
		return this.edge.id();
	}

	public long a() {
		return this.edge.a();
	}

	public long b() {
		return this.edge.b();
	}

	/** The other end from {@code end}. */
	public long other(long end) {
		return this.edge.other(end);
	}

	public RoadNetwork.RoadClass kind() {
		return this.edge.kind();
	}


	/** The api's view. */
	public RoadNetwork.RoadView view(float built) {
		int[] pts = this.route != null ? this.route.points(8) : new int[]{this.ax, 0, this.az, this.bx, 0, this.bz};
		return new RoadNetwork.RoadView(id(), a(), b(), kind(), pts, built, length());
	}
}
