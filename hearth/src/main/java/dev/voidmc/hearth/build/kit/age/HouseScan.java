package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Side;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A finished house drawing read for what can be added to it: its main body, the standing levels of its storeys, where
 * its roof starts, which sides are party walls, how much free yard lies outside each wall, and the way in from the
 * street (which no addition may cover). Read once before each addition, so each sees the house as the last left it.
 */
public final class HouseScan {
	public final Draft d;
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	/** The lowest roof course over the body (the eave): walls stand below it, the roof from it up. */
	public final int eave;
	/** Standing levels of the floors inside the body, lowest first (0 the ground floor). */
	public final List<Integer> floors;
	/** Whether the body is roofed flat (a deck) rather than pitched. */
	public final boolean flat;
	/** The highest y of anything drawn over the body. */
	public final int top;
	private final LongOpenHashSet route;
	/** Cells placed by earlier additions (keys of {@link Draft#key}). */
	public final LongOpenHashSet added;

	private HouseScan(Draft d, int[] b, int eave, List<Integer> floors, boolean flat, int top, LongOpenHashSet route, LongOpenHashSet added) {
		this.added = added;
		this.d = d;
		this.x0 = b[0];
		this.z0 = b[1];
		this.x1 = b[2];
		this.z1 = b[3];
		this.eave = eave;
		this.floors = floors;
		this.flat = flat;
		this.top = top;
		this.route = route;
	}

	/** Reads a drawing (null when it has no body of walls to add to). */
	public static @Nullable HouseScan of(Draft d, LongOpenHashSet route) {
		return of(d, route, new LongOpenHashSet());
	}

