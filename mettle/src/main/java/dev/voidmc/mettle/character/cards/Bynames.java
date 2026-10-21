package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Bynames (docs/BUILD_FEEL.md §2.1), free of players and worlds: which tier a build has earned, which words it reads as,
 * and the name they make ("the Deepdelver", "the Ember Deepdelver", "the Deep Ember Warden", "… of the Mountain").
 *
 * <p>Tiers are earned and never drop: the caller keeps the highest one and passes it in. The words follow the build.
 */
public final class Bynames {
	private Bynames() {
	}

	/** What a build is, as bynames read it. */
	public record Build(List<CardData> active, Map<Identifier, Integer> tiers, Map<Identifier, Integer> counts, List<Identifier> order,
		Optional<Identifier> focus, Function<ResourceKey<Perk>, Optional<Perk>> perks, CardRules.Data data, CardRules.Attributes attributes) {
	}

	/**
	 * The build of a derived deck: the slotted, non-dormant cards, the active sets with their tiers, the keyword counts and
	 * the order keywords were first counted in.
	 */
	public static Build build(List<CardData> cards, CardRules.Derived derived, Optional<Identifier> focus, Function<ResourceKey<Perk>, Optional<Perk>> perks,
		CardRules.Data data, CardRules.Attributes attributes) {
		List<CardData> active = cards.stream().filter(c -> c.slotted() && !derived.dormant().contains(c.id())).toList();
		return new Build(active, derived.tiers(), derived.sets(), derived.order(), focus, perks, data, attributes);
	}

	/**
	 * The tier this build reaches now (§2.1), not counting what was earned before: I with a 2-set, II with a 4-set, III
	 * with a 6-set or a Mastery slotted beside a 4-set, IV when III (now or {@code earned}) holds and a Mythic is slotted.
	 */
	public static int tierNow(Build b, int earned) {
		int top = b.tiers().values().stream().mapToInt(Integer::intValue).max().orElse(0);
		boolean mastery = b.active().stream().anyMatch(c -> b.perks().apply(c.perk()).map(p -> p.kind() == Perk.Kind.MASTERY).orElse(false));
		boolean mythic = b.active().stream().anyMatch(CardData::mythic);
		int t = top >= 6 || top >= 4 && mastery ? 3 : top >= 4 ? 2 : top >= 2 ? 1 : 0;
		if (mythic && Math.max(t, earned) >= 3) {
			t = 4;
		}
		return t;
	}

	/** The tier earned after this build: never below what was earned before. */
	public static int earned(Build b, int before) {
		return Math.max(before, tierNow(b, before));
	}

	/** The active sets in byname order: highest tier, then most cards, then the Focus, then the one reached first. */
	public static List<Identifier> keywords(Build b) {
		List<Identifier> out = new ArrayList<>(b.tiers().keySet());
		out.sort(Comparator.comparingInt((Identifier k) -> -b.tiers().get(k))
			.thenComparingInt(k -> -b.counts().getOrDefault(k, 0))
			.thenComparingInt(k -> b.focus().map(k::equals).orElse(false) ? 0 : 1)
			.thenComparingInt(k -> b.order().indexOf(k) < 0 ? Integer.MAX_VALUE : b.order().indexOf(k)));
		return out;
	}

	/** Slotted points per skill (perks + traits, CARDS.md §14), Mythics left out; by the deck the cards sit in. */
	public static Map<ResourceKey<Skill>, Double> points(Build b) {
		Map<ResourceKey<Skill>, Double> out = new HashMap<>();
		for (CardData c : b.active()) {
			if (!c.mythic()) {
				out.merge(c.place().deck().orElse(c.skill()), CardRules.points(c, b.perks(), b.data(), b.attributes()), Double::sum);
			}
		}
		return out;
	}

