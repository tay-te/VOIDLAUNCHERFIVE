package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementKind;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.Claims;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jspecify.annotations.Nullable;

/**
 * Small places in the wild: post sites and post rules (§7).
 * Lane B3 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 *
 * <p>Per level a {@link PostWorld}: the site overlay ({@link PostSites}), the posts' squares as planned ground, and their
 * founding as chunks near them load ({@link PostHistory}, adopted by the {@link SimWorld}). Once founded a post is a
 * settlement like any other, of kind {@code POST}: stepped every {@code wild.step_interval} ticks, founding no daughters,
 * sending no caravans, its planner only rebuilding ({@link dev.voidmc.hearth.plan.wild.PostPlanner}); its day's business is
 * {@link PostRules}. Tick work is timed under {@code wild} in {@code /hearth sim stats}.
 */
public final class Wild {
	private static WildConfig config = WildConfig.DEFAULTS;
	private static final Map<SimWorld, PostWorld> WORLDS = new ConcurrentHashMap<>();
	public static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("wild");

	private Wild() {
	}

	public static WildConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(WildConfig newConfig) {
		config = newConfig;
	}

	/** A level's posts (made on first use, from any thread). */
	public static @Nullable PostWorld posts(@Nullable SimWorld world) {
		if (world == null) {
			return null;
		}
		return WORLDS.computeIfAbsent(world, PostWorld::of);
	}

	/** Every level's posts now. */
	public static List<PostWorld> all() {
		return new ArrayList<>(WORLDS.values());
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("wild", WildConfig.CODEC, WildConfig.DEFAULTS);
		// saved state registered before any world loads
		java.util.Objects.requireNonNull(PostData.KEY);
		java.util.Objects.requireNonNull(PostBook.KEY);
		// a post's square is planned ground: trees, boulders, Expanse's sights and towns keep off it
		Claims.register((world, x, z, now) -> {
			PostWorld p = posts(world);
			return p != null && p.isPlanned(x, z, now);
		});
		ResidentClaims.register(PostRules.JOBS);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			SimWorld w = SimServer.world(level);
			if (w == null) {
				return;
			}
			long t0 = System.nanoTime();
			try {
				PostWorld p = posts(w);
				if (p != null) {
					p.onChunkLoad(chunk);
				}
			} catch (RuntimeException e) {
				Hearth.LOG.error("hearth wild: founding posts near chunk {} failed", chunk.getPos(), e);
			}
			TIMER.add(System.nanoTime() - t0);
		});
		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> {
			if (settlement.kind() == SettlementKind.POST && settlement instanceof SimSettlement s) {
				long t0 = System.nanoTime();
				PostRules.daily(s, day);
				TIMER.add(System.nanoTime() - t0);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
		WildCommands.register();
		WildFolk.init();
	}
}
