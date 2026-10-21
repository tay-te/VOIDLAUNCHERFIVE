package dev.voidmc.hearth.npc.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Station;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/hearth/place.json} (docs/LIVING_WORLD_2.md §8). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record PlaceConfig(Companions companions, Household household, Commissions commissions, Burghers burghers, Map<String, Double> wages) {
	public static final PlaceConfig DEFAULTS = new PlaceConfig(Companions.DEFAULTS, Household.DEFAULTS, Commissions.DEFAULTS, Burghers.DEFAULTS,
		defaultWages());

	public static final Codec<PlaceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Companions.CODEC.optionalFieldOf("companions", Companions.DEFAULTS).forGetter(PlaceConfig::companions),
		Household.CODEC.optionalFieldOf("household", Household.DEFAULTS).forGetter(PlaceConfig::household),
		Commissions.CODEC.optionalFieldOf("commissions", Commissions.DEFAULTS).forGetter(PlaceConfig::commissions),
		Burghers.CODEC.optionalFieldOf("burghers", Burghers.DEFAULTS).forGetter(PlaceConfig::burghers),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("wages", defaultWages()).forGetter(PlaceConfig::wages)
	).apply(i, PlaceConfig::new));

	/** The bits a working adult of a station earns a day (the economy's household wages, by default the same numbers). */
	public double wage(Station station) {
		Double v = this.wages.get(station.getSerializedName());
		return v != null ? v : defaultWages().getOrDefault(station.getSerializedName(), 3.0);
	}

	private static Map<String, Double> defaultWages() {
		Map<String, Double> m = new LinkedHashMap<>();
		m.put("poor", 2.0);
		m.put("peasant", 4.0);
		m.put("artisan", 5.0);
		m.put("merchant", 7.0);
		m.put("clergy", 4.0);
		m.put("scholar", 5.0);
		m.put("official", 6.0);
		m.put("gentry", 12.0);
		m.put("lord", 25.0);
		return m;
	}

	/**
	 * @param perPlayer    companions a player may hire (section 13: 2)
	 * @param maxPerServer companions embodied server-wide (shrinks as the server slows)
	 * @param feeDays      the hiring fee, in days of their wage
	 * @param wageFactor   their daily wage, times their station's
	 * @param unpaidDays   unpaid this many days running, they go home
	 * @param waitTicks    left behind (the player in another dimension, logged off does not count) this long, they go home
	 * @param offered      hands an innkeeper names at once
	 * @param leaveBelow   loyalty under this, they go home
	 * @param catchUp      blocks behind out of sight before they catch up the way a tamed wolf does
	 */
	public record Companions(int perPlayer, int maxPerServer, int feeDays, double wageFactor, int unpaidDays, long waitTicks, int offered,
		int leaveBelow, int catchUp) {
		public static final Companions DEFAULTS = new Companions(2, 64, 5, 2.0, 2, 24000L, 3, -40, 24);
		public static final Codec<Companions> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 8).optionalFieldOf("per_player", DEFAULTS.perPlayer).forGetter(Companions::perPlayer),
			Codec.intRange(0, 1024).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Companions::maxPerServer),
			Codec.intRange(0, 60).optionalFieldOf("fee_days", DEFAULTS.feeDays).forGetter(Companions::feeDays),
			Codec.doubleRange(0, 20).optionalFieldOf("wage_factor", DEFAULTS.wageFactor).forGetter(Companions::wageFactor),
			Codec.intRange(1, 60).optionalFieldOf("unpaid_days", DEFAULTS.unpaidDays).forGetter(Companions::unpaidDays),
			Codec.LONG.optionalFieldOf("wait_ticks", DEFAULTS.waitTicks).forGetter(Companions::waitTicks),
			Codec.intRange(1, 8).optionalFieldOf("offered", DEFAULTS.offered).forGetter(Companions::offered),
			Codec.intRange(-100, 0).optionalFieldOf("leave_below", DEFAULTS.leaveBelow).forGetter(Companions::leaveBelow),
			Codec.intRange(8, 128).optionalFieldOf("catch_up", DEFAULTS.catchUp).forGetter(Companions::catchUp)
		).apply(i, Companions::new));
	}

	/**
	 * @param servantsPerHouse servants a player's house may keep (and no more than its beds)
	 * @param wageFactor       a servant's daily wage, times the poor station's
	 * @param unpaidDays       unpaid this many days running, they leave
	 * @param larder           food units the larder chest is kept stocked to
	 */
	public record Household(int servantsPerHouse, double wageFactor, int unpaidDays, int larder) {
		public static final Household DEFAULTS = new Household(3, 1.0, 3, 16);
		public static final Codec<Household> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 16).optionalFieldOf("servants_per_house", DEFAULTS.servantsPerHouse).forGetter(Household::servantsPerHouse),
			Codec.doubleRange(0, 20).optionalFieldOf("wage_factor", DEFAULTS.wageFactor).forGetter(Household::wageFactor),
			Codec.intRange(1, 60).optionalFieldOf("unpaid_days", DEFAULTS.unpaidDays).forGetter(Household::unpaidDays),
			Codec.intRange(0, 256).optionalFieldOf("larder", DEFAULTS.larder).forGetter(Household::larder)
		).apply(i, Household::new));
	}

	/**
	 * @param knighthood    the top rank of a lord's commissions is a household knighthood (section 13: yes)
	 * @param retainer      commissions done to be a retainer (with the pledge)
	 * @param sworn         commissions done to be a sworn man (and a Friend of the seat)
	 * @param knight        commissions done to be a household knight
	 * @param knightStanding the standing in the seat a knight needs
	 * @param domainRadius  towns without a lord of their own this near a lord's seat are of his domain
	 */
	public record Commissions(boolean knighthood, int retainer, int sworn, int knight, int knightStanding, int domainRadius) {
		public static final Commissions DEFAULTS = new Commissions(true, 1, 3, 6, 70, 1500);
		public static final Codec<Commissions> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("knighthood", DEFAULTS.knighthood).forGetter(Commissions::knighthood),
			Codec.intRange(0, 100).optionalFieldOf("retainer", DEFAULTS.retainer).forGetter(Commissions::retainer),
			Codec.intRange(0, 100).optionalFieldOf("sworn", DEFAULTS.sworn).forGetter(Commissions::sworn),
			Codec.intRange(0, 100).optionalFieldOf("knight", DEFAULTS.knight).forGetter(Commissions::knight),
			Codec.intRange(-100, 100).optionalFieldOf("knight_standing", DEFAULTS.knightStanding).forGetter(Commissions::knightStanding),
			Codec.intRange(0, 20000).optionalFieldOf("domain_radius", DEFAULTS.domainRadius).forGetter(Commissions::domainRadius)
		).apply(i, Commissions::new));
	}

	/**
	 * @param minStanding   the standing the burgher's oath asks
	 * @param heldDays      days property must have been held
	 * @param feeCrowns     the burgher's fee, crowns a tier (a hamlet one, a city five)
	 * @param marketDayOnly the oath is sworn only on a market day (when the town keeps one)
	 * @param dues          market dues waived: what a burgher pays on buying (1 − dues) and gets on selling (1 + dues)
	 * @param councilDays   the council sits every this many days (a festival month), on a rest day
	 * @param choices       public works the council chooses among
	 */
	public record Burghers(int minStanding, int heldDays, int feeCrowns, boolean marketDayOnly, double dues, int councilDays, int choices) {
		public static final Burghers DEFAULTS = new Burghers(40, 30, 1, true, 0.05, 28, 3);
		public static final Codec<Burghers> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(-100, 100).optionalFieldOf("min_standing", DEFAULTS.minStanding).forGetter(Burghers::minStanding),
			Codec.intRange(0, 3650).optionalFieldOf("held_days", DEFAULTS.heldDays).forGetter(Burghers::heldDays),
			Codec.intRange(0, 100).optionalFieldOf("fee_crowns", DEFAULTS.feeCrowns).forGetter(Burghers::feeCrowns),
			Codec.BOOL.optionalFieldOf("market_day_only", DEFAULTS.marketDayOnly).forGetter(Burghers::marketDayOnly),
			Codec.doubleRange(0, 0.25).optionalFieldOf("dues", DEFAULTS.dues).forGetter(Burghers::dues),
			Codec.intRange(1, 365).optionalFieldOf("council_days", DEFAULTS.councilDays).forGetter(Burghers::councilDays),
			Codec.intRange(1, 6).optionalFieldOf("choices", DEFAULTS.choices).forGetter(Burghers::choices)
		).apply(i, Burghers::new));
	}
}
