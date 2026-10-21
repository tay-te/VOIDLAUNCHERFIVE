package dev.voidmc.hearth.social.events;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One kind of town event as data ({@code data/<ns>/hearth_social/events/<kind>.json}, docs/LIVING_WORLD_2.md §3.7): how
 * often it comes by tier and on what conditions, when in the day and for how long, where, who has a part, the scene's
 * timeline of beats, the barks it uses and its outcome's numbers.
 *
 * @param chance      a day's chance by tier (camp, hamlet, village, town, city); for a triggered kind, the chance it is
 *                    held when its trigger comes (1 by default)
 * @param needs       town facts that must hold ({@code lord}, {@code timber}, {@code stage}, {@code chapel}, {@code autumn}...)
 * @param start       the earliest minute of the day it begins (0 = midnight)
 * @param latest      the latest minute it may begin (the seed picks between them)
 * @param duration    minutes it lasts
 * @param venue       where it is held (a {@link SceneVenues} name)
 * @param crowd       the most onlookers drawn (§3.4: at most 24)
 * @param barks       the bark category of its crowd and roles ({@code hearth_dialogue/barks/events.json})
 * @param params      outcome numbers by name (a raid's share of the stores, a fire's share burnt)
 * @param scene       for a staged scene of kind {@code scene} (an oath, a dubbing), its name: what
 *                    {@link dev.voidmc.hearth.api.SceneService#stage} is asked for ({@code hearth:<scene>})
 */
public record EventRule(EventKind kind, List<Double> chance, SettlementTier minTier, int minPopulation, List<String> needs, int start, int latest,
	int duration, String venue, int crowd, String barks, List<RoleSpec> roles, List<Beat> timeline, Map<String, Double> params, String scene) {

	/** Minutes of the day, written as {@code "10:30"} or a number of minutes. */
	public static final Codec<Integer> CLOCK = Codec.either(Codec.INT, Codec.STRING).comapFlatMap(e -> e.map(DataResult::success, s -> {
		String[] p = s.trim().split(":");
		try {
			int h = Integer.parseInt(p[0]);
			int m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
			return DataResult.success(Math.floorMod(h * 60 + m, 24 * 60));
		} catch (NumberFormatException ex) {
			return DataResult.error(() -> "not a time of day: " + s);
		}
	}), m -> Either.right(String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)));

	private static final Codec<SettlementTier> TIER = Codec.STRING.comapFlatMap(s -> {
		for (SettlementTier t : SettlementTier.values()) {
			if (t.name().equalsIgnoreCase(s)) {
				return DataResult.success(t);
			}
		}
		return DataResult.error(() -> "unknown tier " + s);
	}, t -> t.name().toLowerCase(java.util.Locale.ROOT));

	public static final Codec<EventRule> CODEC = RecordCodecBuilder.create(i -> i.group(
		EventKind.CODEC.fieldOf("kind").forGetter(EventRule::kind),
		Codec.DOUBLE.listOf().optionalFieldOf("chance", List.of(1.0, 1.0, 1.0, 1.0, 1.0)).forGetter(EventRule::chance),
		TIER.optionalFieldOf("min_tier", SettlementTier.HAMLET).forGetter(EventRule::minTier),
		Codec.INT.optionalFieldOf("min_population", 0).forGetter(EventRule::minPopulation),
		Codec.STRING.listOf().optionalFieldOf("needs", List.of()).forGetter(EventRule::needs),
		CLOCK.optionalFieldOf("start", 10 * 60).forGetter(EventRule::start),
		CLOCK.optionalFieldOf("latest", -1).forGetter(EventRule::latest),
		Codec.INT.optionalFieldOf("duration", 60).forGetter(EventRule::duration),
		Codec.STRING.optionalFieldOf("venue", "square").forGetter(EventRule::venue),
		Codec.intRange(0, 24).optionalFieldOf("crowd", 12).forGetter(EventRule::crowd),
		Codec.STRING.optionalFieldOf("barks", "").forGetter(EventRule::barks),
		RoleSpec.CODEC.listOf().optionalFieldOf("roles", List.of()).forGetter(EventRule::roles),
		Beat.CODEC.listOf().optionalFieldOf("timeline", List.of()).forGetter(EventRule::timeline),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(EventRule::params),
		Codec.STRING.optionalFieldOf("scene", "").forGetter(EventRule::scene)
	).apply(i, EventRule::new));

	/** The day's chance in a town of this tier (0 below the rule's least tier). */
	public double chanceFor(SettlementTier tier) {
		if (tier.ordinal() < this.minTier.ordinal() || this.chance.isEmpty()) {
			return 0;
		}
		return this.chance.get(Math.min(tier.ordinal(), this.chance.size() - 1));
	}

	/** The latest minute it may begin: {@link #start} when not given. */
	public int latestStart() {
		return this.latest < 0 ? this.start : this.latest;
	}

	public double param(String name, double fallback) {
		Double v = this.params.get(name);
		return v == null ? fallback : v;
	}

	/** The role names the timeline and the roles list use, the roles list's first. */
	public List<String> roleNames() {
		List<String> out = new ArrayList<>();
		for (RoleSpec r : this.roles) {
			out.add(r.name());
		}
		return out;
	}

	/**
	 * One part in the scene.
	 *
	 * @param pick  who takes it ({@link SceneCast} names: {@code principals}, {@code family}, {@code priest}, {@code judge},
	 *              {@code guards}, {@code children}, {@code neighbours}, {@code builders}, {@code entertainer}...)
	 * @param count at most this many
	 * @param place where they stand when the timeline does not say ({@link SceneVenues} name), or ""
	 * @param hold  what they carry ({@code minecraft:candle}), or ""
	 */
	public record RoleSpec(String name, String pick, int count, String place, String hold) {
		public static final Codec<RoleSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(RoleSpec::name),
			Codec.STRING.fieldOf("pick").forGetter(RoleSpec::pick),
			Codec.intRange(1, 24).optionalFieldOf("count", 1).forGetter(RoleSpec::count),
			Codec.STRING.optionalFieldOf("place", "").forGetter(RoleSpec::place),
			Codec.STRING.optionalFieldOf("hold", "").forGetter(RoleSpec::hold)
		).apply(i, RoleSpec::new));
	}

	/** The beat actions a timeline may use. */
	public static final Set<String> ACTIONS = Set.of("walk", "stand", "face", "pose", "bark", "sound", "hold", "spectacle", "toll", "dress", "undress",
		"particles", "music", "carry", "spawn", "end");

	/**
	 * One beat of a scene: at {@code at} minutes from its start, a role does something.
	 *
	 * @param role  a role's name, or {@code all}, or {@code scene} for the scene itself (sounds, dressings, the crowd)
	 * @param action one of {@link #ACTIONS}
	 * @param to    a {@link SceneVenues} name for walk, stand and face
	 * @param arg   the action's word: a pose, a bark category, a sound id, a dressing kind, an item id
	 * @param ticks how long a pose holds, a spectacle lasts (seconds), a toll's strokes
	 */
	public record Beat(int at, String role, String action, String to, String arg, int ticks) {
		public static final Codec<Beat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("at").forGetter(Beat::at),
			Codec.STRING.optionalFieldOf("role", "scene").forGetter(Beat::role),
			Codec.STRING.fieldOf("do").forGetter(Beat::action),
			Codec.STRING.optionalFieldOf("to", "").forGetter(Beat::to),
			Codec.STRING.optionalFieldOf("arg", "").forGetter(Beat::arg),
			Codec.INT.optionalFieldOf("ticks", 0).forGetter(Beat::ticks)
		).apply(i, Beat::new));

		public boolean places() {
			return this.action.equals("walk") || this.action.equals("stand") || this.action.equals("carry");
		}
	}
}
