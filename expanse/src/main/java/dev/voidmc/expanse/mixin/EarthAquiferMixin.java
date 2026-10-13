package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.Expanse;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Earth's aquifers answer at once where every aquifer around a block agrees.
 *
 * <p>Vanilla finds, for each open block under the aquifers' ceiling, the nearest four of the twelve aquifer
 * centres around it and blends their fluid levels into barriers. When all twelve have the same fluid status,
 * none of that can change the answer: the block is that status's fluid (or air) at its height, every barrier
 * between two equal statuses is 0 so none closes the block, and no two neighbours differ, so no fluid update
 * is scheduled. The one exception, water straight over the lava sea, is left to vanilla. The Earth keeps
 * its aquifers out of everywhere ({@code expanse:earth/caverns_dry}), so whole neighbourhoods agree (all dry
 * underground, all sea or sky above it) far more often than not, and only the seams between them still take
 * vanilla's search.
 *
 * <p>The aquifer cannot be dropped for the disabled one: a dry aquifer centre leaves a cave below the sea
 * level open, where the disabled aquifer would fill it with the sea, and between a dry and a sea centre the
 * barriers close off open blocks with stone.
 *
 * <p>Every answer is vanilla's to the block; the centres it computes ahead are the ones vanilla would, from
 * the same random and samplers. Only aquifers built from the Earth's settings for a whole chunk's fill take
 * part: one built for a single column (the base heights) asks too few blocks per neighbourhood to repay
 * filling all twelve centres, and keeps vanilla's search. Off with {@code -Dexpanse.fast.noaquifer=false}.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
abstract class EarthAquiferMixin {
	@Unique
	private static final boolean FAST = Boolean.parseBoolean(System.getProperty("expanse.fast.noaquifer", "true"));
	@Unique
	private static final Identifier EARTH_EXCLUSION = Expanse.id("earth/caverns_dry");
	/** The mark of a neighbourhood whose centres do not all agree. */
	@Unique
	private static final Aquifer.FluidStatus MIXED = new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());

	@Shadow
	@Final
	private PositionalRandomFactory positionalRandomFactory;
	@Shadow
	@Final
	private long[] aquiferLocationCache;
	@Shadow
	@Final
	private Aquifer.FluidPicker globalFluidPicker;
	@Shadow
	private boolean shouldScheduleFluidUpdate;
	@Shadow
	@Final
	private int skipSamplingAboveY;
	@Shadow
	@Final
	private Aquifer.@Nullable FluidStatus[] aquiferCache;

	/** Per neighbourhood (by the index of its anchor centre): the status all twelve centres share, {@link #MIXED}, or null until asked. */
	@Unique
	private Aquifer.@Nullable FluidStatus[] expanse$agreed;

	@Shadow
	private int getIndex(int gridX, int gridY, int gridZ) {
		throw new AssertionError();
	}

	@Shadow
	private Aquifer.FluidStatus getAquiferStatus(int index) {
		throw new AssertionError();
	}

	@Shadow
	private static int gridX(int blockCoord) {
		throw new AssertionError();
	}

	@Shadow
	private static int gridY(int blockCoord) {
		throw new AssertionError();
	}

	@Shadow
	private static int gridZ(int blockCoord) {
		throw new AssertionError();
	}

	@Shadow
	private static int fromGridX(int gridCoord, int blockOffset) {
		throw new AssertionError();
	}

	@Shadow
	private static int fromGridY(int gridCoord, int blockOffset) {
		throw new AssertionError();
	}

	@Shadow
	private static int fromGridZ(int gridCoord, int blockOffset) {
		throw new AssertionError();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void expanse$earth(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
		DensityVolume volume, Aquifer.FluidPicker globalFluidPicker, CallbackInfo ci) {
		if (FAST && volume.sizeX() >= 16 && volume.sizeZ() >= 16
			&& config.exclusion() instanceof DensityFunctions.HolderHolder exclusion && exclusion.function().is(EARTH_EXCLUSION)) {
			this.expanse$agreed = new Aquifer.FluidStatus[this.aquiferCache.length];
		}
	}

	@Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
	private void expanse$whereAllAgree(int blockX, int blockY, int blockZ, double density, CallbackInfoReturnable<@Nullable BlockState> cir) {
		Aquifer.FluidStatus[] agreed = this.expanse$agreed;
		// vanilla's own early answers (solid, the open sky, the lava sea) stay vanilla's
		if (agreed == null || density > 0.0 || blockY > this.skipSamplingAboveY
			|| this.globalFluidPicker.computeFluid(blockX, blockY, blockZ).at(blockY).is(Blocks.LAVA)) {
			return;
		}
		int xAnchor = gridX(blockX + -5);
		int yAnchor = gridY(blockY + 1);
		int zAnchor = gridZ(blockZ + -5);
		int anchor = this.getIndex(xAnchor, yAnchor, zAnchor);
		Aquifer.FluidStatus status = agreed[anchor];
		if (status == null) {
			status = agreed[anchor] = this.expanse$agree(xAnchor, yAnchor, zAnchor);
		}
		if (status == MIXED) {
			return;
		}
		BlockState fluid = status.at(blockY);
		if (fluid.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(blockX, blockY - 1, blockZ).at(blockY - 1).is(Blocks.LAVA)) {
			return;
		}
		this.shouldScheduleFluidUpdate = false;
		cir.setReturnValue(SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : fluid);
	}

	/** The status all twelve centres around an anchor share, or {@link #MIXED}; each centre placed and filled as vanilla does it. */
	@Unique
	private Aquifer.FluidStatus expanse$agree(int xAnchor, int yAnchor, int zAnchor) {
		Aquifer.FluidStatus first = null;
		for (int x1 = 0; x1 <= 1; x1++) {
			for (int y1 = -1; y1 <= 1; y1++) {
				for (int z1 = 0; z1 <= 1; z1++) {
					int spacedGridX = xAnchor + x1;
					int spacedGridY = yAnchor + y1;
					int spacedGridZ = zAnchor + z1;
					int index = this.getIndex(spacedGridX, spacedGridY, spacedGridZ);
					if (this.aquiferLocationCache[index] == Long.MAX_VALUE) {
						RandomSource random = this.positionalRandomFactory.at(spacedGridX, spacedGridY, spacedGridZ);
						this.aquiferLocationCache[index] = BlockPos.asLong(
							fromGridX(spacedGridX, random.nextInt(10)), fromGridY(spacedGridY, random.nextInt(9)), fromGridZ(spacedGridZ, random.nextInt(10))
						);
					}
					Aquifer.FluidStatus status = this.getAquiferStatus(index);
					if (first == null) {
						first = status;
					} else if (!first.equals(status)) {
						return MIXED;
					}
				}
			}
		}
		return first;
	}
}
