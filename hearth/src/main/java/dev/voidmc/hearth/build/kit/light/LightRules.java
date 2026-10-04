package dev.voidmc.hearth.build.kit.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * The light rule (docs/LIVING_WORLD.md §1), cell by cell. A blueprint is held to the rule of its family:
 *
 * <table>
 *   <caption>what each family must light</caption>
 *   <tr><th>family</th><th>archetypes</th><th>rule</th></tr>
 *   <tr><td>{@link Family#BUILDING}</td><td>homes, workplaces, commerce, civic, storage, landmarks, gatehouses and
 *   watchtowers</td><td>every walkable cell in a room (roofed and walled all round) reached from a door at block light
 *   <b>≥ 8</b>; every walkable cell of the plot reached from the street or a door, and every sealed pocket under its
 *   roofs, at <b>≥ 1</b> (never 0: hostile mobs spawn only at 0); outside every entrance <b>≥ 8</b> (a lamp at every
 *   door)</td></tr>
 *   <tr><td>{@link Family#SQUARE}</td><td>plazas</td><td>lamp-lit to the tier's standard, as a street: every cell within
 *   half a lamp spacing of one of its own lamps never dark (the rest of a square is lit by the lamp posts, the fountain
 *   and the stalls the planner stands on it, which are plots of their own); under any roof never dark</td></tr>
 *   <tr><td>{@link Family#GROUNDS}</td><td>wells, fountains, statues, lamp posts, bells, parks, gardens</td><td>rooms
 *   (a well house, a pavilion with walls) as a building; the ground under any roof never dark; the open ground is the
 *   street's to light</td></tr>
 *   <tr><td>{@link Family#FARM}</td><td>fields, orchards, pastures, paddies, terraces</td><td>sheds and shelters as a
 *   building (rooms ≥ 8, under roofs never dark); the open land is dark on purpose — the edges of a town are dangerous
 *   at night</td></tr>
 *   <tr><td>{@link Family#WORKS}</td><td>town walls, palisades, bridges, stair streets, terraces, docks,
 *   walkways</td><td>rooms (a covered bridge or walkway closed at the sides, a wall tower) as a building; under their
 *   roofs never dark; open decks are the street's</td></tr>
 *   <tr><td>{@link Family#ROAD}</td><td>roads</td><td>a street (five wide) in a village or larger is lamp-lit to its
 *   tier's spacing (every 14 blocks in a village or town, 10 in a city): between its first and last lamp, every
 *   walkable cell of its surface at ≥ 1; lanes, alleys, paths, tracks and highways are dark on purpose, and so is a
 *   camp's street (its light is the brazier at its heart)</td></tr>
 * </table>
 *
 * The lamp by the door is owed by buildings at every entrance, and by the other families only where a real door stands
 * (a shed in a field, a well house). Cells in water (the river under a stilt house, a ford) are never counted. Door cells
 * count only for "never dark".
 */
public final class LightRules {
	private LightRules() {
	}

	/** The light a room needs: where folk live and work, comfortable to read by (and well above spawning light). */
	public static final int ROOM = 8;
	/** The light the outside of every entrance needs: a lamp by the door. */
	public static final int DOOR = 8;
	/** Never dark: the least light at which no hostile mob spawns. */
	public static final int NEVER_DARK = 1;

	public enum Family {
		BUILDING, SQUARE, GROUNDS, FARM, WORKS, ROAD;

		public boolean plotNeverDark() {
			return this == BUILDING;
		}
	}

	/** The family of an archetype, by its id. */
	public static Family family(Identifier archetype) {
		String p = archetype.getPath();
		if (p.startsWith("farm/") || p.startsWith("extract/plantation")) {
			return Family.FARM;
		}
		if (p.equals("infra/road")) {
			return Family.ROAD;
		}
		if (p.startsWith("infra/") || p.equals("defense/wall") || p.equals("defense/palisade")) {
			return Family.WORKS;
		}
		if (p.equals("landscape/plaza")) {
			return Family.SQUARE;
		}
		if (p.startsWith("landscape/")) {
			return Family.GROUNDS;
		}
		return Family.BUILDING;
	}

	/** What a cell must have. */
	public enum Kind {
		/** In a room. */
		ROOM,
		/** Outside an entrance. */
		DOOR,
		/** Anywhere folk stand in the plot, or a sealed pocket under its roofs. */
		DARK,
		/** On a lit street between its lamps. */
		STREET;

		public String label() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A cell and the light it needs. */
	public record Need(int x, int y, int z, int min, Kind kind) {
	}

	/** Extra facts some families need. */
	public record Context(Family family, int tier, String roadKind, int roadWidth) {
		public static Context of(Family f, int tier) {
			return new Context(f, tier, "", 0);
		}
	}

	/** Every cell the family's rule speaks of, with the light it needs (the strictest need per cell). */
	public static List<Need> needs(Spaces sp, List<BlockPos> entrances, Context ctx) {
		Grid g = sp.grid();
		List<Need> out = new ArrayList<>();
		Family f = ctx.family();
		if (f == Family.ROAD) {
			return out;
		}
		java.util.Set<Long> doorCells = new java.util.HashSet<>();
		for (BlockPos e : entrances) {
			int[] c = settle(sp, e);
			if (c != null && !sp.wet(c[0], c[1], c[2]) && (f == Family.BUILDING || besideDoor(g, c[0], c[1], c[2]))) {
				doorCells.add(BlockPos.asLong(c[0], c[1], c[2]));
				out.add(new Need(c[0], c[1], c[2], DOOR, Kind.DOOR));
			}
		}
		if (f == Family.SQUARE) {
			out.addAll(RoadLight.lampReach(sp, ctx.tier(), Kind.DARK));
		}
		for (int y = g.y0; y < g.y0 + g.sy; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (!g.inPlot(x, z) || !sp.walkable(x, y, z) || sp.wet(x, y, z) || doorCells.contains(BlockPos.asLong(x, y, z))) {
						continue;
					}
					boolean reached = sp.reached(x, y, z);
					boolean roofed = sp.roofed(x, y, z);
					boolean enclosed = roofed && sp.enclosed(x, y, z);
					if (enclosed && reached && !sp.doorway(x, y, z)) {
						out.add(new Need(x, y, z, ROOM, Kind.ROOM));
					} else if (enclosed || reached && (f.plotNeverDark() || roofed)) {
						out.add(new Need(x, y, z, NEVER_DARK, Kind.DARK));
					}
				}
			}
		}
		return out;
	}

	/** Whether a door stands next to a cell (a real door, not an open way onto a square or a field). */
	static boolean besideDoor(Grid g, int x, int y, int z) {
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			int[] s = g.step(d);
			for (int dy = 0; dy <= 1; dy++) {
				if (g.at(x + s[0], y + dy, z + s[2]).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
					return true;
				}
			}
		}
		return false;
	}

	/** The walkable cell an entrance stands for (at its level, up to two below, or one above), or null. */
	public static int[] settle(Spaces sp, BlockPos e) {
		for (int dy = 0; dy >= -2; dy--) {
			if (sp.walkable(e.getX(), e.getY() + dy, e.getZ())) {
				return new int[]{e.getX(), e.getY() + dy, e.getZ()};
			}
		}
		if (sp.walkable(e.getX(), e.getY() + 1, e.getZ())) {
			return new int[]{e.getX(), e.getY() + 1, e.getZ()};
		}
		return null;
	}

	/** The needs the light does not meet. */
	public static List<Need> unmet(LightField light, List<Need> needs) {
		List<Need> out = new ArrayList<>();
		for (Need n : needs) {
			if (light.at(n.x(), n.y(), n.z()) < n.min()) {
				out.add(n);
			}
		}
		return out;
	}
}
