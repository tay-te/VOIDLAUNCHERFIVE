package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.standing.appearance.Appearances;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.logic.Curves;
import dev.voidmc.hearth.standing.logic.GossipRoutes;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.model.Company;
import dev.voidmc.hearth.standing.model.FolkFacts;
import dev.voidmc.hearth.standing.model.Memories;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.model.StandingKeys;
import dev.voidmc.hearth.standing.model.TownLedger;
import dev.voidmc.hearth.standing.rules.DeedRule;
import dev.voidmc.hearth.standing.rules.StandingRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The standing service (docs/STANDING.md): reputation in three layers, deeds, gossip, fame, titles and companies.
 * Event-driven — nothing here runs per tick except what a deed, a day or a delivery check asks for — and all state is in
 * the settlements' and the world's saved data, so it costs nothing in towns nobody touches.
 *
 * <p><b>The settlement layer is the authority.</b> Each town's {@link TownLedger} holds every player's standing there.
 * The first time a player is touched in a town their standing is seeded from the sim's existing reputation number (so
 * worlds from before the standing module keep their reputations), and every change is mirrored back into the sim's
 * number for modules that still read it. When the sim delegates {@code Settlement.reputation} here, the seeding read
 * comes back to this service and is answered without recursion, and the mirror's echo (an {@code hearth:adjust} deed) is
 * ignored.
 *
 * <p>Server thread only.
 */
