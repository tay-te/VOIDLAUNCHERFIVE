package dev.voidmc.hearth.build.archetype.defense;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
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
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Towers;
import dev.voidmc.hearth.build.kit.landscape.Towers.Look;
import dev.voidmc.hearth.build.kit.landscape.Towers.Top;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:defense/gatehouse}: the town gate over a road where it passes the wall. The road runs through its
 * middle (local z, from the field in front to the town behind) under round-shouldered arches front and back; a raised
 * portcullis shows its teeth inside the front arch, the gate leaves stand folded back against the passage walls, and a
 * lantern hangs in the vault. Beside the passage a guard room with the stair up (left) and a store room with the
 * guards' grindstone (right). Over that the gate is raised in the culture's manner ({@link Plan}):
 * <ul>
 *   <li><b>tower gate</b> (Bohemia, the mountains, the vale, the moor, the coast): one rendered tower over the whole
 *   gate, the gate hall and a guards' room over it lit front and back, under a steep hip whose eaves stand out over the
 *   field and the town, the culture's banner over the arch;</li>
 *   <li><b>timber hall</b> (the north): the stone gate carries a timbered gate hall jettied out over the road on its
 *   beams, under a steep saddle roof;</li>
 *   <li><b>drum gate</b> (the south): two towers with their corners struck off, open and battlemented, an open
 *   fighting gallery between them over the arch;</li>
 *   <li><b>lookout gate</b> (the steppe, the tropics, the bayou, the karst): two towers each carrying a roofed lookout
 *   whose eaves stand out round it (a curved hip in the karst), a gallery between them.</li>
 * </ul>
 * Where the wall meets it ({@code over_road}), doorways in the flanks let the wall walk in at the gate hall's level.
 *
 * <p>POIs: GATE in the passage, GUARD_POSTs at the gate's mouth and up top, a VIEWPOINT, the GUARD job site (grindstone)
 * and a chest in the store room.
 *
 * <p>Params: {@code over_road} (boolean: stands over a road on the wall line), {@code passage_width} (int, the road's
 * width: 3 or 5), {@code wall_height} (int, 8), {@code roof} (as for the watchtower), {@code plan} ({@code tower},
 * {@code timber_hall}, {@code drums}, {@code lookouts}), {@code street_y}.
 */
