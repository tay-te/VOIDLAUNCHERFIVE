package dev.voidmc.lod.source;

import dev.voidmc.expanse.registry.LandformBlocks;
import dev.voidmc.expanse.world.terrain.Landform;
import dev.voidmc.lod.core.BiomeIds;
import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.Colors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

/**
 * Expanse's land as it looks from afar, from what its terrain model says ({@link ExpanseFacts}) and the biome: rivers cut
 * to their own water level and threads of water where narrower than a cell, crater lakes and lava, canyon walls in the
 * world's strata, white salt flats, snow where the biome's own temperature, falling with height, lays it, bare rock
 * above the tree line on young ranges, beaches. The same for the singleplayer source and for land a server describes
 * ({@code RemoteSource}): the colours are the client's own textures either way.
 */
public final class ExpanseColours {
	private final BiomeLooks looks;
	private final int seaLevel;
	private final int[] strata;
	private final int[] strataMaterials;
	private final int salt;
	private final int ash;
	private final int basalt;
	private final int saltTopMaterial;
	private final int saltSideMaterial;
	private final int basaltTopMaterial;
	private final int basaltSideMaterial;
	/** The rocks Expanse lays under the soil in place of stone ({@code Column.rock}: sandstone, limestone, basalt). */
	private final int[] rocks = new int[4];
	private final int[] rockMaterials = new int[4];

	/** @param strata the world's badlands strata, one colour per block (null: none), and their blocks' material ids */
	public ExpanseColours(BiomeLooks looks, Materials materials, int seaLevel, int[] strata, int[] strataMaterials) {
		this.looks = looks;
		this.seaLevel = seaLevel;
		this.strata = strata;
		this.strataMaterials = strataMaterials;
		this.salt = looks.colorOf(LandformBlocks.SALT_BLOCK.defaultBlockState());
		this.ash = looks.colorOf(LandformBlocks.VOLCANIC_ASH.defaultBlockState());
		this.basalt = looks.colorOf(Blocks.BASALT.defaultBlockState());
		this.saltTopMaterial = materials.id(LandformBlocks.SALT_BLOCK.defaultBlockState(), true);
		this.saltSideMaterial = materials.id(LandformBlocks.SALT_BLOCK.defaultBlockState(), false);
		this.basaltTopMaterial = materials.id(Blocks.BASALT.defaultBlockState(), true);
		this.basaltSideMaterial = materials.id(Blocks.BASALT.defaultBlockState(), false);
		net.minecraft.world.level.block.state.BlockState[] rocks = {null, Blocks.SANDSTONE.defaultBlockState(),
			dev.voidmc.expanse.registry.ExpanseBlocks.LIMESTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState()};
		for (int r = 1; r < 4; r++) {
			this.rocks[r] = looks.colorOf(rocks[r]);
			this.rockMaterials[r] = materials.id(rocks[r], false);
		}
	}

	public int seaLevel() {
		return this.seaLevel;
	}

