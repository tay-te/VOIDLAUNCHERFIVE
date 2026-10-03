package dev.voidmc.hearth.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The quest module's settings, {@code config/hearth/quest.json}. Every field is optional in the file.
 *
 * @param offersPerBoard       offers a board shows (supply and bounty needs come first)
 * @param offerLifetimeDays    days an untaken offer stays up
 * @param refreshDays          days between board refreshes (new offers fill empty slots)
 * @param maxActive            quests one player may have at once
 * @param maxSupplyOffers      supply offers at once (one per stalled plot and goods)
 * @param supplyMaxItems       most items one supply quest asks for
 * @param coinMultiplier       scales every coin reward
 * @param marketPremium        supply quests pay this times market price when the template does not say
 * @param treasuryShare        share of the treasury a board may promise in rewards at once
 * @param bitsPerEmerald       without the economy module, coin rewards are paid in emeralds at this rate
 * @param reputationComplete   standing gained for a completed quest when the template does not say
 * @param reputationFail       standing lost when a quest fails (deadline passed, escort lost)
 * @param reputationAbandon    standing lost when a quest is abandoned
 * @param pressureThreshold    night monster pressure at which a town posts a bounty
 * @param exploreSearchRadius  blocks round a settlement searched for exploration targets
 * @param courierMaxDistance   farthest settlement a courier or escort quest goes to
 * @param scoutClearance       blocks a scouted site must keep from every settlement
 * @param trackerInterval      ticks between a player's objective checks (position, deadlines)
 * @param giveJournal          give a journal with the first quest a player takes
 */
public record QuestConfig(int offersPerBoard, int offerLifetimeDays, int refreshDays, int maxActive, int maxSupplyOffers, int supplyMaxItems, double coinMultiplier,
	double marketPremium, double treasuryShare, int bitsPerEmerald, int reputationComplete, int reputationFail, int reputationAbandon,
	int pressureThreshold, int exploreSearchRadius, int courierMaxDistance, int scoutClearance, int trackerInterval, boolean giveJournal) {

	public static final QuestConfig DEFAULTS = new QuestConfig(5, 3, 1, 6, 2, 96, 1.0, 1.5, 0.4, 20, 4, -6, -2, 12, 3000, 3000, 500, 20, true);

	private static final Codec<Numbers> NUMBERS = RecordCodecBuilder.create(i -> i.group(
		Codec.intRange(1, 12).optionalFieldOf("offers_per_board", DEFAULTS.offersPerBoard).forGetter(Numbers::offersPerBoard),
		Codec.intRange(1, 60).optionalFieldOf("offer_lifetime_days", DEFAULTS.offerLifetimeDays).forGetter(Numbers::offerLifetimeDays),
		Codec.intRange(1, 30).optionalFieldOf("refresh_days", DEFAULTS.refreshDays).forGetter(Numbers::refreshDays),
		Codec.intRange(1, 20).optionalFieldOf("max_active", DEFAULTS.maxActive).forGetter(Numbers::maxActive),
		Codec.intRange(0, 6).optionalFieldOf("max_supply_offers", DEFAULTS.maxSupplyOffers).forGetter(Numbers::maxSupplyOffers),
		Codec.intRange(1, 1024).optionalFieldOf("supply_max_items", DEFAULTS.supplyMaxItems).forGetter(Numbers::supplyMaxItems),
		Codec.intRange(1, 1000).optionalFieldOf("pressure_threshold", DEFAULTS.pressureThreshold).forGetter(Numbers::pressureThreshold),
		Codec.intRange(256, 16000).optionalFieldOf("explore_search_radius", DEFAULTS.exploreSearchRadius).forGetter(Numbers::exploreSearchRadius),
		Codec.intRange(100, 20000).optionalFieldOf("courier_max_distance", DEFAULTS.courierMaxDistance).forGetter(Numbers::courierMaxDistance),
		Codec.intRange(0, 5000).optionalFieldOf("scout_clearance", DEFAULTS.scoutClearance).forGetter(Numbers::scoutClearance),
		Codec.intRange(5, 200).optionalFieldOf("tracker_interval", DEFAULTS.trackerInterval).forGetter(Numbers::trackerInterval)
	).apply(i, Numbers::new));

	private static final Codec<Rewards> REWARDS = RecordCodecBuilder.create(i -> i.group(
		Codec.doubleRange(0, 100).optionalFieldOf("coin_multiplier", DEFAULTS.coinMultiplier).forGetter(Rewards::coinMultiplier),
		Codec.doubleRange(0, 10).optionalFieldOf("market_premium", DEFAULTS.marketPremium).forGetter(Rewards::marketPremium),
		Codec.doubleRange(0, 1).optionalFieldOf("treasury_share", DEFAULTS.treasuryShare).forGetter(Rewards::treasuryShare),
		Codec.intRange(1, 10000).optionalFieldOf("bits_per_emerald", DEFAULTS.bitsPerEmerald).forGetter(Rewards::bitsPerEmerald),
		Codec.intRange(0, 100).optionalFieldOf("reputation_complete", DEFAULTS.reputationComplete).forGetter(Rewards::reputationComplete),
		Codec.intRange(-100, 0).optionalFieldOf("reputation_fail", DEFAULTS.reputationFail).forGetter(Rewards::reputationFail),
		Codec.intRange(-100, 0).optionalFieldOf("reputation_abandon", DEFAULTS.reputationAbandon).forGetter(Rewards::reputationAbandon),
		Codec.BOOL.optionalFieldOf("give_journal", DEFAULTS.giveJournal).forGetter(Rewards::giveJournal)
	).apply(i, Rewards::new));

	public static final Codec<QuestConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		NUMBERS.optionalFieldOf("board", Numbers.of(DEFAULTS)).forGetter(Numbers::of),
		REWARDS.optionalFieldOf("rewards", Rewards.of(DEFAULTS)).forGetter(Rewards::of)
	).apply(i, (n, r) -> new QuestConfig(n.offersPerBoard, n.offerLifetimeDays, n.refreshDays, n.maxActive, n.maxSupplyOffers, n.supplyMaxItems, r.coinMultiplier,
		r.marketPremium, r.treasuryShare, r.bitsPerEmerald, r.reputationComplete, r.reputationFail, r.reputationAbandon, n.pressureThreshold,
		n.exploreSearchRadius, n.courierMaxDistance, n.scoutClearance, n.trackerInterval, r.giveJournal)));

	private record Numbers(int offersPerBoard, int offerLifetimeDays, int refreshDays, int maxActive, int maxSupplyOffers, int supplyMaxItems, int pressureThreshold,
		int exploreSearchRadius, int courierMaxDistance, int scoutClearance, int trackerInterval) {
		static Numbers of(QuestConfig c) {
			return new Numbers(c.offersPerBoard, c.offerLifetimeDays, c.refreshDays, c.maxActive, c.maxSupplyOffers, c.supplyMaxItems, c.pressureThreshold,
				c.exploreSearchRadius, c.courierMaxDistance, c.scoutClearance, c.trackerInterval);
		}
	}

	private record Rewards(double coinMultiplier, double marketPremium, double treasuryShare, int bitsPerEmerald, int reputationComplete,
		int reputationFail, int reputationAbandon, boolean giveJournal) {
		static Rewards of(QuestConfig c) {
			return new Rewards(c.coinMultiplier, c.marketPremium, c.treasuryShare, c.bitsPerEmerald, c.reputationComplete, c.reputationFail,
				c.reputationAbandon, c.giveJournal);
		}
	}
}
