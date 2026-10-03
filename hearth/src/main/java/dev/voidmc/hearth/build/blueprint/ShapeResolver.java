package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Gives every placed block the shape it would have if a player had built the building by hand, the way
 * {@code builder.py}'s {@code finalize()} does for Expanse's templates: fence, wall, pane and bar connections (with wall
 * posts and tall sides), stair corners, door, bed and tall-plant halves, waterlogging where the block stands in water,
 * snowy grass under snow. Appliers place blueprint steps without neighbour updates, so this is what makes the result
 * final. Works in the local frame (neighbours through {@link Dirs}) and reads only the draft and the site.
 *
 * <p>The logic mirrors 26.3's own ({@code FenceBlock.connectsTo}, {@code WallBlock.updateShape},
 * {@code IronBarsBlock.attachsTo}, {@code StairBlock.getStairsShape}) without block tags, which are not bound outside a
 * running server (the offline preview and unit tests).
 */
public final class ShapeResolver {
	private static final VoxelShape TEST_POST = Block.column(2.0, 0.0, 16.0);
	private static final Map<Direction, VoxelShape> TEST_WALL = Shapes.rotateHorizontal(Block.boxZ(2.0, 16.0, 0.0, 9.0));
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final Draft draft;
	private final Site site;

	private ShapeResolver(Draft draft, Site site) {
		this.draft = draft;
		this.site = site;
	}

	/** Resolves every shape in the draft in place. */
	public static void resolve(Draft draft, Site site) {
		ShapeResolver r = new ShapeResolver(draft, site);
		r.pairs();
		r.connections();
		r.stairs();
		r.water();
		r.snowy();
	}

	private @Nullable BlockState at(int x, int y, int z) {
		return this.draft.get(x, y, z);
	}

	private @Nullable BlockState neighbour(int x, int y, int z, Direction d) {
		return at(x + Dirs.dx(d), y + Dirs.dy(d), z + Dirs.dz(d));
	}

	private interface Visit {
		void visit(int x, int y, int z, BlockState s);
	}

	private void each(Visit v) {
		List<long[]> list = new ArrayList<>();
		this.draft.forEach((x, y, z, c) -> list.add(new long[]{Draft.key(x, y, z)}));
		for (long[] k : list) {
			int x = Draft.keyX(k[0]);
			int y = Draft.keyY(k[0]);
			int z = Draft.keyZ(k[0]);
			BlockState s = at(x, y, z);
			if (s != null) {
				v.visit(x, y, z, s);
			}
		}
	}

	// ---------------------------------------------------------------- halves

	private void pairs() {
		each((x, y, z, s) -> {
			Block b = s.getBlock();
			if (b instanceof DoorBlock) {
				if (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					BlockState up = at(x, y + 1, z);
					BlockState want = s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
					if (up == null || up != want) {
						this.draft.restateOrSet(x, y + 1, z, want);
					}
				} else {
					BlockState down = at(x, y - 1, z);
					if (down == null || !down.is(b) || down.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
						this.draft.remove(x, y, z);
					}
				}
			} else if (b instanceof BedBlock) {
				Direction f = s.getValue(BedBlock.FACING);
				boolean foot = s.getValue(BedBlock.PART) == BedPart.FOOT;
				Direction toOther = foot ? f : f.getOpposite();
				BlockState o = neighbour(x, y, z, toOther);
				if (o == null || !o.is(b) || o.getValue(BedBlock.PART) == s.getValue(BedBlock.PART) || o.getValue(BedBlock.FACING) != f) {
					this.draft.remove(x, y, z);
				}
			} else if (b instanceof DoublePlantBlock && s.hasProperty(DoublePlantBlock.HALF)) {
				if (s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
					BlockState up = at(x, y + 1, z);
					if (up == null || up.isAir()) {
						this.draft.restateOrSet(x, y + 1, z, s.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
					} else if (!up.is(b)) {
						this.draft.remove(x, y, z);
					}
				} else {
					BlockState down = at(x, y - 1, z);
					if (down == null || !down.is(b)) {
						this.draft.remove(x, y, z);
					}
				}
			}
		});
	}

	// ---------------------------------------------------------------- connections

	private void connections() {
		each((x, y, z, s) -> {
			Block b = s.getBlock();
			if (b instanceof FenceBlock fence) {
				BlockState n = s;
				for (Direction d : HORIZONTAL) {
					BlockState o = neighbour(x, y, z, d);
					boolean c = o != null && fenceConnects(fence, o, d.getOpposite());
					n = n.setValue(property(d), c);
				}
				this.draft.restate(x, y, z, n);
			} else if (b instanceof IronBarsBlock) {
				BlockState n = s;
				for (Direction d : HORIZONTAL) {
					BlockState o = neighbour(x, y, z, d);
					boolean c = o != null && barsAttach(o, d.getOpposite());
					n = n.setValue(property(d), c);
				}
				this.draft.restate(x, y, z, n);
			}
		});
		// walls after fences and bars: their posts look at what is above, which may itself be a wall
		each((x, y, z, s) -> {
			if (s.getBlock() instanceof WallBlock) {
				this.draft.restate(x, y, z, wallShape(x, y, z, s));
			}
		});
	}

	private static BooleanProperty property(Direction d) {
		return CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d);
	}

