package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.blueprint.MaterialBill;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The towers of a Bohemian market town and village (the temperate and wisteria traditions' churches and town halls):
 * five square, rendered in the walls' white lime between corner stones of dressed stone over a stone plinth, a string
 * course at each stage, little arched corbels under the belfry, slit lights and a clock, a belfry of paired lights parted
 * by a colonnette, and the caps those towers are known by, all in dark shingle or slate (a tiled or thatched town still
 * shingles its towers; copper only on the odd baroque onion): on a town's tower a gallery on corbels with a timber
 * balustrade before a white attic, roofed by the kick of a tall helm or an onion; on a village's squat tower a broad
 * pyramidal helm or an onion straight off the belfry. No battlements, no corner spirelets.
 */
public final class Bohemian {
	private Bohemian() {
	}

	/** Rows from the belfry's floor to its cornice. */
	public static final int BELFRY = 6;
	/** The fewest rows a crown is given. */
	public static final int CROWN = 9;

	/** What caps a Bohemian tower. */
	public enum Cap {
		/** A tall helm: a needle of shingles off a bell-cast kick, a post and a rod on its point. */
		HELM,
		/** An onion dome on a drum over a skirt of shingles, a gilded ball and a rod. */
		ONION,
		/** A broad, squat pyramidal helm off a kick a block over the belfry (the village tower's), a wrought-iron cross. */
		PYRAMID
	}

	/** A town tower's cap: helms mostly, an onion on a share (more of them in a city, where the baroque reached). */
	public static Cap townCap(Draft d, int tier) {
		return d.chance(tier >= 4 ? 0.45 : 0.3) ? Cap.ONION : Cap.HELM;
	}

	/** A village tower's cap: the broad pyramid mostly, an onion on a share. */
	public static Cap villageCap(Draft d) {
		return d.chance(0.35) ? Cap.ONION : Cap.PYRAMID;
	}

	/**
	 * The covering of Bohemian helms, onions and spires: the roofs' own where they are dark shingle or slate, else the
	 * style's sheet where that is dark (never its copper), else dark oak and spruce shingles or, on a share, slate. Red
	 * tile and thatch do not make a spire, and the turquoise of copper does not belong on a temperate skyline.
	 */
	public static Stone shingle(Draft d) {
		Mat roof = d.mat(Role.ROOF);
		if (!d.habits().roof().thatch() && roof.has(Form.STAIRS) && dark(roof.main(Form.FULL))) {
			return Stone.of(d, Role.ROOF);
		}
		Mat sheet = d.mat(Role.SHEET);
		if (sheet.has(Form.STAIRS) && dark(sheet.main(Form.FULL))) {
			return Stone.of(d, Role.SHEET);
		}
		// chosen by the building's seed, not the draft's random, so asking twice gives the same covering
		return dev.voidmc.hearth.build.draft.Noise.unit(d.seed(), 0x5A7E, 1, 2) < 0.25
			? Stone.fixed(d, StageKind.ROOF, "minecraft:deepslate_tiles", "minecraft:deepslate_tiles", "minecraft:cobbled_deepslate")
			: Stone.fixed(d, StageKind.ROOF, "wood:minecraft:dark_oak", "wood:minecraft:dark_oak", "wood:minecraft:dark_oak", "wood:minecraft:spruce");
	}

	/** The covering of an onion: the shingles, or the copper of the odd baroque onion on a share of a city's. */
	static Stone onionSheet(Draft d, int tier) {
		return tier >= 4 && dev.voidmc.hearth.build.draft.Noise.unit(d.seed(), 0x0410, 3, 4) < 0.25 ? Stone.copper(d) : shingle(d);
	}

	/** Whether a covering reads dark and unsaturated (dark oak shingles, slate), not red tile, copper or a brown or pale wood. */
	static boolean dark(Block b) {
		if (b == null || BuiltInRegistries.BLOCK.getKey(b).getPath().contains("copper")) {
			return false;
		}
		int c = b.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
		int r = (c >> 16) & 0xFF;
		int g = (c >> 8) & 0xFF;
		int bl = c & 0xFF;
		double lum = 0.3 * r + 0.59 * g + 0.11 * bl;
		int sat = Math.max(r, Math.max(g, bl)) - Math.min(r, Math.min(g, bl));
		return MaterialBill.timber(b) ? lum < 90 : lum < 120 && sat < 40;
	}

	/**
	 * The white lime render a Bohemian tower or pillar is laid in: the walls' own wherever they are masonry or a render
	 * (calcite, quartz, mushroom-stem lime, sandstone ochre, even brick), else the masonry stone (a log village's tower is
	 * stone).
	 */
	public static Stone render(Draft d) {
		Block w = d.mat(Role.WALL).main(Form.FULL);
		if (w != null && !MaterialBill.timber(w) && !d.mat(Role.WALL).has(Form.LOG)) {
			return Stone.of(d, Role.WALL);
		}
		return Stone.masonry(d);
	}

	/**
	 * The dressed stone of a Bohemian tower's corners, courses and lights: the style's dressing where it is stone, else
	 * the plinth's stone or a pale stone brick (a brick dressing turns a white tower into a Hanseatic one).
	 */
	public static Stone dressing(Draft d) {
		Stone dr = Stone.dressing(d);
		if (!brick(dr.main(Form.FULL))) {
			return dr;
		}
		if (Stone.stony(d, Role.PLINTH) && !brick(d.mat(Role.PLINTH).main(Form.FULL))) {
			return Stone.of(d, Role.PLINTH).crisp();
		}
		return Stone.fixed(d, StageKind.WALLS, "minecraft:stone_bricks");
	}

	/** Whether a block is fired brick or terracotta (red, not a stone). */
	static boolean brick(Block b) {
		if (b == null) {
			return false;
		}
		String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
		return id.equals("bricks") || id.startsWith("brick_") || id.contains("mud_brick") || id.contains("terracotta");
	}

	/**
	 * Lime render for a town's tower whatever the dressing's walls are: the render where it reads white, else a lime of
	 * smooth quartz and calcite (a town limes its bell tower even where its church is bare stone).
	 */
	public static Stone whiteRender(Draft d) {
		Stone r = render(d);
		Block b = r.main(Form.FULL);
		if (b != null && Towers.light(b)) {
			return r;
		}
		return Stone.fixed(d, StageKind.WALLS, "minecraft:smooth_quartz", "minecraft:smooth_quartz", "minecraft:calcite");
	}

	/**
	 * A whitewashed pillar's stone: the render where it reads white (lime, calcite, quartz), else bare stone, the plinth's
	 * or the masonry's (a wayside pillar is limed or left in the stone, never in a dark daub).
	 */
	public static Stone lime(Draft d) {
		Stone r = render(d);
		Block b = r.main(Form.FULL);
		if (b != null && Towers.light(b)) {
			return r;
		}
		return Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.masonry(d);
	}

	/**
	 * The belfry's floor for a tower beside a roof whose ridge is {@code ridge} (the arches clear of it), lowered toward
	 * {@code plate + 3} where the plot's height would otherwise starve the crown.
	 */
	public static int belfryFloor(int plate, int ridge, int maxH) {
		int top = maxH - 1;
		int yb = Math.max(plate + 4, ridge);
		if (top - (yb + BELFRY) < CROWN) {
			yb = Math.max(plate + 3, top - CROWN - BELFRY);
		}
		return yb;
	}

	/**
	 * A town tower five square from standing level {@code base}, its core at {@code (x0, z0)}: the plinth, the shaft,
	 * stage courses at {@code doorTop} (and half way to the belfry when the shaft is tall), slits in the faces not in
	 * {@code blind}, clocks on the faces in {@code clocks}, the belfry from {@code yb} with its bell, the gallery and a
	 * town's cap. The inside is cleared from the floor to the belfry and floored in the paving; the ringer's ladder hangs
	 * at {@code ladder} ({x, z}, facing {@code ladderFacing}, out of the wall behind it). Doors and ways through are the
	 * caller's. Returns the top.
	 */
	public static int tower(Draft d, int x0, int z0, int base, int doorTop, int yb, int maxH, int tier, Set<Direction> blind,
		Set<Direction> clocks, int[] ladder, Direction ladderFacing) {
		return tower(d, x0, z0, base, doorTop, yb, BELFRY, maxH, tier, blind, clocks, ladder, ladderFacing, true, townCap(d, tier), false);
	}

	/**
	 * {@link #tower(Draft, int, int, int, int, int, int, int, Set, Set, int[], Direction)} with a belfry {@code belfry} rows
	 * tall, with the gallery or without it (a village's squat tower, its cap straight off the cornice), under the given cap;
	 * {@code limed}: in lime render whatever the walls are (a town hall's tower), else in the walls' own render or stone
	 * (a fieldstone church's tower is fieldstone).
	 */
	public static int tower(Draft d, int x0, int z0, int base, int doorTop, int yb, int belfry, int maxH, int tier, Set<Direction> blind,
		Set<Direction> clocks, int[] ladder, Direction ladderFacing, boolean gallery, Cap cap, boolean limed) {
		Stone body = limed ? whiteRender(d) : render(d);
		Stone dress = dressing(d);
		Stone foot = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(x0, z0, 5, body, dress, foot, false);
		int top = maxH - 1;
		int yc = yb + belfry;
		Side fr = t.face(Dirs.FRONT);
		Groundwork.footings(d, t.x0, t.z0, t.x1, t.z1, base - 1);
		t.plinth(d, base, base, true, fr.outX(fr.middle(), 1), fr.outZ(fr.middle(), 1));
		t.shaft(d, base + 1, yc - 1);
		d.clearBox(t.x0 + 1, base, t.z0 + 1, t.x1 - 1, yb - 1, t.z1 - 1);
		t.floor(d, base - 1, d.mat(Role.PAVING).full());
		// a course over the door stage and one under the belfry (a third half way up a tall shaft), and the little arches
		// only under the cornice: the render should read as a white tower between few dressings
		t.course(d, doorTop, false);
		int mid = yb - doorTop >= 12 ? doorTop + (yb - doorTop) / 2 : -1;
		if (mid > 0) {
			t.course(d, mid, false);
		}
		t.course(d, yb, false);
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : t.faces()) {
			if (blind.contains(f.out())) {
				continue;
			}
			int lo = doorTop + 1;
			int hi = (mid > 0 ? mid : yb) - 1;
			if (hi - lo >= 2) {
				Details.slit(d, f, f.middle(), lo + (hi - lo - 1) / 2, 2, dress, glass);
			}
		}
		// the clock under the belfry
		int clo = (mid > 0 ? mid : doorTop) + 1;
		int ycl = Math.max(clo + 1, yb - 2);
		if (ycl + 1 < yb) {
			for (Side f : t.faces()) {
				if (clocks.contains(f.out())) {
					Towers.clock(d, f, 2, ycl, false);
				}
			}
		}
		// the belfry: paired lights parted by a colonnette in every face, open so the bell is heard
		t.openings(d, yb + 1, yc - yb - 2, Tower.Opening.BIFORA, true, null);
		t.floor(d, yb, dress.full());
		t.hollow(d, yb + 1, yc - 1);
		t.band(d, yc - 1);
		t.cornice(d, yc);
		t.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		if (ladder != null) {
			Stairs.ladder(d, ladder[0], ladder[1], base, yb, ladderFacing);
		}
		return gallery ? gallery(d, t, yc, top, dress, tier, cap) : cap(d, t, yc + 1, top, dress, tier, cap);
	}

	/**
	 * The gallery and its cap: the cornice's corbels carry a balustrade of dark timber a block proud of the shaft (a stout
	 * post at each corner), before a white attic with a light out to it in each face; the cap rises off the attic, its kick
	 * roofing the gallery. Returns the top.
	 */
	public static int gallery(Draft d, Tower t, int yc, int top, Stone dress, int tier, Cap cap) {
		int x0 = t.x0 - 1;
		int z0 = t.z0 - 1;
		int x1 = t.x1 + 1;
		int z1 = t.z1 + 1;
		int y = yc + 1;
		Mat rail = d.mat(Role.RAILING);
		Paint post = rail.has(Form.FENCE) ? rail.form(Form.FENCE) : Halls.post(d);
		Paint frame = d.mat(Role.FRAME).log(Direction.Axis.Y);
		Paint glass = d.mat(Role.GLASS).pane();
		try (var st = d.stage(StageKind.WALLS)) {
			// the balustrade, a post at each corner carried up to the eave so the gallery reads as roofed
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = Crowns.isCorner(c, x0, z0, x1, z1);
				d.set(c[0], y, c[1], corner ? frame : post);
				if (corner) {
					d.set(c[0], y + 1, c[1], frame);
				}
			}
			// the attic two courses high, a light out to the gallery in each face
			for (int k = 0; k < 2; k++) {
				for (int[] c : Draft.ring(t.x0, t.z0, t.x1, t.z1)) {
					d.set(c[0], y + k, c[1], t.body.full());
				}
			}
			for (Side f : t.faces()) {
				d.set(f.x(f.middle()), y, f.z(f.middle()), glass);
				d.set(f.x(f.middle()), y + 1, f.z(f.middle()), glass);
			}
		}
		return cap(d, t, y + 2, top, dress, tier, cap == Cap.PYRAMID ? Cap.HELM : cap);
	}

	/**
	 * A cap over the tower from row y, never above {@code top}: a kick of the shingles' stairs a block out round the walls'
	 * square (over the gallery on a town tower), then the helm's tall needle, the pyramid's squat one or the onion on its
	 * drum. Returns the top.
	 */
	public static int cap(Draft d, Tower t, int y, int top, Stone dress, int tier, Cap cap) {
		Stone skin = cap == Cap.ONION ? onionSheet(d, tier) : shingle(d);
		Stone kick = shingle(d);
		int peak = y;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int[] c : Draft.ring(t.x0 - 1, t.z0 - 1, t.x1 + 1, t.z1 + 1)) {
				if (d.allowed(c[0], y, c[1])) {
					d.set(c[0], y, c[1], kick.stairsOrSlab(Dirs.nearest(t.cx - c[0], t.cz - c[1]), Half.BOTTOM));
				}
			}
			for (int z = t.z0; z <= t.z1; z++) {
				for (int x = t.x0; x <= t.x1; x++) {
					d.set(x, y, z, kick.full());
				}
			}
			int room = top - y - 1;
			switch (cap) {
				case HELM -> {
					// a tall needle off the walls' square, about twice the tower's width
					int rows = Math.max(5, Math.min(room - 2, (int) Math.round(t.size * 2.2)));
					int p = Shapes.needle(d, t.cx, t.cz, (t.size - 1) / 2, y + 1, rows, skin.full(), skin, skin.has(Form.WALL) ? skin.wall() : skin.full(), 0);
					peak = Math.max(p, y + rows);
				}
				case PYRAMID -> {
					// a squat pyramid: the square held a course, then drawn in a block a side each course to a point
					int rows = Math.max(3, Math.min(room - 3, 6));
					List<Set<Long>> ladder = Shapes.ladder(t.cx, t.cz, (t.size - 1) / 2);
					List<Set<Long>> outl = new ArrayList<>();
					for (Set<Long> o : ladder) {
						outl.add(o);
						if (o.size() == t.size * t.size && outl.size() < rows - 1) {
							outl.add(o);
						}
					}
					while (outl.size() > rows && outl.size() > 3) {
						outl.remove(1);
					}
					int p = Shapes.stack(d, t.cx, t.cz, y + 1, outl, skin.full(), skin);
					peak = cross(d, t.cx, p + 1, t.cz, top);
				}
				case ONION -> peak = Crowns.crown(d, Crowns.Kind.ONION, t.x0, t.z0, t.x1, t.z1, y + 1, top,
					new Crowns.Mats(d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), dress, d.mat(Role.FRAME), skin));
			}
		}
		return Math.max(peak, y);
	}

	/** A wrought-iron cross on the point at (x, y - 1, z), or a rod where the plot's height allows no more. Returns the top. */
	static int cross(Draft d, int x, int y, int z, int top) {
		if (y > top) {
			return y - 1;
		}
		if (y + 2 > top) {
			d.set(x, y, z, Details.rod());
			return y;
		}
		return dev.voidmc.hearth.build.kit.learning.Sacra.ironCross(d, x, y, z, Direction.Axis.X);
	}

	/**
	 * The ridge turret of a church too short for its tower (a <i>sanktusník</i>): three square on a tie beam over the
	 * nave, rendered up through the roof, a belfry of dark corner posts open on every face with its bell, a dressed
	 * cornice and a squat helm of the shingles off a kick, a wrought-iron cross on top. Returns the top.
	 */
	public static int turret(Draft d, int cx, int z0, int plate, int ridge, int maxH) {
		int x0 = cx - 1;
		int x1 = cx + 1;
		int z1 = z0 + 2;
		Stone body = render(d);
		Stone dress = dressing(d);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, plate, z, beam.log(Direction.Axis.X));
				}
			}
		}
		int bh = 3;
		int yb = Math.max(plate + 2, Math.min(ridge + 1, maxH - 1 - bh - 7));
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = plate + 1; y < yb; y++) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					d.set(c[0], y, c[1], body.full());
				}
			}
			d.set(cx, yb - 1, z0 + 1, dress.full());
			Paint post = d.mat(Role.FRAME).log(Direction.Axis.Y);
			for (int y = yb; y < yb + bh; y++) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					boolean corner = Crowns.isCorner(c, x0, z0, x1, z1);
					d.set(c[0], y, c[1], corner ? post : Paint.of(Blocks.AIR.defaultBlockState()));
				}
				d.set(cx, y, z0 + 1, Paint.of(Blocks.AIR.defaultBlockState()));
			}
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, yb + bh, z, dress.full());
				}
			}
		}
		Towers.bell(d, cx, yb + bh - 1, z0 + 1, Direction.Axis.X);
		Tower t = new Tower(x0, z0, 3, body, dress, dress, false);
		return cap(d, t, yb + bh + 1, maxH - 1, dress, 0, Cap.PYRAMID);
	}

	/** A pointed doorway of dressed stone in cell i of wall f, standing at y, under a little gable of the dressing. */
	public static void portal(Draft d, Side f, int i, int y) {
		Stone dress = dressing(d);
		dev.voidmc.hearth.build.kit.landmark.Grounds.door(d, f, i, y, dress);
		if (dress.has(Form.STAIRS) && f.outZ(i, 1) >= 0) {
			int gy = y + 3;
			try (var st = d.stage(StageKind.WALLS)) {
				d.set(f.outX(i - 1, 1), gy, f.outZ(i - 1, 1), dress.stairs(f.along(), Half.BOTTOM));
				d.set(f.outX(i + 1, 1), gy, f.outZ(i + 1, 1), dress.stairs(f.along().getOpposite(), Half.BOTTOM));
				d.set(f.outX(i, 1), gy, f.outZ(i, 1), dress.full());
				d.set(f.outX(i, 1), gy + 1, f.outZ(i, 1), dress.slabOrNothing(SlabType.BOTTOM));
			}
		}
	}
}