	/**
	 * skill1 (the most slotted points; ties by id) and skill2 (the next, when it holds at least half of skill1's points).
	 */
	public static List<ResourceKey<Skill>> skills(Build b) {
		Map<ResourceKey<Skill>, Double> pts = points(b);
		List<ResourceKey<Skill>> ranked = new ArrayList<>(pts.keySet());
		ranked.sort(Comparator.comparingDouble((ResourceKey<Skill> s) -> -pts.get(s)).thenComparing(s -> s.identifier().toString()));
		List<ResourceKey<Skill>> out = new ArrayList<>();
		if (ranked.isEmpty()) {
			return out;
		}
		out.add(ranked.getFirst());
		if (ranked.size() > 1 && pts.get(ranked.get(1)) > 0 && pts.get(ranked.get(1)) >= pts.get(ranked.getFirst()) / 2) {
			out.add(ranked.get(1));
		}
		return out;
	}

	/**
	 * The byname the build reads as at {@code tier} (the earned tier), or empty when it has no active set or no slotted
	 * points yet. Tier II with one active set reads as tier I's words; tier IV without a Mythic slotted as tier III's.
	 */
	public static Optional<Byname> of(Build b, int tier) {
		if (tier <= 0) {
			return Optional.empty();
		}
		List<Identifier> kws = keywords(b);
		List<ResourceKey<Skill>> skills = skills(b);
		if (kws.isEmpty() || skills.isEmpty()) {
			return Optional.empty();
		}
		Optional<Identifier> kw2 = tier >= 2 && kws.size() > 1 ? Optional.of(kws.get(1)) : Optional.empty();
		Optional<ResourceKey<Skill>> skill2 = tier >= 3 && skills.size() > 1 ? Optional.of(skills.get(1)) : Optional.empty();
		Optional<Identifier> mythic = tier >= 4 ? b.active().stream().filter(CardData::mythic).map(c -> c.mythicId().orElseThrow())
			.min(Comparator.comparing(Identifier::toString)) : Optional.empty();
		return Optional.of(new Byname(Math.min(4, tier), kws.getFirst(), kw2, skills.getFirst(), skill2, mythic));
	}

	// ---------------------------------------------------------------- the words

	/** Where the words come from: the byname tables (data, lang) and each Mythic's epithet. */
	public interface Words {
		Component prefix(Identifier keyword);

		Component word(Identifier keyword);

		Component suffix(ResourceKey<Skill> skill);

		Component noun(ResourceKey<Skill> skill);

		/** The pair's noun (either order), if the table has one. */
		Optional<Component> pair(ResourceKey<Skill> a, ResourceKey<Skill> b);

		/** The line saying what the pair (or the single skill) means. */
		Component description(ResourceKey<Skill> a, Optional<ResourceKey<Skill>> b);

		Component epithet(Identifier mythic);
	}

	/** The byname as words, by its form (§2.1). */
	public static Component render(Byname b, Words w) {
		Component compound = Component.empty().append(w.prefix(b.kw1())).append(w.suffix(b.skill1()));
		if (b.tier() <= 1 || b.tier() == 2 && b.kw2().isEmpty()) {
			return Component.translatable("mettle.byname.form.1", compound);
		}
		if (b.tier() == 2) {
			return Component.translatable("mettle.byname.form.2", w.word(b.kw2().get()), compound);
		}
		Component noun = b.skill2().flatMap(s2 -> w.pair(b.skill1(), s2)).orElseGet(() -> w.noun(b.skill1()));
		Component three = b.kw2().isPresent()
			? Component.translatable("mettle.byname.form.3", w.word(b.kw1()), w.word(b.kw2().get()), noun)
			: Component.translatable("mettle.byname.form.3.one", w.word(b.kw1()), noun);
		if (b.tier() >= 4 && b.mythic().isPresent()) {
			return Component.translatable("mettle.byname.form.4", three, w.epithet(b.mythic().get()));
		}
		return three;
	}

	/** The plain-words line under a byname ("A miner who holds the line."). */
	public static Component line(Byname b, Words w) {
		return w.description(b.skill1(), b.tier() >= 3 ? b.skill2() : Optional.empty());
	}

	/** The pair key in the byname table: two skill ids joined by {@code +}, in the table's order (either is tried). */
	public static String pairKey(ResourceKey<Skill> a, ResourceKey<Skill> b) {
		return a.identifier() + "+" + b.identifier();
	}
}
