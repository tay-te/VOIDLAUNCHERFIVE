package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Towers and what tops them: square shafts with quoins and string courses, open belfries with a bell hung in them,
 * clock faces, and caps in every tradition's manner (a needle spire, a low pyramid, a copper onion, a dome, a curved
 * tiled roof with lifted corners, a cone, battlements), with a finial. Also the small pieces of a roofscape: a cupola on
 * a ridge, a bell-cote on a gable.
 *
 * <p>A tower's square is given by its outer corner {@code (x0, z0)} and {@code size} (odd sizes centre the cap on a
 * block: 3 or 5 for cupolas and towers). Caps never rise past the given top; they flatten their pitch to fit.
 */
public final class Towers {
	private Towers() {
	}

	public enum Cap {
		/** A steep needle spire with a finial (gothic and northern towers). */
		SPIRE,
		/** A low pyramid (stone towers, cupolas). */
		PYRAMID,
		/** A copper onion dome on a drum (alpine and tundra). */
		ONION,
		/** A hemispherical dome (desert, mediterranean). */
		DOME,
		/** A curved tiled hip with lifted corners (karst). */
		PAGODA,
		/** A cone (steppe, round towers). */
		CONE,
		/** Crenellations round a flat top (guard towers, mesa). */
		BATTLEMENT,
		/** A hipped tile cap with a deep eave (mediterranean campanile). */
		HIP
	}

	/** The cap a tradition gives its towers; {@code tall} for a landmark tower rather than a cupola. */
	public static Cap cap(Tradition t, boolean tall) {
		return switch (t) {
			case TIMBER -> Cap.SPIRE;
			case LOG -> Cap.SPIRE;
			case ALPINE -> Cap.ONION;
			case STONE -> tall ? Cap.SPIRE : Cap.PYRAMID;
			case MEDITERRANEAN -> Cap.HIP;
			case ADOBE -> Cap.DOME;
			case STEPPE -> Cap.CONE;
			case KARST -> Cap.PAGODA;
			case STILT -> Cap.PYRAMID;
		};
	}

	// ---------------------------------------------------------------------------------------------------- shafts

