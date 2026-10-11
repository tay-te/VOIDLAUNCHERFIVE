package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.compat.ExpanseCaves;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the underground around the camera for the soundscape, one {@link CaveProbe} a tick through a
 * {@link CaveSurvey}, and eases what it finds into {@link #now()}.
 *
 * <p>Under the sky it costs one heightmap read a tick and nothing else. Once there are more than two blocks of ground
 * overhead it asks one point a tick: of Expanse ({@link ExpanseCaves}) where there is Expanse, or of the blocks where
 * there is not (six short runs from the point, at most 6 x 24 block reads). A survey ends every
 * {@link CaveSurvey#CYCLE} ticks with one look for real bats about.
 */
public final class Caves {
	/** How far a fallback probe runs in each direction looking for the cave's walls, blocks. */
	private static final int RUN = 24;
	/** The ease per tick toward the last survey: about two thirds of the way in one survey. */
	private static final float EASE = 0.045F;

	private final CaveSurvey survey = new CaveSurvey();
	private Hollow now = Hollow.NONE, target = Hollow.NONE;

	/** The underground as last surveyed (eased), {@link Hollow#NONE} under the sky. */
	public Hollow now() {
		return this.now;
	}

	/** Forgets the caves (a new world). */
	public void reset() {
		this.survey.clear();
		this.now = this.target = Hollow.NONE;
	}

	/** One tick: asks the next probe if the camera is under ground, and eases toward the last survey. */
	public void tick(ClientLevel level, Vec3 eye) {
		int ex = (int) Math.floor(eye.x), ey = (int) Math.floor(eye.y), ez = (int) Math.floor(eye.z);
		if (!level.hasChunk(ex >> 4, ez >> 4)) {
			return;
		}
		float cover = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez) - (float) eye.y;
		if (cover < 2) {
			if (this.target != Hollow.NONE) {
				this.survey.clear();
				this.target = Hollow.NONE;
			}
		} else {
			int slot = this.survey.next();
			int x = ex + Math.round(CaveSurvey.offsetX(slot)), z = ez + Math.round(CaveSurvey.offsetZ(slot));
			ExpanseCaves.Sampler expanse = ExpanseCaves.sampler(level);
			CaveProbe probe = expanse != null ? expanse.at(x, ey, z) : level.hasChunk(x >> 4, z >> 4) ? fromBlocks(level, x, ey, z, cover) : null;
			if (this.survey.put(probe)) {
				int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(eye));
				this.target = this.survey.result(eye.y, cover, sky, batsAbout(level, eye));
			}
		}
		this.now = this.now.toward(this.target, EASE);
	}

	/** Real bats flying near the camera, 0..1: three or more is a roost. */
	private static float batsAbout(ClientLevel level, Vec3 eye) {
		int count = level.getEntitiesOfClass(Bat.class, new AABB(eye, eye).inflate(24, 16, 24)).size();
		return Math.min(1, count / 3.0F);
	}

	/**
	 * A probe read from the blocks, where Expanse cannot say: the open run up, down and along each axis gives the
	 * space, the floor and the roof; the blocks the runs end on give water, drips, glow lichen (the vanilla caves'
	 * glowworms), a mine's planks, fences and rails, and lush or dripstone ground for the realm.
	 */
	static CaveProbe fromBlocks(ClientLevel level, int x, int y, int z, float cover) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		boolean underground = cover > 3;
		if (!level.getBlockState(pos).isAir()) {
			return CaveProbe.rock(y);
		}
		int[] run = new int[6];
		BlockState[] end = new BlockState[6];
		for (Direction d : Direction.values()) {
			int i = d.ordinal(), n = 0;
			BlockState state = null;
			while (n < RUN) {
				state = level.getBlockState(pos.set(x + d.getStepX() * (n + 1), y + d.getStepY() * (n + 1), z + d.getStepZ() * (n + 1)));
				if (!state.isAir()) {
					break;
				}
				n++;
			}
			run[i] = n;
			end[i] = state;
		}
		int down = Direction.DOWN.ordinal(), up = Direction.UP.ordinal();
		int vertical = run[down] + run[up];
		int ew = run[Direction.EAST.ordinal()] + run[Direction.WEST.ordinal()];
		int ns = run[Direction.NORTH.ordinal()] + run[Direction.SOUTH.ordinal()];
		float space = Math.max(1, Math.min(vertical, Math.min(ew, ns)) / 2.0F);
		int floor = y - run[down], ceiling = y + run[up];

		int water = CaveKinds.WATER_NONE, surface = Integer.MIN_VALUE;
		float distance = 1000;
		boolean mine = false, glow = false, drip = false;
		int realm = CaveKinds.WILDS;
		for (Direction d : Direction.values()) {
			BlockState state = end[d.ordinal()];
			if (state == null) {
				continue;
			}
			FluidState fluid = state.getFluidState();
			if (!fluid.isEmpty() && run[d.ordinal()] < distance) {
				boolean lava = fluid.is(FluidTags.LAVA);
				water = lava ? CaveKinds.LAVA : fluid.isSource() ? CaveKinds.LAKE : CaveKinds.RIVER;
				distance = d.getAxis() == Direction.Axis.Y ? 0 : run[d.ordinal()];
				surface = d == Direction.DOWN ? floor - 1 : y;
			}
			mine |= state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.RAILS) || state.is(Blocks.COBWEB);
			glow |= state.is(Blocks.GLOW_LICHEN);
			if (state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE)) {
				realm = CaveKinds.DRIPSTONE;
				drip = true;
			} else if (state.is(BlockTags.MOSS_BLOCKS) || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.CAVE_VINES)
				|| state.is(Blocks.CAVE_VINES_PLANT)) {
				realm = CaveKinds.LUSH;
				drip = true;
			}
		}
		drip |= water != CaveKinds.WATER_NONE && water != CaveKinds.LAVA;
		return new CaveProbe(underground, space, floor, ceiling, water, surface, distance, realm, 0, glow, false, drip, mine);
	}
}
