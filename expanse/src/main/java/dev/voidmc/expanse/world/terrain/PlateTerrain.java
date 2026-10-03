package dev.voidmc.expanse.world.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * One tectonic plate's landscape, worked out as a whole on a grid of nodes {@value #G} blocks apart.
 *
 * <p>The plate is laid out first: where its land and sea are, and how fast each part of the land is
 * being pushed up (uplift: highest where it collides with another continent, along a coastal range
 * where it pushes against an ocean plate, gentle in the interior).
 *
 * <p>Then the land is eroded. Rain falling on every node drains downhill to the sea along a drainage
 * tree; the tree comes from a priority flood out of the coast, so every node has a way down and no
 * river ever ends in a pit. How much land drains through a node (its drainage area) is summed down the
 * tree. Rivers cut down until erosion balances uplift. That balance is the stream-power law, whose
 * steady state is a slope: {@code slope = K * uplift / sqrt(area)}, steep on small catchments (ridges and
 * mountain flanks), almost flat on great rivers. Integrated up the tree from the coast, this gives every
 * node its height. The tree is then recomputed from the new heights and the process repeated, until the
 * valleys and the network agree. This is the steady-state shortcut of the uplift and fluvial erosion
 * models used in terrain research (Braun and Willett 2013, Cordonnier et al. 2016), where a simulation
 * runs for thousands of steps to reach the same place.
 *
 * <p>Nodes whose drainage area passes a threshold carry a river; its course is drawn as smooth curves
 * through the nodes, widening downstream, with its water level falling from node to node toward the sea.
 */
final class PlateTerrain {
	static final int G = 32;
	static final float SEA = 63.0F;
	private static final float RIVER_AREA = 48.0F;
	private static final float K = 0.46F;
	private static final float MAX_SLOPE = 1.15F;
	private static final float SQRT2 = (float) Math.sqrt(2.0);
	private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
	private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

	private static final byte NONE = 0;
	private static final byte LAND = 1;
	private static final byte OCEAN = 2;

	final PlateMap.Plate plate;
	private final int i0;
	private final int j0;
	private final int w;
	private final int d;
	private final byte[] kind;
	private final float[] height;
	private final float[] uplift;
	private final float[] coast;
	private final float[] drainBase;
	private final float[] area;
	private final int[] receiver;
	/** A gentle noise added to heights for routing only, so water finds its way across flats irregularly. */
	private final float[] wander;
	/** A small random cost per step for each node: crossing a filled basin, water takes a ragged path, not a straight one. */
	private final float[] stepCost;

	/** The plate's volcanoes, and the height of each one's summit once erosion has shaped it. */
	private final List<Volcano> volcanoes;
	private float[] summits = new float[0];

	/**
	 * A volcano: where it stands, how far its cone spreads and how strongly it is pushed up, its summit
	 * crater's radius, and whether lava or a lake lies in the crater.
	 */
	record Volcano(double x, double z, double radius, double strength, double crater, boolean active) {
	}

	/** River pieces: quadratic curves, {@value #STRIDE} floats each (see {@link #addPiece}). */
	private static final int STRIDE = 15;
	private float[] pieces = new float[STRIDE * 256];
	private int pieceCount;
	private final int[] pieceFirst;
	private final byte[] pieceNum;

	/** Raw per-column values, before the model blends plates and adds detail. */
	static final class Raw {
		float height;
		float uplift;
		float coast;
		float drainBase;
		// river
		float riverDist;
		float riverHalfWidth;
		float riverWater;
		float riverDepth;
	}

