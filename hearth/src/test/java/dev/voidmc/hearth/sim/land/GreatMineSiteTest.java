package dev.voidmc.hearth.sim.land;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.chains.SimData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Great Mine reopened (docs/LIVING_WORLD_2.md §6.2, §6.8): a fake Expanse source's mine is found by the survey as the
 * Great Mine's ORE site, live and in a history's land, and its square is claimed for the towns that reach it.
 */
class GreatMineSiteTest {
	@BeforeAll
	static void boot() {
		TestSupport.boot();
	}

	static final GreatMines.Mine MINE = new GreatMines.Mine(new BlockPos(180, 141, -60), -40, List.of(110, 80, 50, 20, -10, -40));

	/** An underground with one great mine, as Expanse's cavern model would give. */
	static final class FakeUnderground implements UndergroundProbe, GreatMines.Provider {
		final List<GreatMines.Mine> mines = new ArrayList<>(List.of(MINE));

		@Override
		public @Nullable Realm realm(int x, int z) {
			return null;
		}

		@Override
		public List<Realm> realmsNear(int x, int z, int radius) {
			return List.of();
		}

		@Override
		public int floorY(int x, int z, int nearY) {
			return Integer.MIN_VALUE;
		}

		@Override
		public int ceilingY(int x, int z, int floorY) {
			return Integer.MIN_VALUE;
		}

		@Override
		public GreatMines.Source greatMines() {
			return (x, z, r) -> {
				List<GreatMines.Mine> out = new ArrayList<>();
				for (GreatMines.Mine m : this.mines) {
					if (Math.abs(m.pithead().getX() - x) <= r && Math.abs(m.pithead().getZ() - z) <= r) {
						out.add(m);
					}
				}
				return out;
			};
		}
	}

	/** A terrain probe that carries its underground, as a history's generator probe does (its land has none of its own). */
	record Carrying(TerrainProbe base, UndergroundProbe under) implements TerrainProbe {
		@Override
		public long seed() {
			return this.base.seed();
		}

		@Override
		public int minY() {
			return this.base.minY();
		}

		@Override
		public int maxY() {
			return this.base.maxY();
		}

		@Override
		public int seaLevel() {
			return this.base.seaLevel();
		}

		@Override
		public int groundY(int x, int z) {
			return this.base.groundY(x, z);
		}

		@Override
		public int waterY(int x, int z) {
			return this.base.waterY(x, z);
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return this.base.biome(x, z);
		}

		@Override
		public float temperature(int x, int z) {
			return this.base.temperature(x, z);
		}

		@Override
		public UndergroundProbe underground() {
			return this.under;
		}
	}

	private static List<Survey.Found> survey(Land land, int radius) {
		return Survey.run(land, new BlockPos(0, 64, 0), radius, SimData.builtin(), Survey.Params.DEFAULTS, (x, z) -> false);
	}

	private static Survey.@Nullable Found great(List<Survey.Found> found) {
		for (Survey.Found f : found) {
			if (GreatMines.MATERIAL.equals(f.material())) {
				return f;
			}
		}
		return null;
	}

	@Test
	void aTerritoryReachingAGreatMineFindsItsOreSiteOnTheShaft() {
		TestSupport.boot();
		SyntheticTerrain t = SyntheticTerrain.named("hills", 5);
		Survey.Found f = great(survey(Land.of(t, new FakeUnderground()), 256));
		assertNotNull(f, "the Great Mine is found");
		assertEquals(ResourceSite.Kind.ORE, f.kind());
		assertEquals(MINE.pithead(), f.center(), "the site stands on the shaft");
		assertTrue(f.capacity() > 10_000 && f.density() > 1, "rich and deep");
		assertTrue(f.area().minY() < MINE.shaftBottom(), "its workings reach the shaft's foot");
		assertTrue(f.yields()[Goods.ORE.ordinal()] > 0, "a miner's day there gives ore");
		// beyond the territory, or with no Expanse, no Great Mine
		assertNull(great(survey(Land.of(t, new FakeUnderground()), 150)));
		assertNull(great(survey(Land.of(t, null), 256)));
	}

	@Test
	void aHistorysLandFindsItThroughItsTerrainProbe() {
		TestSupport.boot();
		Land land = Land.of(new Carrying(SyntheticTerrain.named("hills", 5), new FakeUnderground()), null);
		assertNotNull(great(survey(land, 256)), "a history surveys the same mine");
	}

	@Test
	void theGreatMineIsMarkedAndKnownByItsShaft() {
		ResourceSite site = new ResourceSite(4, ResourceSite.Kind.ORE, MINE.pithead(), new net.minecraft.world.level.levelgen.structure.BoundingBox(MINE.pithead()),
			60_000, 60_000, 0, new float[Goods.VALUES.length], GreatMines.MATERIAL, PlotView.NONE);
		assertTrue(GreatMines.isGreat(site));
		assertEquals(MINE, GreatMines.at(new FakeUnderground().greatMines(), site));
		ResourceSite hill = new ResourceSite(5, ResourceSite.Kind.ORE, MINE.pithead(), new net.minecraft.world.level.levelgen.structure.BoundingBox(MINE.pithead()),
			600, 600, 0, new float[Goods.VALUES.length], Identifier.withDefaultNamespace("stone"), PlotView.NONE);
		assertFalse(GreatMines.isGreat(hill), "an ordinary ore hill is no Great Mine, wherever it lies");
		assertNull(GreatMines.at(new FakeUnderground().greatMines(), hill));
	}

	@Test
	void theSquareRoundTheShaftIsClaimedWhereATownWillReachIt() {
		GreatMines.Source src = new FakeUnderground().greatMines();
		int x = MINE.pithead().getX();
		int z = MINE.pithead().getZ();
		assertEquals(MINE, GreatMines.claiming(src, x + 12, z - 12, 12), "the square's corner");
		assertNull(GreatMines.claiming(src, x + 13, z, 12), "just outside it");
		assertNull(GreatMines.claiming(src, 0, 0, 12));
		// an atlas site whose largest territory takes the shaft in claims it; one too far does not
		assertTrue(GreatMines.reaches(List.of(new BlockPos(0, 64, 0)), MINE, 512));
		assertFalse(GreatMines.reaches(List.of(new BlockPos(-600, 64, 0)), MINE, 512));
		assertFalse(GreatMines.reaches(List.of(), MINE, 512));
	}
}
