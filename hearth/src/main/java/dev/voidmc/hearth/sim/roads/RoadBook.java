package dev.voidmc.hearth.sim.roads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The roads of one world as built since day 0 (docs/LIVING_WORLD_2.md §1.5), world data {@code hearth:sim/roads}: every
 * road begun live (a hamlet's track to its parent, a historic road one of whose towns was too young to have built it,
 * the short spurs that join a historic road's end to its town's own streets) with its way, how far it is built from
 * each end and what has been given toward it; and the road plots refused because a player had built across them.
 * Historic roads built in history are not kept: the seed gives them again, and a fingerprint of everything else their
 * ways depend on says whether it still does. Server thread only.
 */
public final class RoadBook {
	/** A road being built (or built) live. */
	public static final class LiveRoad {
		final long id;
		final long a;
		final long b;
		final RoadClass kind;
		/** A spur from a historic road's end to its town's streets ({@code b} is the historic road it joins). */
		final boolean spur;
		final int[] ends;
		final List<String> names;
		final List<String> cultures;
		/** {@link Route#encode()}, empty until the way is found; a single 0 when none could be. */
		int[] route;
		/** Plots built from each end. */
		int fromA;
		int fromB;
		/** Work done toward the next plot from each end (work units). */
		double progressA;
		double progressB;
		/** Coin given toward it and not yet spent (bits). */
		long funds;
		final long begun;
		/** Transient: the way made from the route. */
		@Nullable Way way;
		/**
		 * A detour round somebody's work (risk 1): the plot of road {@code b} it replaces, -1 if this is no detour. A detour is
		 * also a spur (not travelled, no signs) and is built whole once its way is found: the plot it replaces was due.
		 */
		int detourOf = -1;
		/** A detour's obstacle: the columns ({@code x, z} pairs) its way keeps clear of. */
		int[] avoid = new int[0];
		/** The columns of road {@code b}'s way a detour replaces (first and last). */
		int[] span = new int[0];

		LiveRoad(long id, long a, long b, RoadClass kind, boolean spur, int[] ends, List<String> names, List<String> cultures, int[] route, int fromA,
			int fromB, double progressA, double progressB, long funds, long begun) {
			this.id = id;
			this.a = a;
			this.b = b;
			this.kind = kind;
			this.spur = spur;
			this.ends = ends;
			this.names = names;
			this.cultures = cultures;
			this.route = route;
			this.fromA = fromA;
			this.fromB = fromB;
			this.progressA = progressA;
			this.progressB = progressB;
			this.funds = funds;
			this.begun = begun;
		}

