package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.air.Noise;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the land around the camera for the soundscape: a few rings of columns out to 34 blocks (16 directions, 6
 * radii) and the columns right around the camera. Each column costs two heightmap reads and one block read (the
 * ground under the top: grass, sand, snow, still or running water; leaves above it), so a read is about 300 lookups,
 * done every {@link Soundscape#SCAN_TICKS} ticks. The results are eased between reads so walking past a pond or out
 * of a wood fades the beds rather than switching them.
 */
final class Land {
	private static final int[] RADII = {3, 6, 10, 16, 24, 34};
	private static final int DIRECTIONS = 16;
	private static final float[] COS = new float[DIRECTIONS], SIN = new float[DIRECTIONS];

	static {
		for (int i = 0; i < DIRECTIONS; i++) {
			COS[i] = (float) Math.cos(i * 2 * Math.PI / DIRECTIONS);
			SIN[i] = (float) Math.sin(i * 2 * Math.PI / DIRECTIONS);
		}
	}

	private Surroundings now = Surroundings.OPEN;
	private boolean read;

	/** The land as last read (eased), {@link Surroundings#OPEN} before the first read. */
	Surroundings now() {
		return this.now;
	}

	/** Forgets the land (a new world): the next read is taken as it is. */
	void reset() {
		this.now = Surroundings.OPEN;
		this.read = false;
	}

	/** Reads the land around {@code eye} and eases what it found into {@link #now()}. */
	void read(ClientLevel level, Vec3 eye) {
		int ex = (int) Math.floor(eye.x), ez = (int) Math.floor(eye.z);
		if (!level.hasChunk(ex >> 4, ez >> 4)) {
			return;
		}
		int sea = level.getSeaLevel();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		// overhead: leaves above the camera in the camera's column and the eight around it
		int canopy = 0;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex + dx, ez + dz);
				int leaves = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ex + dx, ez + dz);
				if (leaves > top && leaves > eye.y + 1) {
					canopy++;
				}
			}
		}
		// what is above the camera: a roof close overhead, or the whole hill over a cave
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez);
		float cover = surface - (float) eye.y;
		float roof = cover > 1 ? 1 - Noise.smoothstep(10, 20, cover) : 0;
		float buried = Noise.smoothstep(8, 24, cover);

		float trees = 0, all = 0, grass = 0, sand = 0, snow = 0, ground = 0;
		float stillW = 0, stillX = 0, stillZ = 0, runW = 0, runX = 0, runZ = 0;
		float groundSum = 0, nearestWater = 99;
		int groundCount = 0, outerWater = 0, outerAll = 0, waterSurface = Integer.MIN_VALUE;
		boolean landNear = !level.getFluidState(pos.set(ex, surface - 1, ez)).is(FluidTags.WATER);
		// the spread of the water columns around the camera, to tell a long narrow river from a round pond
		float wn = 0, wx = 0, wz = 0, wxx = 0, wzz = 0, wxz = 0;
		for (int d = 0; d < DIRECTIONS; d++) {
			for (int r : RADII) {
				int x = ex + Math.round(COS[d] * r), z = ez + Math.round(SIN[d] * r);
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int leaves = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				BlockState state = level.getBlockState(pos.set(x, top - 1, z));
				FluidState fluid = state.getFluidState();
				float near = 1 / (1 + r / 8.0F);
				all += near;
				if (leaves > top) {
					trees += near;
				}
				if (r >= 24) {
					outerAll++;
				}
				if (fluid.is(FluidTags.WATER)) {
					float w = 1 - r / 40.0F;
					nearestWater = Math.min(nearestWater, r);
					waterSurface = Math.max(waterSurface, top - 1);
					if (!fluid.isSource()) {
						runW += w;
						runX += COS[d] * w;
						runZ += SIN[d] * w;
					} else {
						stillW += w;
						stillX += COS[d] * w;
						stillZ += SIN[d] * w;
					}
					if (r >= 24) {
						outerWater++;
					}
					if (r <= 24) {
						float px = COS[d] * r, pz = SIN[d] * r;
						wn++;
						wx += px;
						wz += pz;
						wxx += px * px;
						wzz += pz * pz;
						wxz += px * pz;
					}
				} else {
					ground += near;
					if (state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.DIRT) || state.is(BlockTags.MOSS_BLOCKS) || state.is(BlockTags.MUD)) {
						grass += near;
					} else if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)) {
						sand += near;
					} else if (state.is(BlockTags.SNOW)) {
						snow += near;
					}
					if (r <= 24) {
						groundSum += top;
						groundCount++;
					}
					if (r <= 10) {
						landNear = true;
					}
				}
			}
		}
		if (all == 0) {
			return;
		}
		float water = stillW + runW;
		float still = Math.min(1, water / 4.0F);
		// open water: a good part of the outer rings is water; at its edge the waves come in (most on the sea)
		float openness = outerAll == 0 ? 0 : outerWater / (float) outerAll;
		float onSea = waterSurface >= sea - 2 && waterSurface <= sea + 1 ? 1 : 0.6F;
		float shore = Noise.smoothstep(0.15F, 0.45F, openness) * (1 - Noise.smoothstep(10, 36, nearestWater)) * (landNear ? 1 : 0.5F) * onSea;
		// running water: flowing blocks nearby, or a long, narrow ribbon of water above the sea (a river or a brook)
		float running = Math.min(1, runW / 1.5F);
		if (wn >= 3 && openness < 0.35F && waterSurface > sea + 1) {
			float mx = wx / wn, mz = wz / wn;
			float cxx = wxx / wn - mx * mx, czz = wzz / wn - mz * mz, cxz = wxz / wn - mx * mz;
			float tr = cxx + czz, det = cxx * czz - cxz * cxz;
			float gap = (float) Math.sqrt(Math.max(0, tr * tr / 4 - det));
			float major = tr / 2 + gap, minor = Math.max(0, tr / 2 - gap);
			float elongation = major > 0 ? 1 - minor / major : 0;
			running = Math.max(running, 0.7F * Noise.smoothstep(0.6F, 0.9F, elongation) * (1 - Noise.smoothstep(8, 30, nearestWater)));
		}
		float exposure = groundCount == 0 ? 0 : Noise.smoothstep(2, 24, (float) eye.y - groundSum / groundCount);
		float landShare = Math.max(ground, 1e-3F);

		float sx = dir(stillX + runX, water), sz = dir(stillZ + runZ, water);
		float rx = runW > 0 ? dir(runX, runW) : sx, rz = runW > 0 ? dir(runZ, runW) : sz;
		Surroundings found = new Surroundings(canopy / 9.0F, Math.min(1, trees / all * 1.6F), grass / landShare, sand / landShare, snow / landShare,
			still, running, shore, (float) eye.y - sea, exposure, roof, buried, sx, sz, rx, rz, sx, sz);
		this.now = this.read ? this.now.toward(found, 0.5F) : found;
		this.read = true;
	}

	/** A direction from a weighted sum of unit vectors: 8 blocks long when it all lies one way, shorter when spread. */
	private static float dir(float sum, float weight) {
		return weight > 0 ? 8 * sum / weight : 0;
	}
}
