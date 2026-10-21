package dev.voidmc.hearth.plan.wild;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Each post type's fixed programme laid out on its site (docs/LIVING_WORLD_2.md §7.2): a pure function of the site and
 * the terrain probe, so the planner can lay it out again (to rebuild what was lost) and get the same plots.
 *
 * <p>Most posts are a little close: a lane runs in from the road (or the post's front) to the <b>head</b> building, which
 * faces down it toward the way in (the inn's front to the road, the hospice's gate down the pass, the monastery's gate
 * down the gentlest slope); the rest stand along the lane's two sides facing it, so every door opens onto the lane. The
 * toll gate stands astride the road itself with its watch house beside it; the nomads pitch their yurts in a ring round a
 * fire on one of their two grounds; the harbour runs its pier out into the water with the fishery on the shore and the
 * fishers' cottages up the lane behind.
 */
public final class PostLayouts {
	public static final Identifier ROAD = Hearth.id("infra/road");
	public static final Identifier DOCK = Hearth.id("infra/dock");
	public static final Identifier HOSPICE = Hearth.id("civic/hospice");
	public static final Identifier YURT = Hearth.id("wild/yurt");

	private PostLayouts() {
	}

	/** One building of a programme: where along the close it goes and how big it is. */
	record Piece(Identifier archetype, int frontage, int depth, @Nullable CompoundTag params) {
		static Piece of(String id, int frontage, int depth) {
			return new Piece(Hearth.id(id), frontage, depth, null);
		}

		Piece with(String key, String value) {
			CompoundTag p = this.params == null ? new CompoundTag() : this.params.copy();
			p.putString(key, value);
			return new Piece(this.archetype, this.frontage, this.depth, p);
		}
	}

	/** A post's frame: its centre, the way its front faces (v) and its right (u). */
	record Frame(int cx, int cz, Direction front) {
		Direction right() {
			return this.front.getClockWise();
		}

		int x(double u, double v) {
			return (int) Math.floor(this.cx + right().getStepX() * u + this.front.getStepX() * v);
		}

		int z(double u, double v) {
			return (int) Math.floor(this.cz + right().getStepZ() * u + this.front.getStepZ() * v);
		}

		/** The world box {minX, minZ, maxX, maxZ} of the local cells [u0, u1] × [v0, v1] (inclusive). */
		int[] box(int u0, int v0, int u1, int v1) {
			int ax = x(u0, v0);
			int az = z(u0, v0);
			int bx = x(u1, v1);
			int bz = z(u1, v1);
			return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
		}
	}

	/** The local directions a building may face. */
	enum Face {
		FRONT, BACK, LEFT, RIGHT;

		Direction world(Frame f) {
			return switch (this) {
				case FRONT -> f.front;
				case BACK -> f.front.getOpposite();
				case RIGHT -> f.right();
				case LEFT -> f.right().getOpposite();
			};
		}
	}

	/** Lays the post out: its lane and paths first (built first), then its buildings. Never touches the world. */
	public static List<PlotSpec> layOut(PostSite site, Culture culture, TerrainProbe t) {
		Identifier style = HearthServices.hasBlueprints() ? HearthServices.blueprints().style(culture) : Hearth.id(culture.getSerializedName());
		Builder b = new Builder(site, culture, t, style);
		switch (site.type()) {
			case TOLL_GATE -> b.tollGate();
			case NOMAD_CAMP -> b.camp(0);
			case HARBOUR -> b.harbour();
			default -> b.close(programme(site.type(), culture));
		}
		return b.finish();
	}

	/** A nomad camp pitched on its ground {@code which}: the yurts, the pen and the paths of that ground alone. */
	public static List<PlotSpec> camp(PostSite site, Culture culture, TerrainProbe t, int which) {
		Identifier style = HearthServices.hasBlueprints() ? HearthServices.blueprints().style(culture) : Hearth.id(culture.getSerializedName());
		Builder b = new Builder(site, culture, t, style);
		b.camp(which);
		return b.finish();
	}

	/** The close's head, and what stands on its left and right sides (from the way in, back), for each type. */
	record Programme(Piece head, List<Piece> left, List<Piece> right, String lane, int laneWidth) {
	}

	static Programme programme(PostType type, Culture culture) {
		boolean dry = culture == Culture.DESERT || culture == Culture.STEPPE || culture == Culture.MESA || culture == Culture.MEDITERRANEAN
			|| culture == Culture.KARST;
		return switch (type) {
			case HOSPICE -> new Programme(new Piece(HOSPICE, 23, 23, null), List.of(Piece.of("landscape/garden", 9, 9)), List.of(), "lane", 3);
			case INN -> new Programme(Piece.of("commerce/inn", 13, 13),
				List.of(Piece.of("landscape/well", 5, 5), Piece.of("house/cottage", 9, 9), Piece.of("landscape/garden", 9, 9)),
				List.of(Piece.of("noble/stables", 17, 15), Piece.of("house/cottage", 9, 9)), "lane", 3);
			case HERMITAGE -> new Programme(Piece.of("house/hermit_hut", 7, 8), List.of(Piece.of("civic/shrine", 5, 5)), List.of(), "path", 1);
			case HUNTERS_CAMP -> new Programme(Piece.of("work/hunters_lodge", 9, 9), List.of(Piece.of("house/hut", 6, 6), Piece.of("house/hut", 6, 6)),
				List.of(Piece.of("house/hut", 6, 6)), "path", 3);
			case CARAVAN_STOP -> new Programme(dry ? Piece.of("commerce/caravanserai", 23, 23) : Piece.of("commerce/caravan_yard", 17, 17),
				List.of(Piece.of("landscape/well", 5, 5), Piece.of("house/cottage", 9, 9)), List.of(Piece.of("house/cottage", 9, 9)), "lane", 3);
			case MONASTERY -> new Programme(Piece.of("civic/monastery", 25, 25),
				List.of(Piece.of("landscape/bell", 5, 5), Piece.of("farm/terrace_field", 13, 9)), List.of(Piece.of("farm/terrace_field", 13, 9)),
				"lane", 3);
			case FARMSTEAD -> new Programme(Piece.of("house/farmhouse", 13, 13),
				List.of(Piece.of("work/barn", 11, 13), Piece.of("farm/pasture", 15, 15)), List.of(Piece.of("house/cottage", 9, 9),
				Piece.of("farm/pasture", 15, 15)), "path", 3);
			default -> throw new IllegalArgumentException("no close for " + type);
		};
	}

	/** Lays plots out in a post's frame. */
	static final class Builder {
		final PostSite site;
		final Culture culture;
		final TerrainProbe t;
		final Identifier style;
		final Frame f;
		final int r;
		final List<PlotSpec> roads = new ArrayList<>();
		final List<PlotSpec> buildings = new ArrayList<>();
		int serial;

		Builder(PostSite site, Culture culture, TerrainProbe t, Identifier style) {
			this.site = site;
			this.culture = culture;
			this.t = t;
			this.style = style;
			this.f = new Frame(site.x(), site.z(), site.facing());
			this.r = site.type().radius;
		}

		List<PlotSpec> finish() {
			List<PlotSpec> out = new ArrayList<>(this.roads);
			out.addAll(this.buildings);
			return out;
		}

		long seed() {
			long h = this.site.seed() + 0x9E3779B97F4A7C15L * ++this.serial;
			h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
			return h ^ (h >>> 31);
		}

		int tier() {
			return switch (this.site.type()) {
				case MONASTERY, HOSPICE, CARAVAN_STOP -> 2;
				case INN, TOLL_GATE, HARBOUR, FARMSTEAD -> 1;
				default -> 0;
			};
		}

		int maxHeight(Identifier id, int fallback) {
			BlueprintService bp = HearthServices.hasBlueprints() ? HearthServices.blueprints() : null;
			ArchetypeInfo info = bp == null ? null : bp.archetype(id);
			return info == null ? fallback : info.maxHeight();
		}

		/**
		 * A building on the local cells: {@code u0..u0+wu-1} × {@code v0..v0+wv-1}, facing {@code face}; its floor at the
		 * ground in front of its door (or {@code floorY} when given).
		 */
		@Nullable PlotSpec building(Piece p, int u0, int v0, int wu, int wv, Face face, @Nullable Integer floorY) {
			int[] box = this.f.box(u0, v0, u0 + wu - 1, v0 + wv - 1);
			int sizeX = box[2] - box[0] + 1;
			int sizeZ = box[3] - box[1] + 1;
			Direction facing = face.world(this.f);
			PlotSpec probe = new PlotSpec(p.archetype(), this.style, 0, new BlockPos(box[0], 0, box[1]), sizeX, sizeZ, facing, 1, 0, new CompoundTag());
			int[] door = PlotGeometry.entrance(probe);
			int y = floorY != null ? floorY : this.t.groundY(door[0], door[1]);
			if (floorY == null && this.t.isWater(door[0], door[1])) {
				y = Math.max(y, this.t.waterY(door[0], door[1]));
			}
			// a building whose middle is water would stand in a lake (a dock or fishery is placed apart)
			int mx = (box[0] + box[2]) / 2;
			int mz = (box[1] + box[3]) / 2;
			if (this.t.isWater(mx, mz) && !p.archetype().getPath().equals("work/fishery")) {
				return null;
			}
			PlotSpec spec = new PlotSpec(p.archetype(), this.style, seed(), new BlockPos(box[0], y, box[1]), sizeX, sizeZ, facing,
				maxHeight(p.archetype(), 16), tier(), p.params() == null ? new CompoundTag() : p.params());
			if (!inside(spec)) {
				return null;
			}
			this.buildings.add(spec);
			return spec;
		}

		/** Whether a plot's ground stays in the post's square. */
		boolean inside(PlotSpec spec) {
			int x0 = spec.origin().getX();
			int z0 = spec.origin().getZ();
			return x0 >= this.site.minX() && z0 >= this.site.minZ() && x0 + spec.sizeX() - 1 <= this.site.maxX()
				&& z0 + spec.sizeZ() - 1 <= this.site.maxZ();
		}

		/** A road plot over local points {@code (u, v)} pairs, at the ground (or {@code ys} where given), of a kind and width. */
		@Nullable PlotSpec road(double[] uv, int @Nullable [] ys, String kind, int width) {
			int n = uv.length / 2;
			int[] pts = new int[n * 3];
			for (int i = 0; i < n; i++) {
				int x = this.f.x(uv[i * 2], uv[i * 2 + 1]);
				int z = this.f.z(uv[i * 2], uv[i * 2 + 1]);
				pts[i * 3] = x;
				pts[i * 3 + 1] = ys != null && ys[i] != Integer.MIN_VALUE ? ys[i] : this.t.groundY(x, z);
				pts[i * 3 + 2] = z;
			}
			return line(ROAD, pts, width, kind);
		}

		@Nullable PlotSpec line(Identifier archetype, int[] pts, int width, String kind) {
			CompoundTag p = new CompoundTag();
			p.putIntArray("points", pts);
			p.putInt("width", width);
			if (archetype.equals(ROAD)) {
				p.putString("kind", kind);
			}
			int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			dev.voidmc.hearth.plan.road.Polyline.corridor(pts, width, (x, z) -> {
				box[0] = Math.min(box[0], x);
				box[1] = Math.min(box[1], z);
				box[2] = Math.max(box[2], x);
				box[3] = Math.max(box[3], z);
			});
			for (int i = 1; i < pts.length; i += 3) {
				minY = Math.min(minY, pts[i]);
				maxY = Math.max(maxY, pts[i]);
			}
			if (box[0] > box[2]) {
				return null;
			}
			int n = pts.length / 3;
			int dx = pts[(n - 1) * 3] - pts[0];
			int dz = pts[(n - 1) * 3 + 2] - pts[2];
			Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
			boolean dock = archetype.equals(DOCK);
			int originY = dock ? minY - 1 : minY;
			int height = dock ? 6 : maxY - minY + 5;
			PlotSpec spec = new PlotSpec(archetype, this.style, seed(), new BlockPos(box[0], originY, box[1]), box[2] - box[0] + 1, box[3] - box[1] + 1,
				facing, Math.max(1, height), tier(), p);
			this.roads.add(spec);
			return spec;
		}

		/** Where the post's lane meets the road: the road column nearest the post's middle, in local (u, v) and its y. */
		double @Nullable [] roadMouth() {
			int[] road = this.site.road();
			if (road.length < 3) {
				return null;
			}
			int bi = -1;
			long bd = Long.MAX_VALUE;
			for (int i = 0; i + 2 < road.length; i += 3) {
				long dx = road[i] - this.site.x();
				long dz = road[i + 2] - this.site.z();
				long d = dx * dx + dz * dz;
				if (d < bd) {
					bd = d;
					bi = i;
				}
			}
			int dx = road[bi] - this.site.x();
			int dz = road[bi + 2] - this.site.z();
			Direction rt = this.f.right();
			double u = dx * rt.getStepX() + dz * rt.getStepZ();
			double v = dx * this.f.front.getStepX() + dz * this.f.front.getStepZ();
			if (v <= 6 || v > this.r + 12) {
				return null;
			}
			return new double[]{u, v, road[bi + 1]};
		}

		/** The common close: a lane from the way in to the head, the rest along its sides. */
		void close(Programme p) {
			int back = -this.r + 3;
			Piece head = p.head();
			int headFront = back + head.depth() - 1;
			double[] mouth = roadMouth();
			// the lane's mouth: on the road (the road's own strip and guard keep the buildings 6 back from it), or the front
			double mouthV = mouth != null ? mouth[1] : this.r - 2;
			int sideStart = (int) Math.floor(mouth != null ? mouthV - 8 : this.r - 4);
			int half = p.laneWidth() / 2;
			// the lane: from the mouth (along the road's line to it) back to the head's door
			List<Double> uv = new ArrayList<>();
			List<Integer> ys = new ArrayList<>();
			if (mouth != null) {
				uv.add(Math.max(-2.0, Math.min(2.0, mouth[0])));
				uv.add(mouthV);
				ys.add((int) mouth[2]);
			}
			for (double v = mouth != null ? mouthV - 4 : mouthV; v > headFront + 1; v -= 4) {
				uv.add(0.0);
				uv.add(v);
				ys.add(Integer.MIN_VALUE);
			}
			uv.add(0.0);
			uv.add((double) headFront + 1);
			ys.add(Integer.MIN_VALUE);
			double[] a = new double[uv.size()];
			for (int i = 0; i < a.length; i++) {
				a[i] = uv.get(i);
			}
			int[] y = ys.stream().mapToInt(Integer::intValue).toArray();
			road(a, y, p.lane(), Math.max(1, p.laneWidth()));
			// the head, facing down the lane
			building(head, -(head.frontage() / 2), back, head.frontage(), head.depth(), Face.FRONT, null);
			// the sides: frontage along the lane, depth away from it
			side(p.left(), -1, sideStart, headFront + 2, half);
			side(p.right(), 1, sideStart, headFront + 2, half);
		}

		/** Buildings along one side of the lane, from {@code vFrom} back to {@code vTo}, their fronts on the lane's edge. */
		void side(List<Piece> pieces, int side, int vFrom, int vTo, int half) {
			int v = vFrom;
			for (Piece p : pieces) {
				int v0 = v - p.frontage() + 1;
				if (v0 < vTo) {
					break;
				}
				// the door's column (just outside the front edge) is the lane's edge column
				int u0 = side < 0 ? -half - p.depth() : half + 1;
				// their doors face the lane: the left side's to the right, the right side's to the left
				building(p, u0, v0, p.depth(), p.frontage(), side < 0 ? Face.RIGHT : Face.LEFT, null);
				v = v0 - 3;
			}
		}

		/** The toll gate astride the road, its watch house beside it with a path to the road. */
		void tollGate() {
			int roadY = this.t.groundY(this.site.x(), this.site.z());
			int[] road = this.site.road();
			long bd = Long.MAX_VALUE;
			for (int i = 0; i + 2 < road.length; i += 3) {
				long dx = road[i] - this.site.x();
				long dz = road[i + 2] - this.site.z();
				if (dx * dx + dz * dz < bd) {
					bd = dx * dx + dz * dz;
					roadY = road[i + 1];
				}
			}
			CompoundTag gate = new CompoundTag();
			gate.putBoolean("over_road", true);
			gate.putInt("passage_width", 5);
			gate.putInt("street_y", roadY);
			// the frame's front runs along the road: the gate's frontage (13) across it, its depth (7) along it
			building(new Piece(Hearth.id("law/toll_gate"), 13, 7, gate), -6, -3, 13, 7, Face.FRONT, roadY);
			// the watch house on the right of the road, behind the gate, its door toward the road
			PlotSpec house = building(Piece.of("law/watch_house", 9, 9), 8, -14, 9, 9, Face.LEFT, null);
			if (house != null) {
				road(new double[]{7, -10, 2, -10}, null, "path", 1);
			}
		}

		/** A nomad camp's ground: four yurts round a fire, facing it, the pen behind; paths from each door to the fire. */
		void camp(int which) {
			int gx = this.site.groundX(which) - this.site.x();
			int gz = this.site.groundZ(which) - this.site.z();
			Direction rt = this.f.right();
			double cu = gx * rt.getStepX() + gz * rt.getStepZ();
			double cv = gx * this.f.front.getStepX() + gz * this.f.front.getStepZ();
			int cU = (int) Math.round(cu);
			int cV = (int) Math.round(cv);
			int ring = 7;
			Piece yurt = new Piece(YURT, 9, 9, null);
			// front (+v), back (-v), left (-u), right (+u) of the fire, each facing it
			building(yurt, cU - 4, cV + ring, 9, 9, Face.BACK, null);
			building(yurt, cU - 4, cV - ring - 8, 9, 9, Face.FRONT, null);
			building(yurt, cU - ring - 8, cV - 4, 9, 9, Face.RIGHT, null);
			building(yurt, cU + ring, cV - 4, 9, 9, Face.LEFT, null);
			// the pen in the corner between two yurts, its gate toward the fire
			building(Piece.of("farm/pasture", 15, 15), cU + 10, cV + 10, 15, 15, Face.BACK, null);
			// paths from the doors to the fire (a cross), and on between the yurts to the pen's gate
			road(new double[]{cU, cV + ring - 1, cU, cV - ring + 1}, null, "path", 1);
			road(new double[]{cU, cV, cU + 6, cV + 6, cU + 17, cV + 9}, null, "path", 1);
			road(new double[]{cU - ring + 1, cV, cU + ring - 1, cV}, null, "path", 1);
		}

		/** The harbour: the pier out from the shore, the fishery on the shore beside it, cottages up the lane behind. */
		void harbour() {
			// the shore along the front (toward the water), and the pier's line: the longest run of water out from it
			// (on the post's middle line or a little to either side), inside the post's square
			int shore = -1;
			int pierU = 0;
			int len = 0;
			for (int u : new int[]{0, 3, -3, 6}) {
				int sh = -1;
				for (int v = 0; v <= this.r; v++) {
					if (this.t.isWater(this.f.x(u, v), this.f.z(u, v))) {
						sh = v - 1;
						break;
					}
				}
				if (sh < 4) {
					continue;
				}
				int n = 0;
				for (int v = sh + 1; v <= Math.min(sh + 16, this.r - 2); v++) {
					if (!this.t.isWater(this.f.x(u, v), this.f.z(u, v))) {
						break;
					}
					n++;
				}
				if (shore < 0 || n > len) {
					shore = sh;
					pierU = u;
					len = n;
				}
			}
			if (shore < 4) {
				// no water where the siting saw it: a plain close of cottages
				close(new Programme(Piece.of("work/fishery", 9, 9), List.of(Piece.of("house/cottage", 9, 9)), List.of(Piece.of("house/cottage", 9, 9)),
					"lane", 3));
				return;
			}
			int landY = this.t.groundY(this.f.x(pierU, shore), this.f.z(pierU, shore));
			if (len >= 4) {
				int ex = this.f.x(pierU, shore + len);
				int ez = this.f.z(pierU, shore + len);
				int water = this.t.waterY(ex, ez);
				int deck = Math.max(water, landY - 1);
				line(DOCK, new int[]{this.f.x(pierU, shore), landY, this.f.z(pierU, shore), ex, deck, ez}, 3, "");
			}
			// the fishery on the shore beside the pier, its back to the water and its door to the lane's side
			PlotSpec fishery = building(Piece.of("work/fishery", 9, 9), -13, shore - 8, 9, 9, Face.BACK, null);
			if (fishery != null) {
				road(new double[]{-9, shore - 9, 0, shore - 9}, null, "path", 1);
			}
			// the lane back from the pier's root, the cottages on its sides
			int back = -this.r + 3;
			double[] uv = {pierU, shore, 0, shore - 4, 0, (shore - 4 + back) / 2.0, 0, back + 1};
			road(uv, null, "lane", 3);
			side(List.of(Piece.of("house/cottage", 9, 9)), -1, shore - 12, back, 1);
			side(List.of(Piece.of("house/cottage", 9, 9), Piece.of("house/cottage", 9, 9)), 1, shore - 3, back, 1);
		}
	}
}