	/**
	 * Fills {@code out} for the column (x, z) of a {@code cell}-wide cell: {@code f} with its {@link ExpanseFacts#geometry}
	 * worked out, in {@code biome}; {@code bandOffset} shifts the strata as the world's band noise does there.
	 */
	public void colour(ExpanseFacts f, Holder<Biome> biome, int x, int z, int cell, int bandOffset, ColumnSample out) {
		float ground = f.ground;
		int water = f.water;
		int top = (int) Math.ceil(ground);
		out.biome = biome.unwrapKey().map(k -> BiomeIds.id(k.identifier().toString())).orElse(-1);
		BiomeLooks.Look look = this.looks.get(biome);
		look.fill(out);
		out.ground = ground;
		out.water = water;
		out.lava = f.lava;
		if (f.lava) {
			out.waterColor = this.looks.lava;
		}
		if (f.rock > 0 && f.rock < 4) {
			// the generator lays it in place of the stone and its veins under the soil
			out.rock = this.rocks[f.rock];
			out.rockMaterial = this.rockMaterials[f.rock];
		}
		if (out.bands != null && this.strata != null) {
			out.bands = this.strata;
			out.bandMaterials = this.strataMaterials;
			out.bandHeight = 1;
			out.bandOffset = bandOffset;
		}
		if (out.bands != null) {
			out.side = ColumnSample.rockAt(out.bands, out.bandHeight, out.rock, top - 1 + out.bandOffset);
			out.sideMaterial = ColumnSample.rockMaterialAt(out.bandMaterials, out.bandHeight, out.rockMaterial, top - 1 + out.bandOffset);
		}
		boolean wet = water != Integer.MIN_VALUE && water > ground;
		if (wet) {
			out.top = look.bed();
			out.canopy = 0;
			out.cover = 0;
			return;
		}
		int landform = f.landform;
		switch (landform) {
			case Landform.SALT_FLAT -> {
				out.top = out.side = out.soil = this.salt;
				out.topMaterial = this.saltTopMaterial;
				out.sideMaterial = out.soilMaterial = this.saltSideMaterial;
				out.cover = 0;
			}
			case Landform.VOLCANIC -> {
				out.top = Colors.mix(this.basalt, this.ash, 0.4F);
				out.side = out.soil = out.rock = this.basalt;
				out.topMaterial = this.basaltTopMaterial;
				out.sideMaterial = out.soilMaterial = out.rockMaterial = this.basaltSideMaterial;
				out.cover = 0;
			}
			default -> {
			}
		}
		if (landform == Landform.NONE || landform == Landform.FJORD || landform == Landform.TEPUI) {
			// snow where the chunks will lay it: the biome's own temperature at this height
			float snow = this.snow(biome, x, top, z, cell);
			// steep soil the generator strips to the rock under it (not strata, which are no soil; not under snow)
			// (never next to the sea or a river, as the generator leaves those)
			out.bareWhenSteep = out.bands == null && snow < 0.5F
				&& (look.ground() == BiomeLooks.Ground.GRASS || look.ground() == BiomeLooks.Ground.PODZOL)
				&& f.height >= this.seaLevel + 1 && !(f.nearRiver && f.riverDist < f.riverHalfWidth + 1.5F);
			// bare rock above the tree line on high, young ranges
			float bare = Colors.smooth(135, 175, top) * Colors.smooth(0.95F, 1.25F, f.uplift);
			if (look.ground() == BiomeLooks.Ground.GRASS || look.ground() == BiomeLooks.Ground.PODZOL) {
				out.top = Colors.mix(out.top, this.looks.stone, bare * 0.9F);
				out.side = Colors.mix(out.side, this.looks.stone, bare);
				// a mixed colour is textured as its larger share
				if (bare * 0.9F >= 0.5F) {
					out.topMaterial = this.looks.stoneTopMaterial;
				}
				if (bare >= 0.5F) {
					out.sideMaterial = this.looks.stoneSideMaterial;
				}
				if (top <= this.seaLevel + 2 && f.coast < 48) {
					// beaches
					float beach = Colors.smooth(48, 20, f.coast);
					out.top = Colors.mix(out.top, this.looks.sand, beach);
					out.side = Colors.mix(out.side, this.looks.sand, beach);
					if (beach >= 0.5F) {
						out.topMaterial = this.looks.sandTopMaterial;
						out.sideMaterial = this.looks.sandSideMaterial;
					}
				}
			}
			if (snow > 0) {
				if (look.ground() != BiomeLooks.Ground.SNOWCAP) {
					out.top = Colors.mix(out.top, this.looks.snowTop, snow);
					out.side = Colors.mix(out.side, this.looks.snowSide, snow * 0.8F);
					if (snow >= 0.5F) {
						out.topMaterial = this.looks.snowTopMaterial;
					}
					if (snow * 0.8F >= 0.5F) {
						out.sideMaterial = this.looks.snowSideMaterial;
					}
				}
				if (out.canopy > 0) {
					out.leavesTop = Colors.mix(out.leaves, this.looks.snowTop, snow * 0.9F);
				}
			}
			out.cover *= 1 - bare;
		}
		if (f.river > 0) {
			int surface = Colors.water(out.waterColor, look.bed(), 2, false);
			out.top = Colors.mix(out.top, surface, f.river);
			out.cover *= 1 - f.river;
			if (f.river >= 0.5F) {
				// mostly a thread of water: no block texture
				out.topMaterial = 0;
			}
		}
	}

	/**
	 * How much of the ground here lies under snow, by vanilla's own test (the biome's temperature, falling with height,
	 * ragged with its noise), so the snow line is where the chunks will put it; out where a cell spans a stretch of slope,
	 * a soft band around the line.
	 */
	private float snow(Holder<Biome> biome, int x, int y, int z, int cell) {
		Biome b = biome.value();
		if (b.getBaseTemperature() < 0.15F) {
			return 1;
		}
		if (cell <= 2) {
			return b.coldEnoughToSnow(new BlockPos(x, y, z), this.seaLevel) ? 1 : 0;
		}
		float line = this.seaLevel + 17 + (b.getBaseTemperature() - 0.15F) / 0.00125F;
		float soft = 8 + cell;
		return Colors.smooth(line - soft, line + soft, y);
	}
}