public final class Gatehouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("defense/gatehouse"), ArchetypeCategory.DEFENSE, Set.of(JobType.GUARD), 0,
		13, 15, 7, 9, 14, 22, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 0, 1.0);

	/** The gate's form, by the culture. */
	enum Plan {
		TOWER, TIMBER_HALL, DRUMS, LOOKOUTS
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	static Plan plan(Draft d) {
		String p = d.param("plan", "");
		for (Plan k : Plan.values()) {
			if (k.name().equalsIgnoreCase(p)) {
				return k;
			}
		}
		return switch (Manner.culture(d)) {
			case TEMPERATE, WISTERIA, ALPINE, MOORLAND, MARITIME -> Plan.TOWER;
			case NORTHERN, TUNDRA -> Plan.TIMBER_HALL;
			case DESERT, MESA, MEDITERRANEAN -> Plan.DRUMS;
			default -> Plan.LOOKOUTS;
		};
	}

	/** Where everything stands. */
	private record G(int w, int dp, int c, int px0, int px1, int lx1, int rx0, int walk, int hall, int cz, int back, int top) {
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		Plan plan = plan(d);
		int pw = Math.clamp(d.param("passage_width", w >= 13 ? 5 : 3), 3, Math.max(3, w - 8));
		int c = (w - 1) / 2;
		int px0 = c - pw / 2;
		int px1 = px0 + pw - 1;
		int walk = Math.clamp(d.param("wall_height", 8), 6, 12) - 2;
		// a roofed gate stands a row in from the town side, so its eaves can stand out there as they do over the field
		int back = plan == Plan.DRUMS ? dp - 1 : dp - 2;
		G g = new G(w, dp, c, px0, px1, px0 - 1, px1 + 1, walk, walk, Math.min((dp - 1) / 2, back - 2), back, d.maxHeight() - 1);
		boolean overRoad = d.param("over_road", false);

		d.entrance(c, 0, -1);
		d.poi(PoiType.GATE, c, 0, g.cz(), Dirs.FRONT);
		Groundwork.level(d, px0, 0, px1, dp - 1, 0, true);
		pave(d, px0, px1, dp);
		Groundwork.footings(d, 0, 0, g.lx1(), back, -1);
		Groundwork.footings(d, g.rx0(), 0, w - 1, back, -1);
		Towers.floor(d, l, 1, 1, g.lx1() - 1, back - 1, -1, true);
		Towers.floor(d, l, g.rx0() + 1, 1, w - 2, back - 1, -1, true);
		// the ground storey either side of the passage, to the gate hall's floor
		Towers.shell(d, l, 0, 0, g.lx1(), back, 0, g.hall() - 1);
		Towers.shell(d, l, g.rx0(), 0, w - 1, back, 0, g.hall() - 1);
		Towers.buttresses(d, l, 0, 0, g.lx1(), back, 1);
		Towers.buttresses(d, l, g.rx0(), 0, w - 1, back, 1);
		passage(d, l, g);
		rooms(d, l, g);

		int hi = switch (plan) {
			case TOWER -> towerGate(d, l, g, overRoad);
			case TIMBER_HALL -> timberHall(d, l, g, overRoad);
			case DRUMS -> drums(d, l, g, overRoad);
			case LOOKOUTS -> lookouts(d, l, g, overRoad);
		};
		d.poi(PoiType.GUARD_POST, g.px0(), 0, 0, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, g.px1(), 0, 0, Dirs.FRONT);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, Math.max(hi + 1, g.hall() + 4)));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
		dev.voidmc.hearth.build.kit.noble.Mason.settle(d, "gatehouse");
	}

	/** The passage: vault, arches with round shoulders, portcullis, gate leaves, its lantern, lamps at the mouth. */
	private static void passage(Draft d, Look l, G g) {
		Mat body = l.body();
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = 0; z <= g.back(); z++) {
				for (int x = g.px0(); x <= g.px1(); x++) {
					for (int y = 0; y < g.hall() - 1; y++) {
						d.set(x, y, z, air);
					}
					d.set(x, g.hall() - 1, z, (z == 0 || z == g.back()) && x == g.c() ? l.quoin().full() : body.full());
				}
			}
			for (int z : new int[]{0, g.back()}) {
				if (body.has(Form.STAIRS)) {
					d.set(g.px0(), g.hall() - 2, z, body.stairs(Dirs.LEFT, Half.TOP));
					d.set(g.px1(), g.hall() - 2, z, body.stairs(Dirs.RIGHT, Half.TOP));
				}
			}
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x = g.px0(); x <= g.px1(); x++) {
				d.set(x, g.hall() - 2, 1, Blocks.IRON_BARS.defaultBlockState());
			}
			Mat door = d.mat(Role.DOOR);
			if (door.has(Form.TRAPDOOR)) {
				for (int y = 0; y < g.hall() - 2; y++) {
					for (int z = 2; z <= Math.min(3, g.back() - 2); z++) {
						d.set(g.px0(), y, z, door.trapdoor(Dirs.RIGHT, Half.BOTTOM, true));
						d.set(g.px1(), y, z, door.trapdoor(Dirs.LEFT, Half.BOTTOM, true));
					}
				}
			}
		}
		Furniture.hangingLantern(d, g.c(), g.hall() - 1, g.cz() + 1, 0);
		for (int x : new int[]{g.lx1(), g.rx0()}) {
			if (d.isAbsent(x, 3, -1)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 4, -1, d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, Half.TOP, false));
					d.set(x, 3, -1, States.lantern(Lamps.lantern(d), true));
				}
			}
		}
	}

	/** The guard room (the stair up to the gate hall) and the store room (the grindstone, arrows, arms). */
	private static void rooms(Draft d, Look l, G g) {
		// the stair up in the guard room; a gate too shallow for its landing gets a ladder in the back corner instead,
		// climbing through a hatch in the hall's floor a block in from the tower's flank
		boolean ladder = g.back() < 6;
		if (!ladder) {
			Stairs.switchback(d, 1, 0, 2, Dirs.BACK, g.hall(), Dirs.RIGHT);
		}
		Openings.door(d, Side.of(0, 0, g.lx1(), g.back(), Dirs.RIGHT), g.back() - 2, 0, 1, Openings.DoorOpts.BACK);
		Openings.door(d, Side.of(g.rx0(), 0, g.w() - 1, g.back(), Dirs.LEFT), g.back() - 2, 0, 1, Openings.DoorOpts.BACK);
		Furniture.workstation(d, JobType.GUARD, g.w() - 2, 0, 1, Dirs.BACK);
		Furniture.chest(d, g.w() - 2, 0, g.back() - 1, Dirs.FRONT, List.of(new Object[]{"minecraft:arrow", 24}, new Object[]{"minecraft:iron_ingot", 3}));
		Watchtower.armsRack(d, g.rx0() + 1, 1, g.back() - 1, Dirs.RIGHT);
		Furniture.hangingLantern(d, g.rx0() + 1, g.hall() - 1, 1, 0);
		// the floors at the gate hall's level over both rooms (the passage's vault carries the middle)
		Towers.floor(d, l, 1, 1, g.lx1() - 1, g.back() - 1, g.hall() - 1, false);
		Towers.floor(d, l, g.rx0() + 1, 1, g.w() - 2, g.back() - 1, g.hall() - 1, false);
		if (ladder) {
			try (var s = d.stage(StageKind.FLOORS)) {
				d.set(2, g.hall() - 1, g.back() - 1, Blocks.AIR.defaultBlockState());
			}
			Stairs.ladder(d, 2, g.back() - 1, 0, g.hall(), Dirs.FRONT);
		}
		Towers.slit(d, 0, 3, g.cz());
		Towers.slit(d, g.w() - 1, 3, g.cz());
	}

	/** The wall walk's doorways in the flanks at the hall's level, where the gate stands on the wall's line. */
	private static void walkDoors(Draft d, G g, boolean overRoad) {
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x : new int[]{0, g.w() - 1}) {
				if (overRoad) {
					d.set(x, g.walk(), g.cz(), air);
					d.set(x, g.walk() + 1, g.cz(), air);
					// and on through the hall's wall where it stands a block in
					int ix = x == 0 ? 1 : g.w() - 2;
					if (!d.isEmpty(ix, g.walk(), g.cz())) {
						d.set(ix, g.walk(), g.cz(), air);
						d.set(ix, g.walk() + 1, g.cz(), air);
					}
				} else {
					Towers.slit(d, x, g.hall() + 1, g.cz());
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the tower gate

	/** The Bohemian gate tower: the hall, a guards' room over it where the sky allows, a steep hip with deep eaves. */
	private static int towerGate(Draft d, Look l, G g, boolean overRoad) {
		int w = g.w();
		int b = g.back();
		int hall = g.hall();
		boolean upper = g.top() - (hall + 8) >= 5;
		int plate = upper ? hall + 7 : hall + 3;
		// the tower stands a block in from the gate's flanks over the hall's floor, so its eaves stand out over the wall
		// walks that meet it; the stone storey's head between is weathered off
		Towers.shell(d, l, 1, 0, w - 2, b, hall, plate);
		Towers.string(d, l, 1, 0, w - 2, b, hall - 1);
		if (upper) {
			Towers.string(d, l, 1, 0, w - 2, b, hall + 3);
		}
		ledges(d, l, g);
		// the stair on up from the hall to the guards' room, in the right half: its two lanes inside the tower's wall
		// (w - 4, w - 3), drawn after the floor so its well is cut through it
		if (upper) {
			Towers.floor(d, l, 2, 1, w - 3, b - 1, hall + 3, false);
			Stairs.switchback(d, w - 4, hall, 2, Dirs.BACK, 4, Dirs.RIGHT);
		}
		// lights front and back on each storey, the arch's banner and the town's arms over it
		for (int y : upper ? new int[]{hall + 1, hall + 5} : new int[]{hall + 1}) {
			for (int z : new int[]{0, b}) {
				for (int x : new int[]{g.lx1() / 2 + 1, g.c(), w - 1 - (g.lx1() / 2 + 1)}) {
					window(d, x, y, z);
				}
			}
		}
		Towers.banner(d, g.c() + 2, hall + 2, -1, Dirs.FRONT);
		walkDoors(d, g, overRoad);
		// the guards' furniture in the hall (and the room above), light
		furnishHall(d, g, hall, 2, w - 3, b - 1);
		if (upper) {
			Furniture.chest(d, 2, hall + 4, 1, Dirs.RIGHT, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:arrow", 16}));
			Furniture.table(d, g.c(), hall + 4, b - 1);
			Lighting.room(d, 2, 1, w - 3, b - 1, hall + 4);
		}
		// the hip, its eaves a block out over the field and the town
		int half = Math.max(1, (b + 1) / 2 + 1);
		double pitch = Math.clamp((g.top() - 1 - plate) / (double) half, 0.6, 1.4);
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1).withPitch(pitch);
		Roofs.Built rb = Roofs.build(d, 1, 0, w - 2, b, plate, RoofForm.HIP, Direction.Axis.X, o, false, false);
		d.poi(PoiType.GUARD_POST, g.c(), hall, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.c() - 1, hall, 1, Dirs.FRONT);
		int ridge = rb.roof().ridgeY;
		if (ridge + 2 <= g.top()) {
			Towers.finial(d, g.c(), ridge + 1, (b) / 2);
			return ridge + 2;
		}
		return ridge;
	}

	/** The stone storey's head along the flanks where the upper work stands a block in: a coping of slabs. */
	private static void ledges(Draft d, Look l, G g) {
		Mat body = l.body();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x : new int[]{0, g.w() - 1}) {
				for (int z = 0; z <= g.back(); z++) {
					if (d.isSolid(x, g.hall() - 1, z) && d.isEmpty(x, g.hall(), z)) {
						d.set(x, g.hall(), z, body.has(Form.SLAB) ? body.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) : body.full());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the north

	/** The stone gate under a timbered gate hall jettied out over the road on its beams, a steep saddle roof. */
	private static int timberHall(Draft d, Look l, G g, boolean overRoad) {
		int w = g.w();
		int b = g.back();
		int hall = g.hall();
		int plate = hall + 3;
		Mat post = d.mat(Role.FRAME);
		Mat boards = d.mat(Role.FLOOR);
		Mat shutter = d.mat(Role.SHUTTER);
		// the beams out over the road and the town, a jetty a block deep front and back
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = 1; x < w - 1; x++) {
				if ((x & 1) == 1) {
					d.set(x, hall - 1, -1, post.log(Direction.Axis.Z).orElse(boards.full()));
					d.set(x, hall - 1, b + 1, post.log(Direction.Axis.Z).orElse(boards.full()));
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = 1; x < w - 1; x++) {
				for (int z : new int[]{-1, b + 1}) {
					if (d.isAbsent(x, hall - 1, z)) {
						d.set(x, hall - 1, z, boards.has(Form.SLAB) ? boards.slab(net.minecraft.world.level.block.state.properties.SlabType.TOP) : boards.full());
					}
				}
			}
		}
		// the timbered walls of the hall on the jetty: posts every third, boards between, shutters open in the bays
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(1, -1, w - 2, b + 1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					for (int y = hall; y <= plate; y++) {
						Paint p;
						if (side.isCorner(i) || i % 3 == 0) {
							p = post.log(Direction.Axis.Y).orElse(boards.full());
						} else if (y == plate) {
							p = post.log(side.axis()).orElse(boards.full());
						} else if (y == hall + 1 && i % 3 == 2 && shutter.has(Form.TRAPDOOR) && side.axis() == Direction.Axis.X) {
							p = shutter.trapdoor(side.out().getOpposite(), Half.TOP, true);
						} else {
							p = boards.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
		}
		// the hall's floor over the jetty
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = 1; x < w - 1; x++) {
				for (int z : new int[]{0, b}) {
					if (d.isEmpty(x, hall, z)) {
						d.set(x, hall - 1, z, boards.full());
					}
				}
			}
		}
		ledges(d, l, g);
		walkDoors(d, g, overRoad);
		furnishHall(d, g, hall, 2, w - 3, b);
		double pitch = Math.clamp((g.top() - 1 - plate) / (double) Math.max(1, (b + 3) / 2), 0.8, 1.6);
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1).withPitch(pitch);
		Roofs.Built rb = Roofs.build(d, 1, -1, w - 2, b + 1, plate, RoofForm.GABLE, Direction.Axis.X, o, false, false);
		Towers.banner(d, g.c(), hall - 2, -1, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, g.c(), hall, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.c() - 1, hall, 1, Dirs.FRONT);
		return rb.roof().ridgeY;
	}

	// ------------------------------------------------------------------------------------------------ the south

	/** Two towers with their corners struck off, open and battlemented, a fighting gallery between them. */
	private static int drums(Draft d, Look l, G g, boolean overRoad) {
		int w = g.w();
		int b = g.back();
		int hall = g.hall();
		int tt = hall + 6 <= g.top() - 3 ? hall + 6 : hall + 4;
		Towers.shell(d, l, 0, 0, g.lx1(), b, hall, tt - 2);
		Towers.shell(d, l, g.rx0(), 0, w - 1, b, hall, tt - 2);
		gallery(d, l, g);
		// the stairs on up to both tops
		Stairs.switchback(d, g.rx0() + 1, hall, 2, Dirs.BACK, tt - hall, Dirs.RIGHT);
		openings(d, g);
		walkDoors(d, g, overRoad);
		Towers.floor(d, l, g.rx0(), 0, w - 1, b, tt - 1, !l.timber());
		Towers.battlements(d, l, g.rx0(), 0, w - 1, b, tt);
		Towers.floor(d, l, 0, 0, g.lx1(), b, tt - 1, !l.timber());
		Towers.battlements(d, l, 0, 0, g.lx1(), b, tt);
		// the towers' corners struck off up their whole height: they read round from the field
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] cn : new int[][]{{0, 0}, {g.lx1(), 0}, {0, b}, {g.lx1(), b}, {g.rx0(), 0}, {w - 1, 0}, {g.rx0(), b}, {w - 1, b}}) {
				if (cn[0] == g.lx1() && cn[1] == 0 || cn[0] == g.rx0() && cn[1] == 0) {
					continue;
				}
				for (int y = 0; y <= tt + 1; y++) {
					d.set(cn[0], y, cn[1], Blocks.AIR.defaultBlockState());
				}
			}
		}
		Lighting.room(d, 1, 1, g.lx1() - 1, b - 1, hall);
		Lighting.room(d, g.rx0() + 1, 1, w - 2, b - 1, hall);
		d.poi(PoiType.GUARD_POST, g.c(), hall, 2, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.c() - 1, hall, 2, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 3, tt, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.rx0() + 1, tt, 2, Dirs.FRONT);
		return tt + 1;
	}

	// ------------------------------------------------------------------------------------------------ lookouts

	/** Two towers, each carrying a roofed lookout whose eaves stand out round it, a gallery between them. */
	private static int lookouts(Draft d, Look l, G g, boolean overRoad) {
		int w = g.w();
		int b = g.back();
		int hall = g.hall();
		Top top = Watchtower.topFor(d);
		if (top == Top.OPEN) {
			top = Top.PYRAMID;
		}
		// (an even rise: the switchback's two flights then land square under the lookout's floor)
		int tt = hall + 6 <= g.top() - 7 ? hall + 6 : hall + 4;
		Towers.shell(d, l, 0, 0, g.lx1(), b, hall, tt - 2);
		Towers.shell(d, l, g.rx0(), 0, w - 1, b, hall, tt - 2);
		gallery(d, l, g);
		Stairs.switchback(d, g.rx0() + 1, hall, 2, Dirs.BACK, tt - hall, Dirs.RIGHT);
		openings(d, g);
		walkDoors(d, g, overRoad);
		Towers.floor(d, l, g.rx0(), 0, w - 1, b, tt - 1, !l.timber());
		Towers.floor(d, l, 0, 0, g.lx1(), b, tt - 1, !l.timber());
		int hi = Math.max(Towers.lookout(d, l, 0, 0, g.lx1(), b, tt, top, 1, 2), Towers.lookout(d, l, g.rx0(), 0, w - 1, b, tt, top, 1, 2));
		Lighting.room(d, 1, 1, g.lx1() - 1, b - 1, hall);
		Lighting.room(d, g.rx0() + 1, 1, w - 2, b - 1, hall);
		d.poi(PoiType.GUARD_POST, g.c(), hall, 2, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.c() - 1, hall, 2, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 3, tt, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, g.rx0() + 1, tt, 2, Dirs.FRONT);
		return hi;
	}

	/** The open gallery over the arch between two towers: a crenellated band corbelled out front, a parapet behind. */
	private static void gallery(Draft d, Look l, G g) {
		Mat body = l.body();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = g.px0(); x <= g.px1(); x++) {
				d.set(x, g.hall(), g.back(), body.full());
				d.set(x, g.hall() + 1, g.back(), ((x - g.px0()) & 1) == 0 ? body.full()
					: body.has(Form.SLAB) ? body.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) : body.full());
			}
			for (int x = g.lx1(); x <= g.rx0(); x++) {
				d.set(x, g.hall() - 1, -1, body.has(Form.STAIRS) ? body.stairs(Dirs.BACK, Half.TOP) : body.full());
				d.set(x, g.hall(), -1, body.full());
				d.set(x, g.hall() + 1, -1, ((x - g.lx1()) & 1) == 0 ? body.full()
					: body.has(Form.SLAB) ? body.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) : body.full());
			}
			// the gallery's floor joined to the band's
			for (int x = g.px0(); x <= g.px1(); x++) {
				if (d.isAbsent(x, g.hall() - 1, -1) || d.isEmpty(x, g.hall() - 1, -1)) {
					d.set(x, g.hall() - 1, -1, body.full());
				}
			}
		}
		Towers.banner(d, g.c(), g.hall() + 2, -1, Dirs.FRONT);
		// the windlass that raises the portcullis, on the gallery over its slot
		try (var s = d.stage(StageKind.INTERIOR)) {
			Mat beam = d.mat(Role.BEAM);
			for (int x = g.px0() + 1; x <= g.px1() - 1; x++) {
				d.set(x, g.hall(), 1, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
			d.set(g.px0(), g.hall(), 1, Lamps.post(d));
			d.set(g.px1(), g.hall(), 1, Lamps.post(d));
		}
	}

	/** Doorways from the towers onto the gallery, slits up the towers. */
	private static void openings(Draft d, G g) {
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x : new int[]{g.lx1(), g.rx0()}) {
				d.set(x, g.hall(), 2, air);
				d.set(x, g.hall() + 1, 2, air);
				d.set(x, g.hall() + 2, 2, air);
			}
		}
		Towers.slit(d, g.lx1() / 2, g.hall() + 3, 0);
		Towers.slit(d, g.w() - 1 - g.lx1() / 2, g.hall() + 3, 0);
	}

	/** The gate hall's furniture: the windlass over the portcullis, a bench of the guards, a barrel, light. */
	private static void furnishHall(Draft d, G g, int hall, int x0, int x1, int z1) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			Mat beam = d.mat(Role.BEAM);
			for (int x = g.px0() + 1; x <= g.px1() - 1; x++) {
				d.set(x, hall, 1, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
			d.set(g.px0(), hall, 1, Lamps.post(d));
			d.set(g.px1(), hall, 1, Lamps.post(d));
			// (by the front wall, clear of the stair on up that climbs the back half on the right)
			d.set(x1, hall, 1, States.facing(Blocks.BARREL, Direction.UP));
		}
		Lighting.room(d, x0, 1, x1, z1 - 1, hall);
	}

	/** A window of the glass two high at (x, y, z) in a wall, a sill under it where the wall is solid. */
	private static void window(Draft d, int x, int y, int z) {
		if (!d.isSolid(x, y, z) || !d.isSolid(x, y + 1, z)) {
			return;
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, d.mat(Role.GLASS).pane());
			d.set(x, y + 1, z, d.mat(Role.GLASS).pane());
		}
	}

	/** Setts down the passage: the paving with a kerb of the masonry along each side. */
	private static void pave(Draft d, int px0, int px1, int dp) {
		Mat paving = Manner.wood(d.mat(Role.PAVING)) ? Manner.masonry(d) : d.mat(Role.PAVING);
		Mat kerb = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, -1, z, x == px0 || x == px1 ? kerb.full() : paving.full());
				}
			}
		}
	}
}
