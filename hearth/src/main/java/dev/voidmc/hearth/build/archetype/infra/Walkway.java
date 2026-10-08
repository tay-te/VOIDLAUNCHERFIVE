package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.kit.infra.Demo;
import dev.voidmc.hearth.build.kit.infra.Lamps;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.LineMat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:infra/walkway}: a covered gallery bridging a street between the fronts of two tall houses, eight blocks
 * over the street (PLANNING.md §6, "Walkways"), its two doors the houses' own at {@code walkway_y}. It takes no ground:
 * it rests on the two facades.
 *
 * <ul>
 *   <li>Stone cultures (mediterranean, desert, mesa, moorland, maritime): an enclosed passage of the wall stone on a
 *   flying arch, small windows each side, a flat roof behind a coped parapet: the Bridge of Sighs.</li>
 *   <li>Timber cultures: a gallery on beams with knee braces against both facades, a boarded rail, a window band, posts
 *   at both ends and the middle, a gable roof of the culture's covering; open and roofed in the karst and wisteria
 *   manner, an open plank walk with rope rails on the bayou and in the tropics.</li>
 * </ul>
 *
 * A lantern hangs under it over the street, another inside. Posts (stone piers under the stone passage) carry it to
 * the ground at both ends and at least every fourth step along both edges, so it never hangs on the facades alone.
 *
 * <p>Params: {@code points} (two ends just outside the two facades at the deck's walking level), {@code width} (3),
 * {@code deck_y} (walking level on the deck), {@code street_y} (the street below), {@code posts} (default true: false
 * only where the planner knows both facades stand built to carry it).
 */
public final class Walkway implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/walkway"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		3, 13, 3, 13, 9, 14, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 0, 1.0);

	enum Make {
		STONE, TIMBER, PAVILION, PLANK
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Lay lay = new Lay(d);
		int[] pts = Demo.points(d);
		boolean demo = pts == null;
		int street = d.param("street_y", lay.floorY());
		int deck;
		int width;
		if (demo) {
			width = Demo.odd(Math.min(3, d.frontage()));
			deck = Math.min(street + 8, lay.floorY() + d.maxHeight() - 5);
			int cx = d.frontage() / 2;
			pts = new int[]{lay.wx(cx, d.depth() - 1), deck, lay.wz(cx, d.depth() - 1), lay.wx(cx, 0), deck, lay.wz(cx, 0)};
		} else {
			width = Demo.odd(d.param("width", 3));
			deck = d.param("deck_y", pts[1]);
		}
		Course c = new Course(pts, width);
		Culture culture = d.style().culture();
		Make make = make(culture);
		int n = c.steps();
		// the ends: the doors the houses open onto it (in a test, the street under its front end: with no house at either
		// end the gallery is railed across them, see below)
		if (demo) {
			lay.entrance(c.dx(n - 1), lay.surface(c.dx(n - 1), c.dz(n - 1)), c.dz(n - 1));
		} else {
			lay.entrance(c.dx(n - 1), deck, c.dz(n - 1));
			lay.entrance(c.dx(0), deck, c.dz(0));
		}
		LineMat wood = LineMat.of(lay, Role.STILT);
		LineMat beam = LineMat.of(lay, Role.BEAM);
		LineMat frame = LineMat.of(lay, Role.FRAME);
		LineMat stone = LineMat.of(lay, Role.FOUNDATION);
		LineMat wall = LineMat.of(lay, Role.WALL);
		LineMat accent = LineMat.of(lay, Role.WALL_ACCENT);
		LineMat plinth = LineMat.of(lay, Role.PLINTH);
		boolean thatch = d.style().habits().roof().thatch();
		LineMat roof = LineMat.of(lay, thatch ? Role.THATCH : Role.ROOF);
		LineMat trim = LineMat.of(lay, thatch ? Role.THATCH : Role.ROOF_TRIM);
		LineMat rail = LineMat.of(lay, Role.RAILING);
		LineMat glass = LineMat.of(lay, Role.GLASS);
		int top = lay.topY();
		int half = n / 2;
		for (Course.Col col : c.cols()) {
			if (col.beyond() || !lay.allowed(col.x(), deck, col.z())) {
				continue;
			}
			int k = col.k();
			boolean end = k == 0 || k == n - 1;
			boolean edge = col.ring() == 0;
			Direction t = c.cardinal(k);
			Direction out = col.off() >= 0 ? t.getClockWise() : t.getCounterClockWise();
			Direction in = out.getOpposite();
			double u = n <= 1 ? 0 : (k - (n - 1) / 2.0) / ((n - 1) / 2.0);
			int x = col.x();
			int z = col.z();
			// under the deck: a flying arch (stone) or beams on knee braces (timber)
			if (make == Make.STONE) {
				int rise = Math.min(3, Math.max(1, n / 3));
				int bottom = deck - 2 - (int) Math.round(rise * u * u);
				try (var s = d.stage(StageKind.WALLS)) {
					for (int y = bottom; y <= deck - 2; y++) {
						lay.set(x, y, z, (edge ? accent : stone).full());
					}
					if (Math.abs(u) > 0.2 && bottom - 1 > street + 2) {
						Direction toward = k < half ? t.getOpposite() : t;
						lay.set(x, bottom - 1, z, (edge ? accent : stone).stairsOrSlab(toward, Half.TOP));
					}
				}
			} else {
				try (var s = d.stage(StageKind.FRAME)) {
					if (edge) {
						lay.set(x, deck - 2, z, beam.log(t.getAxis()));
						if (end && deck - 3 > street + 2) {
							Direction toward = k == 0 ? t.getOpposite() : t;
							lay.set(x, deck - 3, z, beam.stairsOrSlab(toward, Half.TOP));
						}
					} else if (k % 2 == 0) {
						lay.set(x, deck - 2, z, beam.log(t.getClockWise().getAxis()));
					}
				}
			}
			// the deck
			try (var s = d.stage(StageKind.FLOORS)) {
				lay.set(x, deck - 1, z, make == Make.STONE ? (edge ? plinth : LineMat.of(lay, Role.PAVING)).full() : wood.full());
			}
			if (!edge) {
				if (k == half) {
					// one under it over the street (from the arch, a beam or the deck), one inside under the roof
					int ceiling = make == Make.STONE ? deck - 2 - (int) Math.round(Math.min(3, Math.max(1, n / 3)) * u * u)
						: k % 2 == 0 ? deck - 2 : deck - 1;
					if (ceiling - 1 > street + 2) {
						Lamps.hanging(lay, x, ceiling, z);
					}
					if ((make == Make.STONE || make == Make.TIMBER) && deck + 3 <= top) {
						Lamps.hanging(lay, x, deck + 3, z);
					}
				}
				continue;
			}
			// the sides
			boolean post = end || k == half;
			try (var s = d.stage(StageKind.WALLS)) {
				switch (make) {
					case STONE -> {
						for (int y = deck; y <= Math.min(deck + 2, top); y++) {
							boolean window = y == deck + 1 && !end && Math.floorMod(k, 3) == 1;
							lay.set(x, y, z, window ? glass.full() : (end ? accent : wall).full());
						}
					}
					case TIMBER -> {
						if (post) {
							for (int y = deck; y <= Math.min(deck + 2, top); y++) {
								lay.set(x, y, z, frame.log(Direction.Axis.Y));
							}
						} else {
							// the infill in the culture's manner: plaster in a timber frame, logs laid along, boards
							var kind = d.style().habits().walls().kind();
							if (kind == dev.voidmc.hearth.build.style.Habits.WallKind.LOG) {
								lay.set(x, deck, z, frame.log(t.getAxis()));
							} else if (kind == dev.voidmc.hearth.build.style.Habits.WallKind.TIMBER_FRAME) {
								lay.set(x, deck, z, wall.full());
							} else {
								lay.set(x, deck, z, wood.full());
							}
							lay.set(x, deck + 1, z, glass.full());
							if (deck + 2 <= top) {
								lay.set(x, deck + 2, z, beam.log(t.getAxis()));
							}
						}
					}
					case PAVILION -> {
						if (post) {
							for (int y = deck; y <= Math.min(deck + 2, top); y++) {
								lay.set(x, y, z, frame.log(Direction.Axis.Y));
							}
						} else {
							lay.set(x, deck, z, rail.post());
							if (deck + 2 <= top) {
								lay.set(x, deck + 2, z, beam.log(t.getAxis()));
							}
						}
					}
					default -> lay.set(x, deck, z, rail.post());
				}
			}
			// the roof
			if (make == Make.PLANK) {
				continue;
			}
			try (var s = d.stage(StageKind.ROOF)) {
				if (make == Make.STONE) {
					if (deck + 3 <= top) {
						lay.set(x, deck + 3, z, plinth.full());
					}
					if (deck + 4 <= top) {
						lay.set(x, deck + 4, z, plinth.has(Form.SLAB) ? plinth.slab(SlabType.BOTTOM) : plinth.full());
					}
				} else if (deck + 3 <= top) {
					lay.set(x, deck + 3, z, roof.stairs(in, Half.BOTTOM));
				}
			}
		}
		// the ridge (timber) or the flat roof (stone) over the middle line
		for (Course.Col col : c.cols()) {
			if (col.beyond() || col.ring() == 0 || !lay.allowed(col.x(), deck, col.z())) {
				continue;
			}
			try (var s = d.stage(StageKind.ROOF)) {
				if (make == Make.STONE && deck + 3 <= top) {
					lay.set(col.x(), deck + 3, col.z(), plinth.has(Form.SLAB) ? plinth.slab(SlabType.TOP) : plinth.full());
				} else if ((make == Make.TIMBER || make == Make.PAVILION) && deck + 4 <= top) {
					lay.set(col.x(), deck + 4, col.z(), trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : trim.full());
					lay.set(col.x(), deck + 3, col.z(), beam.log(c.cardinal(col.k()).getAxis()));
				}
			}
		}
		// posts to the ground at both ends and at least every fourth step along both edges (art review 2 item 5): the
		// facades carry its ends, but a gallery whose house is not yet built, or stands back from it, never hangs in the
		// air on its own. Stone piers under the stone passage, the frame's posts under timber, piles under the planks.
		boolean posts = d.param("posts", true);
		for (Course.Col col : c.cols()) {
			int k = col.k();
			boolean carried = posts && (k == 0 || k == n - 1 || k % 4 == 0 || k == half && n < 8);
			if (col.beyond() || col.ring() != 0 || !carried || !lay.allowed(col.x(), deck, col.z())) {
				continue;
			}
			try (var s = d.stage(StageKind.FRAME)) {
				for (int y = lay.ground(col.x(), col.z()) - 1; y < deck - 1; y++) {
					if (lay.absent(col.x(), y, col.z()) || lay.empty(col.x(), y, col.z())) {
						lay.set(col.x(), y, col.z(), make == Make.STONE ? stone.full() : make == Make.PLANK ? wood.log(Direction.Axis.Y)
							: frame.log(Direction.Axis.Y));
					}
				}
			}
		}
		// standing alone (tests, previews): no house at either end, so the gallery is railed across its ends
		if (demo) {
			LineMat bar = LineMat.of(lay, Role.RAILING);
			for (Course.Col col : c.cols()) {
				int k = col.k();
				if (!col.beyond() && col.ring() != 0 && (k == 0 || k == n - 1) && lay.allowed(col.x(), deck, col.z()) && lay.empty(col.x(), deck, col.z())) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						lay.set(col.x(), deck, col.z(), bar.post());
					}
				}
			}
		}
		int mid = c.steps() / 2;
		lay.poi(PoiType.VIEWPOINT, c.dx(mid), deck, c.dz(mid), c.cardinal(mid).getClockWise());
	}

	static Make make(Culture c) {
		if (c == null) {
			return Make.TIMBER;
		}
		return switch (c) {
			case MEDITERRANEAN, DESERT, MESA, MOORLAND, MARITIME -> Make.STONE;
			case KARST, WISTERIA -> Make.PAVILION;
			case BAYOU, TROPICAL -> Make.PLANK;
			default -> Make.TIMBER;
		};
	}
}
