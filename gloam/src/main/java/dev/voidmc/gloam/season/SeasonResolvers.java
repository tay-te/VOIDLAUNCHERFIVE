package dev.voidmc.gloam.season;

import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

/**
 * Vanilla's biome colour resolvers with the season on top ({@code BiomeColorsMixin} swaps them in): the colour vanilla
 * gives, turned by the current {@link SeasonColours} for the biome's climate. Called for every tinted block a chunk mesh
 * is built with (blended over the biome blend radius), on the meshing threads, so it stays allocation-free and cheap.
 *
 * <p>Grass turns in patches: each spot's year is moved a little by a soft noise about ten blocks across, so a field
 * yellows and browns unevenly, as real fields do. Crowns are varied tree by tree in {@link SeasonLeaves} instead.
 *
 * <p>While VOID LOD works out what blocks look like ({@code LodApi.bakingColours}) these give vanilla's own colour: the
 * far land takes the season per tile, through {@link SeasonLod}.
 */
public final class SeasonResolvers {
	private static final int GRASS = 0, FOLIAGE = 1, DRY_FOLIAGE = 2;
	/** How much of a season (as a share of the year) the grass patches run ahead of or behind the field. */
	private static final float PATCH_SHIFT = 0.03F;

	private SeasonResolvers() {
	}

	public static ColorResolver grass(ColorResolver vanilla) {
		return new Resolver(vanilla, GRASS);
	}

	public static ColorResolver foliage(ColorResolver vanilla) {
		return new Resolver(vanilla, FOLIAGE);
	}

	public static ColorResolver dryFoliage(ColorResolver vanilla) {
		return new Resolver(vanilla, DRY_FOLIAGE);
	}

	/** Whether {@code resolver} is one of these, and so gives the season's colours. */
	public static boolean seasonal(ColorResolver resolver) {
		return resolver instanceof Resolver;
	}

	/** Vanilla's colour for {@code biome} at (x, z) from one of these resolvers, without the season. */
	public static int vanilla(ColorResolver resolver, Biome biome, double x, double z) {
		return resolver instanceof Resolver r ? r.vanilla.getColor(biome, x, z) : resolver.getColor(biome, x, z);
	}

	private record Resolver(ColorResolver vanilla, int kind) implements ColorResolver {
		@Override
		public int getColor(Biome biome, double x, double z) {
			int base = this.vanilla.getColor(biome, x, z);
			SeasonColours colours = Seasons.colours();
			if (!colours.on() || Seasons.lodBaking()) {
				return base;
			}
			float t = biome.getBaseTemperature(), d = biome.climateSettings.downfall();
			int rgb = base & 0xFFFFFF;
			int out = switch (this.kind) {
				case GRASS -> colours.grass(rgb, t, d, PATCH_SHIFT * patch(x, z));
				case FOLIAGE -> colours.foliage(rgb, t, d);
				default -> colours.dryFoliage(rgb, t, d);
			};
			return base & 0xFF000000 | out;
		}
	}

	/** Soft patches about ten blocks across, with a finer ragged edge: -1..1. */
	static float patch(double x, double z) {
		return 0.75F * Hash.value(0x5EA5, x / 11.0, z / 11.0) + 0.25F * Hash.value(0x5EA6, x / 3.7, z / 3.7);
	}
}
