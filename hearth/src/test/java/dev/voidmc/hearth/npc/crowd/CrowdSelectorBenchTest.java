package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the crowd's choice costs a pass (docs/LIVING_WORLD_2.md §5.4: ≤ 20 µs a player a pass in all): a market crowd of 24
 * round a player with a blade drawn, in armour, bloodied and known, half of them already reacting.
 */
class CrowdSelectorBenchTest {
	@Test
	void aPassesChoiceIsAFewMicroseconds() {
		List<CrowdSelector.Candidate> near = new ArrayList<>();
		Role[] roles = Role.VALUES;
		Map<UUID, CrowdSelector.Ongoing> ongoing = new HashMap<>();
		UUID player = new UUID(1, 1);
		for (int i = 0; i < 24; i++) {
			UUID id = new UUID(7, i);
			near.add(new CrowdSelector.Candidate(id, roles[i % roles.length], 1.5 + i * 0.6));
			if (i % 2 == 0) {
				ongoing.put(id, new CrowdSelector.Ongoing(player, Stimulus.BLADE, Reaction.SCATTER));
			}
		}
		List<Stimuli.Sensed> sensed = List.of(new Stimuli.Sensed(Stimulus.BLADE, 9), new Stimuli.Sensed(Stimulus.ARMOUR, 5),
			new Stimuli.Sensed(Stimulus.BLOODIED, 4), new Stimuli.Sensed(Stimulus.FRIEND, 2));
		List<CrowdSelector.PlayerInput> in = List.of(new CrowdSelector.PlayerInput(player, sensed, near));
		CrowdSelector.Cooldowns cd = new CrowdSelector.Cooldowns();
		int sink = 0;
		for (int i = 0; i < 20_000; i++) {
			sink += CrowdSelector.select(in, ongoing, cd, i * 10L, 1200, 12, true).size();
		}
		int n = 100_000;
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			sink += CrowdSelector.select(in, ongoing, cd, 200_000 + i * 10L, 1200, 12, true).size();
		}
		double us = (System.nanoTime() - t0) / 1e3 / n;
		System.out.printf(java.util.Locale.ROOT, "crowd selector: %.2f us a player a pass (24 folk, 4 stimuli)%n", us);
		assertTrue(sink > 0);
		assertTrue(us < 10, us + " us");
	}
}