		static final Codec<LiveRoad> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(r -> r.id),
			Codec.LONG.fieldOf("a").forGetter(r -> r.a),
			Codec.LONG.fieldOf("b").forGetter(r -> r.b),
			Codec.STRING.xmap(s -> s.equals("kings_road") ? RoadClass.KINGS_ROAD : RoadClass.TRACK, k -> k == RoadClass.KINGS_ROAD ? "kings_road" : "track")
				.fieldOf("kind").forGetter(r -> r.kind),
			Codec.BOOL.optionalFieldOf("spur", false).forGetter(r -> r.spur),
			Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("ends").forGetter(r -> r.ends),
			Codec.STRING.listOf().optionalFieldOf("names", List.of("", "")).forGetter(r -> r.names),
			Codec.STRING.listOf().optionalFieldOf("cultures", List.of("temperate", "temperate")).forGetter(r -> r.cultures),
			Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).optionalFieldOf("route", new int[0]).forGetter(r -> r.route),
			Codec.INT.optionalFieldOf("from_a", 0).forGetter(r -> r.fromA),
			Codec.INT.optionalFieldOf("from_b", 0).forGetter(r -> r.fromB),
			Codec.DOUBLE.optionalFieldOf("progress_a", 0.0).forGetter(r -> r.progressA),
			Codec.DOUBLE.optionalFieldOf("progress_b", 0.0).forGetter(r -> r.progressB),
			Codec.LONG.optionalFieldOf("funds", 0L).forGetter(r -> r.funds),
			Codec.LONG.optionalFieldOf("begun", 0L).forGetter(r -> r.begun),
			Detour.CODEC.optionalFieldOf("detour").forGetter(r -> r.detourOf < 0 ? java.util.Optional.empty() : java.util.Optional.of(new Detour(r.detourOf, r.avoid, r.span)))
		).apply(i, (id, a, b, kind, spur, ends, names, cultures, route, fromA, fromB, progressA, progressB, funds, begun, detour) -> {
			LiveRoad r = new LiveRoad(id, a, b, kind, spur, ends, names, cultures, route, fromA, fromB, progressA, progressB, funds, begun);
			detour.ifPresent(d -> {
				r.detourOf = d.plot();
				r.avoid = d.avoid();
				r.span = d.span();
			});
			return r;
		}));

		/** What a detour keeps: the plot it replaces and its obstacle's columns. */
		record Detour(int plot, int[] avoid, int[] span) {
			static final Codec<Detour> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("plot").forGetter(Detour::plot),
				Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).optionalFieldOf("avoid", new int[0]).forGetter(Detour::avoid),
				Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).optionalFieldOf("span", new int[0]).forGetter(Detour::span)
			).apply(i, Detour::new));
		}

		/** Whether this is a detour round somebody's work. */
		public boolean detour() {
			return this.detourOf >= 0;
		}

		public long id() {
			return this.id;
		}

		public long a() {
			return this.a;
		}

		public long b() {
			return this.b;
		}

		public boolean spur() {
			return this.spur;
		}

		public boolean routed() {
			return this.route.length > 1;
		}

		public boolean unroutable() {
			return this.route.length == 1;
		}

		/** Plots in all (0 until routed). */
		public int plots() {
			return this.way == null ? 0 : this.way.segments().size();
		}

		public boolean finished() {
			return routed() && this.way != null && this.fromA + this.fromB >= this.way.segments().size();
		}

		/** Whether plot {@code i} is built. */
		public boolean built(int i) {
			int n = plots();
			return i < this.fromA || i >= n - this.fromB;
		}

		/** The plot being built from an end next (-1 if none), and how far along it is (0–1). */
		public int head(boolean fromA) {
			int n = plots();
			if (n == 0 || this.fromA + this.fromB >= n) {
				return -1;
			}
			return fromA ? this.fromA : n - 1 - this.fromB;
		}

		public double built() {
			int n = plots();
			return n == 0 ? 0 : Math.min(1.0, (this.fromA + this.fromB) / (double) n);
		}

		public long funds() {
			return this.funds;
		}

		/** The way (once routed), or null. */
		public @Nullable Way way() {
			return this.way;
		}

		public RoadClass kind() {
			return this.kind;
		}

		public List<String> names() {
			return this.names;
		}

		/** Plots built from the {@code a} end and from the {@code b} end. */
		public int fromA() {
			return this.fromA;
		}

		public int fromB() {
			return this.fromB;
		}
	}

	public static final Codec<RoadBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		LiveRoad.CODEC.listOf().optionalFieldOf("live", List.of()).forGetter(b -> new ArrayList<>(b.live.values())),
		Codec.unboundedMap(Codec.STRING, Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of)).optionalFieldOf("refused", Map.of())
			.forGetter(b -> {
				Map<String, int[]> out = new HashMap<>();
				b.refused.forEach((k, v) -> out.put(Long.toHexString(k), v));
				return out;
			}),
		Codec.LONG.optionalFieldOf("fingerprint", 0L).forGetter(b -> b.fingerprint)
	).apply(i, (live, refused, fp) -> {
		RoadBook b = new RoadBook();
		for (LiveRoad r : live) {
			b.live.put(r.id, r);
		}
		refused.forEach((k, v) -> {
			try {
				b.refused.put(Long.parseUnsignedLong(k, 16), v);
			} catch (NumberFormatException ignored) {
				// a hand-edited key
			}
		});
		b.fingerprint = fp;
		return b;
	}));

	public static final HearthDataKey<RoadBook> KEY = HearthDataKey.register(Hearth.id("sim/roads"), HearthDataKey.Scope.WORLD, CODEC, RoadBook::new);

	final Map<Long, LiveRoad> live = new LinkedHashMap<>();
	/** Road id to the plots of it refused (a player's work across them). */
	final Map<Long, int[]> refused = new HashMap<>();
	/** {@link RoadWorld#fingerprint()} when the world's roads were first written live; 0 before. */
	long fingerprint;

	public List<LiveRoad> live() {
		return new ArrayList<>(this.live.values());
	}

	public @Nullable LiveRoad road(long id) {
		return this.live.get(id);
	}

	public boolean refused(long road, int plot) {
		int[] r = this.refused.get(road);
		if (r == null) {
			return false;
		}
		for (int p : r) {
			if (p == plot) {
				return true;
			}
		}
		return false;
	}

	void refuse(long road, int plot) {
		if (refused(road, plot)) {
			return;
		}
		int[] r = this.refused.getOrDefault(road, new int[0]);
		int[] next = java.util.Arrays.copyOf(r, r.length + 1);
		next[r.length] = plot;
		this.refused.put(road, next);
	}

	public int refusedCount() {
		int n = 0;
		for (int[] r : this.refused.values()) {
			n += r.length;
		}
		return n;
	}
}
