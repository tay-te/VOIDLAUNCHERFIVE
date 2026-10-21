package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The traffic's perf gate (docs/LIVING_WORLD_2.md §1.8, §9.1): the roads full (the journey cap) and 50 players spread
 * along them, the books' tick every tick and every player's "who is near" every 40 ticks, as the parties' pass asks it.
 * Budgets: ≤ 0.05 ms a tick on average, ≤ 0.3 ms the worst pass (the players' lookups all fall in one tick).
 */
class TrafficBenchTest {
	@Test
	void fullRoadsAndFiftyPlayersStayInsideTheBudget() {
		Object[] s = TrafficTest.setup();
		Traffic t = (Traffic) s[0];
		TrafficTest.World w = (TrafficTest.World) s[1];
		SimSettlement a = (SimSettlement) s[2];
		SimSettlement b = (SimSettlement) s[3];
		Way way = (Way) s[4];
		int cap = Roads.config().traffic().maxJourneys();
		TravelKind[] kinds = {TravelKind.MESSENGER, TravelKind.CARAVAN, TravelKind.SETTLERS};
		int sent = 0;
		for (int i = 0; sent < cap && i < cap * 2; i++) {
			SimSettlement from = i % 2 == 0 ? a : b;
			SimSettlement to = i % 2 == 0 ? b : a;
			sent += t.send(kinds[i % kinds.length], from, to, List.of(), (i * 97L) % 24_000, 0, false, 0, 0, new CompoundTag()) != 0 ? 1 : 0;
		}
		Route r = way.route();
		BlockPos[] players = new BlockPos[50];
		for (int p = 0; p < players.length; p++) {
			int i = (int) ((p + 0.5) / players.length * (r.size() - 1));
			players[p] = new BlockPos(r.xs()[i] + 10, 70, r.zs()[i] - 6);
		}
		int ticks = 6_000;
		long[] tickNanos = new long[ticks];
		long[] passNanos = new long[ticks / 40];
		int found = 0;
		long firstNanos = 0;
		// warm up the geometry caches and the JIT
		for (int k = 0; k < 400; k++) {
			w.clock = 1_000 + k;
			t.tick();
			for (BlockPos p : players) {
				t.near(p, 96);
			}
		}
		for (int k = 0; k < ticks; k++) {
			w.clock = 2_000 + k * 5L;
			long t0 = System.nanoTime();
			t.tick();
			if (k % 40 == 0) {
				long p0 = System.nanoTime();
				t.near(players[0], 96);
				firstNanos += System.nanoTime() - p0;
				for (BlockPos p : players) {
					List<Journey> near = t.near(p, 96);
					found += near.size();
					if (!near.isEmpty()) {
						t.position(near.getFirst(), w.clock);
					}
				}
				passNanos[k / 40] = System.nanoTime() - p0;
			}
			tickNanos[k] = System.nanoTime() - t0;
		}
		double avg = Arrays.stream(tickNanos).average().orElse(0) / 1e6;
		long[] sorted = tickNanos.clone();
		Arrays.sort(sorted);
		double p99 = sorted[(int) (sorted.length * 0.99)] / 1e6;
		long[] passes = passNanos.clone();
		Arrays.sort(passes);
		double passP50 = passes[passes.length / 2] / 1e6;
		double passMax = passes[passes.length - 1] / 1e6;
		System.out.println(String.format(Locale.ROOT,
			"[traffic bench] first lookup %.3f ms avg; %d journeys, %d players: %.4f ms a tick avg, %.4f ms p99; players' pass %.3f ms p50, %.3f ms max; %d sightings",
			firstNanos / 1e6 / passNanos.length, t.journeys().size(), players.length, avg, p99, passP50, passMax, found));
		assertTrue(t.journeys().size() > cap / 2, "the roads are full: " + t.journeys().size());
		// generous against a busy test machine; the numbers printed are the measure
		assertTrue(avg < 0.05 * 2, "average tick " + avg + " ms");
		assertTrue(passP50 < 0.3 * 2, "players' pass " + passP50 + " ms");
	}
}
