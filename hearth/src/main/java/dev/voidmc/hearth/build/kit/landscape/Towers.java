package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The parts of towers and gatehouses: masonry shells with toothed quoins and a plinth, arrow slits, corbel rings under
 * an oversailing top, crenellated parapets, and roofed lookouts on corner posts (a pyramid, a cone, a curved karst
 * roof) or open battlemented tops, in the culture's manner. Timber cultures (bayou, tropical) frame their towers in
 * logs with boarded walls.
 */
public final class Towers {
	private Towers() {
	}

	/** How a culture tops its towers. */
	public enum Top {
		/** Crenellated, open to the sky (desert, mesa, mediterranean, and some temperate towers). */
		OPEN,
		/** A pyramid roof on corner posts over a lookout. */
		PYRAMID,
		/** A cone (steppe). */
		CONE,
		/** A curved, flared hip roof (karst). */
		PAGODA
	}

	/** The materials of a fortification in this style. */
	public record Look(Mat body, Mat quoin, Mat found, Mat floor, Mat post, boolean timber) {
		/** The fortification dressing of the culture (see {@link Manner#fort}): a town's towers and gates match its wall. */
		public static Look of(Draft d) {
			boolean timber = Manner.timberForts(d);
			Mat body = timber ? Manner.fort(d, Role.WALL) : Manner.fortMasonry(d);
			Mat accent = Manner.fort(d, Role.WALL_ACCENT);
			Mat quoin = Manner.wood(accent) || timber ? body : accent;
			Mat found = Manner.wood(Manner.fort(d, Role.FOUNDATION)) && !timber ? body : Manner.fort(d, Role.FOUNDATION);
			Mat floor = Manner.fort(d, Role.FLOOR);
			Mat post = timber ? Manner.fort(d, Role.FRAME) : Manner.fort(d, Role.BEAM);
			return new Look(body, quoin, found, floor, post, timber);
		}
	}

	public static Top top(Draft d) {
		Culture c = Manner.culture(d);
		return switch (c) {
			case DESERT, MESA, MEDITERRANEAN -> Top.OPEN;
			case KARST -> Top.PAGODA;
			case TEMPERATE, MARITIME, MOORLAND -> d.chance(0.3) ? Top.OPEN : Top.PYRAMID;
			default -> Top.PYRAMID;
		};
	}

