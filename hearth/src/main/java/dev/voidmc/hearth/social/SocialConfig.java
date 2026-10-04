package dev.voidmc.hearth.social;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.List;

/**
 * {@code config/hearth/social.json}. Every field is optional, so an old file keeps working when fields are added. Times
 * of day are written {@code "HH:MM"} (the sim clock: 06:00 is sunrise, a sim hour is 1000 ticks). Amounts are in bits.
 * See docs/SOCIAL.md for what each number does.
 */
public record SocialConfig(Life life, Bells bells, Market market, Festivals festivals, Family family, Offices offices, Inn inn, Bath bath,
	Stables stables, Wages wages, Dice dice) {
	public static final SocialConfig DEFAULTS = new SocialConfig(Life.DEFAULTS, Bells.DEFAULTS, Market.DEFAULTS, Festivals.DEFAULTS, Family.DEFAULTS,
		Offices.DEFAULTS, Inn.DEFAULTS, Bath.DEFAULTS, Stables.DEFAULTS, Wages.DEFAULTS, Dice.DEFAULTS);

	/** "HH:MM" ↔ minute of the day. */
	public static final Codec<Integer> CLOCK = Codec.STRING.comapFlatMap(SocialConfig::parseClock, SocialConfig::formatClock);

	public static final Codec<SocialConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Life.CODEC.optionalFieldOf("life", Life.DEFAULTS).forGetter(SocialConfig::life),
		Bells.CODEC.optionalFieldOf("bells", Bells.DEFAULTS).forGetter(SocialConfig::bells),
		Market.CODEC.optionalFieldOf("market", Market.DEFAULTS).forGetter(SocialConfig::market),
		Festivals.CODEC.optionalFieldOf("festivals", Festivals.DEFAULTS).forGetter(SocialConfig::festivals),
		Family.CODEC.optionalFieldOf("family", Family.DEFAULTS).forGetter(SocialConfig::family),
		Offices.CODEC.optionalFieldOf("offices", Offices.DEFAULTS).forGetter(SocialConfig::offices),
		Inn.CODEC.optionalFieldOf("inn", Inn.DEFAULTS).forGetter(SocialConfig::inn),
		Bath.CODEC.optionalFieldOf("bath", Bath.DEFAULTS).forGetter(SocialConfig::bath),
		Stables.CODEC.optionalFieldOf("stables", Stables.DEFAULTS).forGetter(SocialConfig::stables),
		Wages.CODEC.optionalFieldOf("wages", Wages.DEFAULTS).forGetter(SocialConfig::wages),
		Dice.CODEC.optionalFieldOf("dice", Dice.DEFAULTS).forGetter(SocialConfig::dice)
	).apply(i, SocialConfig::new));

	static DataResult<Integer> parseClock(String s) {
		String[] parts = s.trim().split(":");
		try {
			int h = Integer.parseInt(parts[0]);
			int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
			if (h < 0 || h > 23 || m < 0 || m > 59) {
				return DataResult.error(() -> "not a time of day: " + s);
			}
			return DataResult.success(h * 60 + m);
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "not a time of day (HH:MM): " + s);
		}
	}

	public static String formatClock(int minute) {
		int m = Math.floorMod(minute, 24 * 60);
		return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60);
	}

	/**
	 * @param enabled          the whole of town life (events, behaviours, bells, dressings)
	 * @param passTicks        how often embodied settlements are looked at (bells, dressings, notices to cry)
	 * @param announceInChat   new notices are also written to players inside the settlement
	 * @param attendStanding   attending a wedding, funeral or festival is a small kindness (a standing deed, once each)
	 * @param attendRadius     how near the gathering a player must be to count as attending
	 * @param maxNotices       notices kept per settlement
	 */
	public record Life(boolean enabled, int passTicks, boolean announceInChat, boolean attendStanding, int attendRadius, int maxNotices) {
		public static final Life DEFAULTS = new Life(true, 20, true, true, 24, 12);
		public static final Codec<Life> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Life::enabled),
			Codec.intRange(1, 1200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Life::passTicks),
			Codec.BOOL.optionalFieldOf("announce_in_chat", DEFAULTS.announceInChat).forGetter(Life::announceInChat),
			Codec.BOOL.optionalFieldOf("attend_standing", DEFAULTS.attendStanding).forGetter(Life::attendStanding),
			Codec.intRange(4, 128).optionalFieldOf("attend_radius", DEFAULTS.attendRadius).forGetter(Life::attendRadius),
			Codec.intRange(1, 64).optionalFieldOf("max_notices", DEFAULTS.maxNotices).forGetter(Life::maxNotices)
		).apply(i, Life::new));
	}

	/**
	 * @param times   when the bells ring (dawn: work begins; noon; dusk: work ends)
	 * @param strokes strokes for each time, in the same order
	 * @param volume  how far they carry (16 blocks per unit of volume)
	 */
	public record Bells(boolean enabled, List<Integer> times, List<Integer> strokes, float volume) {
		public static final Bells DEFAULTS = new Bells(true, List.of(6 * 60, 12 * 60, 17 * 60), List.of(3, 4, 3), 5.0F);
		public static final Codec<Bells> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Bells::enabled),
			CLOCK.listOf().optionalFieldOf("times", DEFAULTS.times).forGetter(Bells::times),
			Codec.intRange(1, 12).listOf().optionalFieldOf("strokes", DEFAULTS.strokes).forGetter(Bells::strokes),
			Codec.floatRange(0.5F, 16F).optionalFieldOf("volume", DEFAULTS.volume).forGetter(Bells::volume)
		).apply(i, Bells::new));

		public int strokesAt(int index) {
			return index < this.strokes.size() ? this.strokes.get(index) : 3;
		}
	}

	/**
	 * @param weekDays       a market day comes once in this many days (each settlement keeps its own weekday)
	 * @param minTier        the smallest settlement that holds one
	 * @param open           market opens
	 * @param close          market closes
	 * @param stalls         extra stalls set up in the square: base, plus this many per tier above the minimum
	 * @param stallsPerTier  see {@code stalls}
	 * @param maxStalls      at most
	 * @param crowdShare     share of folk at leisure who come to the market in any hour
	 * @param visitingWares  a visiting trader's wares are sold at the stalls
	 * @param visitingMarkup visiting wares cost this much over the town's own price
	 * @param wareStock      lots of each visiting ware a market day brings
	 * @param stallFee       bits each stall pays the treasury on a market day (also while nobody is near)
	 */
	public record Market(boolean enabled, int weekDays, SettlementTier minTier, int open, int close, int stalls, int stallsPerTier, int maxStalls,
		double crowdShare, boolean visitingWares, double visitingMarkup, int wareStock, int stallFee) {
		public static final Market DEFAULTS = new Market(true, 7, SettlementTier.HAMLET, 7 * 60, 17 * 60, 2, 1, 6, 0.35, true, 1.3, 4, 2);
		public static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Market::enabled),
			Codec.intRange(2, 60).optionalFieldOf("week_days", DEFAULTS.weekDays).forGetter(Market::weekDays),
			SettlementTier.CODEC.optionalFieldOf("min_tier", DEFAULTS.minTier).forGetter(Market::minTier),
			CLOCK.optionalFieldOf("open", DEFAULTS.open).forGetter(Market::open),
			CLOCK.optionalFieldOf("close", DEFAULTS.close).forGetter(Market::close),
			Codec.intRange(0, 32).optionalFieldOf("stalls", DEFAULTS.stalls).forGetter(Market::stalls),
			Codec.intRange(0, 8).optionalFieldOf("stalls_per_tier", DEFAULTS.stallsPerTier).forGetter(Market::stallsPerTier),
			Codec.intRange(0, 32).optionalFieldOf("max_stalls", DEFAULTS.maxStalls).forGetter(Market::maxStalls),
			Codec.doubleRange(0, 1).optionalFieldOf("crowd_share", DEFAULTS.crowdShare).forGetter(Market::crowdShare),
			Codec.BOOL.optionalFieldOf("visiting_wares", DEFAULTS.visitingWares).forGetter(Market::visitingWares),
			Codec.doubleRange(0.1, 10).optionalFieldOf("visiting_markup", DEFAULTS.visitingMarkup).forGetter(Market::visitingMarkup),
			Codec.intRange(0, 64).optionalFieldOf("ware_stock", DEFAULTS.wareStock).forGetter(Market::wareStock),
			Codec.intRange(0, 1000).optionalFieldOf("stall_fee", DEFAULTS.stallFee).forGetter(Market::stallFee)
		).apply(i, Market::new));
	}

	/**
	 * @param yearDays        days in the festival year (Minecraft has no seasons; the year is a cycle of feast days)
	 * @param midsummerDay    day of the year of midsummer
	 * @param cultureDay      day of the year of the culture's own festival
	 * @param harvestDay      day of the year of the harvest feast
	 * @param foundersDay     each settlement also keeps the day it was founded
	 * @param minTier         the smallest settlement that holds festivals
	 * @param start           festivities begin (night festivals begin two hours later and run past midnight)
	 * @param end             and end
	 * @param share           share of folk who come
	 * @param feastFoodPerHead food the feast eats from the stockpile, per resident (also while nobody is near)
	 * @param decorations     poles, bonfires and tables set up in the square while it lasts (embodied only)
	 * @param music           musicians play
	 */
	public record Festivals(boolean enabled, int yearDays, int midsummerDay, int cultureDay, int harvestDay, boolean foundersDay, SettlementTier minTier,
		int start, int end, double share, double feastFoodPerHead, boolean decorations, boolean music) {
		public static final Festivals DEFAULTS = new Festivals(true, 56, 14, 28, 42, true, SettlementTier.HAMLET, 17 * 60, 23 * 60, 0.85, 0.5, true,
			true);
		public static final Codec<Festivals> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Festivals::enabled),
			Codec.intRange(7, 3650).optionalFieldOf("year_days", DEFAULTS.yearDays).forGetter(Festivals::yearDays),
			Codec.INT.optionalFieldOf("midsummer_day", DEFAULTS.midsummerDay).forGetter(Festivals::midsummerDay),
			Codec.INT.optionalFieldOf("culture_day", DEFAULTS.cultureDay).forGetter(Festivals::cultureDay),
			Codec.INT.optionalFieldOf("harvest_day", DEFAULTS.harvestDay).forGetter(Festivals::harvestDay),
			Codec.BOOL.optionalFieldOf("founders_day", DEFAULTS.foundersDay).forGetter(Festivals::foundersDay),
			SettlementTier.CODEC.optionalFieldOf("min_tier", DEFAULTS.minTier).forGetter(Festivals::minTier),
			CLOCK.optionalFieldOf("start", DEFAULTS.start).forGetter(Festivals::start),
			CLOCK.optionalFieldOf("end", DEFAULTS.end).forGetter(Festivals::end),
			Codec.doubleRange(0, 1).optionalFieldOf("share", DEFAULTS.share).forGetter(Festivals::share),
			Codec.doubleRange(0, 10).optionalFieldOf("feast_food_per_head", DEFAULTS.feastFoodPerHead).forGetter(Festivals::feastFoodPerHead),
			Codec.BOOL.optionalFieldOf("decorations", DEFAULTS.decorations).forGetter(Festivals::decorations),
			Codec.BOOL.optionalFieldOf("music", DEFAULTS.music).forGetter(Festivals::music)
		).apply(i, Festivals::new));
	}

	/**
	 * @param weddings           single adults court and marry
	 * @param weddingChance      chance per day that a settlement with an eligible couple announces a wedding
	 * @param bannsDays          days between the announcement and the wedding
	 * @param weddingTime        the ceremony (an hour), then the feast (three hours)
	 * @param sameSex            weddings may join two men or two women
	 * @param sameSexShare       of all weddings, roughly this share (when allowed)
	 * @param weddingFood        food the wedding feast eats from the stockpile
	 * @param funerals           the dead are buried with the town gathered
	 * @param funeralDelayDays   days between a death and the funeral
	 * @param funeralTime        the funeral (an hour)
	 * @param births             births are announced
	 * @param guestShare         share of the town (beyond the family) at a wedding
	 * @param mournerShare       share of the town (beyond the family) at a funeral
	 */
	public record Family(boolean weddings, double weddingChance, int bannsDays, int weddingTime, boolean sameSex, double sameSexShare, int weddingFood,
		boolean funerals, int funeralDelayDays, int funeralTime, boolean births, double guestShare, double mournerShare) {
		public static final Family DEFAULTS = new Family(true, 0.08, 3, 11 * 60, true, 0.1, 12, true, 1, 10 * 60, true, 0.4, 0.25);
		public static final Codec<Family> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("weddings", DEFAULTS.weddings).forGetter(Family::weddings),
			Codec.doubleRange(0, 1).optionalFieldOf("wedding_chance", DEFAULTS.weddingChance).forGetter(Family::weddingChance),
			Codec.intRange(0, 30).optionalFieldOf("banns_days", DEFAULTS.bannsDays).forGetter(Family::bannsDays),
			CLOCK.optionalFieldOf("wedding_time", DEFAULTS.weddingTime).forGetter(Family::weddingTime),
			Codec.BOOL.optionalFieldOf("same_sex", DEFAULTS.sameSex).forGetter(Family::sameSex),
			Codec.doubleRange(0, 1).optionalFieldOf("same_sex_share", DEFAULTS.sameSexShare).forGetter(Family::sameSexShare),
			Codec.intRange(0, 1000).optionalFieldOf("wedding_food", DEFAULTS.weddingFood).forGetter(Family::weddingFood),
			Codec.BOOL.optionalFieldOf("funerals", DEFAULTS.funerals).forGetter(Family::funerals),
			Codec.intRange(0, 10).optionalFieldOf("funeral_delay_days", DEFAULTS.funeralDelayDays).forGetter(Family::funeralDelayDays),
			CLOCK.optionalFieldOf("funeral_time", DEFAULTS.funeralTime).forGetter(Family::funeralTime),
			Codec.BOOL.optionalFieldOf("births", DEFAULTS.births).forGetter(Family::births),
			Codec.doubleRange(0, 1).optionalFieldOf("guest_share", DEFAULTS.guestShare).forGetter(Family::guestShare),
			Codec.doubleRange(0, 1).optionalFieldOf("mourner_share", DEFAULTS.mournerShare).forGetter(Family::mournerShare)
		).apply(i, Family::new));
	}

	/**
	 * @param enabled     appoint and keep officials
	 * @param reeveTier   from this tier a reeve runs the town
	 * @param lordTier    from this tier a lord holds it, with a steward
	 * @param judges      appoint a judge once a courthouse stands, a jailer once a jail does
	 * @param courtStart  the lord holds court (at the throne of the keep, else the town hall)
	 * @param courtEnd    until
	 * @param dynasties   a dead lord's eldest grown child inherits
	 */
	public record Offices(boolean enabled, SettlementTier reeveTier, SettlementTier lordTier, boolean judges, int courtStart, int courtEnd,
		boolean dynasties) {
		public static final Offices DEFAULTS = new Offices(true, SettlementTier.VILLAGE, SettlementTier.TOWN, true, 10 * 60, 13 * 60, true);
		public static final Codec<Offices> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Offices::enabled),
			SettlementTier.CODEC.optionalFieldOf("reeve_tier", DEFAULTS.reeveTier).forGetter(Offices::reeveTier),
			SettlementTier.CODEC.optionalFieldOf("lord_tier", DEFAULTS.lordTier).forGetter(Offices::lordTier),
			Codec.BOOL.optionalFieldOf("judges", DEFAULTS.judges).forGetter(Offices::judges),
			CLOCK.optionalFieldOf("court_start", DEFAULTS.courtStart).forGetter(Offices::courtStart),
			CLOCK.optionalFieldOf("court_end", DEFAULTS.courtEnd).forGetter(Offices::courtEnd),
			Codec.BOOL.optionalFieldOf("dynasties", DEFAULTS.dynasties).forGetter(Offices::dynasties)
		).apply(i, Offices::new));
	}

	/**
	 * @param bed         a bed for the night (a respawn point until {@code checkout})
	 * @param checkout    the bed is yours until this time the next morning
	 * @param room        a room with a chest, per day
	 * @param roomGraceDays after the rent runs out, the chest stays yours this many days for you to empty it
	 * @param meal        a hot meal: hunger filled
	 * @param mealRegenSeconds a meal also warms you (regeneration, seconds; 0 for none)
	 */
	public record Inn(boolean enabled, int bed, int checkout, int room, int roomGraceDays, int meal, int mealRegenSeconds) {
		public static final Inn DEFAULTS = new Inn(true, 6, 9 * 60, 12, 2, 3, 10);
		public static final Codec<Inn> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Inn::enabled),
			Codec.intRange(0, 100_000).optionalFieldOf("bed", DEFAULTS.bed).forGetter(Inn::bed),
			CLOCK.optionalFieldOf("checkout", DEFAULTS.checkout).forGetter(Inn::checkout),
			Codec.intRange(0, 100_000).optionalFieldOf("room", DEFAULTS.room).forGetter(Inn::room),
			Codec.intRange(0, 30).optionalFieldOf("room_grace_days", DEFAULTS.roomGraceDays).forGetter(Inn::roomGraceDays),
			Codec.intRange(0, 100_000).optionalFieldOf("meal", DEFAULTS.meal).forGetter(Inn::meal),
			Codec.intRange(0, 600).optionalFieldOf("meal_regen_seconds", DEFAULTS.mealRegenSeconds).forGetter(Inn::mealRegenSeconds)
		).apply(i, Inn::new));
	}

	/**
	 * @param price        a bath
	 * @param regenSeconds the warm water's regeneration (0 for none)
	 */
	public record Bath(boolean enabled, int price, int regenSeconds) {
		public static final Bath DEFAULTS = new Bath(true, 4, 15);
		public static final Codec<Bath> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Bath::enabled),
			Codec.intRange(0, 100_000).optionalFieldOf("price", DEFAULTS.price).forGetter(Bath::price),
			Codec.intRange(0, 600).optionalFieldOf("regen_seconds", DEFAULTS.regenSeconds).forGetter(Bath::regenSeconds)
		).apply(i, Bath::new));
	}

	/**
	 * @param hirePerDay   hiring a mount, per day
	 * @param deposit      left with the stable, returned (less overdue days) when the mount comes back to any stable
	 * @param buy          buying a mount outright
	 * @param returnRadius the mount must be this near the stablemaster to be returned
	 * @param innsHire     innkeepers with a hitching post hire mounts where there is no stable
	 */
	public record Stables(boolean enabled, int hirePerDay, int deposit, int buy, int returnRadius, boolean innsHire) {
		public static final Stables DEFAULTS = new Stables(true, 6, 40, 160, 24, true);
		public static final Codec<Stables> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Stables::enabled),
			Codec.intRange(0, 100_000).optionalFieldOf("hire_per_day", DEFAULTS.hirePerDay).forGetter(Stables::hirePerDay),
			Codec.intRange(0, 100_000).optionalFieldOf("deposit", DEFAULTS.deposit).forGetter(Stables::deposit),
			Codec.intRange(0, 1_000_000).optionalFieldOf("buy", DEFAULTS.buy).forGetter(Stables::buy),
			Codec.intRange(4, 128).optionalFieldOf("return_radius", DEFAULTS.returnRadius).forGetter(Stables::returnRadius),
			Codec.BOOL.optionalFieldOf("inns_hire", DEFAULTS.innsHire).forGetter(Stables::innsHire)
		).apply(i, Stables::new));
	}

	/**
	 * @param harvestCrops   crops to bring in for a harvest job
	 * @param harvestWage    bits per crop brought in
	 * @param haulLoad       stone carried from the quarry to the builders for a hauling job
	 * @param haulWage       bits for a load
	 * @param timeLimitTicks how long a job may take
	 * @param perDay         jobs a player may take per settlement per day
	 */
	public record Wages(boolean enabled, int harvestCrops, int harvestWage, int haulLoad, int haulWage, int timeLimitTicks, int perDay) {
		public static final Wages DEFAULTS = new Wages(true, 16, 1, 32, 6, 7200, 3);
		public static final Codec<Wages> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Wages::enabled),
			Codec.intRange(1, 256).optionalFieldOf("harvest_crops", DEFAULTS.harvestCrops).forGetter(Wages::harvestCrops),
			Codec.intRange(0, 1000).optionalFieldOf("harvest_wage", DEFAULTS.harvestWage).forGetter(Wages::harvestWage),
			Codec.intRange(1, 256).optionalFieldOf("haul_load", DEFAULTS.haulLoad).forGetter(Wages::haulLoad),
			Codec.intRange(0, 10_000).optionalFieldOf("haul_wage", DEFAULTS.haulWage).forGetter(Wages::haulWage),
			Codec.intRange(600, 72_000).optionalFieldOf("time_limit_ticks", DEFAULTS.timeLimitTicks).forGetter(Wages::timeLimitTicks),
			Codec.intRange(0, 100).optionalFieldOf("per_day", DEFAULTS.perDay).forGetter(Wages::perDay)
		).apply(i, Wages::new));
	}

	/**
	 * @param target         points to win
	 * @param stakes         the stakes a player may choose from (each folk caps them by station and temper)
	 * @param anywhere       folk play anywhere, not only at a gaming table or in a tavern or inn
	 * @param dailyLossLimit a folk stops playing for the day after losing this many times their largest stake
	 * @param loadedBias     chance a loaded die that lands badly tumbles again
	 * @param detectBase     chance per loaded roll that the folk notices (plus their perceptiveness)
	 * @param cheatReport    a caught cheat is reported to the law as theft of the pot
	 * @param cheatStanding  the standing deed for being caught (negative)
	 */
	public record Dice(boolean enabled, int target, List<Integer> stakes, boolean anywhere, int dailyLossLimit, double loadedBias, double detectBase,
		boolean cheatReport, int cheatStanding) {
		public static final Dice DEFAULTS = new Dice(true, 2000, List.of(5, 10, 25, 50, 100), false, 4, 0.45, 0.03, true, -15);
		public static final Codec<Dice> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Dice::enabled),
			Codec.intRange(500, 20_000).optionalFieldOf("target", DEFAULTS.target).forGetter(Dice::target),
			Codec.intRange(0, 100_000).listOf().optionalFieldOf("stakes", DEFAULTS.stakes).forGetter(Dice::stakes),
			Codec.BOOL.optionalFieldOf("anywhere", DEFAULTS.anywhere).forGetter(Dice::anywhere),
			Codec.intRange(0, 100).optionalFieldOf("daily_loss_limit", DEFAULTS.dailyLossLimit).forGetter(Dice::dailyLossLimit),
			Codec.doubleRange(0, 1).optionalFieldOf("loaded_bias", DEFAULTS.loadedBias).forGetter(Dice::loadedBias),
			Codec.doubleRange(0, 1).optionalFieldOf("detect_base", DEFAULTS.detectBase).forGetter(Dice::detectBase),
			Codec.BOOL.optionalFieldOf("cheat_report", DEFAULTS.cheatReport).forGetter(Dice::cheatReport),
			Codec.intRange(-100, 0).optionalFieldOf("cheat_standing", DEFAULTS.cheatStanding).forGetter(Dice::cheatStanding)
		).apply(i, Dice::new));
	}
}
