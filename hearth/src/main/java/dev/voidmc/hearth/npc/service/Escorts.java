package dev.voidmc.hearth.npc.service;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Escorts: a resident who follows a player toward a destination ({@link NpcService#follow}) — a courier on an escort
 * quest, a prisoner walked to the cell. The escort is a {@link NpcService.FolkBehaviour} at priority 280 (above the
 * routine and the folk's own fear, below guards on duty), and the resident is pinned while it lasts so the embodiment
 * manager does not fold them when they leave their settlement's range.
 *
 * <p>It ends {@link Status#ARRIVED} within {@code escort.arrive_distance} of the destination, or {@link Status#LOST} if
 * the player is gone (logged off, another dimension, dead) for {@code escort.lose_after} ticks or the folk is folded or
 * dies. Either way {@link #FINISHED} fires and {@link #status} keeps the outcome for ten minutes, so the module that
 * asked can poll it or listen (docs/requests/p3-npc.md asks for an api form of this).
 */
public final class Escorts {
	public static final Identifier ID = Hearth.id("npc/follow");
	public static final int PRIORITY = 280;
	/** Game ticks an outcome is remembered after the escort ends. */
	private static final long REMEMBER = 20 * 60 * 10;

	public enum Status {
		ACTIVE, ARRIVED, LOST
	}

	/** Fired once when an escort ends. Server thread. */
	public static final Event<Finished> FINISHED = EventFactory.createArrayBacked(Finished.class, ls -> (resident, player, status) -> {
		for (Finished l : ls) {
			l.onFinished(resident, player, status);
		}
	});

	@FunctionalInterface
	public interface Finished {
		void onFinished(UUID resident, UUID player, Status status);
	}

	private static final class Escort {
		final UUID resident;
		UUID player;
		ResourceKey<Level> dimension;
		BlockPos destination;
		Status status = Status.ACTIVE;
		long playerGoneSince = -1;
		long endedAt;

		Escort(UUID resident, UUID player, ResourceKey<Level> dimension, BlockPos destination) {
			this.resident = resident;
			this.player = player;
			this.dimension = dimension;
			this.destination = destination;
		}
	}

	private static final Map<UUID, Escort> ESCORTS = new HashMap<>();
	/** Residents pinned to a holder (companions to the player who hired them; docs/LIVING_WORLD_2.md §8): never folded. */
	private static final Map<UUID, UUID> PINS = new HashMap<>();

	private Escorts() {
	}

	/** Starts (or redirects) an escort. */
	public static void begin(UUID resident, ServerPlayer player, BlockPos destination) {
		Escort e = ESCORTS.get(resident);
		if (e == null || e.status != Status.ACTIVE) {
			e = new Escort(resident, player.getUUID(), player.level().dimension(), destination.immutable());
			ESCORTS.put(resident, e);
		} else {
			e.player = player.getUUID();
			e.dimension = player.level().dimension();
			e.destination = destination.immutable();
			e.playerGoneSince = -1;
		}
	}

	/** The state of a resident's escort: active, how it ended (for ten minutes), or null if there is none. */
	public static @Nullable Status status(UUID resident) {
		Escort e = ESCORTS.get(resident);
		return e == null ? null : e.status;
	}

	/** Whether the embodiment manager must leave this resident's entity alone (an active escort, or a pin). */
	public static boolean pinned(UUID resident) {
		if (!PINS.isEmpty() && PINS.containsKey(resident)) {
			return true;
		}
		Escort e = ESCORTS.get(resident);
		return e != null && e.status == Status.ACTIVE;
	}

	/** Pins a resident to a holder ({@link NpcService#pin}): the embodiment manager never folds their folk while it lasts. */
	public static void pin(UUID resident, UUID holder) {
		PINS.put(resident, holder);
	}

	public static void unpin(UUID resident) {
		PINS.remove(resident);
	}

	/** The holder a resident is pinned to, or null. */
	public static @Nullable UUID pinHolder(UUID resident) {
		return PINS.get(resident);
	}

	/** The residents pinned to a holder. */
	public static java.util.List<UUID> pinnedTo(UUID holder) {
		java.util.List<UUID> out = new java.util.ArrayList<>(2);
		for (Map.Entry<UUID, UUID> e : PINS.entrySet()) {
			if (e.getValue().equals(holder)) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	/** The resident died or was folded away mid-escort. */
	public static void gone(UUID resident) {
		Escort e = ESCORTS.get(resident);
		if (e != null && e.status == Status.ACTIVE) {
			finish(e, Status.LOST, 0);
		}
	}

	/** Forgets ended escorts after a while; called now and then. */
	public static void housekeeping(long gameTime) {
		ESCORTS.values().removeIf(e -> e.status != Status.ACTIVE && gameTime - e.endedAt > REMEMBER);
	}

	public static void clear() {
		ESCORTS.clear();
		PINS.clear();
	}

	public static int active() {
		int n = 0;
		for (Escort e : ESCORTS.values()) {
			if (e.status == Status.ACTIVE) {
				n++;
			}
		}
		return n;
	}

	private static void finish(Escort e, Status status, long gameTime) {
		e.status = status;
		e.endedAt = gameTime;
		FINISHED.invoker().onFinished(e.resident, e.player, status);
	}

	/** The behaviour that does the following. Registered by the npc module itself. */
	public static final class Behaviour implements NpcService.FolkBehaviour {
		@Override
		public Identifier id() {
			return ID;
		}

		@Override
		public int priority() {
			return PRIORITY;
		}

		@Override
		public boolean wants(NpcService.FolkHandle folk) {
			Escort e = ESCORTS.get(folk.resident().id);
			return e != null && e.status == Status.ACTIVE;
		}

		@Override
		public void start(NpcService.FolkHandle folk) {
			if (folk.entity() instanceof Folk f && f.isSleeping()) {
				f.stopSleeping();
			}
		}

		@Override
		public boolean tick(NpcService.FolkHandle folk) {
			Escort e = ESCORTS.get(folk.resident().id);
			if (e == null || e.status != Status.ACTIVE) {
				return false;
			}
			if (!(folk.entity().level() instanceof ServerLevel level)) {
				return false;
			}
			NpcConfig.Escort cfg = NpcModule.config().escort();
			long now = level.getGameTime();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.player);
			Folk me = (Folk) folk.entity();
			if (me.position().distanceToSqr(e.destination.getX() + 0.5, e.destination.getY(), e.destination.getZ() + 0.5)
				<= cfg.arriveDistance() * cfg.arriveDistance()) {
				folk.stop();
				if (player != null) {
					folk.lookAt(player);
				}
				folk.say(Component.translatable("hearth.npc.escort.arrived"), null);
				finish(e, Status.ARRIVED, now);
				return false;
			}
			boolean present = player != null && player.isAlive() && player.level() == level && !player.isSpectator();
			if (!present) {
				folk.stop();
				if (e.playerGoneSince < 0) {
					e.playerGoneSince = now;
				} else if (now - e.playerGoneSince > cfg.loseAfter()) {
					folk.say(Component.translatable("hearth.npc.escort.lost"), null);
					finish(e, Status.LOST, now);
					return false;
				}
				return true;
			}
			e.playerGoneSince = -1;
			double d2 = me.distanceToSqr(player);
			if (d2 > cfg.teleportDistance() * cfg.teleportDistance()) {
				// Left far behind (a fast rider, a cliff): catch up the way a tamed wolf does.
				BlockPos spot = FolkSpots.standableNear(level, player.blockPosition(), 3);
				if (spot != null && level.isPositionEntityTicking(spot)) {
					me.getNavigation().stop();
					me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				}
				return true;
			}
			if (d2 > 3.5 * 3.5) {
				folk.moveTo(player.blockPosition(), d2 > 10 * 10 ? 0.85 : 0.65);
			} else {
				folk.stop();
				folk.lookAt(player);
			}
			return true;
		}

		@Override
		public void stop(NpcService.FolkHandle folk) {
			folk.stop();
			Escort e = ESCORTS.get(folk.resident().id);
			if (e != null && e.status == Status.ACTIVE && !folk.entity().isAlive()) {
				finish(e, Status.LOST, folk.gameTime());
			}
		}
	}
}