	/**
	 * The four walls of a rectangle from {@code y0} to {@code y1}: masonry with toothed quoins (or log corner posts and
	 * boards in timber), the first course in the foundation stone.
	 */
	public static void shell(Draft d, Look l, int x0, int z0, int x1, int z1, int y0, int y1) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				for (int y = y0; y <= y1; y++) {
					Paint p;
					if (l.timber) {
						p = corner ? l.post.log(Direction.Axis.Y).orElse(l.body.full()) : l.body.full();
					} else if (y == y0) {
						p = l.found.full();
					} else if (corner && ((y - y0) & 1) == 1) {
						p = l.quoin.full();
					} else {
						p = l.body.full();
					}
					d.set(c[0], y, c[1], p);
				}
			}
		}
	}

	/** A course of the accent (a string course) round a rectangle at y, or a beam in timber. */
	public static void string(Draft d, Look l, int x0, int z0, int x1, int z1, int y) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < side.length(); i++) {
					d.set(side.x(i), y, side.z(i), l.timber ? l.post.log(side.axis()).orElse(l.body.full()) : l.quoin.full());
				}
			}
		}
	}

	/** An arrow slit: a one-block opening at head height (too low to walk through). */
	public static void slit(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, Blocks.AIR.defaultBlockState());
		}
	}

	/** Upside-down stairs round a rectangle at y, their full side against what they carry (a corbel table). */
	public static void corbels(Draft d, Look l, int x0, int z0, int x1, int z1, int y) {
		Mat m = l.body.has(Form.STAIRS) ? l.body : l.quoin;
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (side.isCorner(i) || !m.has(Form.STAIRS)) {
						d.set(x, y, z, m.full());
					} else {
						d.set(x, y, z, m.stairs(side.out().getOpposite(), Half.TOP));
					}
				}
			}
		}
	}

	/** A parapet round a rectangle at y and merlons on it at y + 1 (corners always, then every other block). */
	public static void battlements(Draft d, Look l, int x0, int z0, int x1, int z1, int y) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (l.timber) {
						d.set(x, y, z, l.post.log(Direction.Axis.Y).orElse(l.body.full()));
						Mat fence = d.mat(Role.FENCE);
						if ((i & 1) == 0 && fence.has(Form.FENCE) && y + 1 < d.maxHeight()) {
							d.set(x, y + 1, z, fence.fence());
						}
						continue;
					}
					d.set(x, y, z, l.body.full());
					boolean merlon = side.isCorner(i) || (i & 1) == 0 && i != side.last() - 1 || i == side.last();
					if (merlon && y + 1 < d.maxHeight()) {
						d.set(x, y + 1, z, d.tier() >= 3 ? l.quoin.full() : l.body.full());
					}
				}
			}
		}
	}

	/** A low parapet between corner posts at y: a wall of the masonry, or a railing in timber. */
	public static void rail(Draft d, Look l, int x0, int z0, int x1, int z1, int y) {
		Mat fence = d.mat(Role.RAILING);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				if (l.timber || Manner.wood(l.body)) {
					d.set(c[0], y, c[1], fence.has(Form.FENCE) ? fence.fence() : l.body.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				} else {
					d.set(c[0], y, c[1], l.body.has(Form.WALL) ? l.body.wall() : l.body.full());
				}
			}
		}
	}

	/**
	 * A roofed lookout on a platform over a rectangle whose floor is at standing level y: a low parapet round the
	 * platform's edge, posts three high set in a block from it, a wall plate of beams on them, and a roof in the
	 * culture's form whose eaves reach out over the parapet. Returns the roof's top y.
	 */
	public static int lookout(Draft d, Look l, int x0, int z0, int x1, int z1, int y, Top top) {
		return lookout(d, l, x0, z0, x1, z1, y, top, 1);
	}

	/** {@link #lookout} with the posts set {@code inset} blocks in from the platform's edge (0: on its corners). */
	public static int lookout(Draft d, Look l, int x0, int z0, int x1, int z1, int y, Top top, int inset) {
		Mat post = l.post;
		int plate = y + 3;
		int px0 = x0 + inset;
		int pz0 = z0 + inset;
		int px1 = x1 - inset;
		int pz1 = z1 - inset;
		rail(d, l, x0, z0, x1, z1, y);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{px0, pz0}, {px1, pz0}, {px0, pz1}, {px1, pz1}}) {
				for (int yy = y; yy < plate; yy++) {
					d.set(c[0], yy, c[1], l.timber || Manner.wood(post) ? post.log(Direction.Axis.Y).orElse(l.body.full()) : l.body.full());
				}
			}
			for (Side side : Side.of(px0, pz0, px1, pz1)) {
				for (int i = 0; i < side.length(); i++) {
					d.set(side.x(i), plate, side.z(i), post.log(side.axis()).orElse(l.body.full()));
				}
			}
		}
		Habits.Roof hr = d.habits().roof();
		Culture c = Manner.culture(d);
		double pitch = switch (c) {
			case NORTHERN, TUNDRA, TEMPERATE, MOORLAND, MARITIME, WISTERIA -> Math.max(hr.pitch(), 1.4);
			case ALPINE -> 0.8;
			default -> Math.max(hr.pitch(), 1.0);
		};
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(inset, inset).withPitch(pitch);
		RoofForm form = top == Top.PAGODA ? RoofForm.PAGODA : RoofForm.HIP;
		Roofs.Built b = Roofs.build(d, px0, pz0, px1, pz1, plate, form, Direction.Axis.X, o, false, false);
		int cx = (px0 + px1) / 2;
		int cz = (pz0 + pz1) / 2;
		if ((px1 - px0) % 2 == 0 && (pz1 - pz0) % 2 == 0) {
			finial(d, cx, b.roof().ridgeY + 1, cz);
			return b.roof().ridgeY + 2;
		}
		return b.roof().ridgeY;
	}

	/**
	 * A wall banner of the culture's colours hanging on a wall at (x, y, z), facing {@code out}: the main cloth colour
	 * with a device in the second, the same for every banner of the culture (a town's heraldry).
	 */
	public static void banner(Draft d, int x, int y, int z, Direction out) {
		var cloth = d.mat(Role.CLOTH).palette();
		net.minecraft.world.level.block.Block main = cloth.main().block(Form.WALL_BANNER);
		if (main == null) {
			return;
		}
		String second = "white";
		for (var e : cloth.entries()) {
			net.minecraft.world.level.block.Block b = e.material().block(Form.WALL_BANNER);
			if (b != null && b != main) {
				second = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().replace("_wall_banner", "");
				break;
			}
		}
		String[] devices = {"stripe_center", "cross", "rhombus", "half_horizontal", "chevron", "border", "circle", "stripe_downright"};
		String device = devices[Math.floorMod(Manner.culture(d).ordinal() * 7 + 3, devices.length)];
		java.util.List<String[]> patterns = java.util.List.of(new String[]{device, second}, new String[]{"border", second});
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, main.defaultBlockState().trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, out),
				dev.voidmc.hearth.build.draft.BlockEntities.banner(patterns));
		}
	}

	/**
	 * Clasping buttresses at the foot of a shaft's four corners: on the two faces beside each corner, masonry up to
	 * {@code h} with a sloping cap, standing on the ground.
	 */
	public static void buttresses(Draft d, Look l, int x0, int z0, int x1, int z1, int h) {
		if (l.timber) {
			return;
		}
		Mat m = l.found.has(Form.STAIRS) ? l.found : l.body;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				int ox = c[0] == x0 ? -1 : 1;
				int oz = c[1] == z0 ? -1 : 1;
				for (int[] b : new int[][]{{c[0] + ox, c[1]}, {c[0], c[1] + oz}}) {
					int bx = b[0];
					int bz = b[1];
					if (bx < 0 || bz < 0 || bx >= d.frontage() || bz >= d.depth()) {
						continue;
					}
					int g = d.site().ground(bx, bz);
					Direction out = bx != c[0] ? (ox < 0 ? Dirs.LEFT : Dirs.RIGHT) : (oz < 0 ? Dirs.FRONT : Dirs.BACK);
					for (int y = Math.min(g, 0) - 1; y < h; y++) {
						d.set(bx, y, bz, y < Math.min(g, 0) + 1 ? l.found.full() : l.body.full());
					}
					d.set(bx, h, bz, m.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/**
	 * A free cell on a floor at standing level y in a rectangle's ring for a piece facing into it, with the cell in front
	 * of it free too; {x, z, dirIndex} or null.
	 */
	public static int @org.jspecify.annotations.Nullable [] spot(Draft d, int x0, int z0, int x1, int z1, int y, java.util.Set<Long> taken) {
		for (Side side : Side.of(x0, z0, x1, z1)) {
			Direction in = side.out().getOpposite();
			for (int i = 1; i < side.length() - 1; i++) {
				int x = side.x(i);
				int z = side.z(i);
				int fx = x + Dirs.dx(in);
				int fz = z + Dirs.dz(in);
				if (taken.contains(Draft.key(x, 0, z)) || taken.contains(Draft.key(fx, 0, fz))) {
					continue;
				}
				if (d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.isSolid(x, y - 1, z) && d.isEmpty(fx, y, fz) && d.isEmpty(fx, y + 1, fz)
					&& d.isSolid(fx, y - 1, fz)) {
					taken.add(Draft.key(x, 0, z));
					taken.add(Draft.key(fx, 0, fz));
					return new int[]{x, z, java.util.Arrays.asList(Dirs.HORIZONTAL).indexOf(in)};
				}
			}
		}
		return null;
	}

	/** A finial on a roof's peak: a fence post or a wall stub with a lightning-rod tip in towns. */
	public static void finial(Draft d, int x, int y, int z) {
		if (y >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			Mat trim = d.mat(Role.ROOF_TRIM);
			BlockState rod = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
				net.minecraft.resources.Identifier.withDefaultNamespace("waxed_lightning_rod")).defaultBlockState();
			if (trim.has(Form.FENCE)) {
				d.set(x, y, z, trim.fence());
			} else if (trim.has(Form.WALL)) {
				d.set(x, y, z, trim.wall());
			} else {
				d.set(x, y, z, d.mat(Role.FRAME).fence().orElse(trim.full()));
			}
			if (y + 1 < d.maxHeight() && d.tier() >= 3 && !rod.isAir()) {
				d.set(x, y + 1, z, rod);
			}
		}
	}

	/** Footings under a rectangle of walls standing on floor block row {@code floorBlockY}, and the inside made up. */
	public static void footings(Draft d, int x0, int z0, int x1, int z1, int floorBlockY) {
		dev.voidmc.hearth.build.terrain.Groundwork.footings(d, x0, z0, x1, z1, floorBlockY);
	}

	/** A floor of boards (or masonry paving in stone towers' ground storeys) over a rectangle at y. */
	public static void floor(Draft d, Look l, int x0, int z0, int x1, int z1, int y, boolean stone) {
		Mat m = stone && !l.timber ? (Manner.wood(d.mat(Role.PAVING)) ? l.body : d.mat(Role.PAVING)) : l.floor;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (d.isAbsent(x, y, z)) {
						d.set(x, y, z, m.full());
					}
				}
			}
		}
	}

	/**
	 * A spiral stair ({@link dev.voidmc.hearth.build.kit.Stairs#spiral}) with a third block of headroom over every
	 * step, so floors laid afterwards leave a stairwell a person can climb through (a half step up needs the head room of
	 * the step after it).
	 */
	public static void spiral(Draft d, int cx, int cz, int y0, int rise, int startAt) {
		dev.voidmc.hearth.build.kit.Stairs.spiral(d, cx, cz, y0, rise, startAt);
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int k = 1; k <= rise * 2; k++) {
				int[] c = ring[(startAt + k) % 8];
				int y = y0 + (k - 1) / 2;
				if (y + 3 < y0 + rise && d.isAbsent(cx + c[0], y + 3, cz + c[1])) {
					d.set(cx + c[0], y + 3, cz + c[1], Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** The direction out of a rectangle's side wall at a cell (for doors and slits). */
	public static Direction out(int x, int z, int x0, int z0, int x1, int z1) {
		if (z == z0) {
			return Dirs.FRONT;
		}
		if (z == z1) {
			return Dirs.BACK;
		}
		return x == x0 ? Dirs.LEFT : Dirs.RIGHT;
	}
}