	/** Reads a drawing that earlier additions have placed {@code added} on. */
	public static @Nullable HouseScan of(Draft d, LongOpenHashSet route, LongOpenHashSet added) {
		int[] b = d.bodyRect();
		boolean declared = !(b[0] == 0 && b[1] == 0 && b[2] == d.frontage() - 1 && b[3] == d.depth() - 1);
		if (!declared) {
			// no body declared: the box round the walls at head height
			int ax = Integer.MAX_VALUE;
			int az = Integer.MAX_VALUE;
			int bx = Integer.MIN_VALUE;
			int bz = Integer.MIN_VALUE;
			for (var e : d.cells().long2ObjectEntrySet()) {
				int y = Draft.keyY(e.getLongKey());
				StageKind k = e.getValue().stage();
				if (added.contains(e.getLongKey())) {
					// an addition is not the house's body
					continue;
				}
				if ((y == 1 || y == 2) && (k == StageKind.WALLS || k == StageKind.FRAME) && full(e.getValue().state())) {
					ax = Math.min(ax, Draft.keyX(e.getLongKey()));
					az = Math.min(az, Draft.keyZ(e.getLongKey()));
					bx = Math.max(bx, Draft.keyX(e.getLongKey()));
					bz = Math.max(bz, Draft.keyZ(e.getLongKey()));
				}
			}
			if (ax == Integer.MAX_VALUE) {
				return null;
			}
			b = new int[]{ax, az, bx, bz};
		}
		if (b[2] - b[0] < 3 || b[3] - b[1] < 3) {
			return null;
		}
		int lowRoof = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;
		int[] roofTops = new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (var e : d.cells().long2ObjectEntrySet()) {
			if (e.getValue().state().isAir()) {
				continue;
			}
			int x = Draft.keyX(e.getLongKey());
			int y = Draft.keyY(e.getLongKey());
			int z = Draft.keyZ(e.getLongKey());
			boolean in = x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3];
			if (!in) {
				continue;
			}
			top = Math.max(top, y);
			if (e.getValue().stage() == StageKind.ROOF && y >= 2) {
				lowRoof = Math.min(lowRoof, y);
				if (x > b[0] && x < b[2] && z > b[1] && z < b[3]) {
					roofTops[0] = Math.min(roofTops[0], y);
					roofTops[1] = Math.max(roofTops[1], y);
				}
			}
		}
		int declaredEave = d.eaveY();
		int eave = declaredEave != Integer.MIN_VALUE && declaredEave >= 2 ? declaredEave : lowRoof == Integer.MAX_VALUE ? top + 1 : lowRoof;
		// flat: most of the inside is roofed at one height (a deck), whatever stands on it here and there; a pitched roof's
		// surface changes height across its span
		it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap tops = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
		int cols = 0;
		for (int x = b[0] + 1; x < b[2]; x++) {
			for (int z = b[1] + 1; z < b[3]; z++) {
				int hi = Integer.MIN_VALUE;
				for (int y = 2; y <= top; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (c != null && !c.state().isAir() && c.stage() == StageKind.ROOF) {
						hi = y;
					}
				}
				if (hi != Integer.MIN_VALUE) {
					tops.addTo(hi, 1);
					cols++;
				}
			}
		}
		int mode = 0;
		int modeY = Integer.MIN_VALUE;
		for (var e : tops.int2IntEntrySet()) {
			if (e.getIntValue() > mode) {
				mode = e.getIntValue();
				modeY = e.getIntKey();
			}
		}
		// against the walls: a deck lies at or below the head of every wall (its parapet), a pitched roof rises above the
		// eaves of at least two of them
		int lowestSide = Integer.MAX_VALUE;
		for (Side side : Side.of(b[0], b[1], b[2], b[3])) {
			List<Integer> heads = new ArrayList<>();
			for (int i = 1; i < side.last(); i++) {
				for (int y = top; y >= 0; y--) {
					if (!d.isEmpty(side.x(i), y, side.z(i))) {
						heads.add(y);
						break;
					}
				}
			}
			if (!heads.isEmpty()) {
				java.util.Collections.sort(heads);
				lowestSide = Math.min(lowestSide, heads.get(heads.size() / 2));
			}
		}
		boolean flat = cols > 0 && mode * 10 >= cols * 7 && modeY <= lowestSide;
		List<Integer> floors = new ArrayList<>();
		int interior = (b[2] - b[0] - 1) * (b[3] - b[1] - 1);
		for (int y = 0; y <= top; y++) {
			int n = 0;
			for (int x = b[0] + 1; x < b[2]; x++) {
				for (int z = b[1] + 1; z < b[3]; z++) {
					BlockState below = d.get(x, y - 1, z);
					boolean floor = y == 0 ? below != null && !below.isAir() || y - 1 < d.site().ground(x, z) : below != null && full(below)
						&& d.cell(x, y - 1, z).stage() == StageKind.FLOORS;
					if (floor && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z)) {
						n++;
					}
				}
			}
			if (n * 4 >= interior) {
				floors.add(y);
			}
		}
		if (floors.isEmpty() || floors.get(0) != 0) {
			floors.add(0, 0);
		}
		return new HouseScan(d, b, eave, floors, flat, top, route, added);
	}

	static boolean full(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	public Side side(Direction out) {
		return Side.of(this.x0, this.z0, this.x1, this.z1, out);
	}

	/** Whether a side of the body is a party wall (no additions there, ever). */
	public boolean party(Direction out) {
		if (out == Dirs.LEFT) {
			return this.d.partyLocalLeft() && this.x0 <= 0;
		}
		if (out == Dirs.RIGHT) {
			return this.d.partyLocalRight() && this.x1 >= this.d.frontage() - 1;
		}
		return false;
	}

	public boolean onRoute(int x, int y, int z) {
		for (int dy = -1; dy <= 2; dy++) {
			if (this.route.contains(Draft.key(x, y + dy, z))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a column outside the house is free yard from the floor up to {@code yTop}: in the plot, off the way in,
	 * holding nothing but air, plants, paths and yard things (no walls, roofs, porches or sheds), on ground no more than
	 * a block above the floor and no more than four below it.
	 */
	public boolean yard(int x, int z, int yTop) {
		if (x < 0 || z < 0 || x >= this.d.frontage() || z >= this.d.depth() || yTop >= this.d.maxHeight()) {
			return false;
		}
		int g = this.d.site().ground(x, z);
		if (g > 1 || g < -4 || this.d.site().water(x, z) != dev.voidmc.hearth.build.terrain.Site.NONE) {
			return false;
		}
		for (int y = 0; y <= yTop; y++) {
			if (onRoute(x, y, z)) {
				return false;
			}
			Draft.Cell c = this.d.cell(x, y, z);
			if (c == null || c.state().isAir()) {
				continue;
			}
			StageKind k = c.stage();
			if (k != StageKind.EXTERIOR && k != StageKind.CLEAR && !(k == StageKind.EARTHWORK && y < g)) {
				return false;
			}
			if (c.state().getBlock() instanceof DoorBlock || c.state().getBlock() instanceof LadderBlock) {
				return false;
			}
			// a step of the way in or a stair of a garden terrace is not yard to build over
			if (c.state().getBlock() instanceof StairBlock) {
				return false;
			}
		}
		return true;
	}

	/** Free yard columns outward from cell i of a side, up to {@code max}, each clear to {@code yTop}. */
	public int depthOut(Side s, int i, int max, int yTop) {
		int n = 0;
		for (int k = 1; k <= max; k++) {
			if (!yard(s.outX(i, k), s.outZ(i, k), yTop)) {
				break;
			}
			n++;
		}
		return n;
	}

	/** The lowest roof (or anything) over a column above y0, minus one: how high an addition may reach there. */
	public int ceiling(int x, int z, int y0) {
		for (int y = y0; y < this.d.maxHeight(); y++) {
			if (!this.d.isEmpty(x, y, z)) {
				return y - 1;
			}
		}
		return this.d.maxHeight() - 1;
	}

	/** A plain wall block of the body at a cell (full, in the walls or frame stage): somewhere a door can be cut. */
	public boolean plainWall(int x, int y, int z) {
		Draft.Cell c = this.d.cell(x, y, z);
		return c != null && full(c.state()) && (c.stage() == StageKind.WALLS || c.stage() == StageKind.FRAME)
			&& !(c.state().getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock && c.stage() == StageKind.FRAME
			&& c.state().getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y && isCornerPost(x, z));
	}

	private boolean isCornerPost(int x, int z) {
		return (x == this.x0 || x == this.x1) && (z == this.z0 || z == this.z1);
	}

	/** Whether a person can stand at a cell inside (feet and head free, a floor under them). */
	public boolean standable(int x, int y, int z) {
		BlockState below = this.d.get(x, y - 1, z);
		return this.d.isEmpty(x, y, z) && this.d.isEmpty(x, y + 1, z) && below != null && full(below);
	}
}
