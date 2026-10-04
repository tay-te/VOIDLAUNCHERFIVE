package dev.voidmc.hearth.quest.template;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.TurnIn;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * A kind of quest, as data ({@code data/<ns>/hearth_quest/<name>.json}): what it asks, when a settlement offers it, what
 * it says and what it pays. The generator binds a template to a settlement's real state to make a quest instance.
 * See docs/QUESTS.md for the file format.
 *
 * @param weight     relative chance among eligible templates
 * @param when       when it may be offered
 * @param giverJobs  residents of these jobs give it (any adult if none of them live there, unless {@code councilOnly})
 * @param text       title, summary and spoken lines, each a list of variants with placeholders
 * @param deadlineDays days allowed after accepting (0: none)
 * @param cooldownDays days before the same settlement offers it again
 * @param repeatable whether a player may do it again after completing it
 */
public record QuestTemplate(Identifier id, QuestType type, double weight, Conditions when, List<JobType> giverJobs, boolean councilOnly,
	List<ObjectiveSpec> objectives, Text text, RewardSpec reward, int deadlineDays, int cooldownDays, boolean repeatable, TurnIn turnIn,
	boolean sequential) {

	public QuestTemplate {
		giverJobs = List.copyOf(giverJobs);
		objectives = List.copyOf(objectives);
	}

	/** This template's weight for a culture (0 where it is not offered). */
	public double weightFor(Culture culture) {
		return this.weight * this.when.cultureWeight(culture);
	}

	/**
	 * @param cultures     culture → weight multiplier; empty means every culture at 1. Unlisted cultures get
	 *                     {@code otherCultures} (0 unless the file gives {@code "*"}).
	 * @param goods        supply and contracts: the goods this template covers (empty: any)
	 * @param requiresMods mod ids that must be loaded (e.g. {@code expanse})
	 * @param minTitle     the standing title a player needs to take it (null: any), e.g. {@code friend}
	 * @param wanted       how it treats players the law wants in the town
	 * @param requires     the town's state it needs ({@code stalled}, {@code crime}, {@code job:miner}, {@code site:ore},
	 *                     {@code !declining}...; see {@code TownState})
	 * @param events       trigger {@code event}: the happenings it answers ({@code fire}, {@code caravan_robbed}...)
	 */
	public record Conditions(QuestType.Trigger trigger, Map<Culture, Double> cultures, double otherCultures, SettlementTier minTier,
		SettlementTier maxTier, int minReputation, List<Identifier> prerequisites, List<String> requiresMods, Set<Goods> goods,
		StandingService.@Nullable Title minTitle, Wanted wanted, List<String> requires, List<String> events) {

		public Conditions {
			cultures = Map.copyOf(cultures);
			prerequisites = List.copyOf(prerequisites);
			requiresMods = List.copyOf(requiresMods);
			goods = Set.copyOf(goods);
			requires = List.copyOf(requires);
			events = List.copyOf(events);
		}

		/** The phase 2 shape: no title, wanted players refused, no state or events. */
		public Conditions(QuestType.Trigger trigger, Map<Culture, Double> cultures, double otherCultures, SettlementTier minTier,
			SettlementTier maxTier, int minReputation, List<Identifier> prerequisites, List<String> requiresMods, Set<Goods> goods) {
			this(trigger, cultures, otherCultures, minTier, maxTier, minReputation, prerequisites, requiresMods, goods, null, Wanted.REFUSE, List.of(),
				List.of());
		}

		public double cultureWeight(Culture culture) {
			if (this.cultures.isEmpty()) {
				return 1.0;
			}
			return this.cultures.getOrDefault(culture, this.otherCultures);
		}

		public boolean allowsTier(SettlementTier tier) {
			return tier.ordinal() >= this.minTier.ordinal() && tier.ordinal() <= this.maxTier.ordinal();
		}
	}

	/** How a template treats a player the law wants in the town (docs/LIVING_WORLD.md §4: wanted players are refused work). */
	public enum Wanted implements StringRepresentable {
		/** Not offered to wanted players (the default). */
		REFUSE("refuse"),
		/** Offered to anyone (a fence's work). */
		ALLOW("allow"),
		/** Only for wanted players: clearing your name. */
		ONLY("only");

		private final String name;

		Wanted(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	/** Lists of variants; one is picked per quest (the same index is used across fields when their sizes match). */
	public record Text(List<String> title, List<String> summary, List<String> offer, List<String> progress, List<String> complete) {
		public Text {
			title = List.copyOf(title);
			summary = List.copyOf(summary);
			offer = List.copyOf(offer);
			progress = List.copyOf(progress);
			complete = List.copyOf(complete);
		}
	}

	/**
	 * @param coinMin       base coin in bits (before tier and config multipliers)
	 * @param coinMax       as above
	 * @param marketPremium deliver objectives also pay this times the market price of what is brought (0: not)
	 * @param items         item rewards
	 * @param reputation    standing gained on completion (0: the config default)
	 * @param deed          the deed reported to the standing service on completion ({@code quest:rescued_miners}); empty for
	 *                      {@code quest:<type>}
	 * @param aliveBonus    apprehend: the coin is multiplied by this when the culprit is brought in alive (and divided by it
	 *                      when killed)
	 */
	public record RewardSpec(int coinMin, int coinMax, double marketPremium, List<ItemSpec> items, int reputation, String deed, double aliveBonus) {
		public RewardSpec {
			items = List.copyOf(items);
		}

		public RewardSpec(int coinMin, int coinMax, double marketPremium, List<ItemSpec> items, int reputation) {
			this(coinMin, coinMax, marketPremium, items, reputation, "", 1.5);
		}
	}

	/** An item reward with a count range. */
	public record ItemSpec(String item, Amount count) {
	}
}
