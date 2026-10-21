package dev.voidmc.hearth.npc.work.field;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The remote sites' pass at the design's scale (docs/LIVING_WORLD_2.md §6.4, §9.1: ≤ 10 µs per player per 40 ticks):
 * 200 towns over a 12 000-block square, 12 sites each, 50 players. Each player costs the town prefilter (one distance a
 * town) and, for the few towns in reach, the sites' checks — the arithmetic of {@link RemoteEmbodiment#near}, without
 * the level. The measured figure is printed; the assertion is loose (×5) so a busy machine does not fail it.
 */
class RemoteSitesCostTest {
	private record Town(BlockPos center, int territory, List<RemoteSites.Site> sites) {
	}

	@Test
	void aPassCostsAFewMicrosecondsAPlayer() {
		Random r = new Random(20261021);
		List<Town> towns = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			BlockPos c = new BlockPos(r.nextInt(12_000) - 6_000, 70, r.nextInt(12_000) - 6_000);
			List<RemoteSites.Site> sites = new ArrayList<>();
			for (int k = 0; k < 12; k++) {
				double a = r.nextDouble() * Math.PI * 2;
				int d = 80 + r.nextInt(220);
				BlockPos w = c.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
				sites.add(new RemoteSites.Site(RemoteSites.Kind.PASTURE, w, c.offset(10, 0, 0), 12, k, k, true));
			}
			towns.add(new Town(c, 160, sites));
		}
		List<BlockPos> players = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			// players near towns, as they are on a server
			BlockPos c = towns.get(r.nextInt(towns.size())).center();
			players.add(c.offset(r.nextInt(600) - 300, 0, r.nextInt(600) - 300));
		}
		FieldWorkConfig.Sites cfg = FieldWorkConfig.Sites.DEFAULTS;
		double reach = cfg.embodyDistance() + 16;
		int hits = 0;
		// warm up, then time
		for (int round = 0; round < 2_000; round++) {
			hits += pass(towns, players, cfg, reach);
		}
		int rounds = 2_000;
		long t0 = System.nanoTime();
		for (int round = 0; round < rounds; round++) {
			hits += pass(towns, players, cfg, reach);
		}
		long nanos = System.nanoTime() - t0;
		double perPlayer = nanos / 1e3 / rounds / players.size();
		System.out.println(String.format(Locale.ROOT, "[hearth work] remote sites pass: %.2f us a player (200 towns, 12 sites, 50 players; %d hits)",
			perPlayer, hits));
		assertTrue(perPlayer <= 50, "remote sites pass " + perPlayer + " us a player");
	}

	private static int pass(List<Town> towns, List<BlockPos> players, FieldWorkConfig.Sites cfg, double reach) {
		int hits = 0;
		for (Town t : towns) {
			double townReach = t.territory() + cfg.pastureDistance() + reach;
			boolean anyNear = false;
			for (BlockPos p : players) {
				if (Commute.distance(p, t.center()) <= townReach) {
					anyNear = true;
					break;
				}
			}
			if (!anyNear) {
				continue;
			}
			for (RemoteSites.Site s : t.sites()) {
				for (BlockPos p : players) {
					if (RemoteEmbodiment.nearSite(p, s, reach)) {
						hits++;
						break;
					}
				}
			}
		}
		return hits;
	}
}
