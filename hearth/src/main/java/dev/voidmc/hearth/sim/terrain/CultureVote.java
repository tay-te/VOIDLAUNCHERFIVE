package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.hearth.api.Culture;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The culture of a site is the culture of its land, not of the strip of beach or riverbank its well happens to stand on:
 * a town on the shore of a taiga is a northern town. So the site's culture is voted on by the biomes round it (out to a
 * hundred blocks), where shores, rivers and the sea only count if there is nothing else.
 */
public final class CultureVote {
	private static final int[][] RING = {{0, 0}, {48, 0}, {-48, 0}, {0, 48}, {0, -48}, {96, 96}, {-96, 96}, {96, -96}, {-96, -96}, {96, 0}, {-96, 0},
		{0, 96}, {0, -96}};

	private CultureVote() {
	}

	public static Culture at(int x, int z, BiFunction<Integer, Integer, Holder<Biome>> biomes, Function<@Nullable Holder<Biome>, Culture> culture) {
		Map<Culture, Integer> land = new EnumMap<>(Culture.class);
		Map<Culture, Integer> shore = new EnumMap<>(Culture.class);
		for (int[] d : RING) {
			Holder<Biome> b = biomes.apply(x + d[0], z + d[1]);
			Culture c = culture.apply(b);
			boolean wet = b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH);
			// the centre counts double: it is where they chose to live
			(wet ? shore : land).merge(c, d[0] == 0 && d[1] == 0 ? 2 : 1, Integer::sum);
		}
		Map<Culture, Integer> votes = land.isEmpty() ? shore : land;
		Culture best = culture.apply(biomes.apply(x, z));
		int most = -1;
		for (Map.Entry<Culture, Integer> e : votes.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}
}
