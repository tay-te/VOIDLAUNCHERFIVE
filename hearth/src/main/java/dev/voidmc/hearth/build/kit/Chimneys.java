package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Chimney stacks: masonry from the hearth up through the roof to clear the ridge line nearby, and a pot at the top that
 * smokes and shows no flame. The smoke comes from a lit campfire; a campfire standing bare on a stack reads from the
 * street as a roof on fire (a flame on every roof by day, a beacon by night: art review 2 §8 item 18). So the fire sits
 * inside the pot: the stack's top course is a crown of corbels (upside-down stairs of the stack's stone, their full
 * backs against the fire) round the campfire on all four sides, and a slab of the same stone caps it. From the street
 * and from above the head is a corbelled stone cap with smoke rising from it; no flame shows and no light spills onto
 * the roof by night.
 */
public final class Chimneys {
	private Chimneys() {
	}

	/**
	 * A 1 × 1 stack at (x, z) from {@code fromY} up through the roof, ending at least two blocks above the roof surface
	 * at its column and no lower than {@code minTop}. Returns the y of the smoking top.
	 */
	public static int stack(Draft d, int x, int z, int fromY, Roofs.Roof roof, int minTop) {
		Mat stone = d.mat(Role.CHIMNEY);
		int surface = roof.covers(x, z) ? roof.surfaceY(x, z) : fromY;
		int top = Math.max(surface + 2, minTop);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = fromY; y < top; y++) {
				d.set(x, y, z, stone.full());
			}
			// a stone without stairs cannot crown the pot: a little corbel of slabs under the bare fire instead
			if (!stone.has(Form.STAIRS) && top - surface >= 3 && stone.has(Form.SLAB)) {
				int cy = top - 1;
				for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int ox = x + o[0];
					int oz = z + o[1];
					if (d.isEmpty(ox, cy, oz) && !roof.covers(ox, oz) || d.isEmpty(ox, cy, oz) && roof.surfaceY(ox, oz) < cy - 1) {
						if (ox >= 0 && oz >= -1 && ox < d.frontage() && oz < d.depth()) {
							d.set(ox, cy, oz, stone.slab(SlabType.TOP));
						}
					}
				}
			}
			pot(d, x, top, z, null);
		}
		return top;
	}

	/**
	 * The smoking pot at the head of a stack: a lit campfire at (x, y, z) and, on each side where nothing is drawn, a
	 * corbel (an upside-down stair of the stack's stone, its full back against the fire) that hides the flame, and a slab
	 * cap over it. {@code
	 * stone}: the stack's stone; null reads it off the block under the fire. In the stage open, else ROOF.
	 */
	public static void pot(Draft d, int x, int y, int z, @Nullable BlockState stone) {
		BlockState under = stone != null ? stone : d.get(x, y - 1, z);
		d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
		crownOrSink(d, x, y, z, under);
	}

	/**
	 * {@link #crown}, or, where the stack reaches the plot's height and no cap may go over the fire, the fire sunk one
	 * course into the stack and capped where it stood (its smoke still rises clear: a campfire's smoke starts up to two
	 * blocks over it).
	 */
	private static void crownOrSink(Draft d, int x, int y, int z, @Nullable BlockState under) {
		if (!d.allowed(x, y + 1, z) && under != null && d.isSolid(x, y - 2, z)) {
			BlockState fire = d.get(x, y, z);
			d.set(x, y, z, Blocks.AIR.defaultBlockState());
			d.set(x, y - 1, z, fire);
			crown(d, x, y - 1, z, under);
			return;
		}
		crown(d, x, y, z, under);
	}

	private static void crown(Draft d, int x, int y, int z, @Nullable BlockState under) {
		Block stairs = under == null ? null : stairsOf(under.getBlock());
		Mat chimney = d.mat(Role.CHIMNEY);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int ox = x + Dirs.dx(side);
			int oz = z + Dirs.dz(side);
			if (!d.isEmpty(ox, y, oz)) {
				continue;
			}
			// the stair faces the fire: its full back is the side toward it, the corbel's step underneath faces out
			Direction toFire = side.getOpposite();
			BlockState corbel;
			if (stairs != null) {
				corbel = stairs.defaultBlockState().setValue(StairBlock.FACING, toFire).setValue(StairBlock.HALF, Half.TOP);
			} else if (chimney.has(Form.STAIRS)) {
				corbel = chimney.stairs(toFire, Half.TOP).at(ox, y, oz);
			} else {
				corbel = under != null && under.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? under : chimney.full().at(ox, y, oz);
			}
			if (corbel != null) {
				d.set(ox, y, oz, corbel);
			}
		}
		// the cap: a slab of the stone over the fire closes the pot, so no flame shows even from above (and no light
		// spills over the roof by night). The smoke still rises from it: a campfire puts its smoke out up to two blocks
		// over itself, and smoke starting inside a block moves out of it freely
		if (d.isEmpty(x, y + 1, z)) {
			BlockState cap = under == null ? null : slabOf(under.getBlock());
			if (cap == null) {
				cap = chimney.has(Form.SLAB) ? chimney.slab(SlabType.BOTTOM).at(x, y + 1, z) : null;
			}
			if (cap != null) {
				d.set(x, y + 1, z, cap);
			}
		}
	}

	/** The bottom slab cut from a block (stone bricks: a stone brick slab), or null. */
	public static @Nullable BlockState slabOf(Block b) {
		if (b instanceof net.minecraft.world.level.block.SlabBlock) {
			return b.defaultBlockState();
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		String p = id.getPath();
		List<String> names = new ArrayList<>();
		if (p.endsWith("_planks")) {
			names.add(p.substring(0, p.length() - "_planks".length()) + "_slab");
		}
		if (p.endsWith("_block")) {
			names.add(p.substring(0, p.length() - "_block".length()) + "_slab");
		}
		if (p.endsWith("s")) {
			names.add(p.substring(0, p.length() - 1) + "_slab");
		}
		names.add(p + "_slab");
		for (String n : names) {
			Block s = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), n));
			if (s instanceof net.minecraft.world.level.block.SlabBlock) {
				return s.defaultBlockState();
			}
		}
		return null;
	}

	/**
	 * Every chimney fire in the draft that stands bare (a lit campfire on a full block, three or more over the ground,
	 * open air over it) gets its crown and cap, whichever family drew it (town homes, tower houses, flat roofs, halls draw
	 * their own stacks). Run by the compiler before shapes are resolved; idempotent. Returns how many it crowned.
	 */
	public static int crownBareFires(Draft d) {
		List<long[]> fires = new ArrayList<>();
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			// a fire on a stack's head, whatever stage drew it (a roof's chimney, a kiln's or a furnace's stack on its
			// straw): well over the ground, so not a camp's fire pit (a brazier stands on a wall, not a full block)
			if (!(s.getBlock() instanceof CampfireBlock) || !s.getValue(CampfireBlock.LIT) || y < d.site().ground(x, z) + 3) {
				return;
			}
			BlockState under = d.get(x, y - 1, z);
			if (under == null || under.getBlock() instanceof CampfireBlock || !under.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				return;
			}
			if (!d.isEmpty(x, y + 1, z)) {
				return;
			}
			fires.add(new long[]{x, y, z});
		});
		fires.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : a[1] != b[1] ? Long.compare(a[1], b[1]) : Long.compare(a[2], b[2]));
		for (long[] f : fires) {
			int x = (int) f[0];
			int y = (int) f[1];
			int z = (int) f[2];
			Draft.Cell fire = d.cell(x, y, z);
			try (var s = d.stage(fire.stage().ordinal() < StageKind.ROOF.ordinal() ? StageKind.ROOF : fire.stage())) {
				crownOrSink(d, x, y, z, d.get(x, y - 1, z));
			}
		}
		return fires.size();
	}

	/** The stairs cut from a block (stone bricks: stone brick stairs; mud bricks: mud brick stairs), or null. */
	static @Nullable Block stairsOf(Block b) {
		if (b instanceof StairBlock) {
			return b;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		String p = id.getPath();
		List<String> names = new ArrayList<>();
		if (p.endsWith("_planks")) {
			names.add(p.substring(0, p.length() - "_planks".length()) + "_stairs");
		}
		if (p.endsWith("_block")) {
			names.add(p.substring(0, p.length() - "_block".length()) + "_stairs");
		}
		if (p.endsWith("s")) {
			names.add(p.substring(0, p.length() - 1) + "_stairs");
		}
		names.add(p + "_stairs");
		for (String n : names) {
			Block s = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), n));
			if (s instanceof StairBlock) {
				return s;
			}
		}
		return null;
	}
}
