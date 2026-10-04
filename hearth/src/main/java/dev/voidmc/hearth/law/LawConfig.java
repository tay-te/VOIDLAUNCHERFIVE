package dev.voidmc.hearth.law;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService.Offence;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * {@code config/hearth/law.json}: every rule of the law a server owner might want to change. Every field is optional
 * and falls back to its default, so an old file keeps working when fields are added.
 *
 * <p>{@link #mode} picks the overall severity: {@code full} is Kingdom Come (witnesses, guards, fines, jail, the
 * pillory, outlaws), {@code gentle} keeps witnesses and fines but drops jail, the pillory, outlaws and violent guards and
 * lets bounties lapse, and {@code off} turns crime detection off entirely (ownership and stolen marks still show).
 * {@link #effective()} applies the mode on top of the individual switches.
 *
 * @param exemptCreative       creative and spectator players commit no crimes and pass every lock
 * @param exemptOps            game masters (permission level 2) too (off by default: single-player worlds with cheats
 *                             would otherwise have no law)
 * @param pvpInTowns           players may hurt each other inside a settlement's bounds
 * @param protectPlayerProperty theft from another player's deeded house counts as a crime when folk see it
 */
public record LawConfig(Mode mode, boolean exemptCreative, boolean exemptOps, boolean pvpInTowns, boolean protectPlayerProperty,
	Crimes crimes, Witnesses witnesses, Fines fines, Justice justice, Stealth stealth) {

	public static final LawConfig DEFAULTS = new LawConfig(Mode.FULL, true, false, false, true, Crimes.DEFAULTS, Witnesses.DEFAULTS,
		Fines.DEFAULTS, Justice.DEFAULTS, Stealth.DEFAULTS);

	public static final Codec<LawConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		opt(Mode.CODEC, "mode", DEFAULTS.mode).forGetter(LawConfig::mode),
		opt(Codec.BOOL, "exempt_creative", DEFAULTS.exemptCreative).forGetter(LawConfig::exemptCreative),
		opt(Codec.BOOL, "exempt_ops", DEFAULTS.exemptOps).forGetter(LawConfig::exemptOps),
		opt(Codec.BOOL, "pvp_in_towns", DEFAULTS.pvpInTowns).forGetter(LawConfig::pvpInTowns),
		opt(Codec.BOOL, "protect_player_property", DEFAULTS.protectPlayerProperty).forGetter(LawConfig::protectPlayerProperty),
		opt(Crimes.CODEC, "crimes", Crimes.DEFAULTS).forGetter(LawConfig::crimes),
		opt(Witnesses.CODEC, "witnesses", Witnesses.DEFAULTS).forGetter(LawConfig::witnesses),
		opt(Fines.CODEC, "fines", Fines.DEFAULTS).forGetter(LawConfig::fines),
		opt(Justice.CODEC, "justice", Justice.DEFAULTS).forGetter(LawConfig::justice),
		opt(Stealth.CODEC, "stealth", Stealth.DEFAULTS).forGetter(LawConfig::stealth)
	).apply(i, LawConfig::new));

	/**
	 * A field read as optional (missing: the default) but always written, so the file a server owner opens lists every
	 * switch with its value.
	 */
	static <A> MapCodec<A> opt(Codec<A> codec, String name, A defaultValue) {
		return codec.optionalFieldOf(name).xmap(o -> o.orElse(defaultValue), java.util.Optional::of);
	}

	/** The settings as they apply: the mode laid over the individual switches. */
	public LawConfig effective() {
		return switch (this.mode) {
			case FULL -> this;
			case OFF -> new LawConfig(this.mode, this.exemptCreative, this.exemptOps, this.pvpInTowns, this.protectPlayerProperty,
				Crimes.NONE, this.witnesses, this.fines, this.justice, this.stealth);
			case GENTLE -> new LawConfig(this.mode, this.exemptCreative, this.exemptOps, this.pvpInTowns, this.protectPlayerProperty,
				new Crimes(this.crimes.theft, this.crimes.pickpocketing, false, this.crimes.vandalism, this.crimes.assault, this.crimes.murder,
					this.crimes.animals, this.crimes.crops, this.crimes.lockpicking),
				this.witnesses,
				this.fines.scaled(0.5),
				new Justice(false, this.justice.jailBitsPerMinute, this.justice.jailMinMinutes, this.justice.jailMaxMinutes,
					this.justice.sleepThroughJail, false, this.justice.pilloryMaxBounty, this.justice.pilloryMinutes,
					this.justice.confiscateAllStolen, false, false, this.justice.outlawAfterDays,
					this.justice.bountyExpiryDays <= 0 ? 7 : Math.min(7, this.justice.bountyExpiryDays), this.justice.spreadDays,
					this.justice.spreadRadius, this.justice.spreadFraction, this.justice.suspicionSearchThreshold, this.justice.yieldHealth),
				this.stealth);
		};
	}

	public boolean enabled() {
		return this.mode != Mode.OFF;
	}

	public enum Mode implements StringRepresentable {
		FULL, GENTLE, OFF;

		public static final StringRepresentable.EnumCodec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Which deeds are crimes at all. */
	public record Crimes(boolean theft, boolean pickpocketing, boolean trespass, boolean vandalism, boolean assault, boolean murder,
		boolean animals, boolean crops, boolean lockpicking) {
		public static final Crimes DEFAULTS = new Crimes(true, true, true, true, true, true, true, true, true);
		public static final Crimes NONE = new Crimes(false, false, false, false, false, false, false, false, false);
		public static final Codec<Crimes> CODEC = RecordCodecBuilder.create(i -> i.group(
			opt(Codec.BOOL, "theft", true).forGetter(Crimes::theft),
			opt(Codec.BOOL, "pickpocketing", true).forGetter(Crimes::pickpocketing),
			opt(Codec.BOOL, "trespass", true).forGetter(Crimes::trespass),
			opt(Codec.BOOL, "vandalism", true).forGetter(Crimes::vandalism),
			opt(Codec.BOOL, "assault", true).forGetter(Crimes::assault),
			opt(Codec.BOOL, "murder", true).forGetter(Crimes::murder),
			opt(Codec.BOOL, "animals", true).forGetter(Crimes::animals),
			opt(Codec.BOOL, "crops", true).forGetter(Crimes::crops),
			opt(Codec.BOOL, "lockpicking", true).forGetter(Crimes::lockpicking)
		).apply(i, Crimes::new));

		public boolean any() {
			return this.theft || this.pickpocketing || this.trespass || this.vandalism || this.assault || this.murder || this.animals
				|| this.crops || this.lockpicking;
		}
	}

	/**
	 * Who sees what (docs/LIVING_WORLD.md §4).
	 *
	 * @param sightRange        blocks a folk can see a crime from in full light
	 * @param hearingRange      blocks a noisy crime (a smashed door, a fight) carries without line of sight
	 * @param reportSeconds     how long a witness takes to get word to the guard when no guard is near (then it is reported anyway)
	 * @param guardRadius       how far a witness looks for a guard to run to
	 * @param lightWeight       how much darkness hides you (0: light does not matter, 1: a pitch-dark room halves sight range)
	 * @param bribes            witnesses can be bribed to keep quiet
	 * @param threats           witnesses can be threatened into keeping quiet
	 */
	public record Witnesses(int sightRange, int hearingRange, int reportSeconds, int guardRadius, double lightWeight, boolean bribes,
		boolean threats) {
		public static final Witnesses DEFAULTS = new Witnesses(24, 12, 90, 64, 1.0, true, true);
		public static final Codec<Witnesses> CODEC = RecordCodecBuilder.create(i -> i.group(
			opt(Codec.intRange(4, 64), "sight_range", DEFAULTS.sightRange).forGetter(Witnesses::sightRange),
			opt(Codec.intRange(0, 48), "hearing_range", DEFAULTS.hearingRange).forGetter(Witnesses::hearingRange),
			opt(Codec.intRange(5, 3600), "report_seconds", DEFAULTS.reportSeconds).forGetter(Witnesses::reportSeconds),
			opt(Codec.intRange(8, 160), "guard_radius", DEFAULTS.guardRadius).forGetter(Witnesses::guardRadius),
			opt(Codec.doubleRange(0, 1), "light_weight", DEFAULTS.lightWeight).forGetter(Witnesses::lightWeight),
			opt(Codec.BOOL, "bribes", DEFAULTS.bribes).forGetter(Witnesses::bribes),
			opt(Codec.BOOL, "threats", DEFAULTS.threats).forGetter(Witnesses::threats)
		).apply(i, Witnesses::new));
	}

	/**
	 * What each offence costs, in bits. Theft adds {@code valueMultiplier} times what was taken.
	 */
	public record Fines(long theft, double valueMultiplier, long pickpocketing, long lockpicking, long trespass, long poaching,
		long vandalismPerBlock, long assault, long murder, long robbery, long jailbreak, long resistingArrest, double multiplier) {
		public static final Fines DEFAULTS = new Fines(20, 2.0, 30, 25, 15, 30, 3, 60, 1500, 300, 200, 80, 1.0);
		public static final Codec<Fines> CODEC = RecordCodecBuilder.create(i -> i.group(
			opt(Codec.LONG, "theft", DEFAULTS.theft).forGetter(Fines::theft),
			opt(Codec.DOUBLE, "value_multiplier", DEFAULTS.valueMultiplier).forGetter(Fines::valueMultiplier),
			opt(Codec.LONG, "pickpocketing", DEFAULTS.pickpocketing).forGetter(Fines::pickpocketing),
			opt(Codec.LONG, "lockpicking", DEFAULTS.lockpicking).forGetter(Fines::lockpicking),
			opt(Codec.LONG, "trespass", DEFAULTS.trespass).forGetter(Fines::trespass),
			opt(Codec.LONG, "poaching", DEFAULTS.poaching).forGetter(Fines::poaching),
			opt(Codec.LONG, "vandalism_per_block", DEFAULTS.vandalismPerBlock).forGetter(Fines::vandalismPerBlock),
			opt(Codec.LONG, "assault", DEFAULTS.assault).forGetter(Fines::assault),
			opt(Codec.LONG, "murder", DEFAULTS.murder).forGetter(Fines::murder),
			opt(Codec.LONG, "robbery", DEFAULTS.robbery).forGetter(Fines::robbery),
			opt(Codec.LONG, "jailbreak", DEFAULTS.jailbreak).forGetter(Fines::jailbreak),
			opt(Codec.LONG, "resisting_arrest", DEFAULTS.resistingArrest).forGetter(Fines::resistingArrest),
			opt(Codec.DOUBLE, "multiplier", DEFAULTS.multiplier).forGetter(Fines::multiplier)
		).apply(i, Fines::new));

		/** The base fine of an offence (before the value of what was taken and the multiplier). */
		public long base(Offence offence) {
			return switch (offence) {
				case THEFT -> this.theft;
				case PICKPOCKETING -> this.pickpocketing;
				case LOCKPICKING -> this.lockpicking;
				case TRESPASS -> this.trespass;
				case POACHING -> this.poaching;
				case VANDALISM -> this.vandalismPerBlock;
				case ASSAULT -> this.assault;
				case MURDER -> this.murder;
				case ROBBERY -> this.robbery;
				case JAILBREAK -> this.jailbreak;
				case RESISTING_ARREST -> this.resistingArrest;
			};
		}

		Fines scaled(double factor) {
			return new Fines(this.theft, this.valueMultiplier, this.pickpocketing, this.lockpicking, this.trespass, this.poaching,
				this.vandalismPerBlock, this.assault, this.murder, this.robbery, this.jailbreak, this.resistingArrest, this.multiplier * factor);
		}
	}

	/**
	 * Guards and what they do with you.
	 *
	 * @param jail                    submitting means the cell (otherwise the fine, then standing)
	 * @param jailBitsPerMinute       how much bounty one minute in the cell pays off
	 * @param sleepThroughJail        in single player, a sentence is slept through (the clock jumps) instead of served
	 * @param pillory                 lesser crimes may be paid off in the pillory
	 * @param pilloryMaxBounty        the most bounty the pillory can settle
	 * @param pilloryMinutes          real minutes in the pillory
	 * @param confiscateAllStolen     on arrest, every stolen item is taken, not only this town's
	 * @param guardsAttack            guards fight those who resist or flee (otherwise they only follow and shout)
	 * @param outlaws                 an unpaid murder makes an outlaw, attacked on sight in the region
	 * @param outlawAfterDays         days a murderer has to settle before becoming an outlaw
	 * @param bountyExpiryDays        days after which an unpaid bounty lapses (0: never)
	 * @param spreadDays              days for word of a warrant to reach neighbouring towns
	 * @param spreadRadius            how far it travels (blocks between centres)
	 * @param spreadFraction          the part of the bounty neighbours hold you to
	 * @param suspicionSearchThreshold suspicion (0–100) at which guards stop and search you
	 * @param yieldHealth             health at which a fleeing or resisting player is beaten down and arrested
	 */
	public record Justice(boolean jail, int jailBitsPerMinute, int jailMinMinutes, int jailMaxMinutes, boolean sleepThroughJail, boolean pillory,
		int pilloryMaxBounty, int pilloryMinutes, boolean confiscateAllStolen, boolean guardsAttack, boolean outlaws, int outlawAfterDays,
		int bountyExpiryDays, int spreadDays, int spreadRadius, double spreadFraction, int suspicionSearchThreshold, float yieldHealth) {
		public static final Justice DEFAULTS = new Justice(true, 10, 2, 30, true, true, 60, 3, true, true, true, 1, 0, 2, 1500, 0.5, 40, 6.0F);
		private static final MapCodec<Justice> PART_A = RecordCodecBuilder.mapCodec(i -> i.group(
			opt(Codec.BOOL, "jail", DEFAULTS.jail).forGetter(Justice::jail),
			opt(Codec.intRange(1, 10000), "jail_bits_per_minute", DEFAULTS.jailBitsPerMinute).forGetter(Justice::jailBitsPerMinute),
			opt(Codec.intRange(0, 600), "jail_min_minutes", DEFAULTS.jailMinMinutes).forGetter(Justice::jailMinMinutes),
			opt(Codec.intRange(1, 1440), "jail_max_minutes", DEFAULTS.jailMaxMinutes).forGetter(Justice::jailMaxMinutes),
			opt(Codec.BOOL, "sleep_through_jail", DEFAULTS.sleepThroughJail).forGetter(Justice::sleepThroughJail),
			opt(Codec.BOOL, "pillory", DEFAULTS.pillory).forGetter(Justice::pillory),
			opt(Codec.intRange(0, 100000), "pillory_max_bounty", DEFAULTS.pilloryMaxBounty).forGetter(Justice::pilloryMaxBounty),
			opt(Codec.intRange(1, 60), "pillory_minutes", DEFAULTS.pilloryMinutes).forGetter(Justice::pilloryMinutes),
			opt(Codec.BOOL, "confiscate_all_stolen", DEFAULTS.confiscateAllStolen).forGetter(Justice::confiscateAllStolen)
		).apply(i, (a, b, c, d, e, f, g, h, k) -> new Justice(a, b, c, d, e, f, g, h, k, DEFAULTS.guardsAttack, DEFAULTS.outlaws,
			DEFAULTS.outlawAfterDays, DEFAULTS.bountyExpiryDays, DEFAULTS.spreadDays, DEFAULTS.spreadRadius, DEFAULTS.spreadFraction,
			DEFAULTS.suspicionSearchThreshold, DEFAULTS.yieldHealth)));
		private static final MapCodec<Justice> PART_B = RecordCodecBuilder.mapCodec(i -> i.group(
			opt(Codec.BOOL, "guards_attack", DEFAULTS.guardsAttack).forGetter(Justice::guardsAttack),
			opt(Codec.BOOL, "outlaws", DEFAULTS.outlaws).forGetter(Justice::outlaws),
			opt(Codec.intRange(0, 100), "outlaw_after_days", DEFAULTS.outlawAfterDays).forGetter(Justice::outlawAfterDays),
			opt(Codec.intRange(0, 1000), "bounty_expiry_days", DEFAULTS.bountyExpiryDays).forGetter(Justice::bountyExpiryDays),
			opt(Codec.intRange(0, 100), "spread_days", DEFAULTS.spreadDays).forGetter(Justice::spreadDays),
			opt(Codec.intRange(0, 20000), "spread_radius", DEFAULTS.spreadRadius).forGetter(Justice::spreadRadius),
			opt(Codec.doubleRange(0, 1), "spread_fraction", DEFAULTS.spreadFraction).forGetter(Justice::spreadFraction),
			opt(Codec.intRange(1, 100), "suspicion_search_threshold", DEFAULTS.suspicionSearchThreshold).forGetter(Justice::suspicionSearchThreshold),
			opt(Codec.floatRange(1, 20), "yield_health", DEFAULTS.yieldHealth).forGetter(Justice::yieldHealth)
		).apply(i, (a, b, c, d, e, f, g, h, k) -> new Justice(DEFAULTS.jail, DEFAULTS.jailBitsPerMinute, DEFAULTS.jailMinMinutes,
			DEFAULTS.jailMaxMinutes, DEFAULTS.sleepThroughJail, DEFAULTS.pillory, DEFAULTS.pilloryMaxBounty, DEFAULTS.pilloryMinutes,
			DEFAULTS.confiscateAllStolen, a, b, c, d, e, f, g, h, k)));
		/** Two halves read from one flat JSON object (a record codec takes at most 16 fields). */
		public static final Codec<Justice> CODEC = Codec.mapPair(PART_A, PART_B).xmap(
			p -> merge(p.getFirst(), p.getSecond()), j -> com.mojang.datafixers.util.Pair.of(j, j)).codec();

		private static Justice merge(Justice a, Justice b) {
			return new Justice(a.jail, a.jailBitsPerMinute, a.jailMinMinutes, a.jailMaxMinutes, a.sleepThroughJail, a.pillory, a.pilloryMaxBounty,
				a.pilloryMinutes, a.confiscateAllStolen, b.guardsAttack, b.outlaws, b.outlawAfterDays, b.bountyExpiryDays, b.spreadDays,
				b.spreadRadius, b.spreadFraction, b.suspicionSearchThreshold, b.yieldHealth);
		}
	}

	/**
	 * Locks, keys and the thief's trade.
	 *
	 * @param lockDoors          homes and shops lock their doors at night
	 * @param lockContainers     chests and strongboxes in private buildings are locked
	 * @param nightStart         minute of the day homes lock (21:00 = 1260)
	 * @param nightEnd           minute of the day they open again
	 * @param shopClose          minute of the day workshops and shops lock
	 * @param shopOpen           minute they open
	 * @param stolenFadeDays     sim days until a stolen mark fades
	 * @param pickpocketAwake    how fast an awake mark's awareness fills (per second, 1 = caught)
	 * @param pickpocketAsleep   the same for someone asleep
	 * @param pocketRefreshDays  days before a resident's pockets fill again
	 * @param lockpickMinSeconds the least time a lock of the lowest tier can be picked in (a sanity check on the client's result)
	 */
	public record Stealth(boolean lockDoors, boolean lockContainers, int nightStart, int nightEnd, int shopClose, int shopOpen,
		int stolenFadeDays, double pickpocketAwake, double pickpocketAsleep, int pocketRefreshDays, double lockpickMinSeconds) {
		public static final Stealth DEFAULTS = new Stealth(true, true, 21 * 60, 6 * 60, 19 * 60, 7 * 60, 7, 0.14, 0.05, 3, 0.6);
		public static final Codec<Stealth> CODEC = RecordCodecBuilder.create(i -> i.group(
			opt(Codec.BOOL, "lock_doors", DEFAULTS.lockDoors).forGetter(Stealth::lockDoors),
			opt(Codec.BOOL, "lock_containers", DEFAULTS.lockContainers).forGetter(Stealth::lockContainers),
			opt(Codec.intRange(0, 1439), "night_start", DEFAULTS.nightStart).forGetter(Stealth::nightStart),
			opt(Codec.intRange(0, 1439), "night_end", DEFAULTS.nightEnd).forGetter(Stealth::nightEnd),
			opt(Codec.intRange(0, 1439), "shop_close", DEFAULTS.shopClose).forGetter(Stealth::shopClose),
			opt(Codec.intRange(0, 1439), "shop_open", DEFAULTS.shopOpen).forGetter(Stealth::shopOpen),
			opt(Codec.intRange(0, 1000), "stolen_fade_days", DEFAULTS.stolenFadeDays).forGetter(Stealth::stolenFadeDays),
			opt(Codec.doubleRange(0.001, 10), "pickpocket_awake", DEFAULTS.pickpocketAwake).forGetter(Stealth::pickpocketAwake),
			opt(Codec.doubleRange(0.001, 10), "pickpocket_asleep", DEFAULTS.pickpocketAsleep).forGetter(Stealth::pickpocketAsleep),
			opt(Codec.intRange(0, 100), "pocket_refresh_days", DEFAULTS.pocketRefreshDays).forGetter(Stealth::pocketRefreshDays),
			opt(Codec.doubleRange(0, 30), "lockpick_min_seconds", DEFAULTS.lockpickMinSeconds).forGetter(Stealth::lockpickMinSeconds)
		).apply(i, Stealth::new));
	}
}
