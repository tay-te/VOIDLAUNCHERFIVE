package dev.voidmc.hearth.social.dev;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev tooling, off unless {@code -Dhearth.dev.social=SECONDS}: on a dedicated server (with the sim's dev harness
 * founding towns, e.g. {@code -Dhearth.dev.found=2 -Dhearth.dev.simulate=40}), once a live settlement exists it is
 * held embodied, its day's social business run, a market and a festival held, the bell rung, and the stalls and
 * dressings counted block by block in the world; after {@code SECONDS} it is let go and the harness checks that every
 * dressing came down ({@code -Dhearth.dev.social.stop=true} then stops the server). Everything is logged with the prefix
 * {@code [hearth-social]}.
 */
public final class SocialDevHarness {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static int seconds;
	private static long started = -1;
	private static int phase;
	private static Settlement town;
	private static long firstSeen = -1;
	private static long heldSince;
	private static final List<long[]> FORCED = new ArrayList<>();

	private SocialDevHarness() {
	}

	public static void installIfRequested() {
		String s = System.getProperty("hearth.dev.social");
		if (s == null) {
			return;
		}
		seconds = Integer.parseInt(s);
		ServerTickEvents.END_SERVER_TICK.register(SocialDevHarness::tick);
		LOG.info("[hearth-social] dev harness on ({} s)", seconds);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HearthWorld w = HearthWorld.of(level);
		if (w == null || phase > 3) {
			return;
		}
		long t = level.getGameTime();
		try {
			switch (phase) {
				case 0 -> {
					// wait for the sim's harness to found towns and fast-forward them
					if (w.settlements().isEmpty() || t % 100 != 0) {
						return;
					}
					if (firstSeen < 0) {
						firstSeen = t;
					}
					if (t - firstSeen < Integer.getInteger("hearth.dev.social.delay", 1200)) {
						return;
					}
					town = w.settlements().stream().max((a, b) -> Integer.compare(a.population(), b.population())).orElseThrow();
					// load the town's chunks (at most 24 x 24 round its heart), then hold it embodied
					var b = town.bounds();
					int cx0 = Math.max(b.minX() >> 4, (town.center().getX() >> 4) - 12);
					int cx1 = Math.min(b.maxX() >> 4, (town.center().getX() >> 4) + 12);
					int cz0 = Math.max(b.minZ() >> 4, (town.center().getZ() >> 4) - 12);
					int cz1 = Math.min(b.maxZ() >> 4, (town.center().getZ() >> 4) + 12);
					for (int cx = cx0; cx <= cx1; cx++) {
						for (int cz = cz0; cz <= cz1; cz++) {
							level.setChunkForced(cx, cz, true);
							FORCED.add(new long[] {cx, cz});
						}
					}
					run(server, level, town.center(), "hearth sim embody " + town.id());
					started = t;
					phase = 1;
					LOG.info("[hearth-social] holding {} (#{}, {} souls, {}) embodied", town.name(), town.id(), town.population(), town.tier());
				}
				case 1 -> {
					if (t - started < 100) {
						return;
					}
					TownLife.runDay(town);
					report(w, "after the day's business");
					long before = level.getGameTime();
					TownEvent market = TownLife.force(town, TownEvent.Kind.MARKET, null, 6);
					TownEvent festival = TownLife.force(town, TownEvent.Kind.FESTIVAL, dev.voidmc.hearth.social.calendar.Festival.HARVEST, 6);
					boolean bell = TownLife.ringNow(town);
					LOG.info("[hearth-social] forced {} and {}; bell rung: {}", market == null ? "-" : market.key(), festival == null ? "-" : festival.key(),
						bell);
					phase = 2;
					started = before;
					heldSince = before;
				}
				case 2 -> {
					if (t - started < 100) {
						return;
					}
					SocialLedger ledger = town.data(SocialModule.LEDGER);
					int present = 0;
					List<String> kinds = new ArrayList<>();
					for (SocialLedger.Dressing d : ledger.dressings) {
						BlockPos p = BlockPos.of(d.pos());
						String there = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
						if (there.equals(d.block())) {
							present++;
						}
						if (!kinds.contains(d.block())) {
							kinds.add(d.block());
						}
					}
					TownEvent market = TownLife.current(town, TownEvent.Kind.MARKET);
					List<Dressings.Stall> stalls = market == null ? List.of() : TownLife.stalls(town, market.key());
					LOG.info("[hearth-social] dressings: {} blocks recorded, {} standing in the world; stalls {}; blocks used {}", ledger.dressings.size(),
						present, stalls.size(), kinds);
					for (Dressings.Stall st : stalls) {
						LOG.info("[hearth-social]   stall {} counter {} keeper {}", st.index(), st.counter().toShortString(), st.keeper().toShortString());
					}
					for (String line : TownLife.stats()) {
						LOG.info("[hearth-social] {}", line);
					}
					if (t - heldSince >= seconds * 20L) {
						run(server, level, town.center(), "hearth sim embody " + town.id());
						phase = 3;
						started = t;
					} else {
						started = t + 300; // report again in 20 s
					}
				}
				case 3 -> {
					if (t - started < 100) {
						return;
					}
					SocialLedger ledger = town.data(SocialModule.LEDGER);
					int left = 0;
					for (SocialLedger.Dressing d : ledger.dressings) {
						BlockPos p = BlockPos.of(d.pos());
						if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString().equals(d.block())) {
							left++;
						}
					}
					LOG.info("[hearth-social] let go: embodied={}, {} dressing records left, {} dressing blocks still standing", town.embodied(),
						ledger.dressings.size(), left);
					for (long[] c : FORCED) {
						level.setChunkForced((int) c[0], (int) c[1], false);
					}
					phase = 4;
					if (Boolean.getBoolean("hearth.dev.social.stop")) {
						server.halt(false);
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			LOG.error("[hearth-social] harness failed", e);
			phase = 4;
		}
	}

	private static void report(HearthWorld w, String when) {
		SocialLedger ledger = town.data(SocialModule.LEDGER);
		long now = TownLife.now(town);
		LOG.info("[hearth-social] {} — {} day {} {}:", town.name(), when, TownLife.today(w, town), dev.voidmc.hearth.social.SocialConfig.formatClock(SocialTime.minute(now)));
		for (Office o : Office.values()) {
			Resident r = Officials.holder(town, ledger, o);
			if (r != null) {
				LOG.info("[hearth-social]   {}: {} ({})", o.id(), r.fullName(), r.job.getSerializedName());
			}
		}
		for (TownEvent e : TownLife.events(town)) {
			LOG.info("[hearth-social]   event {} {}-{}", e.key(), SocialTime.minute(e.start()), SocialTime.minute(e.end()));
		}
		for (SocialLedger.Notice n : Notices.current(ledger, TownLife.today(w, town))) {
			LOG.info("[hearth-social]   notice: {}", Notices.text(n, TownLife.today(w, town)).getString());
		}
		for (SocialLedger.FamilyEvent f : ledger.family) {
			LOG.info("[hearth-social]   {} on day {}: {}", f.kind().getSerializedName(), f.day(), f.names());
		}
	}

	private static void run(MinecraftServer server, ServerLevel level, BlockPos at, String command) {
		var source = server.createCommandSourceStack().withLevel(level).withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(at));
		server.getCommands().performPrefixedCommand(source, command);
	}
}
