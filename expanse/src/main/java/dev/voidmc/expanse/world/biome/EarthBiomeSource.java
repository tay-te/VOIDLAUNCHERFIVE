package dev.voidmc.expanse.world.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * The Earth generator's biome source: the overworld's, with vanilla's cave biomes swapped for the
 * Expanse's own ({@link UndergroundBiomes}). The swap lives here and not in the overworld's biome table
 * because that table is shared by every world: a world without the cavern model (large biomes, amplified,
 * the Earth pack switched off) keeps vanilla's lush caves, dripstone and deep dark, grown by vanilla's
 * features, while the Earth's caverns get biomes that {@link dev.voidmc.expanse.world.terrain.CavernLife}
 * decorates.
 *
 * <p>Built by {@link dev.voidmc.expanse.world.terrain.EarthChunkGenerator} around the source it was given,
 * which is the one it saves; this one is never written to a world, but has a codec as every source must.
 */
public final class EarthBiomeSource extends BiomeSource {
	public static final MapCodec<EarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BiomeSource.CODEC.fieldOf("source").forGetter(s -> s.source),
		RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(i, i.stable(EarthBiomeSource::new)));

	private static final List<ResourceKey<Biome>> VANILLA = List.of(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK, Biomes.SULFUR_CAVES);

	private final BiomeSource source;
	/** Ours for each of {@link #VANILLA}, or null where the biome is missing (the swap is then skipped). */
	private final Holder<Biome>[] ours;

	@SuppressWarnings("unchecked")
	public EarthBiomeSource(BiomeSource source, HolderGetter<Biome> biomes) {
		this.source = source;
		this.ours = VANILLA.stream()
			.map(b -> biomes.get(UndergroundBiomes.remap(b)).<Holder<Biome>>map(h -> h).orElse(null))
			.toArray(Holder[]::new);
	}

	public BiomeSource source() {
		return this.source;
	}

	private Holder<Biome> swap(Holder<Biome> b) {
		for (int k = 0; k < this.ours.length; k++) {
			if (this.ours[k] != null && b.is(VANILLA.get(k))) {
				return this.ours[k];
			}
		}
		return b;
	}

	private BiomeResolver swap(BiomeResolver resolver) {
		return (quartX, quartY, quartZ) -> this.swap(resolver.getNoiseBiome(quartX, quartY, quartZ));
	}

	@Override
	protected MapCodec<EarthBiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return this.source.possibleBiomes().stream().map(this::swap);
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return this.swap(this.source.createResolver(sampler));
	}

	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
		int quartSizeX, int quartSizeY, int quartSizeZ) {
		return this.swap(this.source.createResolverForChunk(sampler, minQuartX, minQuartY, minQuartZ, quartSizeX, quartSizeY, quartSizeZ));
	}

	@Override
	public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
		this.source.addDebugInfo(result, feetPos, sampler);
	}
}