	/**
	 * A square shaft of walls from {@code y0} to {@code y1} inclusive: the wall role in its body, the wall-accent stone in
	 * toothed quoins at the corners (masonry) or the frame at the corners (timber and log), a string course at
	 * {@code courseY} if it is inside the run. The inside is left alone.
	 */
	public static void shaft(Draft d, int x0, int z0, int size, int y0, int y1, Tradition t, int courseY) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat frame = d.mat(Role.FRAME);
		Habits.WallKind kind = d.habits().walls().kind();
		boolean timber = kind == Habits.WallKind.TIMBER_FRAME || kind == Habits.WallKind.LOG || kind == Habits.WallKind.PLANK
			|| kind == Habits.WallKind.BAMBOO;
		int x1 = x0 + size - 1;
		int z1 = z0 + size - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				boolean nextToCorner = !corner && (c[0] == x0 + 1 || c[0] == x1 - 1) && (c[1] == z0 || c[1] == z1)
					|| !corner && (c[1] == z0 + 1 || c[1] == z1 - 1) && (c[0] == x0 || c[0] == x1);
				for (int y = y0; y <= y1; y++) {
					Paint p;
					if (y == courseY) {
						p = accent.full();
					} else if (corner) {
						p = timber ? frame.log(Direction.Axis.Y) : accent.full();
					} else if (!timber && nextToCorner && ((y - y0) & 1) == 0) {
						p = accent.full();
					} else if (kind == Habits.WallKind.LOG) {
						p = wall.log(c[1] == z0 || c[1] == z1 ? Direction.Axis.X : Direction.Axis.Z);
					} else {
						p = wall.full();
					}
					d.set(c[0], y, c[1], p);
				}
			}
		}
	}

	/**
	 * An open belfry stage on a square: piers at the corners from {@code y0} to {@code y0 + h - 1}, an arch (or a
	 * braced opening) in every face, a floor at {@code y0 - 1}, a beam course at {@code y0 + h} over which the cap goes,
	 * and a bell hung from the middle of that course. Faces of size 3 get one opening, of 5 or more an arcade.
	 */
	public static void belfry(Draft d, int x0, int z0, int size, int y0, int h, Tradition t, boolean bell) {
		int x1 = x0 + size - 1;
		int z1 = z0 + size - 1;
		Arches.Kind kind = Arches.kind(t);
		if (kind == Arches.Kind.POSTED) {
			kind = Arches.Kind.BRACED;
		}
		Paint pier = Arches.pierPaint(d, kind);
		Paint course = Arches.timber(kind) ? d.mat(Role.BEAM).log(Direction.Axis.X) : d.mat(Role.WALL_ACCENT).full();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, y0 - 1, z, Arches.timber(kind) ? d.mat(Role.FLOOR).full() : d.mat(Role.WALL_ACCENT).full());
					for (int y = y0; y < y0 + h; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		for (Side side : Side.of(x0, z0, x1, z1)) {
			int[] piers = size >= 7 ? Arches.piers(0, side.last(), 3) : new int[]{0, side.last()};
			Paint c = Arches.timber(kind) ? d.mat(Role.BEAM).log(side.axis()) : course;
			Arches.arcade(d, side, piers, y0, y0 + h, kind, pier, c);
			// a low parapet between the piers: something to lean on, nothing to fall through
			for (int i = 1; i < side.last(); i++) {
				boolean isPier = false;
				for (int p : piers) {
					isPier |= p == i;
				}
				if (!isPier) {
					int fi = i;
					d.inStage(StageKind.EXTERIOR, () -> d.set(side.x(fi), y0, side.z(fi), Arches.timber(Arches.kind(t))
						? Halls.post(d) : d.mat(Role.WALL_ACCENT).slab(SlabType.BOTTOM)));
				}
			}
		}
		// the course over the openings is solid all over (the cap stands on it)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, y0 + h, z, Arches.timber(kind) ? d.mat(Role.CEILING).full() : d.mat(Role.WALL_ACCENT).full());
				}
			}
		}
		if (bell && size >= 3) {
			int cx = x0 + size / 2;
			int cz = z0 + size / 2;
			bell(d, cx, y0 + h - 1, cz, Direction.Axis.X);
		}
	}

	/** A bell hung from the block above (x, y + 1, z), swinging across {@code axis}. */
	public static void bell(Draft d, int x, int y, int z, Direction.Axis axis) {
		BlockState b = Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, axis == Direction.Axis.X ? Dirs.RIGHT : Dirs.FRONT);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, b));
	}

	// ---------------------------------------------------------------------------------------------------- caps

	/**
	 * A cap over the square {@code x0, z0, size}, its eave course standing on {@code baseY} (the top of the walls). Never
	 * rises past {@code maxTop}. Returns the highest y drawn (finial included).
	 */
	public static int cap(Draft d, int x0, int z0, int size, int baseY, Cap cap, int maxTop) {
		int x1 = x0 + size - 1;
		int z1 = z0 + size - 1;
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		int room = maxTop - baseY;
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		switch (cap) {
			case SPIRE -> {
				return spire(d, x0, z0, size, baseY, maxTop);
			}
			case PYRAMID, HIP -> {
				if (room < 4) {
					// no room under the plot's height for a pyramid: a flat cap of slabs, a block in the middle
					try (var st = d.stage(StageKind.ROOF)) {
						for (int z = z0; z <= z1; z++) {
							for (int x = x0; x <= x1; x++) {
								if (d.allowed(x, baseY + 1, z)) {
									d.set(x, baseY + 1, z, roof.slab(SlabType.BOTTOM));
								}
							}
						}
					}
					return baseY + 1;
				}
				// a small pyramid stands on its own walls (no eave ring: it would read as a hat); a hip overhangs
				int e = cap == Cap.HIP ? 1 : size <= 3 ? 0 : 1;
				int half = size / 2 + e;
				double want = cap == Cap.PYRAMID ? (size <= 3 ? 2.0 : 1.25) : 0.6;
				// the apex at baseY + 1 + pitch * half, a finial above it
				double pitch = Math.max(0.5, Math.min(want, (room - 3.0) / Math.max(1, half)));
				Roofs.Field f = Roofs.hip(x0 - e, z0 - e, x1 + e, z1 + e, Roofs.Profile.straight(pitch));
				Roofs.Roof r = Roofs.raster(d, f, baseY + 1 - e, Math.max(0, x0 - e), Math.max(-1, z0 - e), Math.min(d.frontage() - 1, x1 + e),
					Math.min(d.depth() - 1, z1 + e), roof, trim, Habits.Ridge.FULL);
				int top = r.ridgeY;
				if (pitch >= 1.5) {
					// a steep cap is solid under its slopes (a spire's rings would otherwise stand apart)
					solidUnder(d, r, baseY + 1, roof);
				}
				if (cap == Cap.SPIRE && size % 2 == 1) {
					// a needle: two more courses of wall posts on the apex
					Paint needle = trim.has(Form.WALL) ? trim.wall() : d.mat(Role.WALL_ACCENT).wall();
					for (int k = 1; k <= 2 && top + 1 <= maxTop - 1; k++) {
						int yy = top + 1;
						d.inStage(StageKind.ROOF, () -> d.set((int) cx, yy, (int) cz, needle));
						top = yy;
					}
				}
				return finial(d, (int) Math.floor(cx), top + 1, (int) Math.floor(cz), maxTop, cap == Cap.SPIRE);
			}
			case PAGODA -> {
				Roofs.Opts o = Roofs.Opts.of(d).withEaves(Math.min(2, Math.max(1, size / 2)), 1);
				double pitch = Math.max(0.5, Math.min(1.0, (room - 3.0) / Math.max(1, size / 2 + o.eaves())));
				o = o.withPitch(pitch);
				Roofs.Built b = Roofs.build(d, x0, z0, x1, z1, baseY - 1, RoofForm.PAGODA, Direction.Axis.X, o, false, false);
				int top = b.roof().ridgeY;
				return finial(d, (int) Math.floor(cx), top + 1, (int) Math.floor(cz), maxTop, false);
			}
			case CONE -> {
				double r = size / 2.0 + 0.5;
				double pitch = Math.max(0.6, Math.min(1.6, (room - 2.0) / Math.max(1, r)));
				Roofs.Field f = Roofs.cone(cx, cz, r, pitch);
				int e = 1;
				Roofs.Roof rr = Roofs.raster(d, f, baseY, Math.max(0, x0 - e), Math.max(-1, z0 - e), Math.min(d.frontage() - 1, x1 + e),
					Math.min(d.depth() - 1, z1 + e), roof, trim, Habits.Ridge.FULL);
				solidUnder(d, rr, baseY + 1, roof);
				return finial(d, (int) Math.floor(cx), rr.ridgeY + 1, (int) Math.floor(cz), maxTop, false);
			}
			case DOME -> {
				Fam dome = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
				double[] radii = domeRadii(size);
				int top = lathe(d, cx, cz, baseY + 1, radii, dome != null ? dome : Fam.of("minecraft:smooth_sandstone", StageKind.ROOF), maxTop);
				return finial(d, (int) Math.floor(cx), top + 1, (int) Math.floor(cz), maxTop, false);
			}
			case ONION -> {
				// copper on alpine towers, shingles of the roof elsewhere (the wooden onions of the north)
				Fam onion = Tradition.of(d) == Tradition.ALPINE ? Fam.of("minecraft:oxidized_cut_copper", StageKind.ROOF)
					: Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
				if (onion == null) {
					onion = Fam.of("minecraft:oxidized_cut_copper", StageKind.ROOF);
				}
				double[] radii = onionRadii(size);
				int top = lathe(d, cx, cz, baseY + 1, radii, onion, maxTop);
				return finial(d, (int) Math.floor(cx), top + 1, (int) Math.floor(cz), maxTop, true);
			}
			case BATTLEMENT -> {
				battlements(d, x0, z0, x1, z1, baseY + 1);
				return baseY + 2;
			}
		}
		return baseY;
	}

	/**
	 * A square needle spire over the square {@code x0, z0, size} from {@code baseY + 1}: a flared eave ring, then courses
	 * drawing in by a block a side, each a ring of stairs on a core of full blocks stacked {@code steep} high, and a needle
	 * of wall posts with a rod. Never rises past {@code maxTop}. Returns the top.
	 */
	public static int spire(Draft d, int x0, int z0, int size, int baseY, int maxTop) {
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int levels = (size - 1) / 2;
		int needle = 2;
		int room = maxTop - 1 - (baseY + 1);
		int steep = levels == 0 ? 0 : Math.clamp((room - levels - needle) / levels, 0, 3);
		int y = baseY + 1;
		int top;
		try (var st = d.stage(StageKind.ROOF)) {
			top = y - 1;
			for (int k = 1; k <= levels && y <= maxTop - 1; k++) {
				if (k > 1) {
					y++;
				}
				ringStairs(d, x0 + k - 1, z0 + k - 1, size - 2 * (k - 1), y, roof);
				square(d, x0 + k, z0 + k, size - 2 * k, y, roof.full());
				for (int c = 0; c < steep && y < maxTop - 1; c++) {
					y++;
					square(d, x0 + k, z0 + k, size - 2 * k, y, roof.full());
				}
				top = y;
			}
			int cx = x0 + size / 2;
			int cz = z0 + size / 2;
			Paint post = trim.has(Form.WALL) ? trim.wall() : d.mat(Role.WALL_ACCENT).has(Form.WALL) ? d.mat(Role.WALL_ACCENT).wall() : roof.full();
			for (int k = 0; k < needle && top + 1 <= maxTop - 1; k++) {
				top++;
				d.set(cx, top, cz, post);
			}
		}
		return finial(d, x0 + size / 2, top + 1, z0 + size / 2, maxTop, true);
	}

	/** Fills a square of cells at height y (only where the plot allows). */
	static void square(Draft d, int x0, int z0, int size, int y, Paint p) {
		for (int z = z0; z < z0 + size; z++) {
			for (int x = x0; x < x0 + size; x++) {
				if (d.allowed(x, y, z)) {
					d.set(x, y, z, p);
				}
			}
		}
	}

	/** A ring of bottom stairs round a square (its outer cells), each rising toward the square's middle. */
	static void ringStairs(Draft d, int x0, int z0, int size, int y, Mat m) {
		double cx = x0 + (size - 1) / 2.0;
		double cz = z0 + (size - 1) / 2.0;
		for (int[] c : Draft.ring(x0, z0, x0 + size - 1, z0 + size - 1)) {
			if (!d.allowed(c[0], y, c[1])) {
				continue;
			}
			Direction in = Dirs.nearest(cx - c[0], cz - c[1]);
			d.set(c[0], y, c[1], m.stairsOrSlab(in, Half.BOTTOM));
		}
	}

	/** Fills every column a roof covers with full blocks from {@code fromY} up to the roof's underside. */
	static void solidUnder(Draft d, Roofs.Roof r, int fromY, Mat m) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					int under = r.bottomY(x, z);
					for (int y = fromY; y < under; y++) {
						if (d.isEmpty(x, y, z) && d.allowed(x, y, z)) {
							d.set(x, y, z, m.full());
						}
					}
				}
			}
		}
	}

	/** Radii per course of a dome over a square of {@code size}. */
	static double[] domeRadii(int size) {
		double r = size / 2.0;
		if (size <= 3) {
			return new double[]{1.2, 0.6};
		}
		if (size <= 5) {
			return new double[]{r, r - 0.3, r - 1.0, 0.8};
		}
		return new double[]{r, r - 0.2, r - 0.7, r - 1.5, r - 2.6, 0.8};
	}

	/** Radii per course of an onion dome over a drum of {@code size}: swelling out, then drawn up to a point. */
	static double[] onionRadii(int size) {
		double r = size / 2.0;
		if (size <= 3) {
			return new double[]{1.6, 2.1, 1.6, 0.8, 0.4};
		}
		return new double[]{r + 0.4, r + 1.0, r + 0.9, r, r - 1.2, 0.8, 0.4};
	}

	/**
	 * A solid of revolution about (cx, cz) from {@code y0}: one course per radius, full blocks inside, stairs on the rims
	 * where the next course draws in (bottom stairs) or the last swelled out (upside-down stairs), all facing the axis.
	 * Returns the top course's y.
	 */
	public static int lathe(Draft d, double cx, double cz, int y0, double[] radii, Fam fam, int maxTop) {
		int top = y0;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k = 0; k < radii.length; k++) {
				int y = y0 + k;
				if (y > maxTop - 1) {
					break;
				}
				double r = radii[k];
				double above = k + 1 < radii.length ? radii[k + 1] : -1;
				double below = k > 0 ? radii[k - 1] : r;
				List<int[]> disc = Draft.ellipseCells(cx, cz, r - 0.35, r - 0.35, true);
				if (disc.isEmpty()) {
					disc = List.of(new int[]{(int) Math.floor(cx), (int) Math.floor(cz)});
				}
				for (int[] c : disc) {
					int x = c[0];
					int z = c[1];
					if (x < 0 || x >= d.frontage() || z < -1 || z >= d.depth() || z == -1 && y < 3) {
						continue;
					}
					boolean inAbove = above > 0 && Draft.insideEllipse(x, z, cx, cz, above - 0.35, above - 0.35);
					boolean inBelow = Draft.insideEllipse(x, z, cx, cz, below - 0.35, below - 0.35);
					Direction in = Dirs.nearest(cx - x, cz - z);
					Paint p;
					if (r < 0.7) {
						p = fam.has(Form.WALL) ? fam.wall() : fam.full();
					} else if (!inAbove && (k + 1 >= radii.length || above < r)) {
						p = fam.stairs(in, Half.BOTTOM);
					} else if (!inBelow && below < r) {
						p = fam.stairs(in, Half.TOP);
					} else {
						p = fam.full();
					}
					d.set(x, y, z, p);
				}
				top = y;
			}
		}
		return top;
	}

	/** A finial on a cap's apex: a lightning rod on spires and onions, a wall post and lantern elsewhere. */
	public static int finial(Draft d, int x, int y, int z, int maxTop, boolean rod) {
		if (y > maxTop - 1 || x < 0 || x >= d.frontage() || z < 0 || z >= d.depth()) {
			return y - 1;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			if (rod) {
				d.set(x, y, z, rod().defaultBlockState());
				return y;
			}
			Mat accent = d.mat(Role.WALL_ACCENT);
			d.set(x, y, z, accent.has(Form.WALL) ? accent.wall() : Halls.post(d));
			if (y + 1 <= maxTop - 1) {
				d.set(x, y + 1, z, rod().defaultBlockState());
				return y + 1;
			}
			return y;
		}
	}

	/** Whether a block reads as light from afar (by its map colour). */
	static boolean light(Block b) {
		int c = b.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
		int r = (c >> 16) & 0xFF;
		int g = (c >> 8) & 0xFF;
		int bl = c & 0xFF;
		return 0.3 * r + 0.59 * g + 0.11 * bl > 150;
	}

	/** The plain copper lightning rod (a finial), or an end rod where there is none. */
	static Block rod() {
		return States.block("minecraft:lightning_rod", Blocks.END_ROD);
	}

	/** Crenellations: a parapet of the wall with merlons every other block, coped. */
	public static void battlements(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.ROOF)) {
			int k = 0;
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], y, c[1], wall.full());
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				if (corner || (k & 1) == 0) {
					d.set(c[0], y + 1, c[1], accent.has(Form.SLAB) ? accent.full() : wall.full());
				}
				k++;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------- faces

	/**
	 * A clock face on wall {@code s} centred on cell {@code i} at height {@code yc}: a round white dial (three by three,
	 * its corners rounded with stairs) with a dark boss, set in the wall plane. {@code small} gives a single dial block.
	 */
	public static void clock(Draft d, Side s, int i, int yc, boolean small) {
		clock(d, s, i, yc, small, 0);
	}

	/** {@link #clock}, the dial set {@code out} blocks proud of the wall (1: standing out in its own shadow). */
	public static void clock(Draft d, Side s0, int i, int yc, boolean small, int out) {
		Side s = s0.shifted(out);
		// a dial that stands out from its wall: dark on light walls, white on dark ones
		boolean lightWall = light(d.mat(Role.WALL).mainOr(Form.FULL, Blocks.STONE));
		Fam dial = Fam.of(lightWall ? "minecraft:polished_blackstone" : "minecraft:smooth_quartz", StageKind.WALLS);
		Block boss = d.tier() >= 4 || lightWall ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE;
		Direction right = s.along();
		Direction left = right.getOpposite();
		try (var st = d.stage(StageKind.WALLS)) {
			if (small) {
				d.set(s.x(i), yc, s.z(i), boss.defaultBlockState());
				return;
			}
			for (int dy = -1; dy <= 1; dy++) {
				for (int di = -1; di <= 1; di++) {
					int j = i + di;
					if (j < 0 || j > s.last()) {
						continue;
					}
					int x = s.x(j);
					int z = s.z(j);
					int y = yc + dy;
					Paint p;
					if (di == 0 && dy == 0) {
						p = Paint.of(boss.defaultBlockState());
					} else if (di != 0 && dy != 0) {
						// the corners are left to the wall: a round dial of four arms round its boss
						continue;
					} else {
						p = dial.full();
					}
					d.set(x, y, z, p);
				}
			}
		}
	}

	/**
	 * A bell-cote: a little pierced wall standing on a gable's apex (or a wall head) at cells {@code i - 1 .. i + 1} of
	 * wall {@code s}, from {@code y0}: an arched opening with a bell in it, capped by a tiny gable of the roof. Returns
	 * the top y.
	 */
	public static int bellCote(Draft d, Side s, int i, int y0, int maxTop) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat roof = d.mat(Role.ROOF);
		Direction right = s.along();
		Direction left = right.getOpposite();
		int top = Math.min(maxTop - 1, y0 + 4);
		if (top - y0 < 3) {
			return y0 - 1;
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int di = -1; di <= 1; di++) {
				int j = i + di;
				int x = s.x(j);
				int z = s.z(j);
				for (int y = y0; y <= top - 1; y++) {
					boolean opening = di == 0 && y > y0 && y < top - 1;
					d.set(x, y, z, opening ? Paint.of(Blocks.AIR.defaultBlockState()) : (di == 0 && y == top - 1 ? accent.full() : wall.full()));
				}
			}
			// the head of the opening and the coping
			d.set(s.x(i - 1), top - 1, s.z(i - 1), accent.stairsOrSlab(left, Half.BOTTOM));
			d.set(s.x(i + 1), top - 1, s.z(i + 1), accent.stairsOrSlab(right, Half.BOTTOM));
			d.set(s.x(i), top, s.z(i), roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : accent.slab(SlabType.BOTTOM));
		}
		bell(d, s.x(i), top - 2, s.z(i), s.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		return top;
	}

	/**
	 * A bell wall (espadana): the front wall carried up above the roof in a stepped crest {@code 2 * halfW + 1} wide,
	 * centred on cell {@code ci} of wall {@code s}, from {@code y0}, pierced by arched openings with bells (one at the top,
	 * two more lower down on a wide wall), coped with stairs stepping up to the middle and a post with a rod on top.
	 * Returns the top.
	 */
	public static int bellWall(Draft d, Side s, int ci, int y0, int halfW, int maxTop) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		int[] rel = halfW >= 3 ? new int[]{6, 5, 4, 2} : halfW == 2 ? new int[]{5, 4, 2} : new int[]{4, 2};
		int over = y0 + rel[0] + 2 - (maxTop - 1);
		if (over > 0) {
			for (int k = 0; k < rel.length; k++) {
				rel[k] = Math.max(1, rel[k] - over);
			}
		}
		if (rel[0] < 4) {
			return y0 - 1;
		}
		Direction right = s.along();
		Direction left = right.getOpposite();
		try (var st = d.stage(StageKind.WALLS)) {
			for (int k = -halfW; k <= halfW; k++) {
				int i = ci + k;
				if (i < 0 || i > s.last()) {
					continue;
				}
				int hk = rel[Math.abs(k)];
				for (int y = y0; y < y0 + hk; y++) {
					d.set(s.x(i), y, s.z(i), wall.full());
				}
				Paint cap = k == 0 ? accent.slab(SlabType.BOTTOM) : accent.stairsOrSlab(k < 0 ? right : left, Half.BOTTOM);
				d.set(s.x(i), y0 + hk, s.z(i), cap);
			}
			// the openings
			int top = y0 + rel[0];
			d.set(s.x(ci), top - 3, s.z(ci), Blocks.AIR.defaultBlockState());
			d.set(s.x(ci), top - 2, s.z(ci), Blocks.AIR.defaultBlockState());
			if (halfW >= 3 && rel[2] >= 4) {
				for (int k : new int[]{-2, 2}) {
					int i = ci + k;
					if (i >= 0 && i <= s.last()) {
						d.set(s.x(i), y0 + rel[2] - 3, s.z(i), Blocks.AIR.defaultBlockState());
						d.set(s.x(i), y0 + rel[2] - 2, s.z(i), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		Direction.Axis swing = s.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		int top = y0 + rel[0];
		bell(d, s.x(ci), top - 2, s.z(ci), swing);
		if (halfW >= 3 && rel[2] >= 4) {
			for (int k : new int[]{-2, 2}) {
				int i = ci + k;
				if (i >= 0 && i <= s.last()) {
					bell(d, s.x(i), y0 + rel[2] - 2, s.z(i), swing);
				}
			}
		}
		return finial(d, s.x(ci), top + 1, s.z(ci), maxTop, false);
	}

	/** A viewpoint (and guard post, for watch towers) on a platform. */
	public static void lookout(Draft d, int x, int y, int z, Direction facing, boolean guard) {
		d.poi(PoiType.VIEWPOINT, x, y, z, facing);
		if (guard) {
			d.poi(PoiType.GUARD_POST, x, y, z, facing);
		}
	}

	/** A flag pole with the town's banner: a fence post on a block, the banner on its side. */
	public static void flagpole(Draft d, int x, int y, int z, int height, Direction facing, String colour, List<String[]> patterns) {
		Mat post = d.mat(Role.FENCE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, Halls.post(d));
			}
		}
		Signs.wallBanner(d, x + Dirs.dx(facing), y + height - 2, z + Dirs.dz(facing), facing, colour, patterns);
	}

	/** A one-block marker of the roof material set at a cell (a ridge ornament, a cap stone). */
	static void stone(Draft d, int x, int y, int z, Paint p) {
		d.inStage(StageKind.ROOF, () -> d.set(x, y, z, p));
	}

	/** States helper for callers that place a lantern on a post. */
	static BlockState lantern(Draft d, boolean hanging) {
		return States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), hanging);
	}
}
