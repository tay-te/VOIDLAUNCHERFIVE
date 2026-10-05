package dev.voidmc.mettle.burden;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Works out every item's weight (BURDEN.md §1) from fixed rules (overrides, leaves) and a recipe graph. Pure: no
 * registries, no server, so JUnit can feed it vanilla's recipe files directly.
 *
 * <ol>
 * <li>Fixed rules win. A {@link Rule.Plus} ("a bucket and 1 kg of water") is a fixed rule that depends on another item.</li>
 * <li>Recipes: an item weighs the lightest way to make it, Σ lightest ingredient option (minus what the grid hands
 * back, like the bucket) ÷ output count. Solved as a shortest-path fixed point with bounded passes, so cycles (ingot ↔
 * nugget ↔ block) settle on the leaf that anchors them and never loop.</li>
 * <li>Cooking only fills gaps: an item no crafting recipe reaches weighs what it was cooked from (cooked food weighs the
 * same as raw).</li>
 * <li>Fallback: technical items 0; anything else 4 kg ÷ max stack size. Items that only exist inside an unanchored
 * cycle (slime ball ↔ slime block) are anchored at their "loose" member, the one recipes hand out several at a time.</li>
 * </ol>
 */
public final class WeightDeriver {
	/** Fallback: a full stack of anything weighs this. */
	public static final float FALLBACK_STACK_KG = 4.0f;
	private static final int MAX_PASSES = 100;
	private static final float EPSILON = 1e-5f;

	private WeightDeriver() {
	}

	/** A fixed weight rule (leaf or override). */
	public sealed interface Rule {
		/** A plain weight. {@code source} is "leaf" or "override", for explanations. */
		record Fixed(float kg, String source) implements Rule {
		}

		/** Another item's weight plus a constant (a filled bucket, an ore = its stone + 0.1). */
		record Plus(Item base, float extra, String source) implements Rule {
		}
	}

	/** One way to make {@code count} of {@code output}: each slot takes one of its options. */
	public record Edge(@Nullable Identifier id, Item output, int count, List<List<Item>> slots, boolean cooking) {
	}

	/** How a weight was reached, for {@code /mettle weight}. */
	public sealed interface How {
		record Fixed(String source) implements How {
		}

		record Plus(Item base, float extra, String source) implements How {
		}

		record Crafted(@Nullable Identifier recipe, int count, List<Part> inputs, List<Part> remainders, boolean cooking) implements How {
		}

		record Fallback(int maxStack) implements How {
		}

		record Technical() implements How {
		}
	}

	/** {@code n} of one item in a recipe, at the weight it had when the recipe was chosen. */
	public record Part(Item item, int n) {
	}

	public record Result(Map<Item, Float> weights, Map<Item, How> how) {
		public float weight(Item item) {
			Float w = this.weights.get(item);
			return w == null ? 0f : w;
		}
	}

	/** One edge with items as indices, so passes are array reads. */
	private record Compiled(Edge edge, int output, int[][] slots, boolean plus, float extra) {
	}

	public static Result derive(Collection<Item> items, Map<Item, Rule> rules, List<Edge> edges, Function<Item, @Nullable Item> remainder,
		ToIntFunction<Item> maxStack, Predicate<Item> technical, Comparator<Item> order) {
		List<Item> list = new ArrayList<>(items);
		Map<Item, Integer> index = new IdentityHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			index.put(list.get(i), i);
		}
		int n = list.size();
		float[] w = new float[n];
		How[] how = new How[n];
		Edge[] chosenEdge = new Edge[n];
		int[][] chosen = new int[n][];
		boolean[] locked = new boolean[n];
		// Weights that rest on a fallback are guesses: a recipe made of real weights always beats one made of guesses,
		// however light the guess (stripped bamboo is not 4 kg ÷ 64 just because nothing crafts it).
		boolean[] guess = new boolean[n];
		int[] rem = new int[n];
		for (int i = 0; i < n; i++) {
			w[i] = Float.NaN;
			Item r = remainder.apply(list.get(i));
			Integer ri = r == null ? null : index.get(r);
			rem[i] = ri == null ? -1 : ri;
		}

		// Fixed rules. Plus rules become one-slot edges that are the only way to their output.
		List<Compiled> ruleEdges = new ArrayList<>();
		boolean[] ruled = new boolean[n];
		rules.forEach((item, rule) -> {
			Integer i = index.get(item);
			if (i == null) {
				return;
			}
			ruled[i] = true;
			switch (rule) {
				case Rule.Fixed f -> {
					w[i] = Math.max(0f, f.kg());
					how[i] = new How.Fixed(f.source());
					locked[i] = true;
				}
				case Rule.Plus p -> {
					Integer b = index.get(p.base());
					if (b == null) {
						w[i] = Math.max(0f, p.extra());
						how[i] = new How.Fixed(p.source());
						locked[i] = true;
					} else {
						ruleEdges.add(new Compiled(new Edge(null, item, 1, List.of(List.of(p.base())), false), i, new int[][] {{b}}, true, p.extra()));
						how[i] = new How.Plus(p.base(), p.extra(), p.source());
					}
				}
			}
		});
		for (int i = 0; i < n; i++) {
			if (!ruled[i] && technical.test(list.get(i))) {
				w[i] = 0f;
				how[i] = new How.Technical();
				locked[i] = true;
			}
		}

