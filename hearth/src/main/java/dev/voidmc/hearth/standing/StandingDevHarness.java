package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService.Deed;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.model.FolkFacts;
import dev.voidmc.hearth.standing.model.Memories;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.model.TownLedger;
import dev.voidmc.hearth.standing.speech.SpeechKind;
import dev.voidmc.hearth.standing.speech.SpeechOdds;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * A self-check of the standing module against the real sim, for a dedicated dev server (docs/STANDING.md, "Seeing it
 * work"). Off unless asked for:
 *
 * <pre>
 *   -Dhearth.dev.standing=true        once live settlements exist, run a scripted scenario through the real service
 *                                     with a stand-in player and log every step as [hearth-standing]
 *   -Dhearth.dev.standing.delay=S     seconds to wait after towns exist (the sim's harness ages them), default 20
 *   -Dhearth.dev.standing.stop=true   stop the server afterwards
 * </pre>
 *
 * Run it with the sim's harness founding towns ({@code -Dhearth.dev.found=4}) and without the sim's own {@code stop}.
 * A second run on the same world first logs what the first one saved, which checks persistence.
 */
final class StandingDevHarness {
	private static final String TAG = "[hearth-standing] ";
	private static final UUID PLAYER = UUID.nameUUIDFromBytes("hearth-standing-devcheck".getBytes(StandardCharsets.UTF_8));

	private StandingDevHarness() {
	}

	static void install() {
		if (!Boolean.getBoolean("hearth.dev.standing")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Thread t = new Thread(() -> run(server), "hearth-standing-dev");
			t.setDaemon(true);
			t.start();
		});
	}

	private static void run(MinecraftServer server) {
		try {
			StandingEngine engine = StandingModule.engine();
			if (engine == null) {
				log("no standing engine");
				return;
			}
			// Wait for the sim (and its harness) to have live towns.
			int waited = 0;
			while (waited < 600 && server.submit(() -> engine.host().settlements().size()).join() < 2) {
				Thread.sleep(1000);
				waited++;
			}
			// Let the sim's harness found and age its towns first.
			Thread.sleep(1000L * Integer.getInteger("hearth.dev.standing.delay", 20));
			server.submit(() -> scenario(engine)).join();
			server.submit(() -> bench(engine)).join();
		} catch (Throwable t) {
			Hearth.LOG.error(TAG + "failed", t);
		}
		if (Boolean.getBoolean("hearth.dev.standing.stop")) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			server.execute(() -> server.halt(false));
		}
	}

	private static void scenario(StandingEngine engine) {
		StandingHost host = engine.host();
		List<Settlement> towns = new ArrayList<>(host.settlements());
		log("live towns: " + towns.size() + ", today is day " + host.day());
		if (towns.isEmpty()) {
			return;
		}
		towns.sort(Comparator.comparingInt(Settlement::population).reversed());
		Settlement a = towns.getFirst();
		for (Settlement s : towns) {
			TownLedger.Entry e = StandingEngine.ledger(s).get(PLAYER);
			if (e != null) {
				log("persisted from an earlier run: " + s.name() + " standing " + rounded(e.value) + " title " + e.title.getSerializedName() + ", news "
					+ StandingEngine.ledger(s).newsOf(PLAYER).size());
			}
		}
		log("first town: " + a.name() + " (" + a.population() + " souls, " + a.tier().getSerializedName() + ") at " + a.center().toShortString());
		List<dev.voidmc.hearth.api.HearthWorld.SiteInfo> sites = host.sitesNear(a, engine.config().gossip().radius());
		StringBuilder near = new StringBuilder();
		for (dev.voidmc.hearth.api.HearthWorld.SiteInfo site : sites) {
			if (site.settlementId() != a.id()) {
				near.append(' ').append(site.name()).append(site.live() ? "" : "*").append('@').append((int) Math.sqrt(site.center().distSqr(a.center())));
			}
		}
		log("towns within " + engine.config().gossip().radius() + " blocks (* not settled):" + near);
		Resident victim = a.residents().stream().filter(r -> r.age != AgeStage.CHILD).findFirst().orElse(null);
		BlockPos at = a.center();
		long today = host.day();
		int queued = host.region().road().size();
		engine.deed(new Deed(PLAYER, a.id(), Deeds.QUEST_COMPLETED, 15, victim == null ? null : victim.id, at, false));
		log("quest:completed +15 (unwitnessed): standing " + engine.standing(a, PLAYER) + ", " + engine.title(a, PLAYER).getSerializedName());
		engine.deed(new Deed(PLAYER, a.id(), "law:theft", -30, victim == null ? null : victim.id, at, true));
		log("law:theft -30 (witnessed): standing " + engine.standing(a, PLAYER) + ", " + engine.title(a, PLAYER).getSerializedName()
			+ (victim == null ? "" : ", " + victim.fullName() + " thinks " + engine.opinion(a, victim, PLAYER) + " (memories " + Memories.of(victim).size() + ")"));
		engine.deed(new Deed(PLAYER, a.id(), "defense:raid_repelled", 30, null, at, true));
		log("defense:raid_repelled +30: standing " + engine.standing(a, PLAYER) + ", " + engine.title(a, PLAYER).getSerializedName());
		List<RegionData.Travelling> road = host.region().road();
		log("news on the road: " + (road.size() - queued) + " new (" + road.size() + " in all)");
		for (RegionData.Travelling t : road) {
			if (t.player().equals(PLAYER) && t.created() == today) {
				Settlement target = host.settlement(t.target());
				log(String.format(Locale.ROOT, "  %s -> %s: %+.1f, arrives in %d day(s)%s", t.kind(), target == null ? "#" + t.target() + " (not settled)" : target.name(),
					t.strength(), t.arrival() - today, target == null ? "" : ", at " + (int) Math.sqrt(target.center().distSqr(at)) + " blocks"));
			}
		}
		for (int d = 1; d <= 8; d++) {
			int n = engine.deliver(today + d);
			if (n > 0) {
				StringBuilder sb = new StringBuilder();
				for (Settlement s : towns) {
					if (s != a && StandingEngine.ledger(s).has(PLAYER)) {
						sb.append(' ').append(s.name()).append('=').append(engine.standing(s, PLAYER));
					}
				}
				log("day +" + d + ": " + n + " delivered; standing elsewhere:" + sb);
			}
		}
		log("fame at " + a.name() + ": " + engine.fame(PLAYER, at) + ", 800 blocks out: " + engine.fame(PLAYER, at.offset(800, 0, 0)) + ", 2500 out: "
			+ engine.fame(PLAYER, at.offset(2500, 0, 0)));
		for (int d = 0; d < 7; d++) {
			engine.hearsay(a, today + d);
		}
		long heard = a.residents().stream().filter(r -> Memories.of(r).all().stream().anyMatch(m -> m.player().equals(PLAYER) && m.kind().startsWith(StandingEngine.HEARSAY))).count();
		log("after a week of talk, " + heard + " of " + a.population() + " folk have heard of it second-hand");
		int shown = 0;
		for (Resident r : a.residents()) {
			if (r.age == AgeStage.CHILD || shown >= 4) {
				continue;
			}
			shown++;
			SpeechOdds.Inputs in = new SpeechOdds.Inputs(engine.standing(a, PLAYER), engine.opinion(a, r, PLAYER), Look.NEUTRAL.charisma(), Look.NEUTRAL.menace(),
				FolkFacts.traits(r), FolkFacts.mood(r), r.station, r.job, 0.1, 1, 0);
			StringBuilder sb = new StringBuilder();
			for (SpeechKind k : SpeechKind.values()) {
				sb.append(' ').append(k.getSerializedName()).append(' ').append(SpeechOdds.of(k, in, 0.05, 0.95).percent()).append('%');
			}
			log("  " + r.fullName() + " (" + r.job.getSerializedName() + ", " + r.station.getSerializedName() + ", traits " + FolkFacts.traits(r) + ", opinion "
				+ in.opinion() + "):" + sb);
		}
	}

	private static void bench(StandingEngine engine) {
		StandingHost host = engine.host();
		List<Settlement> towns = new ArrayList<>(host.settlements());
		if (towns.isEmpty()) {
			return;
		}
		Random random = new Random(7);
		String[] kinds = {"law:theft", "quest:completed", "economy:trade", "defense:monster_slain", "npc:gift", "law:assault"};
		int[] weights = {-30, 20, 2, 15, 5, -25};
		int n = 2000;
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			Settlement s = towns.get(random.nextInt(towns.size()));
			List<Resident> folk = new ArrayList<>(s.residents());
			Resident r = folk.isEmpty() ? null : folk.get(random.nextInt(folk.size()));
			int k = random.nextInt(kinds.length);
			engine.deed(new Deed(new UUID(0xBE7C4, random.nextInt(40)), s.id(), kinds[k], weights[k], r == null ? null : r.id, s.center(), random.nextBoolean()));
		}
		long deeds = System.nanoTime() - t0;
		t0 = System.nanoTime();
		int delivered = 0;
		long today = host.day();
		for (int d = 1; d <= 10; d++) {
			delivered += engine.deliver(today + d);
		}
		long deliver = System.nanoTime() - t0;
		t0 = System.nanoTime();
		for (Settlement s : towns) {
			engine.dayPassed(s, host.day());
		}
		long days = System.nanoTime() - t0;
		log(String.format(Locale.ROOT, "bench: %d deeds over %d towns in %.1f ms (%.1f us each, gossip routed over the real atlas); %d deliveries in %.1f ms; "
			+ "a day passing in every town %.2f ms", n, towns.size(), deeds / 1e6, deeds / 1e3 / n, delivered, deliver / 1e6, days / 1e6));
	}

	private static String rounded(double v) {
		return Integer.toString(dev.voidmc.hearth.standing.logic.Curves.round(v));
	}

	private static void log(String line) {
		Hearth.LOG.info(TAG + line);
	}
}
