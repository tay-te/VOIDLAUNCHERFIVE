package dev.voidmc.hearth.social.burgher;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.economy.works.Projects;
import dev.voidmc.hearth.npc.companion.Companions;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.standing.logic.Titles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The player's place: sworn burghers of a town (docs/LIVING_WORLD_2.md §8). A property owner is a Householder; one who has
 * held it thirty days, stands at 40 or better and pays the fee swears the burgher's oath before the reeve on a market day
 * and is a Burgher (the standing module's title, through {@link Titles#setSworn}). Burghers pay no market dues (a
 * {@code PriceModifier}, inside the economy's 0.5–1.5) and sit in the town council: once a festival month, on a rest day,
 * they choose which of the planner's next three public works the town builds first, and it is put first as funded
 * works are ({@link Projects#councilChoice}).
 *
 * <p>Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 */
public final class Burghers {
	public static final HearthDataKey<BurgherBook> KEY = HearthDataKey.register(Hearth.id("social/burghers"), HearthDataKey.Scope.SETTLEMENT,
		BurgherBook.CODEC, BurgherBook::new);
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("burghers");
	private static volatile @Nullable MinecraftServer server;

	private Burghers() {
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		Titles.setSworn(Burghers::sworn);
		EconomyService eco = HearthServices.economy();
		if (eco != null) {
			eco.addPriceModifier((player, s, one, buys) -> BurgherRules.priceFactor(s.data(KEY).isSworn(player.getUUID()), buys, cfg()));
		}
		DialogueProvider.register(new BurgherDialogue());
		SettlementEvents.DAY_PASSED.register((world, s, day) -> TIMER.time(() -> daily(world, s, day)));
	}

	static PlaceConfig.Burghers cfg() {
		return Companions.config().burghers();
	}

	/**
	 * Whether the player swore the burgher's oath in the town (for the titles, crowds, feasts and mass: any module may ask).
	 * A map lookup per dimension.
	 */
	public static boolean sworn(long settlementId, UUID player) {
		Settlement s = settlement(settlementId);
		return s != null && s.data(KEY).isSworn(player);
	}

	/** Whether the player is a sworn burgher of this town. */
	public static boolean sworn(Settlement s, UUID player) {
		return s.data(KEY).isSworn(player);
	}

	private static @Nullable Settlement settlement(long id) {
		MinecraftServer srv = server;
		if (srv == null) {
			return null;
		}
		for (ServerLevel level : srv.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			Settlement s = w == null ? null : w.settlement(id);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/** The town's day: owners kept in step, the council opened on its rest day and closed the day after. */
	static void daily(HearthWorld world, Settlement s, long day) {
		BurgherBook book = s.data(KEY);
		boolean dirty = BurgherRules.owners(book, s, day);
		if (book.sitting != Long.MIN_VALUE && book.sitting < day) {
			int chosen = BurgherRules.close(book);
			if (chosen >= 0 && Projects.councilChoice(s, chosen, day)) {
				PlotView p = s.plot(chosen);
				Hearth.LOG.debug("council of {} chose plot {} ({})", s.name(), chosen, p == null ? "?" : p.spec().archetype());
			}
			dirty = true;
		}
		SettlementCalendar cal = HearthServices.calendar();
		boolean rest = cal == SettlementCalendar.NONE ? Math.floorMod(day, 7) == 6 : cal.restDay(s, day);
		if (BurgherRules.councilDue(book, day, rest, cfg())) {
			List<Integer> table = new ArrayList<>();
			for (PlotView p : Projects.councilCandidates(s, cfg().choices())) {
				table.add(p.id());
			}
			if (!table.isEmpty()) {
				BurgherRules.sit(book, day, table);
				dirty = true;
			}
		}
		if (dirty) {
			s.markDirty();
		}
	}

	/** Whether the town keeps a market day within a week (so the oath waits for it). */
	static boolean keepsMarket(Settlement s, long today) {
		SettlementCalendar cal = HearthServices.calendar();
		for (long d = today; d < today + 7; d++) {
			if (cal.marketDay(s, d)) {
				return true;
			}
		}
		return false;
	}
}