public final class StandingEngine implements StandingService {
	private final StandingHost host;
	private final Supplier<StandingConfig> config;
	/** resident → settlement id, for opinion lookups by resident alone; validated on use. */
	private final Map<UUID, Long> residentHomes = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
			return size() > 4096;
		}
	};
	/** The prefix of memories heard from others rather than lived. */
	public static final String HEARSAY = "hearsay:";
	private boolean seeding;
	private boolean mirroring;

	public StandingEngine(StandingHost host, Supplier<StandingConfig> config) {
		this.host = host;
		this.config = config;
	}

	public StandingHost host() {
		return this.host;
	}

	public StandingConfig config() {
		return this.config.get();
	}

	public static TownLedger ledger(Settlement s) {
		return s.data(StandingKeys.TOWN);
	}

	// ---------------------------------------------------------------- reads

	@Override
	public int standing(long settlementId, UUID player) {
		Settlement s = this.host.settlement(settlementId);
		return s == null ? 0 : standing(s, player);
	}

	public int standing(Settlement s, UUID player) {
		TownLedger.Entry e = ledger(s).get(player);
		return e != null ? Curves.round(e.value) : seedValue(s, player);
	}

	@Override
	public Title title(long settlementId, UUID player) {
		Settlement s = this.host.settlement(settlementId);
		return s == null ? Title.STRANGER : title(s, player);
	}

	public Title title(Settlement s, UUID player) {
		return Titles.of(standing(s, player), owns(s, player), Titles.sworn(s.id(), player), this.host.outlawByLaw(player, s.id()), config().titles());
	}

	@Override
	public int opinion(UUID resident, UUID player) {
		Settlement s = home(resident);
		if (s == null) {
			return 0;
		}
		Resident r = s.resident(resident);
		return r == null ? 0 : opinion(s, r, player);
	}

	public int opinion(Settlement s, Resident r, UUID player) {
		return Memories.of(r).opinion(player, this.host.day(), standing(s, player), config().opinion());
	}

	@Override
	public int fame(UUID player, BlockPos near) {
		StandingConfig.Fame f = config().fame();
		return (int) Math.round(this.host.region().fameAt(player, near.getX(), near.getZ(), this.host.day(), f.radius(), f.halfLifeDays()));
	}

	@Override
	public int renown(UUID player, BlockPos near) {
		return (int) Math.round(Curves.clamp(renownAt(player, near), -100, 100));
	}

	@Override
	public void remember(UUID resident, UUID player, String kind, int weight) {
		Settlement s = home(resident);
		Resident r = s == null ? null : s.resident(resident);
		if (r != null) {
			remember(r, player, kind, weight);
			s.markDirty();
		}
	}

	@Override
	public Odds odds(ServerPlayer player, Settlement settlement, Resident resident, Speech kind, double difficulty, long bribeBits) {
		return view(dev.voidmc.hearth.standing.speech.SpeechCheck.odds(player, settlement, resident, kindOf(kind), difficulty, bribeBits), kind);
	}

	@Override
	public boolean attempt(ServerPlayer player, Settlement settlement, Resident resident, Speech kind, double difficulty, long bribeBits) {
		return dev.voidmc.hearth.standing.speech.SpeechCheck.attempt(player, settlement, resident, kindOf(kind), difficulty, bribeBits).success();
	}

	@Override
	public long expectedBribe(Resident resident, double difficulty) {
		return dev.voidmc.hearth.standing.speech.SpeechCheck.expectedBribe(resident, difficulty);
	}

	private static dev.voidmc.hearth.standing.speech.SpeechKind kindOf(Speech kind) {
		return dev.voidmc.hearth.standing.speech.SpeechKind.valueOf(kind.name());
	}

	private static Odds view(dev.voidmc.hearth.standing.speech.SpeechOdds.Odds o, Speech kind) {
		List<Map.Entry<String, Double>> f = new ArrayList<>();
		for (dev.voidmc.hearth.standing.speech.SpeechOdds.Factor x : o.factors()) {
			f.add(Map.entry(x.key(), x.delta()));
		}
		return new Odds(kind, o.chance(), List.copyOf(f));
	}

	/** Signed renown at a place: positive if known for good deeds, negative for bad (fame itself is unsigned). */
	public double renownAt(UUID player, BlockPos near) {
		StandingConfig.Fame f = config().fame();
		return this.host.region().renown(player, near.getX(), near.getZ(), this.host.day(), f.radius(), f.halfLifeDays());
	}

	@Override
	public float charisma(ServerPlayer player) {
		return (float) look(player).charisma();
	}

	@Override
	public float menace(ServerPlayer player) {
		return (float) look(player).menace();
	}

	public Look look(ServerPlayer player) {
		StandingConfig cfg = config();
		double violence = this.host.region().violence(player.getUUID(), this.host.day(), cfg.appearance().violenceHalfLifeDays());
		return Appearances.look(player, Appearances.localCulture(player), violence, cfg.appearance());
	}

	@Override
	public @Nullable String company(UUID player) {
		if (!config().companies().enabled()) {
			return null;
		}
		Company c = this.host.region().companyOf(player);
		return c == null ? null : c.name();
	}

	/** Whether the player owns a plot in the town. */
	public static boolean owns(Settlement s, UUID player) {
		for (PlotView p : s.plots()) {
			if (player.equals(p.owner())) {
				return true;
			}
		}
		return false;
	}

	private @Nullable Settlement home(UUID resident) {
		Long id = this.residentHomes.get(resident);
		if (id != null) {
			Settlement s = this.host.settlement(id);
			if (s != null && s.resident(resident) != null) {
				return s;
			}
		}
		for (Settlement s : this.host.settlements()) {
			if (s.resident(resident) != null) {
				this.residentHomes.put(resident, s.id());
				return s;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- seeding

	/** The sim's own number for a player who has no entry yet; 0 when re-entered through a sim that delegates back here. */
	private int seedValue(Settlement s, UUID player) {
		if (!config().town().seedFromSettlement() || this.seeding) {
			return 0;
		}
		this.seeding = true;
		try {
			return Curves.clampInt(s.reputation(player), -100, 100);
		} finally {
			this.seeding = false;
		}
	}

	private TownLedger.Entry entry(Settlement s, TownLedger ledger, UUID player) {
		TownLedger.Entry e = ledger.get(player);
		if (e != null) {
			return e;
		}
		int seed = seedValue(s, player);
		e = ledger.entry(player);
		e.value = seed;
		e.title = Titles.of(seed, owns(s, player), Titles.sworn(s.id(), player), this.host.outlawByLaw(player, s.id()), config().titles());
		e.touched = this.host.day();
		return e;
	}

	// ---------------------------------------------------------------- deeds

	@Override
	public void deed(Deed d) {
		boolean adjust = Deeds.isAdjust(d.kind());
		if (adjust && this.mirroring) {
			return; // our own mirror coming back through a sim that delegates addReputation here
		}
		Settlement s = d.settlementId() == 0 ? null : this.host.settlement(d.settlementId());
		if (d.kind().equals(Deeds.SEED)) {
			if (s != null && !ledger(s).has(d.player())) {
				TownLedger.Entry e = ledger(s).entry(d.player());
				e.value = Curves.clamp(d.weight(), -100, 100);
				e.title = Titles.of(Curves.round(e.value), owns(s, d.player()), Titles.sworn(s.id(), d.player()), this.host.outlawByLaw(d.player(), s.id()), config().titles());
				e.touched = this.host.day();
				s.markDirty();
			}
			return;
		}
		DeedRule rule = adjust ? DeedRule.ADJUST : StandingRules.get().rule(d.kind());
		if (!rule.action().isEmpty()) {
			this.host.action(d, rule);
		}
		StandingConfig cfg = config();
		long day = this.host.day();
		boolean known = d.witnessed() || d.weight() > 0;
		boolean regionChanged = false;

		if (s != null) {
			TownLedger ledger = ledger(s);
			double town = d.weight() * rule.settlement();
			if (!d.witnessed() && !adjust) {
				town *= town < 0 ? cfg.town().unwitnessedBad() : cfg.town().unwitnessedGood();
			}
			TownLedger.Entry e = entry(s, ledger, d.player());
			double applied = change(s, ledger, e, town, !adjust);
			if (!adjust) {
				e.deeds++;
				if (applied != 0) {
					share(s, ledger, d.player(), applied);
				}
				remember(s, d, rule, day);
				if (rule.news() && known && Math.abs(d.weight()) >= cfg.town().newsMinWeight()) {
					ledger.addNews(new TownLedger.News(d.player(), this.host.playerName(d.player()), d.kind(), d.weight(), s.name(), day, true),
						cfg.town().newsPerTown());
				}
				double g = d.weight() * rule.gossip();
				if (cfg.gossip().enabled() && known && Math.abs(g) >= cfg.gossip().threshold()) {
					regionChanged |= spread(s, d, g, day);
				}
			}
			s.markDirty();
		}
		if (!adjust) {
			double fame = d.weight() * rule.fame();
			if (known && Math.abs(fame) >= cfg.fame().threshold()) {
				StandingConfig.Fame f = cfg.fame();
				this.host.region().addFame(d.player(), d.pos().getX(), d.pos().getZ(), fame * f.scale(), day, f.radius() / 4, f.halfLifeDays(), f.sources());
				regionChanged = true;
			}
			if (rule.violent() && d.weight() < 0) {
				this.host.region().addViolence(d.player(), Math.abs(d.weight()) * 0.5, day, cfg.appearance().violenceHalfLifeDays());
				regionChanged = true;
			}
		}
		if (regionChanged) {
			this.host.regionDirty();
		}
	}

	/**
	 * Moves a player's standing in a town, fires STANDING_CHANGED when the rounded number or the title changes, and
	 * mirrors the change into the sim's number. Returns how much it actually moved.
	 */
	private double change(Settlement s, TownLedger ledger, TownLedger.Entry e, double delta, boolean soft) {
		if (delta == 0) {
			return 0;
		}
		StandingConfig cfg = config();
		double old = e.value;
		e.value = soft ? Curves.apply(old, delta, cfg.town().softCap()) : Curves.clamp(old + delta, -100, 100);
		e.touched = this.host.day();
		int before = Curves.round(old);
		int after = Curves.round(e.value);
		if (after != before) {
			mirror(s, e.player, after - before);
		}
		announce(s, e, after != before);
		return e.value - old;
	}

	private void mirror(Settlement s, UUID player, int delta) {
		if (!config().town().mirrorToSettlement() || delta == 0) {
			return;
		}
		this.mirroring = true;
		try {
			s.addReputation(player, delta);
		} finally {
			this.mirroring = false;
		}
	}

	/** Recomputes the title and fires STANDING_CHANGED if the standing moved or the title is not the one last announced. */
	private void announce(Settlement s, TownLedger.Entry e, boolean moved) {
		Title now = Titles.of(Curves.round(e.value), owns(s, e.player), Titles.sworn(s.id(), e.player), this.host.outlawByLaw(e.player, s.id()), config().titles());
		if (moved || now != e.title) {
			Title from = e.title;
			e.title = now;
			this.host.standingChanged(s, e.player, from, now);
		}
	}

	/**
	 * Re-reads a player's title in a town (a plot bought, the law's verdict) and announces it if it changed. The tracker
	 * calls this while a player is in town; cheap when nothing changed.
	 */
	public void refreshTitle(Settlement s, UUID player) {
		TownLedger ledger = ledger(s);
		TownLedger.Entry e = ledger.get(player);
		if (e == null) {
			Title t = title(s, player);
			if (t == Title.STRANGER) {
				return;
			}
			e = entry(s, ledger, player);
			s.markDirty();
		}
		Title before = e.title;
		announce(s, e, false);
		if (before != e.title) {
			s.markDirty();
		}
	}

	/** The company's share of a member's change, to every other member (not passed on again). */
	private void share(Settlement s, TownLedger ledger, UUID player, double applied) {
		StandingConfig.Companies cfg = config().companies();
		if (!cfg.enabled() || cfg.share() <= 0) {
			return;
		}
		Company c = this.host.region().companyOf(player);
		if (c == null) {
			return;
		}
		for (UUID m : c.members()) {
			if (!m.equals(player)) {
				change(s, ledger, entry(s, ledger, m), applied * cfg.share(), true);
			}
		}
	}

	/** The affected resident, their family and the witnesses remember the deed. */
	private void remember(Settlement s, Deed d, DeedRule rule, long day) {
		StandingConfig.Opinion cfg = config().opinion();
		Set<UUID> touched = new HashSet<>();
		if (d.resident() != null && rule.victim() != 0) {
			Resident victim = s.resident(d.resident());
			if (victim != null) {
				memory(victim, d, d.weight() * rule.victim(), day, cfg);
				touched.add(victim.id);
				if (rule.family() != 0 && cfg.familyShare() > 0) {
					for (UUID kin : FolkFacts.family(victim)) {
						Resident k = s.resident(kin);
						if (k != null && touched.add(kin)) {
							memory(k, d, d.weight() * rule.family() * cfg.familyShare(), day, cfg);
						}
					}
				}
			}
		}
		if (d.witnessed() && rule.witness() != 0 && cfg.maxWitnesses() > 0) {
			for (UUID w : this.host.witnesses(d, cfg.witnessRadius(), cfg.maxWitnesses())) {
				Resident r = s.resident(w);
				if (r != null && touched.add(w)) {
					memory(r, d, d.weight() * rule.witness(), day, cfg);
				}
			}
		}
	}

	private static void memory(Resident r, Deed d, double weight, long day, StandingConfig.Opinion cfg) {
		int w = Curves.clampInt(Math.round(weight), -100, 100);
		if (w == 0) {
			return;
		}
		Memories m = Memories.of(r);
		m.add(new Memories.Memory(d.player(), d.kind(), w, day), cfg);
		m.save(r);
	}

	/**
	 * Leaves a memory with a resident directly (speech checks, conversation): no town standing, news or fame. The caller
	 * marks the settlement dirty.
	 */
	public void remember(Resident r, UUID player, String kind, int weight) {
		if (weight == 0) {
			return;
		}
		Memories m = Memories.of(r);
		m.add(new Memories.Memory(player, kind, Curves.clampInt(weight, -100, 100), this.host.day()), config().opinion());
		m.save(r);
	}

	// ---------------------------------------------------------------- gossip

	/** Puts news of a deed on the roads to the towns around. Returns whether any went out. */
	private boolean spread(Settlement origin, Deed d, double strength, long day) {
		StandingConfig.Gossip cfg = config().gossip();
		List<HearthWorld.SiteInfo> sites = this.host.sitesNear(origin, cfg.radius());
		if (sites.isEmpty()) {
			return false;
		}
		List<GossipRoutes.Node> nodes = new ArrayList<>(Math.min(sites.size(), 48));
		for (HearthWorld.SiteInfo site : sites) {
			if (site.settlementId() != origin.id()) {
				nodes.add(new GossipRoutes.Node(site.settlementId(), site.center().getX(), site.center().getZ()));
				if (nodes.size() >= 48) {
					break;
				}
			}
		}
		GossipRoutes.Node from = new GossipRoutes.Node(origin.id(), origin.center().getX(), origin.center().getZ());
		String name = this.host.playerName(d.player());
		RegionData region = this.host.region();
		boolean any = false;
		for (GossipRoutes.Route r : GossipRoutes.routes(from, nodes, cfg.radius(), cfg.linkDistance(), cfg.neighbours(), cfg.hopFade(),
			cfg.blocksPerDay(), cfg.minDelayDays())) {
			double st = strength * r.factor();
			if (Math.abs(st) < 1) {
				continue;
			}
			region.schedule(new RegionData.Travelling(d.player(), name, d.kind(), st, origin.id(), origin.name(), r.id(), day + r.delay(), day));
			any = true;
		}
		if (any) {
			region.trim(cfg.maxQueue());
		}
		return any;
	}

	/**
	 * Delivers the news that has arrived by today: each town hears it, its standing moves, its folk have something new to
	 * talk about. News for towns not settled yet waits for them. Cheap when nothing is due (one comparison).
	 */
	public int deliver() {
		return deliver(this.host.day());
	}

	/** Delivers what has arrived by {@code today} (the dev harness looks ahead with it). */
	public int deliver(long today) {
		RegionData region = this.host.region();
		if (!region.anyArrived(today)) {
			return 0;
		}
		int delivered = 0;
		int held = config().gossip().heldDays();
		for (RegionData.Travelling t : region.arrived(today)) {
			Settlement s = this.host.settlement(t.target());
			if (s == null) {
				if (today - t.arrival() <= held) {
					region.hold(t);
				}
				continue;
			}
			receive(s, t, today);
			delivered++;
		}
		region.expireHeld(today, held);
		this.host.regionDirty();
		return delivered;
	}

	/** A town just settled (adopted): the news that was waiting for it arrives. */
	public void settled(Settlement s) {
		List<RegionData.Travelling> waiting = this.host.region().release(s.id());
		if (waiting.isEmpty()) {
			return;
		}
		long today = this.host.day();
		for (RegionData.Travelling t : waiting) {
			receive(s, t, today);
		}
		this.host.regionDirty();
	}

	private void receive(Settlement s, RegionData.Travelling t, long today) {
		StandingConfig cfg = config();
		TownLedger ledger = ledger(s);
		TownLedger.Entry e = entry(s, ledger, t.player());
		double applied = change(s, ledger, e, t.strength() * cfg.gossip().standingFactor(), true);
		ledger.addNews(new TownLedger.News(t.player(), t.playerName(), t.kind(), Curves.round(t.strength()), t.originName(), today, false),
			cfg.town().newsPerTown());
		s.markDirty();
		int delta = Curves.round(applied);
		if (cfg.gossip().tellPlayer() && delta != 0) {
			this.host.gossipArrived(t.player(), s, t.originName(), delta);
		}
	}

	// ---------------------------------------------------------------- days

	/**
	 * A day passed in a town: bad names slowly heal (and good ones fade, if the server wants that). Only entries whose
	 * rounded number changes announce anything, so catch-up over many days stays cheap.
	 */
	public void dayPassed(Settlement s, long day) {
		hearsay(s, day);
		StandingConfig.Town cfg = config().town();
		if (cfg.negativeDriftPerDay() <= 0 && cfg.positiveDriftPerDay() <= 0) {
			return;
		}
		TownLedger ledger = ledger(s);
		boolean dirty = false;
		for (TownLedger.Entry e : ledger.entries()) {
			if (e.value == 0) {
				continue;
			}
			double old = e.value;
			e.value = Curves.drift(old, cfg.negativeDriftPerDay(), cfg.positiveDriftPerDay());
			if (e.value != old) {
				dirty = true;
				int before = Curves.round(old);
				int after = Curves.round(e.value);
				if (after != before) {
					mirror(s, e.player, after - before);
					announce(s, e, true);
				}
			}
		}
		if (dirty) {
			s.markDirty();
		}
	}

	/**
	 * Gossip between folk: a few times a day someone passes the strongest thing they recently saw or suffered at a
	 * player's hands on to a friend (or a neighbour), who remembers a share of it as hearsay. Hearsay is not passed on
	 * again, so a story spreads through a town over days without echoing. Seeded by town and day, so catch-up is
	 * repeatable; a handful of record reads per town per day.
	 */
	void hearsay(Settlement s, long day) {
		StandingConfig.Opinion cfg = config().opinion();
		if (cfg.hearsayPerDay() <= 0 || cfg.hearsayShare() <= 0) {
			return;
		}
		List<Resident> folk = new ArrayList<>(s.residents());
		if (folk.size() < 2) {
			return;
		}
		java.util.Random random = new java.util.Random(s.id() * 0x9E3779B97F4A7C15L + day);
		boolean dirty = false;
		for (int i = 0; i < cfg.hearsayPerDay(); i++) {
			Resident teller = folk.get(random.nextInt(folk.size()));
			Memories.Memory story = null;
			for (Memories.Memory m : Memories.of(teller).all()) {
				if (day - m.day() <= 7 && Math.abs(m.weight()) >= 15 && !m.kind().startsWith(HEARSAY) && !m.kind().startsWith("standing:")
					&& (story == null || Math.abs(m.weight()) > Math.abs(story.weight()))) {
					story = m;
				}
			}
			if (story == null) {
				continue;
			}
			Resident listener = null;
			for (UUID f : FolkFacts.friends(teller)) {
				Resident r = s.resident(f);
				if (r != null) {
					listener = r;
					break;
				}
			}
			if (listener == null) {
				listener = folk.get(random.nextInt(folk.size()));
			}
			if (listener == teller) {
				continue;
			}
			int w = Curves.clampInt(Curves.round(story.weight() * cfg.hearsayShare()), -100, 100);
			Memories heard = Memories.of(listener);
			if (w != 0 && !heard.has(story.player(), HEARSAY + story.kind(), Long.MIN_VALUE)) {
				heard.add(new Memories.Memory(story.player(), HEARSAY + story.kind(), w, day), cfg);
				heard.save(listener);
				dirty = true;
			}
		}
		if (dirty) {
			s.markDirty();
		}
	}

	// ---------------------------------------------------------------- admin

	/** Sets a player's standing outright (admin command); fires STANDING_CHANGED. */
	public void set(Settlement s, UUID player, int value) {
		TownLedger ledger = ledger(s);
		TownLedger.Entry e = entry(s, ledger, player);
		change(s, ledger, e, Curves.clamp(value, -100, 100) - e.value, false);
		s.markDirty();
	}
}
