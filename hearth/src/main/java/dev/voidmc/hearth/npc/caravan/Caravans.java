package dev.voidmc.hearth.npc.caravan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Caravans between towns, embodied where a player meets them (docs/LIVING_WORLD.md §7.6). The sim moves goods between
 * settlements abstractly and fires {@link SettlementEvents#CARAVAN}; this keeps each departed caravan as a
 * {@link Journey} until it arrives (or is robbed or lost), works out where it is on the road from the time since it left,
 * and — every two seconds, for at most one caravan per pass — puts its party on the road when a player is within the
 * embodiment spawn radius of that spot in ticking chunks, and folds the party away when no player is near any more.
 *
 * <p>Costs nothing per tick but a check of the live parties; journeys are a small capped list. Pack animals carry
 * {@link #TAG}, which the {@code EntityNoSaveMixin} reads so they are never written into chunks.
 */
public final class Caravans {
	/** Entity tag on the caravans' pack animals: never saved, never herded, never stabled. */
	public static final String TAG = "hearth.caravan";
	/** Game ticks after which a journey the sim never closed is forgotten (three sim days). */
	private static final long FORGET = 24000L * 3;
	private static final int PASS = 40;

	private final List<Journey> journeys = new ArrayList<>();
	private final Map<Long, CaravanParty> parties = new HashMap<>();
	private long nextId = 1;
	private long ticks;

	/** The sim's caravan news. */
	public void onCaravan(HearthWorld world, Settlement from, Settlement to, SettlementEvents.CaravanEvent.Kind kind, long caravanId, int[] cargo) {
		if (kind == SettlementEvents.CaravanEvent.Kind.DEPARTED) {
			NpcConfig.Caravans cfg = NpcModule.config().caravans();
			if (cfg.maxActive() == 0 || from.dimension() != to.dimension()) {
				return;
			}
			while (this.journeys.size() >= cfg.maxActive()) {
				end(world.level().getServer(), this.journeys.get(0));
			}
			HearthWorld.CaravanView view = caravanId > 0 ? world.caravan(caravanId) : null;
			long now = world.level().getGameTime();
			this.journeys.add(new Journey(this.nextId++, from.dimension(), from.id(), to.id(), to.name(), from.center(), to.center(), cargo.clone(),
				view != null ? view.departTick() : now, caravanId, view != null ? view.arriveTick() : 0));
			return;
		}
		// arrived, robbed or lost: that caravan (or, for one without an id, the oldest on that road) ends
		for (Journey j : this.journeys) {
			if (caravanId > 0 ? j.simId() == caravanId : j.fromId() == from.id() && j.toId() == to.id()) {
				end(world.level().getServer(), j);
				return;
			}
		}
	}

	public void tick(MinecraftServer server) {
		this.ticks++;
		if (this.journeys.isEmpty()) {
			return;
		}
		NpcConfig.Caravans cfg = NpcModule.config().caravans();
		NpcConfig.Embodiment emb = NpcModule.config().embodiment();
		// live parties walk every tick
		for (Journey j : this.journeys) {
			CaravanParty party = this.parties.get(j.id());
			ServerLevel level = server.getLevel(j.dimension());
			if (party != null && level != null) {
				party.tick(level, j.progress(level.getGameTime(), cfg.speed()));
			}
		}
		if (this.ticks % PASS != 0) {
			return;
		}
		boolean spawned = false;
		Iterator<Journey> it = this.journeys.iterator();
		while (it.hasNext()) {
			Journey j = it.next();
			ServerLevel level = server.getLevel(j.dimension());
			if (level == null) {
				continue;
			}
			long now = level.getGameTime();
			CaravanParty party = this.parties.get(j.id());
			if (party != null) {
				if (!party.alive()) {
					// overcome on the road: the people are gone, and with them this caravan's view (the sim keeps its own books)
					party.fold();
					this.parties.remove(j.id());
					if (party.robbed()) {
						it.remove();
						// the sim's books: the cargo is gone, the buyer pays nothing (requests/p3-npc.md §4)
						HearthWorld world = HearthWorld.of(level);
						if (world != null && j.simId() > 0) {
							world.caravanLost(j.simId(), party.robber());
						}
					}
					continue;
				}
				if (party.nearestPlayer(level) > emb.despawnRadius()) {
					party.fold();
					this.parties.remove(j.id());
				}
				continue;
			}
			if (now - j.departed() > FORGET) {
				it.remove();
				continue;
			}
			if (!cfg.embodied() || spawned) {
				continue;
			}
			HearthWorld w = HearthWorld.of(level);
			HearthWorld.CaravanView sim = w == null || j.simId() <= 0 ? null : w.caravan(j.simId());
			if (sim != null && sim.escorted()) {
				// an escort quest's own train is this caravan on the road: no second party
				continue;
			}
			// a party counts against the server's folk cap like any other embodied people
			if (NpcModule.embodiment().count() + liveEntities() + cfg.guards() + cfg.traders() + cfg.animals() > emb.maxPerServer()) {
				continue;
			}
			BlockPos at = j.at(j.progress(now, cfg.speed()));
			if (!level.isLoaded(at) || !level.isPositionEntityTicking(at) || nearestPlayer(level, at) > emb.spawnRadius()) {
				continue;
			}
			HearthWorld world = HearthWorld.of(level);
			Settlement from = world == null ? null : world.settlement(j.fromId());
			Culture culture = from != null ? from.culture() : Culture.TEMPERATE;
			CaravanParty p = CaravanParty.spawn(level, j, at, culture, cfg);
			if (p != null) {
				this.parties.put(j.id(), p);
				spawned = true;
			}
		}
	}

	private int liveEntities() {
		int n = 0;
		for (CaravanParty p : this.parties.values()) {
			n += p.size();
		}
		return n;
	}

	private static double nearestPlayer(ServerLevel level, BlockPos at) {
		double best = Double.MAX_VALUE;
		// spectators count, as they do for embodiment: an admin watching the road should see the caravan
		for (ServerPlayer p : level.players()) {
			best = Math.min(best, Math.sqrt(p.distanceToSqr(at.getX() + 0.5, p.getY(), at.getZ() + 0.5)));
		}
		return best;
	}

	private void end(@Nullable MinecraftServer server, Journey j) {
		CaravanParty party = this.parties.remove(j.id());
		if (party != null) {
			party.fold();
		}
		this.journeys.remove(j);
	}

	/** A tagged pack animal died: if a player killed it, the caravan was robbed. */
	public void onAnimalDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob mob) || !entity.entityTags().contains(TAG)) {
			return;
		}
		for (CaravanParty p : this.parties.values()) {
			p.animalKilled(level, mob, source);
		}
	}

	/** Everyone off the road (server stopping). */
	public void foldAll() {
		for (CaravanParty p : this.parties.values()) {
			p.fold();
		}
		this.parties.clear();
	}

	public void clear() {
		foldAll();
		this.journeys.clear();
	}

	public int journeys() {
		return this.journeys.size();
	}

	public int parties() {
		return this.parties.size();
	}

	/** A short report for the debug command. */
	public List<String> describe(long gameTime) {
		List<String> out = new ArrayList<>();
		double speed = NpcModule.config().caravans().speed();
		for (Journey j : this.journeys) {
			BlockPos at = j.at(j.progress(gameTime, speed));
			CaravanParty p = this.parties.get(j.id());
			out.add(String.format("#%d %d -> %s, %d units, %.0f%% at %s%s", j.id(), j.fromId(), j.toName(), j.units(), j.progress(gameTime, speed) * 100,
				at.toShortString(), p != null ? " (on the road, " + p.size() + ")" : ""));
		}
		return out;
	}

	/** Starts a caravan by hand (the dev command), as if the sim had sent one. */
	public void depart(HearthWorld world, Settlement from, Settlement to, int[] cargo) {
		onCaravan(world, from, to, SettlementEvents.CaravanEvent.Kind.DEPARTED, 0, cargo);
	}
}
