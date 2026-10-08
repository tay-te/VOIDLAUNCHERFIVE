package dev.voidmc.hearth.plan.society;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The castle hill prefers the places the terrain marks for a castle ({@code TerrainProbe#site}: Expanse's level tops of
 * lone hills and escarpment rims, requests/expanse.md 2026-10-07) over ground merely as high.
 */
class CastleSiteTest {
	/** Flat land with a hilltop (or a rim) marked round (cx, cz): the same height as everywhere else, only the mark differs. */
	private record Marked(TerrainProbe land, int cx, int cz, int r, int site) implements TerrainProbe {
		@Override
		public long seed() {
			return this.land.seed();
		}

		@Override
		public int minY() {
			return this.land.minY();
		}

		@Override
		public int maxY() {
			return this.land.maxY();
		}

		@Override
		public int seaLevel() {
			return this.land.seaLevel();
		}

		@Override
		public int groundY(int x, int z) {
			return this.land.groundY(x, z);
		}

		@Override
		public int waterY(int x, int z) {
			return this.land.waterY(x, z);
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return this.land.biome(x, z);
		}

		@Override
		public float temperature(int x, int z) {
			return this.land.temperature(x, z);
		}

		@Override
		public int site(int x, int z) {
			return (x - this.cx) * (x - this.cx) + (z - this.cz) * (z - this.cz) <= this.r * this.r ? this.site : SITE_NONE;
		}
	}

	@Test
	void theCastleTakesTheMarkedHilltop() {
		check(TerrainProbe.SITE_HILLTOP);
	}

	@Test
	void theCastleTakesTheMarkedRim() {
		check(TerrainProbe.SITE_RIM);
	}

	private static void check(int site) {
		HearthServices.setBlueprints(new CatalogueDouble());
		SyntheticTerrain flat = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 7, 0.8F);
		BlockPos heart = new BlockPos(0, flat.groundY(0, 0), 0);
		// out past the streets, but nearer the heart than the far ring the search reaches
		Marked land = new Marked(flat, 64, 30, 12, site);
		MiniSim sim = new MiniSim(Culture.TEMPERATE, land, 7, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), heart);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		state.castleHill = null;
		PlanContext ctx = new PlanContext(sim.settlement, state, land, RandomSource.create(1), Catalogue.current(), PlanConfig.DEFAULTS);
		ctx.occ = state.occupancy;
		int[] hill = Nobility.hill(ctx);
		double off = Math.hypot(hill[0] - land.cx(), hill[1] - land.cz());
		assertTrue(off <= land.r() + 11, "the castle hill " + hill[0] + ", " + hill[1] + " is " + (int) off + " from the marked site");
	}
}
