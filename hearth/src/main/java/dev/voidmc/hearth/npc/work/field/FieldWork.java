package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.land.GreatMines;
import dev.voidmc.hearth.social.life.StallWares;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Work you can see: remote work sites, droves, clamps, boats, stall wares, haulers' handcarts (docs/LIVING_WORLD_2.md §6).
 * Lane A4 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 *
 * <p>Nothing here produces anything: the sim's chains already make the logs, charcoal, wool, fish and ore; this shows the
 * work where a player can see it, from the books. Its tick is timed as {@code field work} in {@code /hearth sim stats}
 * (the remote sites' embodiment pass as {@code remote sites}, the stalls as {@code stall wares}).
 */
public final class FieldWork {
	private static FieldWorkConfig config = FieldWorkConfig.DEFAULTS;
	private static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("field work");
	// its parts, so a spike can be told apart (the flocks' budget is §9.1's ≤ 1 ms a tick at the cap)
	private static final LivingWorldStats.Timer FLOCKS = LivingWorldStats.timer("field work: flocks");
	private static final LivingWorldStats.Timer CLAMPS = LivingWorldStats.timer("field work: clamps");
	private static final LivingWorldStats.Timer BOATS = LivingWorldStats.timer("field work: boats");
	/** Entity tags of the views this lane puts into the world (flocks, boats, stall wares): never saved with a chunk. */
	public static final Set<String> NO_SAVE = Set.of(Herds.TAG, Boats.TAG, StallWares.TAG, Handcarts.TAG);
	private static long ticks;
	private static long healedDay = Long.MIN_VALUE;

	private FieldWork() {
	}

	public static FieldWorkConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(FieldWorkConfig newConfig) {
		config = newConfig;
		GreatMines.setEnabled(newConfig.mines().greatMines());
		FieldSites.clear();
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("fieldwork", FieldWorkConfig.CODEC, FieldWorkConfig.DEFAULTS);
		GreatMines.setEnabled(config.mines().greatMines());
		Flocks.touch();
		Clamps.touch();
		// the quest module's culture-aware goods table, shared (stall wares, carts)
		HearthServices.setGoodsItems(dev.voidmc.hearth.quest.gen.GoodsItems.SERVICE);
		// herders out with the flock 07:00-18:00, miners at the pithead 06:00-18:00
		ScheduleOverrides.register(FieldSites.WINDOWS);
		// the Great Mine's square, so Expanse's abandoned pithead stands down where a town reopens it
		GreatMines.registerClaims(config.mines().claim());
		StallWares.init();
		ServerTickEvents.END_SERVER_TICK.register(FieldWork::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(Herds.TAG) && entity.level() instanceof ServerLevel level) {
				Herds.onDeath(level, entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((Entity entity, ServerLevel level) -> {
			if (entity.entityTags().contains(Herds.TAG)) {
				Herds.onRemoved(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Herds.clear();
			Boats.clear();
			Handcarts.clear();
			FieldSites.clear();
			WorkBarks.clear();
			GreatMines.clearClaims();
			healedDay = Long.MIN_VALUE;
		});
	}

	/** Whether an entity is one of this lane's views (never written into a chunk). */
	public static boolean noSave(Entity entity) {
		Set<String> tags = entity.entityTags();
		if (tags.isEmpty()) {
			return false;
		}
		for (String t : NO_SAVE) {
			if (tags.contains(t)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		FieldSites.setClock(server.overworld().getGameTime());
		if (!config.sites().enabled()) {
			return;
		}
		TIMER.time(() -> {
			FLOCKS.time(() -> Herds.tick(server));
			CLAMPS.time(() -> Clamps.tick(server));
			BOATS.time(() -> Boats.tick(server));
			Handcarts.tick(server);
			if (ticks % 1200 == 0) {
				daily(server);
			}
		});
	}

	/** Once a sim day (looked for every minute): flock losses made good, Great Mines found by live towns claimed. */
	private static void daily(MinecraftServer server) {
		long day = NpcTime.today();
		if (day == healedDay) {
			return;
		}
		healedDay = day;
		for (ServerLevel level : server.getAllLevels()) {
			for (Settlement s : SettlementSources.all(level)) {
				Herds.heal(s, day);
				for (ResourceSite site : s.sites()) {
					if (GreatMines.isGreat(site)) {
						GreatMines.reopened(site.center());
					}
				}
			}
		}
	}
}
