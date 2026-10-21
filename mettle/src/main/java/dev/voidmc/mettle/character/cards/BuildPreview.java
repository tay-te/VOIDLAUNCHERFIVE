package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What a card would do to a build (docs/BUILD_FEEL.md §6.1), free of players and worlds: where it goes (fills a slot,
 * replaces a card and what that costs, waits for a level), how set counts and active tiers move (what opens, what
 * breaks), trait totals before and after (capped as the game applies them), and the byname it would read as. The same
 * for a Temper or an Inscription before it is confirmed. Every number comes from {@link CardRules#derive}, so the
 * preview is what the game would do.
 */
public final class BuildPreview {
	private BuildPreview() {
	}

	/** A character as previews read it. */
	public record Context(List<CardData> cards, ToIntFunction<ResourceKey<Skill>> levels, int wits, CardRules.Attributes attributes,
		Function<ResourceKey<Perk>, Optional<Perk>> perks, CardRules.Data data, List<Identifier> pins, List<Identifier> order, Optional<Identifier> focus,
		int bynameTier, int characterLevel, int freeSwaps, List<ResourceKey<Skill>> freeRebuilds, long gameTime) {
		public CardRules.Derived derive(List<CardData> with) {
			return CardRules.derive(with, this.levels, this.wits, this.attributes, this.perks, this.data, this.pins, this.order);
		}

		public Optional<Byname> byname(List<CardData> with, CardRules.Derived d) {
			Bynames.Build b = Bynames.build(with, d, this.focus, this.perks, this.data, this.attributes);
			return Bynames.of(b, Bynames.earned(b, this.bynameTier));
		}
	}

	public enum Place {
		/** Into a free slot of its deck. */
		FILL,
		/** In place of a slotted card (the same perk, or the weakest when the deck is full): a free swap or a rebuild. */
		REPLACE,
		/** Its skill is below the card's requirement: it waits in the binder. */
		WAITS,
		/** A slot rule refuses it (a Mastery without its side, three tempered cards…). */
		BLOCKED,
		/** A card changed where it is (a Temper or an Inscription). */
		CHANGE
	}

	/**
	 * Where the card goes. {@code free} is the free way that would pay a replacement ({@link CardRules#freeWay}); with none,
	 * {@code fee} bits and a rebuild (draught, scar).
	 */
	public record Placement(Place kind, int slot, Optional<CardData> replaces, double pointsDelta, Optional<CardRules.FreeWay> free, int fee,
		int needs, Optional<CardRules.Refusal> refusal) {
	}

	/** One keyword's count and active tier before and after; {@code asleep}: a 6 waiting for its decks to reach the gate. */
	public record SetDelta(Identifier keyword, int from, int to, int tierFrom, int tierTo, boolean asleep) {
		public boolean opens() {
			return this.tierTo > this.tierFrom;
		}

		public boolean breaks() {
			return this.tierTo < this.tierFrom;
		}
	}

	/** One trait's active total before and after, against its total cap. */
	public record TraitDelta(Identifier trait, double from, double to, double cap) {
	}

	public record Preview(Placement placement, List<SetDelta> sets, List<TraitDelta> traits, Optional<Byname> before, Optional<Byname> after,
		boolean focus) {
		/** The byname would change. */
		public boolean renames() {
			return this.after.isPresent() && !this.after.equals(this.before);
		}
	}

	/** An offer of a hand as a card (not in the ledger: id -1), in the binder, attuned. */
	public static CardData asCard(DraftState.Card offer, Perk perk) {
		CardData.Holder h = new CardData.Holder(new UUID(0, 0), "", 0);
		return new CardData(-1, perk.skill(), offer.perk(), offer.rarity(), offer.rank(), offer.keywords(), offer.traits(), offer.needs(),
			new CardData.Drawn(h.player(), "", 0, 0, 0, CardData.How.DRAFT), h.player(), CardData.History.first(h), 0, 0, CardData.Place.BINDER, 0);
	}

	/** What keeping (and slotting) this card would do (§6.1). Mythic offers have no preview (empty). */
	public static Optional<Preview> of(Context ctx, CardData card) {
		if (card.mythic()) {
			return Optional.empty();
		}
		ResourceKey<Skill> skill = card.skill();
		int level = ctx.levels().applyAsInt(skill);
		CardData binder = card.withPlace(CardData.Place.BINDER);
		if (level < card.needs()) {
			return Optional.of(new Preview(new Placement(Place.WAITS, -1, Optional.empty(), 0, Optional.empty(), 0, card.needs(), Optional.empty()), List.of(),
				List.of(), Optional.empty(), Optional.empty(), focus(ctx, card)));
		}
		List<CardData> others = ctx.cards().stream().filter(c -> c.id() != card.id()).toList();
		List<CardData> deck = others.stream().filter(c -> c.slotted() && c.place().deck().map(skill::equals).orElse(false))
			.sorted(Comparator.comparingInt(c -> c.place().index())).toList();
		List<CardData> allSlotted = others.stream().filter(CardData::slotted).toList();
		int extra = CardRules.extraSlots(others);
		Optional<CardData> same = deck.stream().filter(c -> c.perk().equals(card.perk())).findFirst();
		CardData replaced = null;
		int at;
		if (same.isPresent()) {
			replaced = same.get();
			at = replaced.place().index();
		} else {
			Optional<CardRules.Refusal> no = CardRules.canSlot(binder, -1, deck, level, ctx.wits(), ctx.gameTime(), ctx.perks(), allSlotted, extra);
			int base = CardRules.slots(level, ctx.wits());
			at = CardRules.firstFree(deck, base == 0 ? 0 : base + extra);
			if (no.isPresent() && no.get() == CardRules.Refusal.NO_SLOT && !deck.isEmpty()) {
				// A full deck: the weakest card that isn't a Mastery or a Mythic gives way.
				CardData weakest = deck.stream().filter(c -> !c.mythic() && ctx.perks().apply(c.perk()).map(p -> p.kind() != Perk.Kind.MASTERY).orElse(true))
					.min(Comparator.comparingDouble((CardData c) -> CardRules.points(c, ctx.perks(), ctx.data(), ctx.attributes())).thenComparingInt(c -> c.place().index()))
					.orElse(null);
				if (weakest != null) {
					List<CardData> without = deck.stream().filter(c -> c.id() != weakest.id()).toList();
					no = CardRules.canSlot(binder, weakest.place().index(), without, level, ctx.wits(), ctx.gameTime(), ctx.perks(),
						allSlotted.stream().filter(c -> c.id() != weakest.id()).toList(), extra);
					if (no.isEmpty()) {
						replaced = weakest;
						at = weakest.place().index();
					}
				}
			}
			if (no.isPresent() && replaced == null) {
				return Optional.of(new Preview(new Placement(Place.BLOCKED, -1, Optional.empty(), 0, Optional.empty(), 0, card.needs(), no), List.of(),
					List.of(), Optional.empty(), Optional.empty(), focus(ctx, card)));
			}
		}
		List<CardData> after = new ArrayList<>();
		for (CardData c : others) {
			after.add(replaced != null && c.id() == replaced.id() ? c.withPlace(CardData.Place.BINDER) : c);
		}
		after.add(binder.withPlace(CardData.Place.slotted(skill, at)));
		double gain = CardRules.points(card, ctx.perks(), ctx.data(), ctx.attributes())
			- (replaced == null ? 0 : CardRules.points(replaced, ctx.perks(), ctx.data(), ctx.attributes()));
		Optional<CardRules.FreeWay> free = replaced == null ? Optional.empty()
			: CardRules.freeWay(ctx.characterLevel(), List.of(replaced), ctx.freeRebuilds(), ctx.freeSwaps());
		int fee = replaced == null || free.isPresent() ? 0 : CardRules.feeBits(replaced.needs());
		Placement placement = new Placement(replaced == null ? Place.FILL : Place.REPLACE, at, Optional.ofNullable(replaced), CardRules.round(gain), free, fee,
			card.needs(), Optional.empty());
		return Optional.of(diff(ctx, others, after, placement, focus(ctx, card)));
	}

	/** What a change to a card already owned (a Temper, an Inscription) would do, before it is confirmed. */
	public static Preview change(Context ctx, CardData before, CardData after) {
		List<CardData> was = ctx.cards();
		List<CardData> now = new ArrayList<>();
		for (CardData c : was) {
			now.add(c.id() == before.id() ? after : c);
		}
		double gain = CardRules.points(after, ctx.perks(), ctx.data(), ctx.attributes()) - CardRules.points(before, ctx.perks(), ctx.data(), ctx.attributes());
		Placement p = new Placement(Place.CHANGE, before.slotted() ? before.place().index() : -1, Optional.empty(), CardRules.round(gain), Optional.empty(), 0,
			after.needs(), Optional.empty());
		return diff(ctx, was, now, p, focus(ctx, after));
	}

	private static boolean focus(Context ctx, CardData card) {
		return ctx.focus().map(card.keywords()::contains).orElse(false);
	}

	private static Preview diff(Context ctx, List<CardData> before, List<CardData> after, Placement placement, boolean focus) {
		CardRules.Derived a = ctx.derive(before);
		CardRules.Derived b = ctx.derive(after);
		Set<Identifier> keys = new HashSet<>(a.sets().keySet());
		keys.addAll(b.sets().keySet());
		keys.addAll(a.tiers().keySet());
		keys.addAll(b.tiers().keySet());
		List<SetDelta> sets = new ArrayList<>();
		Map<Identifier, Set<ResourceKey<Skill>>> decks = decks(after, b);
		for (Identifier k : keys.stream().sorted(Comparator.comparing(Identifier::toString)).toList()) {
			int from = a.sets().getOrDefault(k, 0);
			int to = b.sets().getOrDefault(k, 0);
			int tf = a.tiers().getOrDefault(k, 0);
			int tt = b.tiers().getOrDefault(k, 0);
			if (from != to || tf != tt) {
				sets.add(new SetDelta(k, from, to, tf, tt, CardRules.sixAsleep(to, decks.getOrDefault(k, Set.of()), ctx.levels())));
			}
		}
		// The keywords the change touches first (the ones it opens or breaks), then by name.
		sets.sort(Comparator.comparingInt((SetDelta d) -> d.opens() || d.breaks() ? 0 : 1).thenComparing(d -> d.keyword().toString()));
		List<TraitDelta> traits = new ArrayList<>();
		Set<Identifier> tk = new HashSet<>(a.traits().keySet());
		tk.addAll(b.traits().keySet());
		for (Identifier t : tk.stream().sorted(Comparator.comparing(Identifier::toString)).toList()) {
			double from = a.traits().getOrDefault(t, 0.0);
			double to = b.traits().getOrDefault(t, 0.0);
			if (Math.abs(from - to) > 1e-9) {
				traits.add(new TraitDelta(t, from, to, ctx.data().trait(t).map(CardRules::totalCap).orElse(to)));
			}
		}
		return new Preview(placement, sets, traits, ctx.byname(before, a), ctx.byname(after, b), focus);
	}

	/** Which decks hold each keyword's slotted, non-dormant cards. */
	static Map<Identifier, Set<ResourceKey<Skill>>> decks(List<CardData> cards, CardRules.Derived d) {
		Map<Identifier, Set<ResourceKey<Skill>>> out = new java.util.HashMap<>();
		for (CardData c : cards) {
			if (c.slotted() && !d.dormant().contains(c.id())) {
				c.keywords().forEach(k -> out.computeIfAbsent(k, x -> new HashSet<>()).add(c.place().deck().orElse(c.skill())));
			}
		}
		return out;
	}
}
