package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;

/**
 * Laid floors for squares, courts and paths, in a pattern a culture would lay: square rings or circles round a
 * medallion, a grid of bands with inlays at the crossings, a checker of two stones, rays out from the middle, staggered
 * setts, plain setts with mended patches, or boards with log sleepers in timber cultures. The main stone is the
 * style's paving (mixed in patches); the bands are a contrasting stone of its palette; the inlays its accent.
 */
public final class Paving {
	private Paving() {
	}

	public enum Pattern {
		RINGS, CIRCLES, CHECKER, GRID, RADIAL, STAGGERED, PLAIN, BOARDS
	}

	/** The pattern a culture lays its squares in (two choices for most, by seed). */
	public static Pattern pattern(Draft d) {
		if (Manner.wood(d.mat(Role.PAVING))) {
			return Pattern.BOARDS;
		}
		boolean a = d.chance(0.5);
		return switch (Manner.culture(d)) {
			case TEMPERATE -> a ? Pattern.RINGS : Pattern.GRID;
			case WISTERIA -> a ? Pattern.CHECKER : Pattern.CIRCLES;
			case MEDITERRANEAN -> a ? Pattern.STAGGERED : Pattern.RADIAL;
			case DESERT -> a ? Pattern.GRID : Pattern.RADIAL;
			case KARST -> a ? Pattern.GRID : Pattern.CHECKER;
			case MESA -> a ? Pattern.RINGS : Pattern.STAGGERED;
			case STEPPE -> Pattern.CIRCLES;
			case MARITIME -> a ? Pattern.RADIAL : Pattern.CIRCLES;
			case ALPINE, MOORLAND, NORTHERN, TUNDRA -> a ? Pattern.PLAIN : Pattern.RINGS;
			default -> Pattern.RINGS;
		};
	}

	/** The paving's contrasting band stone: the first of masonry, foundation and accent whose look differs. */
	public static Mat band(Draft d) {
		Mat paving = d.mat(Role.PAVING);
		String main = Manner.path(paving, Form.FULL);
		for (Mat m : new Mat[]{Manner.masonry(d), d.mat(Role.WALL_ACCENT), d.mat(Role.FOUNDATION), d.mat(Role.PLINTH)}) {
			if (!Manner.wood(m) && !Manner.path(m, Form.FULL).equals(main)) {
				return m;
			}
		}
		return Manner.masonry(d);
	}

	/** The inlay stone for medallions and crossings: the style's accent if it is a stone, else the band. */
	public static Mat inlay(Draft d) {
		Mat a = d.mat(Role.ACCENT);
		return Manner.wood(a) || Manner.path(a, Form.FULL).contains("moss") || Manner.path(a, Form.FULL).contains("ice") ? band(d) : a;
	}

	/**
	 * A paint laying the pattern round a centre (half-block centres allowed), bands every {@code period} blocks, with a
	 * three-by-three medallion in the middle when {@code medallion}.
	 */
	public static Paint paint(Draft d, Pattern p, double cx, double cz, int period, boolean medallion) {
		Mat main = d.mat(Role.PAVING);
		Mat band = band(d);
		Mat inlay = inlay(d);
		Mat log = d.mat(Role.FRAME);
		long seed = d.seed() ^ 0xA7E5;
		return (x, y, z) -> {
			double dx = x - cx;
			double dz = z - cz;
			int ix = (int) Math.round(Math.abs(dx));
			int iz = (int) Math.round(Math.abs(dz));
			if (medallion && ix <= 1 && iz <= 1 && p != Pattern.BOARDS) {
				return (ix == 0 && iz == 0 ? inlay : ix + iz == 1 ? band : main).full().at(x, y, z);
			}
			Mat m = switch (p) {
				case RINGS -> Math.max(ix, iz) % period == 0 ? band : main;
				case CIRCLES -> (int) Math.round(Math.hypot(dx, dz)) % period == 0 ? band : main;
				case CHECKER -> ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0 ? band : main;
				case GRID -> {
					boolean bx = ix % period == 0;
					boolean bz = iz % period == 0;
					yield bx && bz ? inlay : bx || bz ? band : main;
				}
				case RADIAL -> ix == 0 || iz == 0 || ix == iz ? band : main;
				case STAGGERED -> ((Math.floorDiv(x + ((z & 1) == 0 ? 0 : 1), 2) + z) & 1) == 0 && Noise.unit(seed, x, 0, z) < 0.8 ? band : main;
				case BOARDS -> main;
				default -> Noise.value(seed, x / 2.5, 0, z / 2.5) < 0.22 ? band : main;
			};
			if (p == Pattern.BOARDS) {
				// boards with a log sleeper every few blocks
				if (ix % period == 0 && log.has(Form.LOG)) {
					return log.strippedLog(Direction.Axis.Z).orElse(log.log(Direction.Axis.Z)).at(x, y, z);
				}
				return main.full().at(x, y, z);
			}
			return m.full().at(x, y, z);
		};
	}

	/** Lays a paint at y - 1 over a rectangle (inclusive), where nothing has been laid. */
	public static void lay(Draft d, int x0, int z0, int x1, int z1, int y, Paint p) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					d.set(x, y - 1, z, p);
				}
			}
		}
	}

	/** A kerb of the band stone round a rectangle at y - 1. */
	public static void kerb(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat band = band(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], y - 1, c[1], band.full());
			}
		}
	}
}