	private static boolean exception(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LeavesBlock || b instanceof ShulkerBoxBlock || b == Blocks.BARRIER || b == Blocks.CARVED_PUMPKIN || b == Blocks.JACK_O_LANTERN
			|| b == Blocks.MELON || b == Blocks.PUMPKIN;
	}

	private static boolean sturdy(BlockState s, Direction face) {
		return s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
	}

	private static boolean wooden(Block fence) {
		return fence != Blocks.NETHER_BRICK_FENCE;
	}

	private static boolean gateConnects(BlockState gate, Direction face) {
		return gate.getValue(FenceGateBlock.FACING).getAxis() == face.getClockWise().getAxis();
	}

	/** FenceBlock.connectsTo: {@code face} is the neighbour's face toward the fence. */
	private static boolean fenceConnects(FenceBlock fence, BlockState o, Direction face) {
		Block ob = o.getBlock();
		boolean same = ob instanceof FenceBlock && wooden(ob) == wooden(fence);
		boolean gate = ob instanceof FenceGateBlock && gateConnects(o, face);
		return !exception(o) && sturdy(o, face) || same || gate;
	}

	/** IronBarsBlock.attachsTo (panes and bars). */
	private static boolean barsAttach(BlockState o, Direction face) {
		return !exception(o) && sturdy(o, face) || o.getBlock() instanceof IronBarsBlock || o.getBlock() instanceof WallBlock;
	}

	/** WallBlock.connectsTo. */
	private static boolean wallConnects(BlockState o, Direction face) {
		Block ob = o.getBlock();
		boolean gate = ob instanceof FenceGateBlock && gateConnects(o, face);
		return ob instanceof WallBlock || !exception(o) && sturdy(o, face) || ob instanceof IronBarsBlock || gate;
	}

