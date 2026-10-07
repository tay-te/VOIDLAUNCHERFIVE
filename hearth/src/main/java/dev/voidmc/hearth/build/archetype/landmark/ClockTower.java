package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.kit.learning.Instruments;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/clock_tower}: the town's clock on its square, raised in the culture's own manner (one Big Ben
 * everywhere read as a theme park):
 * <ul>
 *   <li><b>The town hall tower</b> (temperate, wisteria, alpine: Bohemia and its mountains): a square shaft in the white
 *   render between corner stones over a stone plinth, string courses at its stages, a clock of one gilded dial on each
 *   face under a gallery corbelled out on the belfry's floor (a walk all round behind a balustrade, the town spread
 *   below), paired lights in the belfry, and a dark onion with its lantern: slate in Bohemia, copper or shingle in the
 *   mountains.</li>
 *   <li><b>The campanile</b> (mediterranean, mesa): a slender brick or rendered shaft between clasping piers, Lombard
 *   bands under its courses, a small dial high on each face under the open belfry, a low tiled pyramid (a little dome
 *   in the mesa's missions).</li>
 *   <li><b>The minaret</b> (desert, steppe: no clock tower; the timekeeper keeps the hours from a minaret): a square base
 *   with the keeper's room, a round shaft banded in courses, a corbelled balcony with its balustrade, an arcaded lantern
 *   at the head where the hours are called, a small lime dome and a gilded finial; a sundial in the forecourt.</li>
 *   <li><b>The drum tower</b> (karst): a masonry podium with an arched gate, a timber pavilion on it under two curved
 *   roofs with lifted corners, a walk round the pavilion behind a parapet, the great drum and a bell inside.</li>
 *   <li><b>The civic clock tower</b> (the north, moorland, maritime, bayou, tropics): a plain shaft on a chamfered
 *   plinth, a clock stage corbelled out wider than itself with one dial on each face and pinnacles at its corners, the
 *   bell chamber behind a walkable gallery, a crown in the culture's manner (spires, stave caps, cupolas, pyramids).</li>
 * </ul>
 * Inside, a spiral stair climbs past the clock room (the works, the keeper's lectern) to the bells or the lantern.
 *
 * <p>Params (all optional): {@code manner} ({@code town_hall}, {@code campanile}, {@code minaret}, {@code drum},
 * {@code civic}), {@code crown} (a {@link Crowns.Kind} name, for the civic and campanile manners), {@code variant}.
 */
public final class ClockTower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/clock_tower"), ArchetypeCategory.LANDMARK, Set.of(), 0,
		7, 11, 7, 11, 28, 44, SettlementTier.CITY, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL, ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	/** The manners a clock tower is raised in. */
	public enum Manner {
		TOWN_HALL, CAMPANILE, MINARET, DRUM, CIVIC
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int peak = switch (manner(d)) {
			case TOWN_HALL -> townHall(d);
			case CAMPANILE -> campanile(d);
			case MINARET -> minaret(d);
			case DRUM -> drumTower(d);
			case CIVIC -> civic(d);
		};
		Groundwork.clear(d, 0, 0, d.frontage() - 1, d.depth() - 1, Math.min(d.maxHeight() - 1, peak + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The manner, from the param or the culture. */
	static Manner manner(Draft d) {
		String p = d.param("manner", "");
		if (!p.isEmpty()) {
			try {
				return Manner.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through to the culture's
			}
		}
		Culture c = d.style().culture();
		if (c == null) {
			return d.habits().roof().weight(Habits.RoofForm.FLAT) > 2 ? Manner.MINARET : Manner.CIVIC;
		}
		return switch (c) {
			case TEMPERATE, WISTERIA, ALPINE -> Manner.TOWN_HALL;
			case MEDITERRANEAN, MESA -> Manner.CAMPANILE;
			case DESERT, STEPPE -> Manner.MINARET;
			case KARST -> Manner.DRUM;
			default -> Manner.CIVIC;
		};
	}

	// ================================================================================================ the dials

	/**
	 * A clock face of one block set in the wall of face {@code f} at cell i, height y: a dark dial flecked with gold on a
	 * light wall, a white one on a dark wall, in a round frame of four stones of the dressing. At a tower's scale this is
	 * the dial a town reads from its square, not a Big Ben's.
	 */
	static void dial(Draft d, Side f, int i, int y, Stone dress) {
		Draft.Cell there = d.cell(f.x(i), y, f.z(i));
		Block wall = there != null && !there.state().isAir() ? there.state().getBlock() : d.mat(Role.WALL).mainOr(Form.FULL, Blocks.STONE);
		BlockState face = light(wall) ? States.block("minecraft:gilded_blackstone", Blocks.POLISHED_BLACKSTONE).defaultBlockState()
			: Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				int j = i + o[0];
				if (j > 0 && j < f.last()) {
					d.set(f.x(j), y + o[1], f.z(j), dress.full());
				}
			}
			d.set(f.x(i), y, f.z(i), face);
		}
	}

	/** Whether a block reads as light from afar (by its map colour). */
	static boolean light(Block b) {
		int c = b.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
		return 0.3 * ((c >> 16) & 0xFF) + 0.59 * ((c >> 8) & 0xFF) + 0.11 * (c & 0xFF) > 140;
	}

	// ================================================================================================ the town hall tower

	/**
	 * The Bohemian town hall tower: rendered shaft, corner stones, courses, a dial on each face, the gallery on the
	 * belfry's floor, paired lights, a dark onion. Returns the top.
	 */
	private static int townHall(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		int size = avail >= 9 ? 7 : 5;
		int x0 = (w - size) / 2;
		int z0 = Math.max(1, (dp - size + 1) / 2);
		// the white render of the town's walls whatever block lays it (a log or board wall: the masonry instead), one
		// material, never mottled
		Stone body = dev.voidmc.hearth.build.kit.landmark.Wooden.timberCulture(d) ? Stone.panel(d) : Stone.of(d, Role.WALL).crisp();
		Block wallBlock = body.main(Form.FULL);
		if (wallBlock == null || !light(wallBlock)) {
			// a stone-walled variant still renders its town hall's tower in lime, as Bohemia's are
			body = Stone.fixed(d, StageKind.WALLS, "minecraft:calcite");
		}
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(x0, z0, size, body, dress, base, false);
		d.body(t.x0, t.z0, t.x1, t.z1);
		int top = d.maxHeight() - 1;
		int height = Math.max(26, (int) Math.round(top * (0.9 + 0.1 * d.random().nextDouble())));
		int yc = height - Crowns.rows(Crowns.Kind.ONION, size, height);   // the belfry's cornice
		int yb = yc - 6;                                                  // the belfry's floor and the gallery's deck
		int doorTop = 6;
		if (yb < doorTop + 9) {
			yb = doorTop + 9;
			yc = yb + 6;
		}
		d.eave(yc);
		Side fr = t.face(Dirs.FRONT);
		int door = fr.middle();

		Grounds.footings(d, t.x0, t.z0, t.x1, t.z1, -1);
		t.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		plain(d, t, 2, yc - 1, body, dress);
		t.course(d, doorTop, false);
		int clockY = yb - 3;
		// the shaft's render runs unbroken between the course over the door and the clock's (a course at every stage grids
		// the face into stone frames round white panels); a tall shaft only takes its lights at two heights
		int mid = clockY - 3 - doorTop >= 8 ? doorTop + (clockY - 3 - doorTop) / 2 : -1;
		t.course(d, clockY - 2, false);
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : t.faces()) {
			if (f.out() != Dirs.FRONT) {
				Details.slit(d, f, f.middle(), 3, 2, dress, glass);
			}
			int lo = doorTop + 1;
			int hi = (mid > 0 ? mid : clockY - 2) - 1;
			if (hi - lo >= 2) {
				Details.slit(d, f, f.middle(), lo + (hi - lo - 1) / 2, 2, dress, glass);
			}
			if (mid > 0 && clockY - 3 - (mid + 1) >= 2) {
				Details.slit(d, f, f.middle(), mid + 1 + (clockY - 3 - mid - 2) / 2, 2, dress, glass);
			}
			dial(d, f, f.middle(), clockY, dress);
		}
		// the gallery on the belfry's floor: a deck a block proud of the shaft on two courses of corbels, a balustrade
		gallery(d, t, yb, dress);
		// the belfry: paired lights open onto the gallery, the bells under the cornice
		t.openings(d, yb + 1, yc - yb - 2, Tower.Opening.BIFORA, true, null);
		t.band(d, yc - 1);
		t.cornice(d, yc);
		int peak = Crowns.crown(d, Crowns.Kind.ONION, t.x0, t.z0, t.x1, t.z1, yc + 1, top, Crowns.Mats.coppered(d, dress, 0.3));

		Grounds.door(d, fr, door, 0, dress);
		t.floor(d, -1, base.full());
		t.floor(d, clockY - 2, d.mat(Role.FLOOR).full());
		t.floor(d, yb, dress.full());
		t.stair(d, 0, yb + 1, Stone.dressing(d), dress.full(), 1);
		t.hollow(d, 0, yb - 1);
		t.hollow(d, yb + 1, yc - 1);
		t.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		keeper(d, t, clockY - 1);
		d.poi(PoiType.VIEWPOINT, t.x0 - 1, yb + 1, t.cz, Dirs.LEFT);
		d.poi(PoiType.VIEWPOINT, t.x1 + 1, yb + 1, t.cz, Dirs.RIGHT);
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);
		Grounds.forecourt(d, t.outer(), fr.x(door), 0, fr.outZ(door, 1));
		return peak;
	}

	/**
	 * A square tower's walls of rows y0..y1 in its walling between whole corner stones of the dressing (no toothed quoins:
	 * on render they read as a checkerboard).
	 */
	static void plain(Draft d, Tower t, int y0, int y1, Stone body, Stone dress) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				for (Side f : t.faces()) {
					for (int i = 0; i < f.length(); i++) {
						d.set(f.x(i), y, f.z(i), f.isCorner(i) ? dress.full() : body.full());
					}
				}
			}
		}
	}

	/**
	 * A gallery round a square tower on row y (its deck): the ring a block outside the walls laid solid on two courses of
	 * upside-down stairs, a balustrade of wall posts on its edge with whole stones at the corners.
	 */
	static void gallery(Draft d, Tower t, int y, Stone dress) {
		int gx0 = t.x0 - 1;
		int gz0 = t.z0 - 1;
		int gx1 = t.x1 + 1;
		int gz1 = t.z1 + 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(gx0, gz0, gx1, gz1)) {
				if (!d.allowed(c[0], y + 1, c[1])) {
					continue;
				}
				boolean corner = Crowns.isCorner(c, gx0, gz0, gx1, gz1);
				d.set(c[0], y, c[1], dress.full());
				d.set(c[0], y + 1, c[1], corner ? dress.full() : dress.wallOrFull());
				if (corner && dress.has(Form.SLAB)) {
					d.set(c[0], y + 2, c[1], dress.slab(SlabType.BOTTOM));
				}
				Direction in = inward(gx0, gz0, gx1, gz1, c[0], c[1]);
				if (in != null && d.isEmpty(c[0], y - 1, c[1])) {
					d.set(c[0], y - 1, c[1], dress.stairsOrSlab(in, Half.TOP));
				}
			}
		}
	}

	/** The keeper's corner of the clock room: the works (a grindstone for the wheels), a lectern, a chest. */
	private static void keeper(Draft d, Tower t, int y) {
		int[][] corners = {{t.x0 + 1, t.z1 - 1}, {t.x1 - 1, t.z1 - 1}};
		// only where the spiral leaves the corner free (a seven-wide tower: the corners lie outside its ring)
		if (t.size < 7) {
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(corners[0][0], y, corners[0][1], States.facing(Blocks.LECTERN, Dirs.FRONT));
			d.set(corners[1][0], y, corners[1][1], States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.LEFT));
		}
		d.poi(PoiType.LECTERN, corners[0][0], y, corners[0][1], Dirs.FRONT);
	}

	// ================================================================================================ the campanile

	/**
	 * The campanile: a slender shaft between clasping piers, Lombard bands under its courses, a dial on each face under the
	 * open belfry, a low pyramid on brackets (a little dome in the mesa). Returns the top.
	 */
	private static int campanile(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		int core = avail >= 11 ? 7 : 5;
		int outer = core + 2;
		int ox = (w - outer) / 2 + 1;
		int oz = Math.max(1, (dp - outer + 1) / 2) + 1;
		Stone dress = Stone.dressing(d);
		Stone body = brick(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(ox, oz, core, body, dress, base, true);
		d.body(t.x0, t.z0, t.x1, t.z1);
		Crowns.Kind crown = crown(d, d.style().culture() == Culture.MESA ? Crowns.Kind.DOME : Crowns.Kind.PYRAMID);
		int top = d.maxHeight() - 1;
		int height = Math.max(26, (int) Math.round(top * (0.88 + 0.12 * d.random().nextDouble())));
		int yc = Math.max(16, height - Crowns.rows(crown, outer, height));
		int yb = yc - 6;
		d.eave(yc);
		Side fr = t.face(Dirs.FRONT);
		int door = fr.middle();

		Grounds.footings(d, t.outer()[0], t.outer()[1], t.outer()[2], t.outer()[3], -1);
		t.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		t.shaft(d, 2, yc - 1);
		t.ring(d, yb + 1, yc - 1, dress);
		// stages of seven or so rows, each course over its band of little arches
		Paint glass = d.mat(Role.GLASS).pane();
		int doorTop = 7;
		int clockY = yb - 3;
		t.course(d, doorTop, true);
		t.band(d, doorTop - 1);
		int stage = doorTop;
		while (clockY - 2 - stage >= 12) {
			stage += Math.max(6, (clockY - 2 - doorTop) / 3);
			if (clockY - 2 - stage < 5) {
				break;
			}
			t.course(d, stage, true);
			t.band(d, stage - 1);
		}
		t.course(d, clockY - 2, true);
		t.band(d, clockY - 3);
		t.course(d, yb, true);
		t.band(d, yb - 1);
		for (Side f : t.faces()) {
			Details.slit(d, f, f.middle(), f.out() == Dirs.FRONT ? 5 : 3, 2, dress, glass);
			for (int y = doorTop + 2; y + 2 < clockY - 3; y += 6) {
				Details.slit(d, f, f.middle(), y, 2, dress, glass);
			}
			dial(d, f, f.middle(), clockY, dress);
		}
		// the belfry: open arches on every face over a balustrade
		Tower.Opening belfry = core >= 7 ? Tower.Opening.TRIFORA : Tower.Opening.ARCH;
		t.openings(d, yb + 1, yc - yb - 2, belfry, true, null);
		if (belfry == Tower.Opening.ARCH) {
			for (Side f : t.faces()) {
				for (int i = f.middle() - 1; i <= f.middle() + 1; i++) {
					d.set(f.x(i), yb + 1, f.z(i), dress.wallOrFull());
				}
			}
		}
		t.band(d, yc - 1);
		t.cornice(d, yc);
		int[] cs = t.outer();
		// a mission's little dome in lime plaster (Adobe.domeSheet), its pyramid in the roof's tiles
		Crowns.Mats mats = Crowns.Mats.of(d, dress);
		if (crown == Crowns.Kind.DOME || crown == Crowns.Kind.CUPOLA) {
			Fam lime = Adobe.domeSheet(d);
			Stone sheet = lime != null && lime.has(Form.STAIRS) ? Stone.fixed(d, StageKind.ROOF, key(lime.blockOr(Form.FULL))) : null;
			mats = new Crowns.Mats(mats.roof(), mats.trim(), dress, mats.frame(), sheet);
		}
		int peak = Crowns.crown(d, crown, cs[0], cs[1], cs[2], cs[3], yc + 1, top, mats);

		Grounds.door(d, fr, door, 0, dress);
		t.floor(d, -1, base.full());
		t.floor(d, yb, dress.full());
		t.stair(d, 0, yb + 1, Stone.dressing(d), dress.full(), 1);
		t.hollow(d, 0, yc - 1);
		t.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		d.poi(PoiType.VIEWPOINT, t.x0 + 1, yb + 1, t.z0 + 1, Dirs.FRONT);
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);
		Grounds.forecourt(d, t.outer(), fr.x(door), 0, fr.outZ(door, 1));
		return peak;
	}

	/** A campanile's walling: the roof's brick where the town roofs in brick or tile, else the style's masonry. */
	private static Stone brick(Draft d) {
		Block roof = d.mat(Role.ROOF).main(Form.FULL);
		if (roof != null && Stone.stony(d, Role.ROOF) && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(roof).getPath().contains("brick")
			&& d.mat(Role.ROOF).has(Form.STAIRS)) {
			return Stone.of(d, Role.ROOF).crisp();
		}
		return Stone.panel(d);
	}

	// ================================================================================================ the minaret

	/**
	 * The minaret: a square base with the keeper's room, a round shaft ringed with courses, a corbelled balcony with its
	 * balustrade two thirds up, an arcaded lantern at the head where the stair comes out (the hours are called from it), a
	 * small lime dome and a gilded finial; a sundial before the door. Returns the top.
	 */
	private static int minaret(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		int b = avail >= 11 ? 9 : 7;
		int rs = b >= 9 ? 3 : 2;
		int cut = rs >= 3 ? 2 : 1;
		int bx0 = (w - b) / 2;
		int bz0 = Math.max(2, (dp - b + 1) / 2);
		if (bz0 + b > dp) {
			bz0 = dp - b;
		}
		Stone body = Stone.panel(d);
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(bx0, bz0, b, body, dress, base, false);
		d.body(t.x0, t.z0, t.x1, t.z1);
		int cx = t.cx;
		int cz = t.cz;
		Set<Long> shaft = Shapes.oct(cx, cz, rs, cut);
		List<Set<Long>> dome = Shapes.domeRows(cx, cz, rs);
		int top = d.maxHeight() - 1;
		int height = Math.max(26, (int) Math.round(top * (0.9 + 0.1 * d.random().nextDouble())));
		int hb = 6;                                          // the base's roof row
		int hs = Math.max(hb + 12, height - dome.size() - 6); // the lantern's floor: the last tread's row
		int hl = hs + 4;                                     // the lantern's roof and cornice
		int balcony = hb + (hs - hb) * 3 / 5;
		d.eave(hl);
		Side fr = t.face(Dirs.FRONT);
		int door = fr.middle();

		// the base: plinth, walls, a course and a flat roof round the foot of the shaft, a parapet
		Grounds.footings(d, t.x0, t.z0, t.x1, t.z1, -1);
		t.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		plain(d, t, 2, hb, body, dress);
		t.course(d, hb - 1, false);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = t.z0; z <= t.z1; z++) {
				for (int x = t.x0; x <= t.x1; x++) {
					d.set(x, hb, z, dress.full());
				}
			}
			for (int[] c : Draft.ring(t.x0, t.z0, t.x1, t.z1)) {
				if (d.allowed(c[0], hb + 1, c[1])) {
					d.set(c[0], hb + 1, c[1], Crowns.isCorner(c, t.x0, t.z0, t.x1, t.z1) ? dress.full() : dress.slabOrNothing(SlabType.BOTTOM));
				}
			}
		}
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : t.faces()) {
			if (f.out() != Dirs.FRONT) {
				Details.slit(d, f, f.middle(), 3, 2, dress, glass);
			}
		}

		// the shaft: round, a course every seven rows, slits turning round it to light the stair
		try (var s = d.stage(StageKind.WALLS)) {
			for (long k : shaft) {
				int x = (int) (k >> 32);
				int z = (int) k;
				if (!rim(shaft, x, z)) {
					continue;
				}
				for (int y = hb + 1; y < hl; y++) {
					d.set(x, y, z, body.full());
				}
			}
		}
		// bands of the dressing laid in the shaft's face every six rows (flush: a projecting course would read as another
		// balcony)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = hb + 6; y < hs - 1; y += 6) {
				if (Math.abs(y - balcony) <= 1) {
					continue;
				}
				for (long k : shaft) {
					int x = (int) (k >> 32);
					int z = (int) k;
					if (rim(shaft, x, z)) {
						d.set(x, y, z, dress.full());
					}
				}
			}
		}
		Side[] faces = Side.of(cx - rs, cz - rs, cx + rs, cz + rs);
		int n = 0;
		for (int y = hb + 3; y < hs - 1; y += 4) {
			Side f = faces[n++ % 4];
			int mid = f.middle();
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(f.x(mid), y, f.z(mid), glass);
			}
		}
		// the balcony: a ring a block proud of the shaft on corbels, a balustrade of wall posts on its edge
		Set<Long> ring = Shapes.oct(cx, cz, rs + 1, cut + 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (long k : ring) {
				if (shaft.contains(k)) {
					continue;
				}
				int x = (int) (k >> 32);
				int z = (int) k;
				d.set(x, balcony, z, dress.full());
				d.set(x, balcony + 1, z, dress.wallOrFull());
				Direction in = Dirs.nearest(cx - x, cz - z);
				if (d.isEmpty(x, balcony - 1, z)) {
					d.set(x, balcony - 1, z, dress.stairsOrSlab(in, Half.TOP));
				}
			}
		}
		// the lantern: the shaft's head opened in arches on its four faces, a cornice, a roof row, the dome
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (Side f : faces) {
				int mid = f.middle();
				for (int y = hs + 1; y <= hs + 2; y++) {
					d.set(f.x(mid), y, f.z(mid), Blocks.AIR.defaultBlockState());
				}
				d.set(f.x(mid), hs + 3, f.z(mid), dress.full());
			}
		}
		ringCourse(d, shaft, hs, dress);
		ringCourse(d, shaft, hl, dress);
		try (var s = d.stage(StageKind.ROOF)) {
			for (long k : shaft) {
				d.set((int) (k >> 32), hl, (int) k, dress.full());
			}
		}
		Fam sheet = Adobe.domeSheet(d);
		int peak;
		try (var s = d.stage(StageKind.ROOF)) {
			Stone domeStone = sheet != null && sheet.has(Form.STAIRS) ? Stone.fixed(d, StageKind.ROOF, key(sheet.blockOr(Form.FULL))) : dress;
			peak = Shapes.stack(d, cx, cz, hl + 1, dome, domeStone.full(), domeStone);
			if (peak + 2 <= top) {
				peak = Details.finial(d, cx, peak + 1, cz, true);
			}
		}

		// inside: the floor, the keeper's room, the stair up the shaft to the lantern
		Grounds.door(d, fr, door, 0, dress);
		t.floor(d, -1, base.full());
		int start = 1;
		t.hollow(d, 0, hb - 1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.carve(x, hb, z, x, hs + 3, z);
				}
			}
		}
		Shapes.spiral(d, cx, cz, 0, hs + 1, start, Stone.dressing(d), dress.full());
		if (b >= 9) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(t.x0 + 1, 0, t.z1 - 1, States.facing(Blocks.LECTERN, Dirs.FRONT));
			}
			d.poi(PoiType.LECTERN, t.x0 + 1, 0, t.z1 - 1, Dirs.FRONT);
			Furniture.chest(d, t.x1 - 1, 0, t.z1 - 1, Dirs.FRONT, List.of(new Object[]{"minecraft:clock", 1}, new Object[]{"minecraft:paper", 6}));
		}
		d.poi(PoiType.VIEWPOINT, cx, hs + 1, cz - 1, Dirs.FRONT);
		// the timekeeper's sundial in the forecourt, beside the way to the door
		int sz = t.z0 - 2;
		if (sz >= 1 && cx + 3 <= w - 1) {
			Instruments.sundial(d, cx + 2, 0, sz);
		}
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);
		Grounds.forecourt(d, t.outer(), fr.x(door), 0, fr.outZ(door, 1));
		return Math.max(peak, hl);
	}

	/** A course of upside-down stairs round a round shaft's outline at row y. */
	private static void ringCourse(Draft d, Set<Long> shape, int y, Stone dress) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.around(shape)) {
				if (!d.allowed(c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					if (Shapes.has(shape, c[0] + Dirs.dx(dir), c[1] + Dirs.dz(dir))) {
						d.set(c[0], y, c[1], dress.stairsOrSlab(dir, Half.TOP));
						break;
					}
				}
			}
		}
	}

	private static boolean rim(Set<Long> s, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!Shapes.has(s, x + Dirs.dx(dir), z + Dirs.dz(dir))) {
				return true;
			}
		}
		return false;
	}

	private static String key(Block b) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString();
	}

	// ================================================================================================ the drum tower

	/**
	 * The drum tower of the karst: a podium of dressed stone with an arched gate, its deck walled by a parapet; on it a
	 * timber pavilion of posts and lattices under a skirt roof, a second storey stepped in under the crowning curved hip,
	 * the great drum and a bell inside. Returns the top.
	 */
	private static int drumTower(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		int p = avail >= 9 ? 9 : 7;
		int px0 = (w - p) / 2;
		int pz0 = Math.max(1, (dp - p + 1) / 2);
		if (pz0 + p > dp) {
			pz0 = dp - p;
		}
		Stone stone = Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower podium = new Tower(px0, pz0, p, stone, dress, base, false);
		d.body(podium.x0, podium.z0, podium.x1, podium.z1);
		int top = d.maxHeight() - 1;
		int hp = Math.max(7, Math.min(10, top / 4));          // the deck row
		int q = p - 2;                                        // the pavilion's square
		int qx0 = px0 + 1;
		int qz0 = pz0 + 1;
		int s1 = hp + 1;                                      // the pavilion's first storey
		int e1 = s1 + 4;                                      // its beam row, the skirt's foot
		int q2 = q - 2;
		int s2 = e1 + 1;                                      // the upper storey stands on the first's ceiling
		int e2 = s2 + 3;
		d.eave(e1);
		Side fr = podium.face(Dirs.FRONT);
		int door = fr.middle();

		// the podium
		Grounds.footings(d, podium.x0, podium.z0, podium.x1, podium.z1, -1);
		podium.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 2; y < hp; y++) {
				for (int[] c : Draft.ring(podium.x0, podium.z0, podium.x1, podium.z1)) {
					d.set(c[0], y, c[1], Crowns.isCorner(c, podium.x0, podium.z0, podium.x1, podium.z1) ? dress.full() : stone.full());
				}
			}
		}
		podium.course(d, hp - 1, false);
		podium.cornice(d, hp);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(podium.x0, podium.z0, podium.x1, podium.z1)) {
				if (d.allowed(c[0], hp + 1, c[1]) && !(c[1] == podium.z0 && Math.abs(c[0] - podium.cx) <= 0)) {
					d.set(c[0], hp + 1, c[1], dress.wallOrFull());
				}
			}
		}
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : podium.faces()) {
			if (f.out() != Dirs.FRONT) {
				Details.slit(d, f, f.middle(), 3, 2, dress, glass);
			}
		}

		// the pavilion: posts round its square, lattices between them over a sill, a beam course
		Mat frame = d.mat(Role.FRAME);
		Mat shutter = d.mat(Role.SHUTTER);
		Stone roofSt = Stone.of(d, Role.ROOF);
		storey(d, qx0, qz0, q, s1, e1, frame, shutter, true);
		// the skirt: a curved roof with lifted corners round the foot of the second storey
		int ex0 = Math.max(0, qx0 - 1);
		int ez0 = Math.max(-1, qz0 - 1);
		int ex1 = Math.min(w - 1, qx0 + q);
		int ez1 = Math.min(dp - 1, qz0 + q);
		Roofs.Field skirt = Shapes.outside(Shapes.capped(Roofs.flared(Roofs.hip(qx0 - 1, qz0 - 1, qx0 + q, qz0 + q, Roofs.Profile.curved(0.8)),
			qx0 - 1, qz0 - 1, qx0 + q, qz0 + q, 1, 2.5), 2.5), qx0 + 1, qz0 + 1, qx0 + q - 2, qz0 + q - 2);
		Roofs.Roof sk = Roofs.raster(d, skirt, e1, ex0, ez0, ex1, ez1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		Crowns.tips(d, sk, ex0, ez0, ex1, ez1, Crowns.Mats.of(d, dress));
		// the second storey and the crown
		storey(d, qx0 + 1, qz0 + 1, q2, s2, e2, frame, shutter, false);
		int peak = Crowns.crown(d, Crowns.Kind.PAGODA, qx0 + 1, qz0 + 1, qx0 + q2, qz0 + q2, e2 + 1, top, Crowns.Mats.of(d, dress));

		// inside: the gate, the stair up through the podium, the drum and the bell
		Grounds.door(d, fr, door, 0, dress);
		podium.floor(d, -1, base.full());
		podium.hollow(d, 0, hp - 1);
		podium.stair(d, 0, s1, Stone.dressing(d), dress.full(), 1);
		try (var s = d.stage(StageKind.INTERIOR)) {
			// the great drum: a barrel on its side on a stand, in the back corner of the pavilion
			int dx = qx0 + q - 2;
			int dz = qz0 + q - 2;
			if (Math.abs(dx - podium.cx) > 1 || Math.abs(dz - podium.cz) > 1) {
				d.set(dx, s1, dz, frame.log(Direction.Axis.Y));
				d.set(dx, s1 + 1, dz, States.facing(Blocks.BARREL, Dirs.LEFT));
				d.set(dx - 1 >= qx0 + 1 && (Math.abs(dx - 1 - podium.cx) > 1 || Math.abs(dz - podium.cz) > 1) ? dx - 1 : dx, s1, dz,
					States.block("minecraft:note_block", Blocks.NOTE_BLOCK).defaultBlockState());
			}
		}
		// the bell in the loft over the drum (its storey closed, lit by a lantern hung beside the bell)
		int bx = qx0 + 1 + q2 / 2;
		int bz = qz0 + 1 + q2 / 2;
		Details.bell(d, bx, e2 - 1, bz, Dirs.FRONT);
		if (q2 >= 5) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(bx + 1, e2 - 1, bz, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), true));
			}
		}
		d.poi(PoiType.VIEWPOINT, podium.x0 + 1, hp + 1, podium.z0 + 1, Dirs.FRONT);
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);
		Grounds.forecourt(d, podium.outer(), fr.x(door), 0, fr.outZ(door, 1));
		return Math.max(peak, e2);
	}

	/**
	 * A storey of a timber pavilion over the square {@code (x0, z0, size)} from row y0 to its beam row y1: posts at the
	 * corners and every other cell, a sill and a lattice of shutters between them (open in the middle of the front on
	 * the ground storey: the way in), a beam course on top.
	 */
	private static void storey(Draft d, int x0, int z0, int size, int y0, int y1, Mat frame, Mat shutter, boolean door) {
		int x1 = x0 + size - 1;
		int z1 = z0 + size - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side f : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < f.length(); i++) {
					boolean post = f.isCorner(i) || (i & 1) == 0;
					boolean way = door && f.out() == Dirs.FRONT && Math.abs(i - f.middle()) <= 0;
					for (int y = y0; y < y1; y++) {
						Paint pt;
						if (post) {
							pt = frame.log(Direction.Axis.Y);
						} else if (way) {
							pt = Paint.of(Blocks.AIR.defaultBlockState());
						} else if (y == y0) {
							pt = d.mat(Role.WALL).full();
						} else {
							pt = shutter.has(Form.TRAPDOOR) ? shutter.trapdoor(f.out(), Half.BOTTOM, true) : frame.fence();
						}
						d.set(f.x(i), y, f.z(i), pt);
					}
					d.set(f.x(i), y1, f.z(i), frame.log(f.axis()));
				}
			}
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, y1, z, d.mat(Role.CEILING).full());
				}
			}
		}
	}

	// ================================================================================================ the civic clock tower

	/**
	 * The civic clock tower of the northern and western traditions: a plain shaft on a chamfered plinth carrying a clock
	 * stage corbelled out wider than itself, a dial on each of its four faces and pinnacles at its corners; above it the
	 * bell chamber steps back behind a balustraded gallery, and a crown in the culture's manner. Returns the top.
	 */
	private static int civic(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		// the clock stage is the widest part; the shaft and the belfry stand two narrower
		int clockSize = avail >= 11 ? 11 : avail >= 9 ? 9 : 7;
		int shaftOuter = clockSize - 2;
		boolean piers = shaftOuter >= 7;
		int core = piers ? shaftOuter - 2 : shaftOuter;
		int cx0 = (w - clockSize) / 2;
		int cz0 = (dp - clockSize + 1) / 2;
		int sx0 = cx0 + 1 + (piers ? 1 : 0);
		int sz0 = cz0 + 1 + (piers ? 1 : 0);
		Stone body = Stone.panel(d);
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower shaft = new Tower(sx0, sz0, core, body, dress, base, piers);
		Tower clock = new Tower(cx0, cz0, clockSize, body, dress, base, false);
		// the bell chamber stands back behind a gallery on the clock stage's deck where there is room for one
		boolean gallery = clockSize >= 11;
		int belSize = gallery ? clockSize - 4 : clockSize - 2;
		int bo = (clockSize - belSize) / 2;
		Tower bel = new Tower(cx0 + bo, cz0 + bo, belSize, dress, dress, base, false);
		d.body(cx0, cz0, cx0 + clockSize - 1, cz0 + clockSize - 1);
		Crowns.Kind crown = crown(d, null);

		int top = d.maxHeight() - 1;
		int height = Math.max(24, (int) Math.round(top * (0.9 + 0.1 * d.random().nextDouble())));
		// a low clock stage: a dial of one block wants a stage of five rows, not a Big Ben's eleven
		int clockRows = 5;
		int crownRows = Crowns.rows(crown, belSize, height);
		int ycB = height - crownRows;                  // the belfry's cornice
		int ycC = ycB - 6;                             // the clock stage's cornice: the gallery deck and the belfry floor
		int yk = ycC - clockRows - 1;                  // the corbel course under the clock stage
		if (yk < 9) {
			int lift = 9 - yk;
			yk += lift;
			ycC += lift;
			ycB += lift;
		}
		d.eave(ycC);
		Side fr = shaft.face(Dirs.FRONT);
		int door = fr.middle();

		// the shaft
		Grounds.footings(d, clock.x0, clock.z0, clock.x1, clock.z1, -1);
		shaft.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		shaft.shaft(d, 2, yk);
		Paint glass = d.mat(Role.GLASS).pane();
		shaft.course(d, 7, true);
		for (Side f : shaft.faces()) {
			Details.slit(d, f, f.middle(), f.out() == Dirs.FRONT ? 5 : 3, 2, dress, glass);
		}
		int lo = 8;
		int hi = yk - 2;
		if (core >= 7 && hi - lo >= 5) {
			shaft.recess(d, lo + 1, hi, 3);
			int lh = Math.min(4, hi - lo - 1);
			for (Side f : shaft.faces()) {
				Details.arch(d, f.shifted(-1), f.middle(), 1, lo + 1 + (hi - lo - lh) / 2, lh, dress, glass);
			}
		} else if (hi - lo >= 2) {
			int lh = Math.min(3, hi - lo);
			shaft.openings(d, lo + (hi - lo + 1 - lh) / 2, lh, Tower.Opening.SINGLE, true, glass);
		}
		shaft.band(d, yk - 1);

		// the clock stage on its corbels, a dial on every face, pinnacles at its corners
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(clock.x0, clock.z0, clock.x1, clock.z1)) {
				Direction in = inward(clock.x0, clock.z0, clock.x1, clock.z1, c[0], c[1]);
				d.set(c[0], yk, c[1], in != null ? dress.stairsOrSlab(in, Half.TOP) : dress.full());
			}
		}
		clock.shaft(d, yk + 1, ycC - 1);
		int ycl = yk + 3;
		for (Side f : clock.faces()) {
			dial(d, f, f.middle(), ycl, dress);
		}
		clock.band(d, ycC - 1);
		clock.cornice(d, ycC);
		for (int[] c : new int[][]{{clock.x0, clock.z0}, {clock.x1, clock.z0}, {clock.x0, clock.z1}, {clock.x1, clock.z1}}) {
			Details.pinnacle(d, c[0], ycC + 1, c[1], 2, dress, true);
		}
		// the gallery's balustrade along the clock stage's edge
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(clock.x0, clock.z0, clock.x1, clock.z1)) {
				if (gallery && d.isEmpty(c[0], ycC + 1, c[1])) {
					d.set(c[0], ycC + 1, c[1], dress.wallOrFull());
				}
			}
		}

		// the bell chamber, set back behind the gallery
		bel.shaft(d, ycC + 1, ycB - 1);
		bel.openings(d, ycC + 1, ycB - ycC - 2, bel.size >= 7 && d.chance(0.5) ? Tower.Opening.TRIFORA : Tower.Opening.ARCH, true, null);
		bel.band(d, ycB - 1);
		bel.cornice(d, ycB);
		int peak = Crowns.crown(d, crown, bel.x0, bel.z0, bel.x1, bel.z1, ycB + 1, top, crown == Crowns.Kind.STAVE || crown == Crowns.Kind.PAGODA
			|| crown == Crowns.Kind.PYRAMID ? Crowns.Mats.of(d, dress) : Crowns.Mats.coppered(d, dress, 0.3));

		// the door, floors, the stair, the clock room and the bells
		Grounds.door(d, fr, door, 0, dress);
		shaft.floor(d, -1, base.full());
		clock.floor(d, yk, d.mat(Role.FLOOR).full());
		bel.floor(d, ycC, dress.full());
		shaft.stair(d, 0, ycC + 1, Stone.dressing(d), dress.full(), 1);
		shaft.hollow(d, 0, yk - 1);
		clock.hollow(d, yk + 1, ycC - 1);
		bel.hollow(d, ycC + 1, ycB - 1);
		bel.bells(d, ycB - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		clockRoom(d, clock, shaft, yk + 1);
		// the gallery: a door out of each belfry arch is the arch itself; the walk is a viewpoint
		if (gallery) {
			d.poi(PoiType.VIEWPOINT, clock.x0 + 1, ycC + 1, clock.z0 + 1, Dirs.FRONT);
			d.poi(PoiType.VIEWPOINT, clock.x1 - 1, ycC + 1, clock.z1 - 1, Dirs.BACK);
		} else {
			d.poi(PoiType.VIEWPOINT, bel.x0 + 1, ycC + 1, bel.z0 + 1, Dirs.FRONT);
		}
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);
		Grounds.forecourt(d, shaft.outer(), fr.x(door), 0, fr.outZ(door, 1));
		return peak;
	}

	/** The direction from a ring cell of a rectangle's outline toward its inside (null at a corner). */
	private static Direction inward(int x0, int z0, int x1, int z1, int x, int z) {
		boolean cornerX = x == x0 || x == x1;
		boolean cornerZ = z == z0 || z == z1;
		if (cornerX && cornerZ) {
			return null;
		}
		if (z == z0) {
			return Dirs.BACK;
		}
		if (z == z1) {
			return Dirs.FRONT;
		}
		return x == x0 ? Dirs.RIGHT : Dirs.LEFT;
	}

	/** The clock room: the works (grindstones for wheels, a chain for the pendulum's weight), a lectern, a chest. */
	private static void clockRoom(Draft d, Tower clock, Tower shaft, int y) {
		int ix0 = clock.x0 + 1;
		int iz0 = clock.z0 + 1;
		int ix1 = clock.x1 - 1;
		int iz1 = clock.z1 - 1;
		// the corners of the room, clear of the spiral
		int[][] corners = {{ix0, iz0}, {ix1, iz0}, {ix0, iz1}, {ix1, iz1}};
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int k = 0; k < corners.length; k++) {
				int[] c = corners[k];
				if (shaft.inside(c[0], c[1]) && Math.abs(c[0] - shaft.cx) <= 1 && Math.abs(c[1] - shaft.cz) <= 1) {
					continue;
				}
				switch (k) {
					case 0 -> d.set(c[0], y, c[1], States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.RIGHT));
					case 1 -> d.set(c[0], y, c[1], States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.LEFT));
					case 2 -> d.set(c[0], y, c[1], States.facing(Blocks.LECTERN, Dirs.FRONT));
					default -> Furniture.chest(d, c[0], y, c[1], Dirs.FRONT, List.of(new Object[]{"minecraft:clock", 1}, new Object[]{"minecraft:iron_ingot", 3}));
				}
			}
		}
		if (clock.size >= 9) {
			d.poi(PoiType.LECTERN, ix0, y, iz1 - 1, Dirs.BACK);
		}
	}

	/** The crown, from the param or the culture's manner ({@code fallback} for the campanile's own). */
	static Crowns.Kind crown(Draft d, Crowns.Kind fallback) {
		String p = d.param("crown", "");
		if (!p.isEmpty()) {
			try {
				return Crowns.Kind.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through
			}
		}
		if (fallback != null) {
			return fallback;
		}
		Culture c = d.style().culture();
		Habits h = d.habits();
		if (h.roof().flare() || h.roof().weight(Habits.RoofForm.PAGODA) > 0) {
			return Crowns.Kind.PAGODA;
		}
		if (c == null) {
			return Crowns.Kind.SPIRE;
		}
		return switch (c) {
			case NORTHERN, TUNDRA -> d.pick(Crowns.Kind.STAVE, Crowns.Kind.SPIRE);
			case BAYOU, TROPICAL -> d.pick(Crowns.Kind.PYRAMID, Crowns.Kind.CUPOLA);
			case MOORLAND -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.PARAPET, Crowns.Kind.OCTAGON);
			default -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.SPIRE, Crowns.Kind.CUPOLA, Crowns.Kind.OCTAGON);
		};
	}
}
