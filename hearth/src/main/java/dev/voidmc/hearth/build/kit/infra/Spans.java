package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The structure of a bridge over a {@link Course}: the deck's profile (rising from the banks on the bridge's own ends,
 * humping over arches, sagging between the towers of a rope bridge) and every make's substructure, deck, parapet and
 * lamps. Heights are world y. The deck's top at each centreline step is kept in half blocks ({@code H}): even is a
 * full block's top, odd a bottom slab's; a full block's rise between steps is a stair.
 *
 * <p>Where the bridge spans ({@link #gapStart()}..{@link #gapEnd()}): the steps over water, or over ground three or more
 * below the deck. Elsewhere (the banks) the deck stands on an abutment of masonry down to the ground.
 */
public final class Spans {
	private final Lay lay;
	private final Course c;
	private final Draft d;
	private final int n;
	/** The deck's top per step, in half blocks. */
	private final int[] h;
	private final int deckY;
	private final int[] water;
	private final int[] bed;
	private final List<List<Integer>> byK = new ArrayList<>();
	private final int ga;
	private final int gb;
	private final double[] sk;

	public Spans(Lay lay, Course c, int deckY, int waterParam) {
		this.lay = lay;
		this.c = c;
		this.d = lay.draft();
		this.n = c.steps();
		this.deckY = deckY;
		this.h = new int[this.n];
		this.water = new int[this.n];
		this.bed = new int[this.n];
		this.sk = new double[this.n];
		for (int k = 0; k < this.n; k++) {
			this.byK.add(new ArrayList<>());
		}
		List<Course.Col> cols = c.cols();
		for (int i = 0; i < cols.size(); i++) {
			Course.Col col = cols.get(i);
			int lx = lay.lx(col.x(), col.z());
			int lz = lay.lz(col.x(), col.z());
			if (lx >= 0 && lz >= 0 && lx < this.d.frontage() && lz < this.d.depth()) {
				this.byK.get(col.k()).add(i);
			}
		}
		for (int k = 0; k < this.n; k++) {
			int x = c.dx(k);
			int z = c.dz(k);
			int w = lay.water(x, z);
			this.water[k] = w;
			int b = lay.ground(x, z);
			for (int i : this.byK.get(k)) {
				b = Math.min(b, lay.ground(cols.get(i).x(), cols.get(i).z()));
			}
			this.bed[k] = b;
			Course.Col cc = c.col(x, z);
			this.sk[k] = cc == null ? k : cc.s();
		}
		int a = -1;
		int bb = -1;
		for (int k = 1; k < this.n - 1; k++) {
			if (this.water[k] != Site.NONE || this.bed[k] <= deckY - 3) {
				if (a < 0) {
					a = k;
				}
				bb = k;
			}
		}
		if (a < 0) {
			// nothing to span (a demo on dry land): the whole deck stands on its abutment
			a = 1;
			bb = 0;
		}
		this.ga = a;
		this.gb = bb;
		// a plain deck until a make shapes it: level at deck_y, rising from the banks
		profile(baseNeed(), false, 2);
	}

	// ------------------------------------------------------------------------------------------------ queries

	public int steps() {
		return this.n;
	}

	public int gapStart() {
		return this.ga;
	}

	public int gapEnd() {
		return this.gb;
	}

	public int gapLength() {
		return Math.max(0, this.gb - this.ga + 1);
	}

	/** The walking level the planner asks for at step k: {@code deck_y} over the span, the points' own levels elsewhere. */
	private int base(int k) {
		return Math.max(this.deckY, this.c.dy(k));
	}

	private int[] baseNeed() {
		int[] need = new int[this.n];
		for (int k = 0; k < this.n; k++) {
			need[k] = base(k);
		}
		return need;
	}

	/** How far the deck stands over the lowest water or ground it spans. */
	public int drop() {
		int low = Integer.MAX_VALUE;
		for (int k = this.ga; k <= this.gb; k++) {
			low = Math.min(low, this.water[k] != Site.NONE ? this.water[k] : this.bed[k]);
		}
		return low == Integer.MAX_VALUE ? 0 : this.deckY - low;
	}

	/** The highest water surface under the span, or {@link Site#NONE}. */
	public int waterTop() {
		int w = Site.NONE;
		for (int k = this.ga; k <= this.gb; k++) {
			if (this.water[k] != Site.NONE) {
				w = Math.max(w, this.water[k]);
			}
		}
		return w;
	}

	/** The walking level on the deck at step k (a slab's top counts as the level above it). */
	public int walk(int k) {
		return (this.h[k] + 1) >> 1;
	}

	/** The cell the deck block sits in at step k. */
	public int deckCell(int k) {
		return (this.h[k] - 1) >> 1;
	}

	public boolean inGap(int k) {
		return k >= this.ga && k <= this.gb;
	}

	private Course.Col col(int i) {
		return this.c.cols().get(i);
	}

	/** The world direction from the centreline toward a column's side (outward), from its offset. */
	private Direction outward(Course.Col col) {
		Direction t = this.c.cardinal(col.k());
		return col.off() >= 0 ? t.getClockWise() : t.getCounterClockWise();
	}

	/** The axis across the bridge at step k. */
	private Direction.Axis across(int k) {
		return this.c.cardinal(k).getClockWise().getAxis();
	}

	private Direction.Axis along(int k) {
		return this.c.cardinal(k).getAxis();
	}

	/**
	 * Sets the deck's profile: every interior step at least its {@code need} (walking levels), the ends at the banks'
	 * levels, rising from them by {@code rate} half blocks a step (1: a ramp of slabs, 2: stairs).
	 *
	 * @param level whether the whole span takes its highest need (multi-arch and covered decks are level)
	 */
	public void profile(int[] need, boolean level, int rate) {
		shape(need, level, rate);
		if (rate < 2) {
			// a ramp of slabs too gentle to reach the planner's levels: stairs instead
			for (int k = 1; k < this.n - 1; k++) {
				if (this.h[k] < 2 * Math.min(base(k), need[k])) {
					shape(need, level, 2);
					return;
				}
			}
		}
	}

	private void shape(int[] need, boolean level, int rate) {
		int y0 = this.c.dy(0);
		int y1 = this.c.dy(this.n - 1);
		int top = Integer.MIN_VALUE;
		for (int k = 1; k < this.n - 1; k++) {
			top = Math.max(top, need[k]);
		}
		for (int k = 0; k < this.n; k++) {
			int target = k == 0 ? y0 : k == this.n - 1 ? y1 : level ? Math.max(top, need[k]) : need[k];
			this.h[k] = Math.min(target * 2, Math.min(y0 * 2 + rate * k, y1 * 2 + rate * (this.n - 1 - k)));
			if (k > 0 && k < this.n - 1) {
				this.h[k] = Math.max(this.h[k], Math.min(y0, y1) * 2);
			}
		}
		// the ends are the banks' own levels
		this.h[0] = y0 * 2;
		this.h[this.n - 1] = y1 * 2;
		// a stair can only take a block at a time
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k < this.n; k++) {
				this.h[k] = Math.min(this.h[k], this.h[k - 1] + 2);
			}
			for (int k = this.n - 2; k >= 0; k--) {
				this.h[k] = Math.min(this.h[k], this.h[k + 1] + 2);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the deck

	/**
	 * Lays the deck: per column at its step's height, a full block, a bottom slab, or a stair where the deck rises a whole
	 * block from the step before; edge columns full (a kerb beam or the parapet's footing).
	 */
	void deck(LineMat floor, LineMat edge, boolean fullEdges) {
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int k = 0; k < this.n; k++) {
				Direction t = this.c.cardinal(k);
				Direction up = null;
				if (k > 0 && this.h[k] - this.h[k - 1] >= 2 && this.h[k] % 2 == 0) {
					up = t;
				} else if (k + 1 < this.n && this.h[k] - this.h[k + 1] >= 2 && this.h[k] % 2 == 0) {
					up = t.getOpposite();
				}
				for (int i : this.byK.get(k)) {
					Course.Col col = col(i);
					boolean rim = col.ring() == 0 && !col.beyond() && fullEdges;
					LineMat m = rim ? edge : floor;
					int cell = rim ? walk(k) - 1 : deckCell(k);
					Paint p;
					if (up != null && m.has(Form.STAIRS)) {
						p = m.stairs(up, Half.BOTTOM);
					} else if (!rim && this.h[k] % 2 == 1) {
						p = m.has(Form.SLAB) ? m.slab(SlabType.BOTTOM) : m.full();
					} else {
						p = m.full();
					}
					this.lay.set(col.x(), cell, col.z(), p);
					if (rim && this.h[k] % 2 == 1) {
						// under a full edge beside a slab deck: nothing hangs, the edge is the deck's kerb
						continue;
					}
				}
			}
		}
	}

	/** Masonry (or earth) from the ground up under every column outside the span: the abutments and wing walls. */
	void abutments(LineMat face, LineMat fill) {
		for (int k = 0; k < this.n; k++) {
			if (inGap(k)) {
				continue;
			}
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				support(col, k, face, fill);
			}
		}
	}

	private void support(Course.Col col, int k, LineMat face, LineMat fill) {
		int g = this.lay.ground(col.x(), col.z());
		int top = col.ring() == 0 ? walk(k) - 2 : deckCell(k) - 1;
		if (this.lay.wet(col.x(), col.z())) {
			try (var s = this.d.stage(StageKind.FOUNDATION)) {
				for (int y = g - 2; y <= top; y++) {
					this.lay.set(col.x(), y, col.z(), face.full());
				}
			}
			return;
		}
		if (g - 1 <= top) {
			boolean edge = col.ring() == 0;
			for (int y = g - 2; y <= top; y++) {
				boolean shows = edge || exposed(col.x(), y, col.z());
				try (var s = this.d.stage(shows ? StageKind.FOUNDATION : StageKind.EARTHWORK)) {
					this.lay.set(col.x(), y, col.z(), shows ? face.full() : fill.full());
				}
			}
		} else {
			try (var s = this.d.stage(StageKind.EARTHWORK)) {
				for (int y = walk(k); y < g; y++) {
					this.lay.air(col.x(), y, col.z());
				}
			}
		}
	}

	private boolean exposed(int x, int y, int z) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (!this.c.contains(nx, nz) && this.lay.surface(nx, nz) <= y) {
				return true;
			}
		}
		return false;
	}

	/** Air over the deck (stage CLEAR) to {@code headroom} above the walking level, where nothing is drawn. */
	void clear(int headroom) {
		try (var s = this.d.stage(StageKind.CLEAR)) {
			for (int k = 0; k < this.n; k++) {
				for (int i : this.byK.get(k)) {
					Course.Col col = col(i);
					int from = walk(k);
					int to = Math.min(walk(k) + headroom, this.lay.topY());
					for (int y = from; y <= to; y++) {
						if (this.lay.absent(col.x(), y, col.z()) && y >= this.lay.surface(col.x(), col.z())) {
							this.lay.air(col.x(), y, col.z());
						}
					}
				}
			}
		}
	}

	/** Whether a step is a newel or pier position along the parapet: the first and last deck steps. */
	private boolean endPost(int k) {
		return k == 1 || k == this.n - 2;
	}

	/** A newel post at walking level on an edge column, with a lantern or a cap on top. */
	private void newel(Course.Col col, int k, LineMat post, boolean lamp, boolean timber) {
		int y = walk(k);
		try (var s = this.d.stage(StageKind.EXTERIOR)) {
			this.lay.set(col.x(), y, col.z(), timber ? post.log(Direction.Axis.Y) : post.full());
		}
		if (lamp && y + 1 <= this.lay.topY()) {
			Lamps.standing(this.lay, col.x(), y + 1, col.z());
		} else if (!timber && y + 1 <= this.lay.topY()) {
			try (var s = this.d.stage(StageKind.EXTERIOR)) {
				this.lay.set(col.x(), y + 1, col.z(), post.has(Form.SLAB) ? post.slab(SlabType.BOTTOM) : post.full());
			}
		}
	}

	/** Parapet or railing along the edges, newels at the ends and at the given steps. */
	void parapet(Paint rail, Paint thin, LineMat newel, boolean timberNewel, java.util.function.IntPredicate posts, boolean lamps,
		java.util.function.IntPredicate where) {
		boolean narrow = this.c.width < 5;
		for (int k = 1; k < this.n - 1; k++) {
			if (!where.test(k)) {
				continue;
			}
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				if (col.ring() != 0 || col.beyond()) {
					continue;
				}
				if (endPost(k)) {
					newel(col, k, newel, lamps, timberNewel);
					continue;
				}
				if (posts.test(k) && !narrow) {
					// a lamp over the middle post of a long bridge only: a row of them would be a fairground
					boolean middle = Math.abs(k - this.n / 2) <= 1 && this.n >= 12;
					newel(col, k, newel, lamps && middle && this.d.tier() >= 2, timberNewel);
					continue;
				}
				try (var s = this.d.stage(StageKind.EXTERIOR)) {
					if (narrow && thin != null) {
						this.lay.set(col.x(), walk(k), col.z(), thin);
					} else {
						this.lay.set(col.x(), walk(k), col.z(), rail);
					}
				}
			}
		}
	}

	/** An open trapdoor standing against a column's outer face: a rail that leaves the whole width walkable. */
	private Paint trapRail(LineMat wood, Course.Col col) {
		return wood.trapdoor(outward(col).getOpposite(), Half.BOTTOM, true);
	}

	// ------------------------------------------------------------------------------------------------ timber

	/**
	 * A timber beam bridge (or a trestle): bents of posts down to the bed every three steps (four for a trestle) with
	 * caps and knee braces, girts across tall bents, stringers under the edges, a plank deck, a railing with posts at the
	 * bents, stone abutments.
	 */
	public void timber(boolean trestle) {
		timber(trestle, false);
	}

	/**
	 * A timber deck on piers: as {@link #timber(boolean)}, the bents stone piers the whole width of the deck (a stone
	 * culture's crossing on a slant, where an arch would come out jagged), newels of dressed stone over them.
	 */
	public void piers() {
		timber(false, true);
	}

	private void timber(boolean trestle, boolean stonePiers) {
		profile(baseNeed(), false, this.c.width >= 5 ? 1 : 2);
		LineMat planks = LineMat.of(this.lay, Role.STILT);
		LineMat beam = LineMat.of(this.lay, Role.BEAM);
		LineMat rail = LineMat.of(this.lay, Role.RAILING);
		LineMat stone = LineMat.of(this.lay, Role.FOUNDATION);
		LineMat fill = LineMat.of(this.lay, Role.FILL);
		int spacing = trestle ? 4 : 3;
		List<Integer> bents = bents(spacing);
		abutments(stone, fill);
		for (int k = this.ga; k <= this.gb; k++) {
			boolean bent = bents.contains(k);
			int cell = deckCell(k);
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				boolean rim = col.ring() == 0;
				int under = rim ? walk(k) - 2 : cell - 1;
				try (var s = this.d.stage(StageKind.FRAME)) {
					if (bent) {
						this.lay.set(col.x(), under, col.z(), beam.log(across(k)));
						boolean post = rim || stonePiers || this.c.width >= 5 && col.ring() == this.c.width / 2 && under - this.lay.ground(col.x(), col.z()) > 4;
						if (post) {
							for (int y = this.lay.ground(col.x(), col.z()) - 2; y < under; y++) {
								this.lay.set(col.x(), y, col.z(), stonePiers ? (y == under - 1 ? LineMat.of(this.lay, Role.PLINTH) : stone).full()
									: planks.log(Direction.Axis.Y));
							}
						}
					} else if (rim || col.ring() == this.c.width / 2 && this.c.width >= 5) {
						this.lay.set(col.x(), under, col.z(), beam.log(along(k)));
					}
				}
			}
			if (bent && !stonePiers) {
				bracing(k, beam, planks, trestle);
			}
		}
		deck(planks, planks, true);
		LineMat post = stonePiers ? Dress.contrast(stone, LineMat.of(this.lay, Role.PLINTH), LineMat.of(this.lay, Role.WALL_ACCENT)) : LineMat.of(this.lay, Role.STILT);
		List<Integer> postAt = bents;
		parapet(rail.post(), null, post, !stonePiers, postAt::contains, this.d.tier() >= 1, k -> true);
		if (this.c.width < 5) {
			trapdoorRails(rail.has(Form.TRAPDOOR) ? rail : planks);
		}
		clear(4);
	}

	/** Bent positions: both ends of the span and every {@code spacing} steps between, evenly. */
	private List<Integer> bents(int spacing) {
		List<Integer> out = new ArrayList<>();
		if (this.gb < this.ga) {
			return out;
		}
		int len = this.gb - this.ga;
		int m = Math.max(1, (int) Math.round(len / (double) spacing));
		for (int j = 0; j <= m; j++) {
			int k = this.ga + (int) Math.round(len * j / (double) m);
			if (!out.contains(k)) {
				out.add(k);
			}
		}
		return out;
	}

	/** Knee braces under a bent's cap, and girts across its posts every three blocks of height. */
	private void bracing(int k, LineMat beam, LineMat post, boolean trestle) {
		for (int i : this.byK.get(k)) {
			Course.Col col = col(i);
			if (col.ring() != 0) {
				continue;
			}
			Direction in = outward(col).getOpposite();
			int bx = col.x() + in.getStepX();
			int bz = col.z() + in.getStepZ();
			Course.Col inner = this.c.col(bx, bz);
			int under = walk(k) - 2;
			if (inner != null && inner.k() == k && under - 1 > this.lay.surface(bx, bz)) {
				try (var s = this.d.stage(StageKind.FRAME)) {
					this.lay.set(bx, under - 1 + (this.h[k] % 2 == 1 ? 0 : 0), bz, beam.stairsOrSlab(in.getOpposite(), Half.TOP));
				}
			}
			int g = this.lay.ground(col.x(), col.z());
			if (trestle) {
				for (int y = under - 4; y > g + 1; y -= 3) {
					for (int j : this.byK.get(k)) {
						Course.Col cj = col(j);
						if (cj.ring() > 0 && this.lay.absent(cj.x(), y, cj.z()) && y > this.lay.surface(cj.x(), cj.z())) {
							try (var s = this.d.stage(StageKind.FRAME)) {
								this.lay.set(cj.x(), y, cj.z(), beam.log(across(k)));
							}
						}
					}
				}
			}
		}
	}

	private void trapdoorRails(LineMat wood) {
		for (int k = 1; k < this.n - 1; k++) {
			if (endPost(k)) {
				continue;
			}
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				if (col.ring() != 0 || col.beyond()) {
					continue;
				}
				try (var s = this.d.stage(StageKind.EXTERIOR)) {
					this.lay.set(col.x(), walk(k), col.z(), trapRail(wood, col));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ stone arches

	/** The stones of an arch bridge: body (spandrels, piers), voussoirs, deck and parapet. */
	public record ArchStyle(LineMat body, LineMat ring, LineMat deck, LineMat parapet, LineMat newel, boolean moon) {
		public static ArchStyle of(Draft d, Spans s) {
			Lay lay = s.lay;
			Culture c = d.style().culture();
			int tier = d.tier();
			LineMat found = LineMat.of(lay, Role.FOUNDATION);
			LineMat plinth = LineMat.of(lay, Role.PLINTH);
			LineMat accent = LineMat.of(lay, Role.WALL_ACCENT);
			LineMat paving = LineMat.of(lay, Role.PAVING).weathered(0.1);
			LineMat body = tier >= 3 && plinth.has(Form.WALL) ? plinth : found;
			LineMat ring = Dress.contrast(body, accent, plinth, found);
			if (!ring.has(Form.STAIRS)) {
				ring = body;
			}
			LineMat parapet = body.has(Form.WALL) ? body : plinth.has(Form.WALL) ? plinth : found;
			LineMat newel = Dress.contrast(body, plinth, accent, found);
			boolean moon = (c == Culture.KARST || c == Culture.WISTERIA) && s.gapLength() <= 9;
			return new ArchStyle(body, ring, paving.has(Form.STAIRS) ? paving : body, parapet, newel, moon);
		}
	}

	/**
	 * A stone arch bridge: a single arch over a short span, segmental arches of about eight on two-wide piers over a long
	 * one, springing a block under the water; voussoirs round each arch on the faces and under it (smoothed with
	 * upside-down stairs), spandrels and piers of the body stone with a pilaster over each pier, the deck humped over
	 * the crowns, coped parapets with newels and lamps.
	 */
	public void arch(ArchStyle st) {
		int w = waterTop();
		int springing = w != Site.NONE ? w - 1 : Math.min(this.bed[this.ga], this.bed[this.gb]);
		double pier = 2;
		// the deck first: humped over a single arch, level over a row of them, rising from the banks as stairs
		double g0 = this.sk[this.gb] - this.sk[this.ga] + 1;
		int m0 = g0 <= 13 || st.moon() ? 1 : Math.max(2, (int) Math.round((g0 + pier) / (8 + pier)));
		double a0 = (g0 - (m0 - 1) * pier) / m0;
		double r0 = st.moon() ? a0 / 2 : m0 == 1 ? Math.max(2, Math.min(a0 / 2, a0 * 0.42)) : Math.max(2, a0 * 0.36);
		int[] need = new int[this.n];
		double mid0 = (this.sk[this.ga] + this.sk[this.gb]) / 2;
		for (int k = 0; k < this.n; k++) {
			double u = (this.sk[k] - mid0) / (g0 / 2 + 1);
			double hump = m0 == 1 ? r0 * Math.sqrt(Math.max(0, 1 - u * u)) : r0;
			need[k] = Math.max(base(k), springing + (int) Math.ceil(hump) + 2);
		}
		// a street's bridge humps gently (a ramp of slabs a cart can take), a lane's and a moon bridge climb in stairs
		profile(need, m0 > 1, st.moon() || this.c.width < 5 ? 2 : 1);
		// the arches go where the deck clears the springing by three; the abutments run out into the water to there
		int za = -1;
		int zb = -1;
		for (int k = this.ga; k <= this.gb; k++) {
			if (walk(k) - 3 >= springing) {
				if (za < 0) {
					za = k;
				}
				zb = k;
			}
		}
		if (za < 0 || zb - za < 2) {
			clapper();
			return;
		}
		// the arches: inset from where the deck allows them until they stand proud (a segmental arch rising at least a
		// quarter of its span, a moon bridge's semicircle), the abutments running out into the water to meet them
		boolean round = st.moon();
		double s0 = 0;
		double a = 0;
		int m = 1;
		double rise = 0;
		double bestScore = -1;
		for (int inset = 0; inset <= 6 && zb - za - 2 * inset >= 2; inset++) {
			int ia = za + inset;
			int ib = zb - inset;
			double ts0 = this.sk[ia] - 0.5;
			double tgap = this.sk[ib] + 0.5 - ts0;
			double tpier = tgap > 24 ? 3 : 2;
			int tm = tgap <= 13 || round ? 1 : Math.max(2, (int) Math.round((tgap + tpier) / (8 + tpier)));
			double ta = (tgap - (tm - 1) * tpier) / tm;
			double tr = fitRise(ia, ib, ts0, ta, tpier, tm, springing, round);
			double score = tr / ta;
			if (score > bestScore + 0.02) {
				bestScore = score;
				s0 = ts0;
				a = ta;
				m = tm;
				rise = tr;
				pier = tpier;
			}
			if (score >= (round ? 0.45 : 0.27)) {
				break;
			}
		}
		if (rise < 1) {
			clapper();
			return;
		}
		double[] soff = new double[this.c.cols().size()];
		java.util.Arrays.fill(soff, Double.NaN);
		for (int k = za; k <= zb; k++) {
			for (int i : this.byK.get(k)) {
				soff[i] = soffit(col(i).s(), s0, a, pier, m, springing, rise, round);
			}
		}
		LineMat fill = LineMat.of(this.lay, Role.FOUNDATION);
		abutments(st.body(), fill);
		// the span: piers solid from the bed, arches over the openings
		for (int k = this.ga; k <= this.gb; k++) {
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				boolean rim = col.ring() == 0;
				int top = rim ? walk(k) - 2 : deckCell(k) - 1;
				double sAt = soff[i];
				int g = this.lay.ground(col.x(), col.z());
				if (Double.isNaN(sAt)) {
					// a pier: solid from the bed, an impost band at the springing, a pilaster up its face
					try (var s = this.d.stage(StageKind.FOUNDATION)) {
						for (int y = g - 2; y <= top; y++) {
							LineMat m2 = rim && y == springing + 1 ? st.ring() : st.body();
							this.lay.set(col.x(), y, col.z(), m2.full());
						}
					}
					continue;
				}
				int ys = (int) Math.round(sAt);
				try (var s = this.d.stage(StageKind.WALLS)) {
					for (int y = Math.max(ys, g); y <= top; y++) {
						boolean voussoir = y <= ys + (rim ? 1 : 0);
						this.lay.set(col.x(), y, col.z(), (voussoir ? st.ring() : rim ? st.body() : fill).full());
					}
				}
				// smooth the soffit where it steps down toward the springing
				Direction t = this.c.cardinal(k);
				for (Direction dir : new Direction[]{t, t.getOpposite()}) {
					int j = this.c.indexOf(col.x() + dir.getStepX(), col.z() + dir.getStepZ());
					double sn = j >= 0 ? soff[j] : Double.NaN;
					int yn = Double.isNaN(sn) ? Integer.MIN_VALUE : (int) Math.round(sn);
					if (!Double.isNaN(sn) && yn < ys && ys - 1 > g && ys - 1 >= springing) {
						try (var s = this.d.stage(StageKind.WALLS)) {
							this.lay.set(col.x(), ys - 1, col.z(), st.ring().stairs(dir, Half.TOP));
						}
						break;
					}
				}
			}
		}
		deck(st.deck(), st.newel(), true);
		// newels over the piers' middles
		java.util.Set<Integer> pierMid = new java.util.HashSet<>();
		for (int j = 1; j < m; j++) {
			double mid = s0 + j * a + (j - 0.5) * pier;
			int best = -1;
			double bd = Double.MAX_VALUE;
			for (int k = this.ga; k <= this.gb; k++) {
				if (Math.abs(this.sk[k] - mid) < bd) {
					bd = Math.abs(this.sk[k] - mid);
					best = k;
				}
			}
			pierMid.add(best);
		}
		LineMat slabKerb = st.parapet().has(Form.SLAB) ? st.parapet() : st.newel();
		parapet(st.parapet().has(Form.WALL) ? st.parapet().wall() : st.parapet().full(), this.d.tier() >= 3 ? null : slabKerb.slab(SlabType.BOTTOM),
			st.newel(), false, pierMid::contains, this.d.tier() >= 1, k -> true);
		clear(4);
	}

	/** Where chainage s falls across its arch (-1 at one springing, 1 at the other), or NaN over a pier. */
	private static double archU(double s, double s0, double a, double pier, int m) {
		double off = s - s0;
		for (int j = 0; j < m; j++) {
			double from = j * (a + pier);
			if (off >= from - 1e-6 && off <= from + a + 1e-6) {
				return ((off - from) - a / 2) / (a / 2);
			}
		}
		return Double.NaN;
	}

	/**
	 * The soffit height of an arch over chainage s, or NaN over a pier: a semicircle (or half ellipse) for a moon bridge,
	 * a segment of a circle otherwise (it springs from its piers at an angle, so a ramped deck can carry it).
	 */
	private static double soffit(double s, double s0, double a, double pier, int m, int springing, double rise, boolean round) {
		double u = archU(s, s0, a, pier, m);
		if (Double.isNaN(u)) {
			return Double.NaN;
		}
		return springing + archHeight(u, a / 2, rise, round);
	}

	private static double archHeight(double u, double half, double rise, boolean round) {
		if (round) {
			return rise * Math.sqrt(Math.max(0, 1 - u * u));
		}
		double rc = (half * half + rise * rise) / (2 * rise);
		double x = u * half;
		return Math.sqrt(Math.max(0, rc * rc - x * x)) - (rc - rise);
	}

	/** The highest rise (at most half the span) whose soffit stays two under the deck over every column of the arches. */
	private double fitRise(int za, int zb, double s0, double a, double pier, int m, int springing, boolean round) {
		double lo = 0;
		double hi = a / 2;
		for (int it = 0; it < 18; it++) {
			double r = (lo + hi) / 2;
			boolean ok = true;
			for (int k = za; k <= zb && ok; k++) {
				for (int i : this.byK.get(k)) {
					double u = archU(col(i).s(), s0, a, pier, m);
					if (Double.isNaN(u)) {
						continue;
					}
					int ys = (int) Math.round(springing + archHeight(u, a / 2, Math.max(r, 1e-3), round));
					int top = col(i).ring() == 0 ? walk(k) - 2 : deckCell(k) - 1;
					if (ys > top) {
						ok = false;
						break;
					}
				}
			}
			if (ok) {
				lo = r;
			} else {
				hi = r;
			}
		}
		return lo;
	}

	// ------------------------------------------------------------------------------------------------ covered

	/**
	 * A covered timber bridge (straight, five wide): timber bents and stone abutments, a level deck reached by steps,
	 * boarded sides under a window band and a wall plate, posts every three, a gable roof along it with gable boards over
	 * both portals, lanterns hung from the ridge.
	 */
	public void covered() {
		profile(baseNeed(), gapLength() > 0, 2);
		LineMat planks = LineMat.of(this.lay, Role.STILT);
		LineMat beam = LineMat.of(this.lay, Role.BEAM);
		LineMat frame = LineMat.of(this.lay, Role.FRAME);
		LineMat stone = LineMat.of(this.lay, Role.FOUNDATION);
		LineMat fill = LineMat.of(this.lay, Role.FILL);
		LineMat boards = LineMat.of(this.lay, Role.GABLE);
		if (!boards.timber()) {
			boards = planks;
		}
		boolean thatch = this.d.style().habits().roof().thatch();
		LineMat roof = LineMat.of(this.lay, thatch ? Role.THATCH : Role.ROOF);
		LineMat trim = LineMat.of(this.lay, thatch ? Role.THATCH : Role.ROOF_TRIM);
		abutments(stone, fill);
		List<Integer> bents = bents(4);
		for (int k = this.ga; k <= this.gb; k++) {
			boolean bent = bents.contains(k);
			int under = deckCell(k) - 1;
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				try (var s = this.d.stage(StageKind.FRAME)) {
					if (bent) {
						this.lay.set(col.x(), under, col.z(), beam.log(across(k)));
						if (col.ring() == 0) {
							for (int y = this.lay.ground(col.x(), col.z()) - 2; y < under; y++) {
								this.lay.set(col.x(), y, col.z(), stone.full());
							}
						}
					} else if (col.ring() == 0 || col.ring() == 2) {
						this.lay.set(col.x(), under, col.z(), beam.log(along(k)));
					}
				}
			}
			if (bent) {
				bracing(k, beam, planks, false);
			}
		}
		deck(planks, planks, false);
		// the house: where the deck is at its full height
		int top = Integer.MIN_VALUE;
		for (int k = 0; k < this.n; k++) {
			top = Math.max(top, this.h[k]);
		}
		int hs = -1;
		int he = -1;
		for (int k = 1; k < this.n - 1; k++) {
			if (this.h[k] == top) {
				if (hs < 0) {
					hs = k;
				}
				he = k;
			}
		}
		if (hs < 0 || he - hs < 3) {
			parapet(LineMat.of(this.lay, Role.RAILING).post(), null, frame, true, k -> false, true, k -> true);
			clear(4);
			return;
		}
		int y = walk(hs);
		int ridgeY = Math.min(y + 5, this.lay.topY());
		// the walls in the culture's manner: plaster over a board rail (timber frame), logs laid along (log), boards
		dev.voidmc.hearth.build.style.Habits.WallKind kind = this.d.style().habits().walls().kind();
		LineMat infill = kind == dev.voidmc.hearth.build.style.Habits.WallKind.TIMBER_FRAME ? LineMat.of(this.lay, Role.WALL) : boards;
		boolean logWalls = kind == dev.voidmc.hearth.build.style.Habits.WallKind.LOG;
		for (int k = hs; k <= he; k++) {
			boolean portal = k == hs || k == he;
			boolean post = portal || (k - hs) % 3 == 0;
			boolean braced = !post && ((k - hs) % 3 == 1 || (k - hs) % 3 == 2);
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				int ring = Math.min(col.ring(), 2);
				Direction in = outward(col).getOpposite();
				Direction t = this.c.cardinal(k);
				if (ring == 0) {
					try (var s = this.d.stage(StageKind.WALLS)) {
						// the sill: a beam along the deck's edge
						this.lay.set(col.x(), y - 1, col.z(), beam.log(along(k)));
						if (post) {
							for (int yy = y; yy <= y + 2; yy++) {
								this.lay.set(col.x(), yy, col.z(), frame.log(Direction.Axis.Y));
							}
						} else {
							this.lay.set(col.x(), y, col.z(), logWalls ? frame.log(along(k)) : boards.full());
							// the window band: arched by braces off the posts, a pot of flowers on the rail between
							if (braced && kind != dev.voidmc.hearth.build.style.Habits.WallKind.LOG) {
								Direction toPost = (k - hs) % 3 == 1 ? t.getOpposite() : t;
								this.lay.set(col.x(), y + 1, col.z(), beam.stairsOrSlab(toPost, Half.TOP));
							} else if (this.d.tier() >= 1) {
								dev.voidmc.hearth.build.kit.Garden.pot(this.d, this.lay.lx(col.x(), col.z()), y + 1 - this.lay.floorY(), this.lay.lz(col.x(), col.z()));
							}
							this.lay.set(col.x(), y + 2, col.z(), logWalls ? frame.log(along(k)) : infill.full());
						}
					}
				}
				// the roof: a gable across the width, eaves in the trim, ridge along the bridge
				try (var s = this.d.stage(StageKind.ROOF)) {
					int ry = y + 3 + ring;
					if (ring < 2) {
						if (ry <= this.lay.topY()) {
							this.lay.set(col.x(), ry, col.z(), (ring == 0 ? trim : roof).stairs(in, Half.BOTTOM));
						}
						if (ring == 1 && ry - 1 <= this.lay.topY()) {
							// the tie beams under the upper course, boards over the portals
							this.lay.set(col.x(), ry - 1, col.z(), portal ? boards.full() : beam.log(along(k)));
						}
					} else if (ridgeY <= this.lay.topY()) {
						this.lay.set(col.x(), ridgeY, col.z(), trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : trim.full());
						if (portal) {
							for (int yy = y + 3; yy < ridgeY; yy++) {
								this.lay.set(col.x(), yy, col.z(), boards.full());
							}
						} else {
							this.lay.set(col.x(), ridgeY - 1, col.z(), beam.log(along(k)));
						}
					}
				}
				if (ring == 2 && !portal && (k - hs) % 4 == 2) {
					Lamps.hanging(this.lay, col.x(), ridgeY - 1, col.z());
				}
				// a lantern hung in the head of each portal, under its gable boards
				if (ring == 2 && portal && y + 3 < ridgeY) {
					Lamps.hanging(this.lay, col.x(), y + 3, col.z());
				}
			}
		}
		// railings on the steps up to the portals (the house's own sides stand between them)
		int fhs = hs;
		int fhe = he;
		parapet(LineMat.of(this.lay, Role.RAILING).post(), null, frame, true, k -> false, false, k -> k < fhs || k > fhe);
		clear(Math.max(4, ridgeY - y));
	}

	// ------------------------------------------------------------------------------------------------ rope

	/**
	 * A rope bridge over a gorge: a tower of posts and a lintel at each bank, a deck of slabs slung between them sagging
	 * a block or two, hand ropes from the tower tops down to rail height in the middle, hangers to the deck.
	 */
	public void rope() {
		int[] need = new int[this.n];
		int mid = (this.ga + this.gb) / 2;
		double half = Math.max(1, (this.gb - this.ga) / 2.0);
		int sag = Math.min(2, Math.max(1, (this.gb - this.ga) / 8));
		int floorMin = waterTop() == Site.NONE ? Integer.MIN_VALUE : waterTop() + 2;
		for (int k = 0; k < this.n; k++) {
			double u = (k - mid) / half;
			int dip = inGap(k) ? (int) Math.round(sag * Math.max(0, 1 - u * u)) : 0;
			need[k] = Math.max(floorMin, base(k) - dip);
		}
		profile(need, false, 1);
		LineMat planks = LineMat.of(this.lay, Role.STILT);
		LineMat rope = LineMat.of(this.lay, Role.RAILING);
		LineMat post = LineMat.of(this.lay, Role.FRAME);
		LineMat stone = LineMat.of(this.lay, Role.FOUNDATION);
		abutments(stone, LineMat.of(this.lay, Role.FILL));
		// the deck: slabs on beams across
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int k = 0; k < this.n; k++) {
				for (int i : this.byK.get(k)) {
					Course.Col col = col(i);
					int cell = walk(k) - 1;
					boolean beamRow = inGap(k) && (k - this.ga) % 2 == 0;
					this.lay.set(col.x(), cell, col.z(), beamRow ? LineMat.of(this.lay, Role.BEAM).log(across(k)) : planks.slab(SlabType.TOP));
				}
			}
		}
		// towers at the first and last deck steps
		int towerTop = Math.min(walk(1) + 4, this.lay.topY());
		int towerTop2 = Math.min(walk(this.n - 2) + 4, this.lay.topY());
		for (int k : new int[]{1, this.n - 2}) {
			int tt = k == 1 ? towerTop : towerTop2;
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				try (var s = this.d.stage(StageKind.FRAME)) {
					if (col.ring() == 0) {
						for (int y = this.lay.ground(col.x(), col.z()) - 2; y <= tt; y++) {
							this.lay.set(col.x(), y, col.z(), post.log(Direction.Axis.Y));
						}
					} else {
						this.lay.set(col.x(), tt, col.z(), post.log(across(k)));
					}
				}
			}
		}
		// hand ropes and hangers
		for (int k = 2; k < this.n - 2; k++) {
			double u = (k - mid) / Math.max(1.0, (this.n - 3) / 2.0);
			int base = walk(k);
			int top = k < mid ? towerTop : towerTop2;
			int ry = base + (int) Math.round((top - base - 1) * Math.min(1, u * u));
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				if (col.ring() != 0) {
					continue;
				}
				try (var s = this.d.stage(StageKind.EXTERIOR)) {
					for (int y = base; y <= Math.min(ry, this.lay.topY()); y++) {
						if (y == ry || y == base || k % 2 == 0) {
							this.lay.set(col.x(), y, col.z(), rope.post());
						}
					}
				}
			}
		}
		// a lantern under each tower's lintel
		for (int k : new int[]{1, this.n - 2}) {
			int tt = k == 1 ? towerTop : towerTop2;
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				if (col.ring() > 0 && col.x() == this.c.dx(k) && col.z() == this.c.dz(k) && tt - 1 > walk(k) + 1) {
					Lamps.hanging(this.lay, col.x(), tt, col.z());
				}
			}
		}
		clear(3);
	}

	// ------------------------------------------------------------------------------------------------ clapper

	/** A clapper bridge: rough stone piers every three steps, great flat slabs across them, no parapet. */
	public void clapper() {
		profile(baseNeed(), gapLength() > 0, 2);
		LineMat stone = LineMat.of(this.lay, Role.FOUNDATION);
		LineMat slab = LineMat.of(this.lay, Role.PLINTH).has(Form.SLAB) ? LineMat.of(this.lay, Role.PLINTH) : stone;
		abutments(stone, LineMat.of(this.lay, Role.FILL));
		List<Integer> piers = bents(3);
		for (int k = this.ga; k <= this.gb; k++) {
			boolean pier = piers.contains(k);
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				int cell = walk(k) - 1;
				if (pier) {
					try (var s = this.d.stage(StageKind.FOUNDATION)) {
						for (int y = this.lay.ground(col.x(), col.z()) - 2; y < cell; y++) {
							this.lay.set(col.x(), y, col.z(), stone.full());
						}
					}
				}
				try (var s = this.d.stage(StageKind.FLOORS)) {
					this.lay.set(col.x(), cell, col.z(), pier ? stone.full() : slab.slab(SlabType.TOP));
				}
			}
		}
		for (int k = 0; k < this.n; k++) {
			if (inGap(k)) {
				continue;
			}
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				try (var s = this.d.stage(StageKind.FLOORS)) {
					this.lay.set(col.x(), walk(k) - 1, col.z(), stone.full());
				}
			}
		}
		clear(4);
	}

	// ------------------------------------------------------------------------------------------------ boardwalk

	/** A boardwalk just over the water: planks on stringers and piles, open at the edges. */
	public void boardwalk() {
		int[] need = new int[this.n];
		for (int k = 0; k < this.n; k++) {
			need[k] = this.c.dy(k);
		}
		profile(need, false, 2);
		LineMat planks = Dress.timberCulture(this.d.style().culture()) ? LineMat.of(this.lay, Role.PAVING) : LineMat.of(this.lay, Role.STILT);
		if (!planks.timber()) {
			planks = LineMat.of(this.lay, Role.STILT);
		}
		LineMat pile = LineMat.of(this.lay, Role.STILT);
		LineMat beam = LineMat.of(this.lay, Role.BEAM);
		for (int k = 0; k < this.n; k++) {
			for (int i : this.byK.get(k)) {
				Course.Col col = col(i);
				int cell = walk(k) - 1;
				int g = this.lay.ground(col.x(), col.z());
				boolean wet = this.lay.wet(col.x(), col.z());
				if (!wet) {
					support(col, k, LineMat.of(this.lay, Role.FOUNDATION), LineMat.of(this.lay, Role.FILL));
				} else {
					boolean pileHere = col.ring() == 0 && Math.floorMod(k + (col.off() > 0 ? 1 : 0), 3) == 0;
					try (var s = this.d.stage(StageKind.FOUNDATION)) {
						if (pileHere) {
							for (int y = g - 2; y < cell; y++) {
								this.lay.set(col.x(), y, col.z(), pile.log(Direction.Axis.Y));
							}
						} else if (col.ring() == 0 && cell - 1 >= this.lay.water(col.x(), col.z()) - 1) {
							this.lay.set(col.x(), cell - 1, col.z(), beam.log(along(k)));
						}
					}
				}
				try (var s = this.d.stage(StageKind.FLOORS)) {
					// a cross board of the darker timber every fourth step: the walk reads in bays, as it was laid
					boolean tie = Math.floorMod(k + this.c.dx(0) + this.c.dz(0), 4) == 0 && wet;
					this.lay.set(col.x(), cell, col.z(), tie ? beam.log(across(k)) : planks.full());
				}
			}
		}
		clear(3);
	}

}