	private BlockState wallShape(int x, int y, int z, BlockState s) {
		BlockState above = at(x, y + 1, z);
		VoxelShape aboveFace = above == null ? Shapes.empty()
			: above.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).getFaceShape(Direction.DOWN);
		BlockState n = s;
		for (Direction d : HORIZONTAL) {
			BlockState o = neighbour(x, y, z, d);
			boolean c = o != null && wallConnects(o, d.getOpposite());
			WallSide side = !c ? WallSide.NONE : covered(aboveFace, TEST_WALL.get(d)) ? WallSide.TALL : WallSide.LOW;
			n = n.setValue(wallProperty(d), side);
		}
		return n.setValue(WallBlock.UP, raisePost(n, above, aboveFace));
	}

	private static EnumProperty<WallSide> wallProperty(Direction d) {
		return WallBlock.PROPERTY_BY_DIRECTION.get(d);
	}

	private static boolean covered(VoxelShape aboveFace, VoxelShape test) {
		return !Shapes.joinIsNotEmpty(test, aboveFace, BooleanOp.ONLY_FIRST);
	}

	/** WallBlock.shouldRaisePost, with the post-override tag spelled out. */
	private static boolean raisePost(BlockState s, @Nullable BlockState above, VoxelShape aboveFace) {
		if (above != null && above.getBlock() instanceof WallBlock && above.getValue(WallBlock.UP)) {
			return true;
		}
		WallSide n = s.getValue(BlockStateProperties.NORTH_WALL);
		WallSide so = s.getValue(BlockStateProperties.SOUTH_WALL);
		WallSide e = s.getValue(BlockStateProperties.EAST_WALL);
		WallSide w = s.getValue(BlockStateProperties.WEST_WALL);
		boolean nn = n == WallSide.NONE;
		boolean sn = so == WallSide.NONE;
		boolean en = e == WallSide.NONE;
		boolean wn = w == WallSide.NONE;
		if (nn && sn && wn && en || nn != sn || wn != en) {
			return true;
		}
		boolean high = n == WallSide.TALL && so == WallSide.TALL || e == WallSide.TALL && w == WallSide.TALL;
		if (high) {
			return false;
		}
		return above != null && postOverride(above) || covered(aboveFace, TEST_POST);
	}

	/** {@code #minecraft:wall_post_override}: torches, signs, banners, pressure plates, tripwire, cactus flowers. */
	private static boolean postOverride(BlockState s) {
		Block b = s.getBlock();
		return b instanceof net.minecraft.world.level.block.TorchBlock || b instanceof net.minecraft.world.level.block.SignBlock
			|| b instanceof net.minecraft.world.level.block.AbstractBannerBlock || b instanceof net.minecraft.world.level.block.BasePressurePlateBlock
			|| b == Blocks.TRIPWIRE || b == Blocks.CACTUS_FLOWER;
	}

	// ---------------------------------------------------------------- stairs

	private void stairs() {
		List<long[]> updates = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		each((x, y, z, s) -> {
			if (s.getBlock() instanceof StairBlock) {
				StairsShape shape = stairShape(x, y, z, s);
				if (shape != s.getValue(StairBlock.SHAPE)) {
					updates.add(new long[]{Draft.key(x, y, z)});
					states.add(s.setValue(StairBlock.SHAPE, shape));
				}
			}
		});
		// apply after computing all, as the game would see them all at once
		for (int i = 0; i < updates.size(); i++) {
			long k = updates.get(i)[0];
			this.draft.restate(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k), states.get(i));
		}
	}

	private static boolean isStairs(@Nullable BlockState s) {
		return s != null && s.getBlock() instanceof StairBlock;
	}

	/** StairBlock.getStairsShape. */
	private StairsShape stairShape(int x, int y, int z, BlockState s) {
		Direction facing = s.getValue(StairBlock.FACING);
		BlockState behind = neighbour(x, y, z, facing);
		if (isStairs(behind) && s.getValue(StairBlock.HALF) == behind.getValue(StairBlock.HALF)) {
			Direction bf = behind.getValue(StairBlock.FACING);
			if (bf.getAxis() != facing.getAxis() && canTakeShape(s, x, y, z, bf.getOpposite())) {
				return bf == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
			}
		}
		BlockState front = neighbour(x, y, z, facing.getOpposite());
		if (isStairs(front) && s.getValue(StairBlock.HALF) == front.getValue(StairBlock.HALF)) {
			Direction ff = front.getValue(StairBlock.FACING);
			if (ff.getAxis() != facing.getAxis() && canTakeShape(s, x, y, z, ff)) {
				return ff == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
			}
		}
		return StairsShape.STRAIGHT;
	}

	private boolean canTakeShape(BlockState s, int x, int y, int z, Direction d) {
		BlockState n = neighbour(x, y, z, d);
		return !isStairs(n) || n.getValue(StairBlock.FACING) != s.getValue(StairBlock.FACING) || n.getValue(StairBlock.HALF) != s.getValue(StairBlock.HALF);
	}

	// ---------------------------------------------------------------- water and snow

	/** Waterlogs what stands in water; drops air steps under the water surface (they would drain it). */
	private void water() {
		each((x, y, z, s) -> {
			int wy = this.site.water(x, z);
			if (wy == Site.NONE || y >= wy || y < this.site.ground(x, z) || this.draft.drained(x, z)) {
				return;
			}
			StageKind stage = this.draft.cell(x, y, z).stage();
			boolean outdoors = stage == StageKind.CLEAR || stage == StageKind.EARTHWORK || stage == StageKind.FOUNDATION || stage == StageKind.EXTERIOR;
			if (s.isAir()) {
				// clearing never drains water; carving a building's inside below the water line is deliberate
				if (stage == StageKind.CLEAR) {
					this.draft.remove(x, y, z);
				}
			} else if (outdoors && s.hasProperty(BlockStateProperties.WATERLOGGED) && !s.getValue(BlockStateProperties.WATERLOGGED)) {
				this.draft.restate(x, y, z, s.setValue(BlockStateProperties.WATERLOGGED, true));
			}
		});
	}

	private void snowy() {
		each((x, y, z, s) -> {
			if (s.getBlock() instanceof SnowyBlock) {
				BlockState above = at(x, y + 1, z);
				boolean snow = above != null && (above.is(Blocks.SNOW) || above.is(Blocks.SNOW_BLOCK) || above.is(Blocks.POWDER_SNOW));
				if (s.getValue(SnowyBlock.SNOWY) != snow) {
					this.draft.restate(x, y, z, s.setValue(SnowyBlock.SNOWY, snow));
				}
			}
		});
	}
}
