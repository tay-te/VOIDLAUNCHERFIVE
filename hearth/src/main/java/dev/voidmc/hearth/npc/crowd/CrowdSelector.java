package dev.voidmc.hearth.npc.crowd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who reacts to whom in one pass of the director (docs/LIVING_WORLD_2.md §5.3): the most salient player first when
 * several are near, each folk at most one reaction, at most {@code cap} folk a player, each folk reacting to the same
 * stimulus at most once a cooldown (a reaction that is still going on is refreshed instead, without a new cooldown). Pure.
 */
public final class CrowdSelector {
	private CrowdSelector() {
	}

	/** A folk near a player. */
	public record Candidate(UUID folk, Role role, double distance) {
	}

	/** One player in the pass: what the crowd notices about them, and the folk near them, nearest first. */
	public record PlayerInput(UUID player, List<Stimuli.Sensed> sensed, List<Candidate> near) {
	}

	/** A reaction a folk is in the middle of. */
	public record Ongoing(UUID player, Stimulus stimulus, Reaction reaction) {
	}

	/**
	 * @param rank    this folk's place among those reacting this way to the same stimulus (0 the first: the one who barks)
	 * @param refresh the folk was already reacting so: keep it going
	 */
	public record Assignment(UUID folk, UUID player, Stimulus stimulus, Reaction reaction, int rank, boolean refresh) {
	}

	/** Per folk, per stimulus: until when they will not react to it again. */
	public static final class Cooldowns {
		private final Map<UUID, long[]> until = new HashMap<>();

		public boolean ready(UUID folk, Stimulus s, long now) {
			long[] u = this.until.get(folk);
			return u == null || u[s.ordinal()] <= now;
		}

		public void mark(UUID folk, Stimulus s, long until) {
			this.until.computeIfAbsent(folk, k -> new long[Stimulus.VALUES.length])[s.ordinal()] = until;
		}

		/** Forgets folk whose every cooldown has run out. */
		public void prune(long now) {
			for (Iterator<long[]> it = this.until.values().iterator(); it.hasNext(); ) {
				long[] u = it.next();
				boolean live = false;
				for (long x : u) {
					if (x > now) {
						live = true;
						break;
					}
				}
				if (!live) {
					it.remove();
				}
			}
		}

		public int size() {
			return this.until.size();
		}

		public void clear() {
			this.until.clear();
		}
	}

	/**
	 * @param ongoing   reactions running now, by folk
	 * @param admitting whether new reactions may start (the server is not slow); refreshes always go on
	 */
	public static List<Assignment> select(List<PlayerInput> players, Map<UUID, Ongoing> ongoing, Cooldowns cooldowns, long now, long cooldownTicks,
		int capPerPlayer, boolean admitting) {
		List<PlayerInput> order = new ArrayList<>(players);
		// most salient first (stable: equal players keep their order)
		order.sort((a, b) -> Integer.compare(Stimuli.top(b.sensed()), Stimuli.top(a.sensed())));
		List<Assignment> out = new ArrayList<>();
		Set<UUID> taken = new HashSet<>();
		int[][] per = new int[Stimulus.VALUES.length][Reaction.VALUES.length];
		for (PlayerInput p : order) {
			for (int[] row : per) {
				java.util.Arrays.fill(row, 0);
			}
			int count = 0;
			for (Stimuli.Sensed sensed : p.sensed()) {
				Stimulus s = sensed.stimulus();
				for (Candidate c : p.near()) {
					if (count >= capPerPlayer) {
						break;
					}
					if (taken.contains(c.folk())) {
						continue;
					}
					Reaction r = Reaction.of(s, c.role(), c.distance());
					if (r == null || per[s.ordinal()][r.ordinal()] >= Reaction.limit(s, r)) {
						continue;
					}
					Ongoing o = ongoing.get(c.folk());
					boolean refresh = o != null && o.player().equals(p.player()) && o.stimulus() == s && o.reaction() == r;
					if (!refresh) {
						if (!admitting || o != null && o.reaction().priority() >= r.priority() || !cooldowns.ready(c.folk(), s, now)) {
							continue;
						}
						cooldowns.mark(c.folk(), s, now + cooldownTicks);
					}
					out.add(new Assignment(c.folk(), p.player(), s, r, per[s.ordinal()][r.ordinal()], refresh));
					per[s.ordinal()][r.ordinal()]++;
					taken.add(c.folk());
					count++;
				}
			}
		}
		return out;
	}
}