	PlateTerrain(TerrainModel model, PlateMap.Plate plate) {
		this.plate = plate;
		PlateMap plates = model.plates();
		PlateMap.Hit hit = new PlateMap.Hit();

		long t1 = System.nanoTime();
		// 1. The plate's extent: a coarse scan for the cells it owns, then a node box round them.
		double reach = 1.25 * PlateMap.SPACING + 500;
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (double x = plate.cx() - reach; x <= plate.cx() + reach; x += 128) {
			for (double z = plate.cz() - reach; z <= plate.cz() + reach; z += 128) {
				plates.locate(x, z, hit);
				if (hit.own == plate) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}
		this.i0 = Mth.floor((minX - 320) / G);
		this.j0 = Mth.floor((minZ - 320) / G);
		this.w = Mth.ceil((maxX + 320) / G) - this.i0 + 1;
		this.d = Mth.ceil((maxZ + 320) / G) - this.j0 + 1;
		int n = this.w * this.d;
		this.kind = new byte[n];
		this.height = new float[n];
		this.uplift = new float[n];
		this.coast = new float[n];
		this.drainBase = new float[n];
		this.area = new float[n];
		this.receiver = new int[n];
		this.wander = new float[n];
		this.stepCost = new float[n];
		this.pieceFirst = new int[n];
		this.pieceNum = new byte[n];
		float[] border = new float[n];
		float[] convergence = new float[n];
		boolean[] acrossContinent = new boolean[n];
		float[] oceanPush = new float[n];

		long t2 = System.nanoTime();
		// 2. Land and sea.
		boolean landlocked = plate.continental() && !this.hasOceanNeighbour(plates);
		long h = plate.seed();
		double seaX = plate.cx() + (PlateMap.unit(h >>> 3) - 0.5) * 1200;
		double seaZ = plate.cz() + (PlateMap.unit(h >>> 27) - 0.5) * 1200;
		double seaR = 480 + PlateMap.unit(h >>> 40) * 420;
		double seaAngle = PlateMap.unit(h >>> 13) * Math.PI;
		double seaCos = Math.cos(seaAngle);
		double seaSin = Math.sin(seaAngle);
		for (int j = 0; j < this.d; j++) {
			for (int i = 0; i < this.w; i++) {
				int k = i + j * this.w;
				double x = (this.i0 + i) * (double) G;
				double z = (this.j0 + j) * (double) G;
				plates.locate(x, z, hit);
				if (hit.own != plate) {
					this.kind[k] = NONE;
					continue;
				}
				border[k] = (float) hit.border;
				if (hit.across != null) {
					convergence[k] = (float) PlateMap.convergence(plate, hit.across);
					acrossContinent[k] = hit.across.continental();
				}
				if (hit.oceanAcross != null) {
					oceanPush[k] = (float) PlateMap.convergence(plate, hit.oceanAcross);
				}
				boolean land;
				if (plate.continental()) {
					double offset = 300 + 300 * model.noise(TerrainModel.N_COAST, x / 2600, z / 2600) + 150 * model.noise(TerrainModel.N_COAST, x / 700 + 31, z / 700)
						+ 60 * model.noise(TerrainModel.N_COAST_FINE, x / 190, z / 190);
					land = hit.oceanBorder > Math.max(140, offset);
					if (landlocked) {
						// an inland sea: stretched one way, its shore wandering at several scales
						double ux = (x - seaX) * seaCos + (z - seaZ) * seaSin;
						double uz = -(x - seaX) * seaSin + (z - seaZ) * seaCos;
						double r = Math.hypot(ux / 1.5, uz * 1.2);
						double shore = seaR * (1 + 0.45 * model.noise(TerrainModel.N_COAST, x / 900 + 7, z / 900) + 0.2 * model.noise(TerrainModel.N_COAST_FINE, x / 220, z / 220));
						land &= r > shore;
					}
				} else {
					land = this.island(model, hit, x, z);
				}
				this.kind[k] = land ? LAND : OCEAN;
			}
		}

		long t3 = System.nanoTime();
		// 3. Distance to the coast, inland and out to sea.
		float[] toSea = this.distanceFrom(OCEAN, LAND);
		float[] toLand = this.distanceFrom(LAND, OCEAN);
		for (int k = 0; k < n; k++) {
			this.coast[k] = this.kind[k] == LAND ? toSea[k] : this.kind[k] == OCEAN ? -toLand[k] : 0;
		}

		// Volcanoes: along the arc where a continent rides over an ocean plate, at the hotspot islands, and now
		// and then a lone hotspot far inland. Their cones go into the uplift, so erosion carves them as it
		// carves everything else: radial valleys round a summit that rivers run down from.
		this.volcanoes = this.findVolcanoes(model, oceanPush);

		long t4 = System.nanoTime();
		// 4. Uplift.
		for (int k = 0; k < n; k++) {
			if (this.kind[k] != LAND) {
				continue;
			}
			double x = (this.i0 + k % this.w) * (double) G;
			double z = (this.j0 + k / this.w) * (double) G;
			// interior: broad lowland basins (barely rising: plains) to upland (rolling hills)
			double interior = 0.5 + 0.5 * model.noise(TerrainModel.N_UPLIFT, x / 3400, z / 3400);
			double u = 0.05 + 0.42 * interior * interior;
			// old, worn ranges inside the continent
			double ridge = 1 - Math.abs(model.noise(TerrainModel.N_RIDGE, x / 2300, z / 2300));
			double ridgeMask = smooth((model.noise(TerrainModel.N_UPLIFT, x / 6000 + 40, z / 6000) - 0.05) / 0.4);
			u += 0.75 * ridge * ridge * ridge * ridgeMask;
			if (plate.continental() && acrossContinent[k]) {
				// collision range along the border with another continent
				double push = 0.3 + 0.7 * Math.max(0, convergence[k]);
				double along = 0.7 + 0.3 * model.noise(TerrainModel.N_RIDGE, x / 900, z / 900);
				u += 2.8 * push * along * Math.exp(-sq(border[k] / 850.0));
			}
			if (plate.continental() && oceanPush[k] > 0.15) {
				// coastal range where the continent rides over an ocean plate
				u += 2.2 * oceanPush[k] * Math.exp(-sq((this.coast[k] - 260) / 340.0));
			}
			if (!plate.continental()) {
				u = 0.55 + 0.35 * u; // volcanic islands: steep little cones
			}
			u += this.volcanicUplift(x, z);
			this.uplift[k] = (float) Mth.clamp(u, 0.04, 4.2);
		}

		long t5 = System.nanoTime();
		// 5. Erosion: the drainage tree and the steady state, repeated until they agree.
		for (int k = 0; k < n; k++) {
			double x = (this.i0 + k % this.w) * (double) G;
			double z = (this.j0 + k / this.w) * (double) G;
			// Routing surface on top of the land's own height: a gentle tilt toward the sea (distance from the
			// coast never has a pit), steeper than the meander noise riding on it, so water nearly always has
			// a way down and seldom has to fill a basin, where a grid would route it in straight lines.
			this.wander[k] = (float) (0.12 * this.coast[k] + 8 * model.noise(TerrainModel.N_ROUGH, x / 420 + 51, z / 420)
				+ 1.5 * model.noise(TerrainModel.N_ISLAND, x / 120 + 17, z / 120));
			this.stepCost[k] = 0.02F + 0.9F * (float) PlateMap.unit(PlateMap.mix(plate.seed(), this.i0 + k % this.w, this.j0 + k / this.w));
		}
		for (int k = 0; k < n; k++) {
			if (this.kind[k] == LAND) {
				double x = (this.i0 + k % this.w) * (double) G;
				double z = (this.j0 + k / this.w) * (double) G;
				float u = this.uplift[k];
				this.height[k] = (float) (SEA + 1 + u * 95 * (1 - Math.exp(-this.coast[k] / 700.0))
					+ 26 * (0.3 + u) * model.noise(TerrainModel.N_ROUGH, x / 520, z / 520) + 9 * model.noise(TerrainModel.N_ROUGH, x / 150 + 9, z / 150));
			}
		}
		int[] order = new int[n];
		int count = 0;
		for (int iteration = 0; iteration < 4; iteration++) {
			count = this.drainageTree(order);
			this.accumulate(order, count);
			this.steadyState(order, count);
		}
		this.smoothHillslopes();
		this.summits = new float[this.volcanoes.size()];
		for (int v = 0; v < this.volcanoes.size(); v++) {
			// the summit: the highest node near where the cone was pushed up
			Volcano volcano = this.volcanoes.get(v);
			float top = SEA;
			int ci = (int) Math.round(volcano.x() / G) - this.i0;
			int cj = (int) Math.round(volcano.z() / G) - this.j0;
			for (int dj = -2; dj <= 2; dj++) {
				for (int di = -2; di <= 2; di++) {
					top = Math.max(top, this.at(this.height, ci + di, cj + dj));
				}
			}
			this.summits[v] = top;
		}

		long t6 = System.nanoTime();
		// 6. The sea floor.
		for (int k = 0; k < n; k++) {
			if (this.kind[k] != OCEAN) {
				continue;
			}
			double x = (this.i0 + k % this.w) * (double) G;
			double z = (this.j0 + k / this.w) * (double) G;
			double out = -this.coast[k];
			double floor = SEA - 3 - 24 * smooth(out / 520) - 17 * smooth((out - 400) / 1300);
			floor += 5 * model.noise(TerrainModel.N_ROUGH, x / 400, z / 400);
			if (!plate.continental() && convergence[k] < -0.3) {
				floor += 14 * Math.exp(-sq(border[k] / 320.0)); // mid-ocean ridge where plates part
			}
			if (convergence[k] > 0.3 && acrossContinent[k] != plate.continental()) {
				floor -= 12 * Math.exp(-sq(border[k] / 220.0)); // trench where an ocean plate dives
			}
			this.height[k] = (float) Mth.clamp(floor, 4, SEA - 2);
		}

		// 7. Height above the nearest river, for telling valleys from ridges.
		for (int idx = 0; idx < n; idx++) {
			this.drainBase[idx] = SEA;
		}
		for (int c = 0; c < count; c++) {
			int k = order[c];
			int r = this.receiver[k];
			this.drainBase[k] = this.area[k] >= RIVER_AREA || r < 0 ? this.height[k] : this.kind[r] == OCEAN ? SEA : this.drainBase[r];
		}

		long t8 = System.nanoTime();
		// 8. Rivers.
		this.buildRivers();

		long t9 = System.nanoTime();
		// 9. Values a little way past the border, so interpolation near it has neighbours to use.
		this.dilate();
		long tEnd = System.nanoTime();
		if (PROFILE && tEnd - t1 > 500_000_000L) {
			System.out.printf("plate %d,%d %s: %dx%d nodes, extent %d, layout %d, coast %d, uplift %d, erosion %d, sea %d, rivers %d (%d pieces), edges %d ms%n",
				plate.gx(), plate.gz(), plate.continental() ? "continental" : "oceanic", this.w, this.d,
				(t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000, (t5 - t4) / 1_000_000, (t6 - t5) / 1_000_000,
				(t8 - t6) / 1_000_000, (t9 - t8) / 1_000_000, this.pieceCount, (tEnd - t9) / 1_000_000);
		}
	}

	private static final boolean PROFILE = Boolean.getBoolean("expanse.terrain.profile");

	// ---------------------------------------------------------------------------------------------
	// Layout

	private boolean hasOceanNeighbour(PlateMap plates) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				PlateMap.Plate p = plates.plate(this.plate.gx() + dx, this.plate.gz() + dz);
				if (p != this.plate && !p.continental() && Math.hypot(p.cx() - this.plate.cx(), p.cz() - this.plate.cz()) < 1.7 * PlateMap.SPACING) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Volcano> findVolcanoes(TerrainModel model, float[] oceanPush) {
		List<Volcano> out = new ArrayList<>();
		long seed = this.plate.seed();
		if (this.plate.continental()) {
			// the arc: a lattice of candidates a kilometre apart, kept where the coastal range rises
			int cell = 1000;
			int ci0 = Math.floorDiv(this.i0 * G, cell);
			int ci1 = Math.floorDiv((this.i0 + this.w) * G, cell);
			int cj0 = Math.floorDiv(this.j0 * G, cell);
			int cj1 = Math.floorDiv((this.j0 + this.d) * G, cell);
			for (int cj = cj0; cj <= cj1; cj++) {
				for (int ci = ci0; ci <= ci1; ci++) {
					long h = PlateMap.mix(seed ^ 0x564F_4C43L, ci, cj);
					double x = (ci + 0.2 + 0.6 * PlateMap.unit(h)) * cell;
					double z = (cj + 0.2 + 0.6 * PlateMap.unit(h >>> 21)) * cell;
					int k = this.node(x, z);
					if (k >= 0 && this.kind[k] == LAND && oceanPush[k] > 0.25F && this.coast[k] > 180 && this.coast[k] < 900
						&& PlateMap.unit(h >>> 40) < 0.5) {
						double u = PlateMap.unit(h >>> 7);
						out.add(new Volcano(x, z, 320 + 180 * u, 1.8 + 0.9 * u, 26 + 22 * PlateMap.unit(h >>> 13), PlateMap.unit(h >>> 29) < 0.45));
					}
				}
			}
			// a lone hotspot far inland, now and then
			if (PlateMap.unit(seed >>> 9) < 0.22) {
				double x = this.plate.cx() + (PlateMap.unit(seed >>> 17) - 0.5) * 2800;
				double z = this.plate.cz() + (PlateMap.unit(seed >>> 33) - 0.5) * 2800;
				int k = this.node(x, z);
				if (k >= 0 && this.kind[k] == LAND && this.coast[k] > 1100) {
					out.add(new Volcano(x, z, 420, 2.4, 44, PlateMap.unit(seed >>> 45) < 0.5));
				}
			}
		} else {
			// each hotspot island is the top of one (see island())
			for (int s = 0; s < 3; s++) {
				long hs = PlateMap.mix(seed, s, 99);
				if (PlateMap.unit(hs >>> 50) > 0.55 || PlateMap.unit(hs >>> 3) > 0.6) {
					continue;  // a hotspot island, and most of them still smoulder
				}
				double hx = this.plate.cx() + (PlateMap.unit(hs) - 0.5) * 2600;
				double hz = this.plate.cz() + (PlateMap.unit(hs >>> 21) - 0.5) * 2600;
				double r = 130 + PlateMap.unit(hs >>> 42) * 220;
				out.add(new Volcano(hx, hz, r * 1.15, 1.5, 18 + 0.08 * r, PlateMap.unit(hs >>> 7) < 0.5));
			}
		}
		return out;
	}

	/** The cones' push: steep near the vent, spreading out over the flanks. */
	private double volcanicUplift(double x, double z) {
		double u = 0;
		for (Volcano v : this.volcanoes) {
			double d = Math.hypot(x - v.x(), z - v.z());
			if (d < v.radius()) {
				u = Math.max(u, v.strength() * Math.pow(1 - d / v.radius(), 1.3));
			}
		}
		return u;
	}

	/** The plate's volcanoes. */
	List<Volcano> volcanoes() {
		return this.volcanoes;
	}

	/** Volcano {@code v}'s summit height after erosion. */
	float summit(int v) {
		return this.summits[v];
	}

	/** The node nearest (x, z), or -1 outside the grid. */
	private int node(double x, double z) {
		int i = (int) Math.round(x / G) - this.i0;
		int j = (int) Math.round(z / G) - this.j0;
		return i < 0 || j < 0 || i >= this.w || j >= this.d ? -1 : i + j * this.w;
	}

	private boolean island(TerrainModel model, PlateMap.Hit hit, double x, double z) {
		// island arcs where two ocean plates collide
		if (hit.across != null && !hit.across.continental()) {
			double c = PlateMap.convergence(this.plate, hit.across);
			if (c > 0.35 && hit.border < 520) {
				double v = model.noise(TerrainModel.N_ISLAND, x / 260, z / 260) + 0.25 * model.noise(TerrainModel.N_COAST_FINE, x / 90, z / 90);
				if (v > 0.62 - 0.35 * (c - 0.35) + hit.border / 1400) {
					return true;
				}
			}
		}
		// hotspot islands
		long h = this.plate.seed();
		for (int s = 0; s < 3; s++) {
			long hs = PlateMap.mix(h, s, 99);
			if (PlateMap.unit(hs >>> 50) > 0.55) {
				continue;
			}
			double hx = this.plate.cx() + (PlateMap.unit(hs) - 0.5) * 2600;
			double hz = this.plate.cz() + (PlateMap.unit(hs >>> 21) - 0.5) * 2600;
			double r = 130 + PlateMap.unit(hs >>> 42) * 220;
			double qx = x + r * 0.7 * model.noise(TerrainModel.N_COAST, x / 260 + 13, z / 260);
			double qz = z + r * 0.7 * model.noise(TerrainModel.N_COAST, x / 260 - 29, z / 260);
			double ragged = 0.3 * model.noise(TerrainModel.N_COAST_FINE, x / 90, z / 90);
			if (Math.hypot(qx - hx, qz - hz) < r * (1 + ragged)) {
				return true;
			}
		}
		return false;
	}

	/** Distance (blocks) from every node of kind {@code to} to the nearest node of kind {@code from}. */
	private float[] distanceFrom(byte from, byte to) {
		int n = this.kind.length;
		float[] dist = new float[n];
		Arrays.fill(dist, Float.MAX_VALUE);
		Heap heap = new Heap(n);
		for (int k = 0; k < n; k++) {
			if (this.kind[k] == from) {
				dist[k] = 0;
				heap.push(0, k);
			}
		}
		while (!heap.isEmpty()) {
			float dk = heap.peekKey();
			int k = heap.pop();
			if (dk > dist[k]) {
				continue;
			}
			int i = k % this.w;
			int j = k / this.w;
			for (int e = 0; e < 8; e++) {
				int ni = i + DX[e];
				int nj = j + DZ[e];
				if (ni < 0 || nj < 0 || ni >= this.w || nj >= this.d) {
					continue;
				}
				int m = ni + nj * this.w;
				if (this.kind[m] != to && this.kind[m] != from) {
					continue;
				}
				float nd = dk + (e < 4 ? G : G * SQRT2);
				if (nd < dist[m]) {
					dist[m] = nd;
					heap.push(nd, m);
				}
			}
		}
		for (int k = 0; k < n; k++) {
			if (dist[k] == Float.MAX_VALUE) {
				dist[k] = 6000;
			}
		}
		return dist;
	}

	// ---------------------------------------------------------------------------------------------
	// Erosion

	/**
	 * Priority flood from the coast: land nodes are reached lowest first, each from a neighbour already
	 * reached, which becomes the node it drains to. Every node gets a downhill (or level) way to the sea,
	 * so pits are filled in the routing without changing the land. Returns the number of nodes in
	 * {@code order}, which lists every land node after the node it drains to.
	 */
	private int drainageTree(int[] order) {
		int n = this.kind.length;
		Arrays.fill(this.receiver, -1);
		boolean[] seen = new boolean[n];
		Heap heap = new Heap(n);
		for (int k = 0; k < n; k++) {
			if (this.kind[k] == OCEAN && this.touches(k, LAND)) {
				seen[k] = true;
				heap.push(SEA, k);
			}
		}
		int count = 0;
		while (true) {
			while (!heap.isEmpty()) {
				float key = heap.peekKey();
				int k = heap.pop();
				int i = k % this.w;
				int j = k / this.w;
				for (int e = 0; e < 8; e++) {
					int ni = i + DX[e];
					int nj = j + DZ[e];
					if (ni < 0 || nj < 0 || ni >= this.w || nj >= this.d) {
						continue;
					}
					int m = ni + nj * this.w;
					if (seen[m] || this.kind[m] != LAND) {
						continue;
					}
					seen[m] = true;
					this.receiver[m] = k;
					order[count++] = m;
					heap.push(Math.max(this.height[m] + this.wander[m], key + this.stepCost[m] * (e < 4 ? 1 : SQRT2)), m);
				}
			}
			// Land the flood never reached (cut off by the border): drain it to its own lowest point.
			int lowest = -1;
			for (int k = 0; k < n; k++) {
				if (this.kind[k] == LAND && !seen[k] && (lowest < 0 || this.height[k] < this.height[lowest])) {
					lowest = k;
				}
			}
			if (lowest < 0) {
				return count;
			}
			seen[lowest] = true;
			order[count++] = lowest;
			heap.push(this.height[lowest], lowest);
		}
	}

	private boolean touches(int k, byte kind) {
		int i = k % this.w;
		int j = k / this.w;
		for (int e = 0; e < 8; e++) {
			int ni = i + DX[e];
			int nj = j + DZ[e];
			if (ni >= 0 && nj >= 0 && ni < this.w && nj < this.d && this.kind[ni + nj * this.w] == kind) {
				return true;
			}
		}
		return false;
	}

	/** Drainage area: each node's own cell plus everything upstream of it, summed down the tree. */
	private void accumulate(int[] order, int count) {
		for (int c = 0; c < count; c++) {
			this.area[order[c]] = 1;
		}
		for (int c = count - 1; c >= 0; c--) {
			int k = order[c];
			int r = this.receiver[k];
			if (r >= 0 && this.kind[r] == LAND) {
				this.area[r] += this.area[k];
			}
		}
	}

	/** The stream-power steady state, built up the tree from the sea. */
	private void steadyState(int[] order, int count) {
		for (int c = 0; c < count; c++) {
			int k = order[c];
			int r = this.receiver[k];
			float slope = Mth.clamp(K * this.uplift[k] / (float) Math.sqrt(this.area[k]), 4.0E-4F, MAX_SLOPE);
			if (r < 0) {
				continue; // an isolated basin's own outlet keeps its height
			}
			float base = this.kind[r] == OCEAN ? SEA : this.height[r];
			float step = this.dist(k, r);
			float hk = base + step * slope;
			// the build height is finite: big ranges flatten off toward the top of the world
			if (hk > 300) {
				hk = 300 + 50 * (1 - (float) Math.exp(-(hk - 300) / 50.0));
			}
			this.height[k] = hk;
		}
	}

	/** A little hillslope creep on the smallest catchments, rounding ridge tops between valleys. */
	private void smoothHillslopes() {
		float[] next = this.height.clone();
		for (int pass = 0; pass < 2; pass++) {
			for (int j = 1; j < this.d - 1; j++) {
				for (int i = 1; i < this.w - 1; i++) {
					int k = i + j * this.w;
					if (this.kind[k] != LAND || this.area[k] > 3) {
						continue;
					}
					float sum = 0;
					int c = 0;
					for (int e = 0; e < 8; e++) {
						int m = k + DX[e] + DZ[e] * this.w;
						if (this.kind[m] == LAND) {
							sum += this.height[m];
							c++;
						}
					}
					if (c > 0) {
						float r = this.receiver[k] >= 0 && this.kind[this.receiver[k]] == LAND ? this.height[this.receiver[k]] : SEA;
						next[k] = Math.max(r + 0.05F, this.height[k] + 0.3F * (sum / c - this.height[k]));
					}
				}
			}
			System.arraycopy(next, 0, this.height, 0, next.length);
		}
	}

	private float dist(int a, int b) {
		int di = Math.abs(a % this.w - b % this.w);
		int dj = Math.abs(a / this.w - b / this.w);
		return di + dj == 2 ? G * SQRT2 : G;
	}

	// ---------------------------------------------------------------------------------------------
	// Rivers

	static float halfWidth(float area) {
		return Math.min(16.0F, 0.6F + 0.9F * (float) Math.sqrt(area / RIVER_AREA));
	}

	private boolean isRiver(int k) {
		return k >= 0 && this.kind[k] == LAND && this.area[k] >= RIVER_AREA;
	}

	private void buildRivers() {
		int n = this.kind.length;
		// river donors, per node
		int[] donors = new int[n];
		int[] firstDonor = new int[n];
		Arrays.fill(firstDonor, -1);
		int[] nextDonor = new int[n];
		for (int k = 0; k < n; k++) {
			if (this.isRiver(k) && this.receiver[k] >= 0) {
				int r = this.receiver[k];
				nextDonor[k] = firstDonor[r];
				firstDonor[r] = k;
				donors[r]++;
			}
		}
		for (int r = 0; r < n; r++) {
			if (!this.isRiver(r) || this.receiver[r] < 0) {
				continue;
			}
			int rr = this.receiver[r];
			boolean mouth = this.kind[rr] == OCEAN;
			// the water surface stands a block above each node's bed height, and halfway between nodes
			// at the mean of the two; it can only fall downstream, and never below the sea
			float wr = Math.max(SEA, this.height[r] + 1);
			float wOut = mouth ? SEA : Math.max(SEA, (this.height[r] + this.height[rr]) * 0.5F + 1);
			float hwR = halfWidth(this.area[r]);
			float mx2 = (this.x(r) + this.x(rr)) * 0.5F;
			float mz2 = (this.z(r) + this.z(rr)) * 0.5F;
			this.pieceFirst[r] = this.pieceCount;
			int made = 0;
			if (donors[r] == 0) {
				this.addPiece(this.x(r), this.z(r), this.x(r), this.z(r), mx2, mz2, wr, wr, wOut, hwR * 0.6F, hwR);
				made++;
			}
			for (int dn = firstDonor[r]; dn >= 0; dn = nextDonor[dn]) {
				float w0 = Math.max(SEA, (this.height[dn] + this.height[r]) * 0.5F + 1);
				this.addPiece((this.x(dn) + this.x(r)) * 0.5F, (this.z(dn) + this.z(r)) * 0.5F, this.x(r), this.z(r), mx2, mz2,
					w0, wr, wOut, halfWidth(this.area[dn]), hwR);
				made++;
			}
			if (mouth) {
				// the last stretch, out into the sea, widening into an estuary
				this.addPiece(mx2, mz2, mx2, mz2, this.x(rr), this.z(rr), SEA, SEA, SEA, hwR, hwR * 1.4F);
				made++;
			}
			this.pieceNum[r] = (byte) Math.min(made, 127);
		}
	}

	private void addPiece(float x0, float z0, float x1, float z1, float x2, float z2, float w0, float w1, float w2, float hw0, float hw2) {
		if ((this.pieceCount + 1) * STRIDE > this.pieces.length) {
			this.pieces = Arrays.copyOf(this.pieces, this.pieces.length * 2);
		}
		int o = this.pieceCount * STRIDE;
		float[] p = this.pieces;
		p[o] = x0;
		p[o + 1] = z0;
		p[o + 2] = x1;
		p[o + 3] = z1;
		p[o + 4] = x2;
		p[o + 5] = z2;
		p[o + 6] = w0;
		p[o + 7] = Math.min(w0, w1);
		p[o + 8] = Math.min(p[o + 7], w2);
		p[o + 9] = hw0;
		p[o + 10] = hw2;
		// bounding box, padded by the widest the valley round the channel gets (and a fjord's trough)
		float pad = Math.max(1.8F * Math.max(hw0, hw2) + 16, 62);
		p[o + 11] = Math.min(x0, Math.min(x1, x2)) - pad;
		p[o + 12] = Math.max(x0, Math.max(x1, x2)) + pad;
		p[o + 13] = Math.min(z0, Math.min(z1, z2)) - pad;
		p[o + 14] = Math.max(z0, Math.max(z1, z2)) + pad;
		this.pieceCount++;
	}

	private float x(int k) {
		return (this.i0 + k % this.w) * (float) G;
	}

	private float z(int k) {
		return (this.j0 + k / this.w) * (float) G;
	}

	// ---------------------------------------------------------------------------------------------
	// Edges

	/** Gives the nodes just past the plate's border the average of their owned neighbours, a few rings deep. */
	private void dilate() {
		float[][] fields = {this.height, this.uplift, this.coast, this.drainBase};
		boolean[] known = new boolean[this.kind.length];
		for (int k = 0; k < known.length; k++) {
			known[k] = this.kind[k] != NONE;
		}
		float[] sums = new float[fields.length];
		for (int ring = 0; ring < 4; ring++) {
			boolean[] next = known.clone();
			for (int j = 0; j < this.d; j++) {
				for (int i = 0; i < this.w; i++) {
					int k = i + j * this.w;
					if (known[k]) {
						continue;
					}
					int c = 0;
					Arrays.fill(sums, 0);
					for (int e = 0; e < 8; e++) {
						int ni = i + DX[e];
						int nj = j + DZ[e];
						if (ni < 0 || nj < 0 || ni >= this.w || nj >= this.d || !known[ni + nj * this.w]) {
							continue;
						}
						for (int f = 0; f < fields.length; f++) {
							sums[f] += fields[f][ni + nj * this.w];
						}
						c++;
					}
					if (c > 0) {
						for (int f = 0; f < fields.length; f++) {
							fields[f][k] = sums[f] / c;
						}
						next[k] = true;
					}
				}
			}
			known = next;
		}
		for (int k = 0; k < known.length; k++) {
			if (!known[k]) {
				this.height[k] = SEA - 20;
				this.drainBase[k] = SEA;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Sampling

	/** Interpolated plate values at (x, z); with {@code rivers}, also the nearest river. */
	void sample(double x, double z, double mx, double mz, boolean rivers, Raw out) {
		double fx = x / G - this.i0;
		double fz = z / G - this.j0;
		int i = Mth.floor(fx);
		int j = Mth.floor(fz);
		float tx = (float) (fx - i);
		float tz = (float) (fz - j);
		out.height = this.bicubic(this.height, i, j, tx, tz);
		out.uplift = this.bilinear(this.uplift, i, j, tx, tz);
		out.coast = this.bilinear(this.coast, i, j, tx, tz);
		out.drainBase = this.bilinear(this.drainBase, i, j, tx, tz);
		out.riverDist = Float.MAX_VALUE;
		out.riverHalfWidth = 0;
		out.riverWater = Float.NaN;
		out.riverDepth = 0;
		if (rivers) {
			this.nearestRiver((float) mx, (float) mz, i, j, out);
		}
	}

	private float at(float[] f, int i, int j) {
		i = Mth.clamp(i, 0, this.w - 1);
		j = Mth.clamp(j, 0, this.d - 1);
		return f[i + j * this.w];
	}

	private float bilinear(float[] f, int i, int j, float tx, float tz) {
		float a = Mth.lerp(tx, this.at(f, i, j), this.at(f, i + 1, j));
		float b = Mth.lerp(tx, this.at(f, i, j + 1), this.at(f, i + 1, j + 1));
		return Mth.lerp(tz, a, b);
	}

	private float bicubic(float[] f, int i, int j, float tx, float tz) {
		float r0 = catmullRom(this.at(f, i - 1, j - 1), this.at(f, i, j - 1), this.at(f, i + 1, j - 1), this.at(f, i + 2, j - 1), tx);
		float r1 = catmullRom(this.at(f, i - 1, j), this.at(f, i, j), this.at(f, i + 1, j), this.at(f, i + 2, j), tx);
		float r2 = catmullRom(this.at(f, i - 1, j + 1), this.at(f, i, j + 1), this.at(f, i + 1, j + 1), this.at(f, i + 2, j + 1), tx);
		float r3 = catmullRom(this.at(f, i - 1, j + 2), this.at(f, i, j + 2), this.at(f, i + 1, j + 2), this.at(f, i + 2, j + 2), tx);
		return catmullRom(r0, r1, r2, r3, tz);
	}

	private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
		return 0.5F * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
	}

	private void nearestRiver(float px, float pz, int i, int j, Raw out) {
		float best = Float.MAX_VALUE;
		float[] p = this.pieces;
		for (int nj = j - 2; nj <= j + 3; nj++) {
			if (nj < 0 || nj >= this.d) {
				continue;
			}
			for (int ni = i - 2; ni <= i + 3; ni++) {
				if (ni < 0 || ni >= this.w) {
					continue;
				}
				int k = ni + nj * this.w;
				int num = this.pieceNum[k];
				for (int q = 0; q < num; q++) {
					int o = (this.pieceFirst[k] + q) * STRIDE;
					if (px < p[o + 11] || px > p[o + 12] || pz < p[o + 13] || pz > p[o + 14]) {
						continue;
					}
					// distance to the quadratic curve, by eight straight steps along it
					float bestT = 0;
					float bestD = Float.MAX_VALUE;
					float ax = p[o];
					float az = p[o + 1];
					for (int s = 1; s <= 8; s++) {
						float t = s / 8.0F;
						float u = 1 - t;
						float bx = u * u * p[o] + 2 * u * t * p[o + 2] + t * t * p[o + 4];
						float bz = u * u * p[o + 1] + 2 * u * t * p[o + 3] + t * t * p[o + 5];
						float sx = bx - ax;
						float sz = bz - az;
						float len2 = sx * sx + sz * sz;
						float f = len2 < 1.0E-6F ? 0 : Mth.clamp(((px - ax) * sx + (pz - az) * sz) / len2, 0, 1);
						float cx = ax + sx * f - px;
						float cz = az + sz * f - pz;
						float dd = cx * cx + cz * cz;
						if (dd < bestD) {
							bestD = dd;
							bestT = (s - 1 + f) / 8.0F;
						}
						ax = bx;
						az = bz;
					}
					float dist = (float) Math.sqrt(bestD);
					float hw = Mth.lerp(bestT, p[o + 9], p[o + 10]);
					float score = dist - hw;
					if (score < best) {
						best = score;
						float u = 1 - bestT;
						out.riverDist = dist;
						out.riverHalfWidth = hw;
						out.riverWater = u * u * p[o + 6] + 2 * u * bestT * p[o + 7] + bestT * bestT * p[o + 8];
						out.riverDepth = 1 + Math.min(5.0F, hw * 0.45F);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------

	private static double sq(double v) {
		return v * v;
	}

	/** Smoothstep of t, clamped to [0, 1] first. */
	private static double smooth(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** A binary min-heap of (float key, int value). */
	private static final class Heap {
		private float[] keys;
		private int[] values;
		private int size;

		Heap(int capacity) {
			this.keys = new float[Math.max(16, capacity)];
			this.values = new int[Math.max(16, capacity)];
		}

		boolean isEmpty() {
			return this.size == 0;
		}

		float peekKey() {
			return this.keys[0];
		}

		void push(float key, int value) {
			if (this.size == this.keys.length) {
				this.keys = Arrays.copyOf(this.keys, this.size * 2);
				this.values = Arrays.copyOf(this.values, this.size * 2);
			}
			int c = this.size++;
			while (c > 0) {
				int parent = (c - 1) >> 1;
				if (this.keys[parent] <= key) {
					break;
				}
				this.keys[c] = this.keys[parent];
				this.values[c] = this.values[parent];
				c = parent;
			}
			this.keys[c] = key;
			this.values[c] = value;
		}

		int pop() {
			int top = this.values[0];
			float key = this.keys[--this.size];
			int value = this.values[this.size];
			int c = 0;
			while (true) {
				int l = 2 * c + 1;
				if (l >= this.size) {
					break;
				}
				int m = l + 1 < this.size && this.keys[l + 1] < this.keys[l] ? l + 1 : l;
				if (this.keys[m] >= key) {
					break;
				}
				this.keys[c] = this.keys[m];
				this.values[c] = this.values[m];
				c = m;
			}
			this.keys[c] = key;
			this.values[c] = value;
			return top;
		}
	}
}
