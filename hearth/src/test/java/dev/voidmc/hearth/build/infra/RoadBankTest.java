package dev.voidmc.hearth.build.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A country road cut through a rise beside a pond that stands above the road (docs/LIVING_WORLD_2.md §12, risk 1: roads
 * written into land that exists): the cut keeps a bank up to the water's top on the pond's side, so the pond is never
 * let out down the road.
 */
class RoadBankTest {
	/** Ground standing at 70; a pond at z 2..10, x 0..10, its bed at 66 and its top at 70 (perched on the rise). */
	static final class PondProbe implements TerrainProbe {
		static boolean pond(int x, int z) {
			return x >= 0 && x <= 10 && z >= 2 && z <= 10;
		}

		@Override
		public long seed() {
			return 1;
		}

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int maxY() {
			return 320;
		}

		@Override
		public int seaLevel() {
			return 63;
		}

		@Override
		public int groundY(int x, int z) {
			return pond(x, z) ? 66 : 70;
		}

		@Override
		public int waterY(int x, int z) {
			return pond(x, z) ? 70 : NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return null;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.8F;
		}
	}

	@Test
	void aCutBesideAPondKeepsItsBank() {
		TerrainProbe t = new PondProbe();
		// a track along z = 0 at walking level 66: four blocks down into the rise, its edge column z = 1 beside the pond
		int n = 41;
		int[] pts = new int[n * 3];
		for (int i = 0; i < n; i++) {
			pts[i * 3] = -15 + i;
			pts[i * 3 + 1] = 66;
			pts[i * 3 + 2] = 0;
		}
		CompoundTag params = new CompoundTag();
		params.putString("kind", "track");
		params.putBoolean("country", true);
		PlotSpec spec = LinearWorksTest.linear("infra/road", Culture.TEMPERATE, 7, pts, 3, params, 66, 9, 0);
		BlueprintImpl bp = LinearWorksTest.SERVICE.compiled(spec, t);
		List<String> breaches = new ArrayList<>();
		int dug = 0;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				if (!bp.state(s, i).isAir()) {
					continue;
				}
				dug++;
				// an opening at the pond's level in a column beside it lets the water out
				boolean besidePond = PondProbe.pond(p.getX(), p.getZ() + 1) || PondProbe.pond(p.getX(), p.getZ() - 1) || PondProbe.pond(p.getX() + 1, p.getZ())
					|| PondProbe.pond(p.getX() - 1, p.getZ());
				if (besidePond && !PondProbe.pond(p.getX(), p.getZ()) && p.getY() < 70) {
					breaches.add("air at " + p.toShortString());
				}
			}
		}
		assertTrue(dug > 0, "the road cuts into the rise");
		assertTrue(breaches.isEmpty(), breaches.size() + " openings into the pond: " + breaches.subList(0, Math.min(6, breaches.size())));
	}
}
