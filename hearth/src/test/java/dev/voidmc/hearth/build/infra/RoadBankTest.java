package dev.voidmc.hearth.build.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
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

	/**
	 * A shallow perched pond on a rise (bed 69, top 70), the land falling away west of it; a track fords the pond at its
	 * bed and runs on down the slope (lane A1's "Roadsend": the ford's water ran down the road). The ford is laid as a
	 * causeway to the water's top, so no water block is left beside or above an opening the road made.
	 */
	@Test
	void aFordThroughAPerchedPondLetsNoWaterOut() {
		TerrainProbe t = new TerrainProbe() {
			@Override
			public long seed() {
				return 2;
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
				return perched(x, z) ? 69 : x >= -4 ? 71 : Math.max(55, 71 + (x + 4));
			}

			@Override
			public int waterY(int x, int z) {
				return perched(x, z) ? 70 : NO_WATER;
			}

			@Override
			public @Nullable ResourceKey<Biome> biome(int x, int z) {
				return null;
			}

			@Override
			public float temperature(int x, int z) {
				return 0.8F;
			}
		};
		int n = 49;
		int[] pts = new int[n * 3];
		for (int i = 0; i < n; i++) {
			int x = -24 + i;
			pts[i * 3] = x;
			pts[i * 3 + 1] = x >= -4 ? 69 : 69 + Math.floorDiv(x + 4, 2);
			pts[i * 3 + 2] = 0;
		}
		CompoundTag params = new CompoundTag();
		params.putString("kind", "track");
		params.putBoolean("country", true);
		PlotSpec spec = LinearWorksTest.linear("infra/road", Culture.TEMPERATE, 11, pts, 3, params, 58, 14, 0);
		BlueprintImpl bp = LinearWorksTest.SERVICE.compiled(spec, t);
		List<String> leaks = leaks(bp, t);
		assertTrue(leaks.isEmpty(), leaks.size() + " places water can leave: " + leaks.subList(0, Math.min(6, leaks.size())));
	}

	static boolean perched(int x, int z) {
		return x >= 0 && x <= 12 && z >= -6 && z <= 6;
	}

	/** Every water block of the finished world (the land overlaid with the blueprint) beside or above an opening. */
	static List<String> leaks(BlueprintImpl bp, TerrainProbe t) {
		Map<BlockPos, BlockState> laid = new HashMap<>();
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i).immutable();
				laid.put(p, bp.state(s, i));
				x0 = Math.min(x0, p.getX());
				x1 = Math.max(x1, p.getX());
				z0 = Math.min(z0, p.getZ());
				z1 = Math.max(z1, p.getZ());
				y0 = Math.min(y0, p.getY());
				y1 = Math.max(y1, p.getY());
			}
		}
		List<String> out = new ArrayList<>();
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int y = y0 - 1; y <= y1 + 1; y++) {
					if (kind(laid, t, x, y, z) != 2) {
						continue;
					}
					int[][] ds = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
					for (int[] d : ds) {
						if (kind(laid, t, x + d[0], y + d[1], z + d[2]) == 0) {
							out.add("water at " + x + "," + y + "," + z + " flows to " + (x + d[0]) + "," + (y + d[1]) + "," + (z + d[2]));
						}
					}
				}
			}
		}
		return out;
	}

	/** 0 air, 1 solid, 2 water in the finished world. */
	private static int kind(Map<BlockPos, BlockState> laid, TerrainProbe t, int x, int y, int z) {
		BlockState b = laid.get(new BlockPos(x, y, z));
		if (b != null) {
			return !b.getFluidState().isEmpty() ? 2 : b.isAir() ? 0 : 1;
		}
		if (y < t.groundY(x, z)) {
			return 1;
		}
		int w = t.waterY(x, z);
		return w != TerrainProbe.NO_WATER && y < w ? 2 : 0;
	}
}