		List<Compiled> crafting = new ArrayList<>(ruleEdges);
		List<Compiled> cooking = new ArrayList<>();
		for (Edge e : edges) {
			Integer out = index.get(e.output());
			if (out == null || ruled[out] || locked[out] || e.count() <= 0 || e.slots().isEmpty()) {
				continue;
			}
			int[][] slots = new int[e.slots().size()][];
			boolean usable = true;
			for (int s = 0; s < slots.length && usable; s++) {
				slots[s] = e.slots().get(s).stream().map(index::get).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).toArray();
				usable = slots[s].length > 0;
			}
			if (usable) {
				(e.cooking() ? cooking : crafting).add(new Compiled(e, out, slots, false, 0f));
			}
		}

		relax(crafting, w, guess, chosenEdge, chosen, locked, rem);
		// Cooking fills only what crafting could not reach.
		List<Compiled> all = new ArrayList<>(crafting);
		for (Compiled c : cooking) {
			if (Float.isNaN(w[c.output()])) {
				all.add(c);
			}
		}
		relax(all, w, guess, chosenEdge, chosen, locked, rem);

		// Fallbacks: first everything no recipe makes, then one anchor per unanchored cycle, until all are known.
		Map<Integer, Integer> bestCount = new HashMap<>();
		for (Compiled c : all) {
			bestCount.merge(c.output(), c.edge().count(), Math::max);
		}
		while (true) {
			List<Integer> unknown = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (Float.isNaN(w[i])) {
					unknown.add(i);
				}
			}
			if (unknown.isEmpty()) {
				break;
			}
			List<Integer> orphans = unknown.stream().filter(i -> !bestCount.containsKey(i)).toList();
			if (orphans.isEmpty()) {
				Comparator<Integer> loose = Comparator.<Integer>comparingInt(i -> -bestCount.getOrDefault(i, 1))
					.thenComparingInt(i -> -maxStack.applyAsInt(list.get(i)))
					.thenComparing(i -> list.get(i), order);
				orphans = List.of(unknown.stream().min(loose).orElseThrow());
			}
			for (int i : orphans) {
				int stack = Math.max(1, maxStack.applyAsInt(list.get(i)));
				w[i] = FALLBACK_STACK_KG / stack;
				how[i] = new How.Fallback(stack);
				guess[i] = true;
				locked[i] = true;
			}
			relax(all, w, guess, chosenEdge, chosen, locked, rem);
		}

		Map<Item, Float> weights = new LinkedHashMap<>();
		Map<Item, How> hows = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			weights.put(list.get(i), w[i]);
			if (chosenEdge[i] != null && !(how[i] instanceof How.Plus)) {
				hows.put(list.get(i), crafted(chosenEdge[i], chosen[i], rem, list));
			} else {
				hows.put(list.get(i), how[i]);
			}
		}
		return new Result(weights, hows);
	}

	/**
	 * Bellman-Ford style: lower each output to its lightest recipe until nothing moves (bounded passes). Real weights
	 * rank before guesses: a candidate replaces the current weight if it is less of a guess, or as much of one and
	 * lighter. Every update strictly lowers (guess, kg), so this settles.
	 */
	private static void relax(List<Compiled> edges, float[] w, boolean[] guess, Edge[] chosenEdge, int[][] chosen, boolean[] locked, int[] rem) {
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			boolean changed = false;
			for (Compiled c : edges) {
				int out = c.output();
				if (locked[out]) {
					continue;
				}
				float sum = 0f;
				boolean guessed = false;
				int[] pick = new int[c.slots().length];
				boolean known = true;
				for (int s = 0; s < c.slots().length && known; s++) {
					float best = Float.NaN;
					boolean bestGuess = true;
					for (int option : c.slots()[s]) {
						if (Float.isNaN(w[option])) {
							continue;
						}
						float net = w[option];
						int r = rem[option];
						if (r >= 0 && !c.plus() && !Float.isNaN(w[r])) {
							net -= w[r];
						}
						if (Float.isNaN(best) || (bestGuess && !guess[option]) || (bestGuess == guess[option] && net < best)) {
							best = net;
							bestGuess = guess[option];
							pick[s] = option;
						}
					}
					known = !Float.isNaN(best);
					sum += known ? best : 0f;
					guessed |= known && bestGuess;
				}
				if (!known) {
					continue;
				}
				float value = Math.max(0f, sum / c.edge().count() + c.extra());
				boolean better = Float.isNaN(w[out]) || (guess[out] && !guessed) || (guess[out] == guessed && value < w[out] - EPSILON);
				if (better) {
					w[out] = value;
					guess[out] = guessed;
					chosenEdge[out] = c.edge();
					chosen[out] = pick;
					changed = true;
				}
			}
			if (!changed) {
				return;
			}
		}
	}

	private static How crafted(Edge edge, int[] pick, int[] rem, List<Item> list) {
		Map<Item, Integer> inputs = new LinkedHashMap<>();
		Map<Item, Integer> back = new LinkedHashMap<>();
		for (int option : pick) {
			inputs.merge(list.get(option), 1, Integer::sum);
			if (rem[option] >= 0) {
				back.merge(list.get(rem[option]), 1, Integer::sum);
			}
		}
		List<Part> in = new ArrayList<>();
		inputs.forEach((item, k) -> in.add(new Part(item, k)));
		List<Part> out = new ArrayList<>();
		back.forEach((item, k) -> out.add(new Part(item, k)));
		return new How.Crafted(edge.id(), edge.count(), in, out, edge.cooking());
	}
}
