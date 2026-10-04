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
 *   roofs, at <b>≥ 1</b> (never 0: hostile mobs spawn only at 0; in the open air only cells a mob could spawn in
 *   count, so the stairs of a roof one could step onto from a hillside need no lamp); outside every entrance
 *   <b>≥ 8</b> (a lamp at every door)</td></tr>
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
 *   <tr><td>{@link Family#EXTRACTION}</td><td>quarry pits, mines, shafts, clay and sand pits (the woodlot is a
 *   {@code FARM})</td><td>the workings underground — every walkable cell reached in the plot with natural rock
 *   anywhere over the head: a mine's galleries, a shaft's ladder way, a winze — at <b>≥ 8</b>, as rooms are (they are
 *   where the miners work); rooms (the headhouse, the shaft house, the quarrymen's hut) at ≥ 8; under a shelter (a roof
 *   at most four blocks over the head: a lean-to, the portal's hood, a crane's platform) never dark; the open pit, its
 *   benches, the ramp and the spoil are open ground, dark on purpose like a field; outside every entrance <b>≥ 8</b> (the
 *   pit head, the adit's mouth, the shaft house door all have their lamp)</td></tr>
 * </table>
 *
 * The lamp by the door is owed by buildings at every entrance, and by the other families only where a real door stands
 * (a shed in a field, a well house). Cells in water (the river under a stilt house, a ford) are never counted. Door cells
 * count only for "never dark".
 */
public final class LightRules {
	private LightRules() {
	}

	/** Columns read round the plot (light reaching out and back in, ways round the outside). */
	public static final int MARGIN = 8;
	/** Blocks read above the highest block of the building and below its lowest (or its floor). */
	public static final int ABOVE = 6;
	public static final int BELOW = 2;

	/** The light a room needs: where folk live and work, comfortable to read by (and well above spawning light). */
	public static final int ROOM = 8;
	/** The light the outside of every entrance needs: a lamp by the door. */
	public static final int DOOR = 8;
	/** Never dark: the least light at which no hostile mob spawns. */
	public static final int NEVER_DARK = 1;

	public enum Family {
		BUILDING, SQUARE, GROUNDS, FARM, WORKS, ROAD, EXTRACTION;

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
		if (p.startsWith("extract/")) {
			return Family.EXTRACTION;
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

	/**
	 * Extra facts the rules need: the family, the plot's tier, and the highest standing level inside the plot's height
	 * (a cell above it, on the very top of a roof that fills the plot's height, belongs to the sky: nothing may be built
	 * there to light it).
	 */
	public record Context(Family family, int tier, int topY) {
		public static Context of(Family f, int tier, int topY) {
			return new Context(f, tier, topY);
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
			if (c != null && !sp.wet(c[0], c[1], c[2]) && (f == Family.BUILDING || f == Family.EXTRACTION || besideDoor(g, c[0], c[1], c[2]))) {
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
					if (y > ctx.topY() || !g.inPlot(x, z) || !sp.walkable(x, y, z) || sp.wet(x, y, z) || doorCells.contains(BlockPos.asLong(x, y, z))) {
						continue;
					}
					boolean reached = sp.reached(x, y, z);
					if (f == Family.EXTRACTION) {
						extraction(sp, x, y, z, reached, out);
						continue;
					}
					boolean roofed = sp.roofed(x, y, z);
					boolean enclosed = roofed && sp.enclosed(x, y, z);
					if (enclosed && reached && !sp.doorway(x, y, z)) {
						out.add(new Need(x, y, z, ROOM, Kind.ROOM));
					} else if (enclosed && !reached && open(g, x, y, z) || reached && (roofed || f.plotNeverDark() && spawnable(g, x, y, z))) {
						out.add(new Need(x, y, z, NEVER_DARK, Kind.DARK));
					}
				}
			}
		}
		return out;
	}

	/** How far over the head a roof still shelters a cell of an open working (a lean-to, a portal's hood). */
	public static final int SHELTER = 4;

	/**
	 * The {@link Family#EXTRACTION} rule for one walkable cell in the plot: underground workings and rooms at
	 * {@link #ROOM}, sheltered cells never dark, the open pit dark on purpose. Sealed pockets (enclosed, not reached)
	 * never dark, as everywhere.
	 */
	private static void extraction(Spaces sp, int x, int y, int z, boolean reached, List<Need> out) {
		Grid g = sp.grid();
		boolean roofed = sp.roofed(x, y, z);
		boolean enclosed = roofed && sp.enclosed(x, y, z);
		if (reached && !sp.doorway(x, y, z) && (enclosed || underground(g, x, y, z))) {
			out.add(new Need(x, y, z, ROOM, Kind.ROOM));
		} else if (enclosed && !reached && open(g, x, y, z) || reached && roofed && sp.roofY(x, y, z) - (y + 1) <= SHELTER) {
			out.add(new Need(x, y, z, NEVER_DARK, Kind.DARK));
		}
	}

	/**
	 * Whether a cell is in a working under the land, not in an open pit: natural ground (rock or earth the blueprint does
	 * not place) stands anywhere over the head (a gallery, a crosscut, a stall), or the feet are on a ladder in a narrow
	 * way, walled within two blocks on all four sides at head height (the ladder way of a shaft or a winze).
	 */
	public static boolean underground(Grid g, int x, int y, int z) {
		for (int yy = y + 2; yy < g.y0 + g.sy; yy++) {
			if (!g.placed(x, yy, z) && Spaces.roofLike(g.at(x, yy, z))) {
				return true;
			}
		}
		if (!(g.at(x, y, z).getBlock() instanceof net.minecraft.world.level.block.LadderBlock)) {
			return false;
		}
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			int[] s = g.step(d);
			boolean closed = false;
			for (int k = 1; k <= 2 && !closed; k++) {
				closed = !Spaces.passable(g.at(x + s[0] * k, y + 1, z + s[2] * k));
			}
			if (!closed) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a hostile mob could spawn standing in this cell, by the game's rules (NaturalSpawner): the block under it
	 * lets mobs spawn on it (a sturdy top face: not a stair, a bottom slab, glass or leaves) and the cell itself is no
	 * full block, no fluid, nothing that forbids spawning inside. Open-air cells where nothing can spawn (the stairs of a
	 * roof one could walk up from a hillside) need no light; under a roof every standing cell does.
	 */
	static boolean spawnable(Grid g, int x, int y, int z) {
		var below = g.at(x, y - 1, z);
		var feet = g.at(x, y, z);
		return below.isValidSpawn(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, net.minecraft.world.entity.EntityTypes.ZOMBIE)
			&& net.minecraft.world.level.NaturalSpawner.isValidEmptySpawnBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, feet,
				feet.getFluidState(), net.minecraft.world.entity.EntityTypes.ZOMBIE);
	}

	/** Feet and head in the open air of a pocket (not a shutter's or a hatch's cell): where something could stand. */
	static boolean open(Grid g, int x, int y, int z) {
		var feet = g.at(x, y, z);
		return (feet.isAir() || feet.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) && g.at(x, y + 1, z).isAir();
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
