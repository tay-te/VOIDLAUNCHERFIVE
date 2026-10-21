package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.npc.entity.Folk;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Crowds that react: stimuli, reactions, shutters, brawls, barks (§5).
 * Lane A3 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 */
public final class Crowds {
	private static CrowdConfig config = CrowdConfig.DEFAULTS;
	private static final CrowdDirector DIRECTOR = new CrowdDirector();
	private static final Shutters SHUTTERS = new Shutters(DIRECTOR);
	private static final Brawls BRAWLS = new Brawls(DIRECTOR);
	private static final List<CrowdBehaviour> BEHAVIOURS = new ArrayList<>();
	private static final BrawlBehaviour BRAWL = new BrawlBehaviour(BRAWLS);

	private Crowds() {
	}

	public static CrowdConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(CrowdConfig newConfig) {
		config = newConfig;
	}

	public static CrowdDirector director() {
		return DIRECTOR;
	}

	public static Shutters shutters() {
		return SHUTTERS;
	}

	public static Brawls brawls() {
		return BRAWLS;
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("crowds", CrowdConfig.CODEC, CrowdConfig.DEFAULTS);
		HearthServices.setCrowds(new CrowdServiceImpl(DIRECTOR, BRAWLS, SHUTTERS));
		NpcService npc = HearthServices.npc();
		for (Reaction r : Reaction.VALUES) {
			CrowdBehaviour b = new CrowdBehaviour(r, DIRECTOR);
			BEHAVIOURS.add(b);
			if (npc != null) {
				npc.registerBehaviour(b);
			}
		}
		if (npc != null) {
			npc.registerBehaviour(BRAWL);
		}
		Barks.registerReloader();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DIRECTOR.tick(server);
			if (server.getTickCount() % 10 == 0) {
				BRAWLS.tick(server);
			}
			SHUTTERS.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		// a title or standing moved: the crowd reads the player afresh (it keeps them 15 s otherwise)
		SettlementEvents.STANDING_CHANGED.register((world, s, player, from, to) -> DIRECTOR.forget(player));
		SettlementEvents.EMBODIMENT_CHANGED.register((world, s, embodied) -> {
			if (!embodied) {
				SHUTTERS.forget(s.id());
			}
		});
		// a body in the street: screams, a crowd, the watch
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof Folk || entity instanceof ServerPlayer)) {
				return;
			}
			if (entity instanceof Folk f && f.brain() == null) {
				return; // a traveller's body is the road's business
			}
			HearthWorld world = HearthWorld.of(level);
			var s = world == null ? null : world.settlementAt(entity.blockPosition());
			if (s != null && s.embodied()) {
				DIRECTOR.body(level, entity.blockPosition(), s);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> BRAWLS.onDamage(entity, source));
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> CrowdCommands.register(dispatcher));
	}

	/** Everything in memory forgotten (server stop, tests). */
	public static void clear() {
		DIRECTOR.clear();
		SHUTTERS.clear();
		BRAWLS.clear();
		BRAWL.clear();
		for (CrowdBehaviour b : BEHAVIOURS) {
			b.clear();
		}
		Barks.clear();
	}
}
