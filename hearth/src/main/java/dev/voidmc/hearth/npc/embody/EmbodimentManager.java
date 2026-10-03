package dev.voidmc.hearth.npc.embody;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.RoutinePlacement;
import dev.voidmc.hearth.npc.work.ScaffoldLedger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Decides which residents walk about as entities and keeps the entities and records in step (ARCHITECTURE.md §4).
 *
 * <p>Every {@code pass_interval} ticks it looks at a few embodied settlements (round robin): where each resident is
 * (their entity, or where their routine puts them), how far that is from the nearest player and whether the chunk
 * ticks entities, then asks {@link EmbodimentSelector} what should change. New entities are queued and spawned a few
 * per tick across the server; entities that go out of range are folded back into their record at once. Entities that
 * go away for other reasons (chunk unload, server stop, death) are handled as they happen.
 *
 * <p>Work per tick is bounded by the configuration, never by the number of settlements or residents in the world.
 */
public final class EmbodimentManager {
	/** Blocks of head start an existing entity gets over a record at a similar distance. */
	private static final double STICKINESS = 16.0;

	private final Map<Long, TownState> towns = new HashMap<>();
	private final Map<UUID, Folk> embodied = new HashMap<>();
	private final Map<UUID, Double> lastDistance = new HashMap<>();
	private final Map<UUID, CachedPlaces> placeCache = new HashMap<>();
	private final Deque<SpawnRequest> queue = new ArrayDeque<>();
	private final Set<UUID> queued = new HashSet<>();
	private final Map<ResourceKey<Level>, Integer> cursors = new HashMap<>();
	private long ticks;

	private record SpawnRequest(ResourceKey<Level> dimension, long settlementId, UUID resident) {
	}

	private record CachedPlaces(ResidentPlaces places, long at) {
	}

	public TownState town(Settlement settlement) {
		return this.towns.computeIfAbsent(settlement.id(), TownState::new);
	}

	public @Nullable TownState townIfKnown(long settlementId) {
		return this.towns.get(settlementId);
	}

	public @Nullable Folk entityOf(UUID resident) {
		return this.embodied.get(resident);
	}

	public Collection<Folk> entities() {
		return this.embodied.values();
	}

	public int count() {
		return this.embodied.size();
	}

	// ---------------------------------------------------------------- the server tick

	public void tick(MinecraftServer server) {
		this.ticks++;
		NpcConfig.Embodiment cfg = NpcModule.config().embodiment();
		if (this.ticks % cfg.passInterval() == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				pass(level, cfg);
			}
		}
		drainQueue(server, cfg);
		refreshMarkers(server);
		if (this.ticks % 1200 == 0) {
			housekeeping(server);
		}
	}

	private void pass(ServerLevel level, NpcConfig.Embodiment cfg) {
		List<Settlement> settlements = SettlementSources.embodied(level);
		// Settlements no longer embodied lose their entities.
		if (!this.embodied.isEmpty()) {
			foldAbandoned(level, settlements);
		}
		if (settlements.isEmpty()) {
			return;
		}
		int cursor = this.cursors.getOrDefault(level.dimension(), 0);
		int n = Math.min(cfg.settlementsPerPass(), settlements.size());
		for (int i = 0; i < n; i++) {
			Settlement s = settlements.get(Math.floorMod(cursor + i, settlements.size()));
			consider(level, s, cfg);
		}
		this.cursors.put(level.dimension(), cursor + n);
	}

	private void consider(ServerLevel level, Settlement settlement, NpcConfig.Embodiment cfg) {
		TownState town = town(settlement);
		town.places.refresh(level, settlement, 2);
		// Spectators count: an admin watching a town should see its people.
		List<ServerPlayer> players = level.players();
		List<EmbodimentSelector.Candidate> candidates = new ArrayList<>(settlement.population());
		double nearestWish = Double.MAX_VALUE;
		for (Resident r : settlement.residents()) {
			Folk f = this.embodied.get(r.id);
			BlockPos pos = f != null ? f.blockPosition() : placement(level, settlement, r, town).pos();
			double d = Double.POSITIVE_INFINITY;
			for (ServerPlayer p : players) {
				d = Math.min(d, Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
			}
			boolean ticking = level.isPositionEntityTicking(pos);
			if (f == null && this.queued.contains(r.id)) {
				continue;
			}
			candidates.add(new EmbodimentSelector.Candidate(r.id, d, f != null, ticking));
			if (f != null) {
				this.lastDistance.put(r.id, d);
			} else if (ticking && d <= cfg.spawnRadius()) {
				nearestWish = Math.min(nearestWish, d);
			}
		}
		int room = cfg.maxPerServer() - this.embodied.size() - this.queue.size();
		if (room <= 0 && nearestWish < Double.MAX_VALUE) {
			// The server is full: someone nearer a player than the farthest entity anywhere takes its place.
			room += makeRoom(level.getServer(), nearestWish);
		}
		EmbodimentSelector.Plan plan = EmbodimentSelector.plan(candidates, cfg.maxPerSettlement(), Math.max(0, room), cfg.spawnRadius(),
			cfg.despawnRadius(), STICKINESS);
		for (UUID id : plan.fold()) {
			Folk f = this.embodied.get(id);
			if (f != null) {
				fold(level, f, true);
			}
		}
		for (UUID id : plan.spawn()) {
			this.queue.addLast(new SpawnRequest(level.dimension(), settlement.id(), id));
			this.queued.add(id);
		}
	}

	private int makeRoom(MinecraftServer server, double nearerThan) {
		UUID worst = null;
		double worstD = nearerThan + STICKINESS;
		for (Map.Entry<UUID, Double> e : this.lastDistance.entrySet()) {
			if (e.getValue() > worstD && this.embodied.containsKey(e.getKey())) {
				worst = e.getKey();
				worstD = e.getValue();
			}
		}
		if (worst == null) {
			return 0;
		}
		Folk f = this.embodied.get(worst);
		if (f != null && f.level() instanceof ServerLevel level) {
			fold(level, f, true);
			return 1;
		}
		return 0;
	}

	private void drainQueue(MinecraftServer server, NpcConfig.Embodiment cfg) {
		int budget = cfg.spawnsPerTick();
		while (budget > 0 && !this.queue.isEmpty()) {
			SpawnRequest req = this.queue.pollFirst();
			this.queued.remove(req.resident);
			ServerLevel level = server.getLevel(req.dimension);
			if (level == null || this.embodied.containsKey(req.resident)) {
				continue;
			}
			Settlement s = SettlementSources.find(level, req.settlementId);
			Resident r = s == null ? null : s.resident(req.resident);
			if (s == null || r == null || !s.embodied()) {
				continue;
			}
			if (spawn(level, s, r) != null) {
				budget--;
			}
		}
	}

	/** Makes a resident into an entity where their routine says they are. Returns null if there is no room to stand. */
	public @Nullable Folk spawn(ServerLevel level, Settlement settlement, Resident resident) {
		TownState town = town(settlement);
		RoutinePlacement.Placement where = placement(level, settlement, resident, town);
		BlockPos stand = where.bed() != null && level.isLoaded(where.bed()) ? where.bed() : FolkSpots.standableNear(level, where.pos(), 4);
		if (stand == null || !level.isPositionEntityTicking(stand)) {
			return null;
		}
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		bind(level, folk, settlement, resident, town);
		folk.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (!level.addFreshEntity(folk)) {
			this.embodied.remove(resident.id, folk);
			settlement.setResidentEmbodied(resident.id, false);
			return null;
		}
		if (where.bed() != null && level.getBlockState(where.bed()).getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock) {
			folk.sleepIn(where.bed());
		}
		return folk;
	}

	/** Attaches an entity to a resident: identity, health, brain, bookkeeping. */
	public void bind(ServerLevel level, Folk folk, Settlement settlement, Resident resident, TownState town) {
		folk.setIdentity(resident.id, resident.fullName(), resident.appearance, settlement.culture(), resident.job, resident.age, resident.female);
		folk.setSettlementId(settlement.id());
		NpcData data = NpcData.of(resident);
		float health = data.health();
		if (health > 0) {
			// Wounds heal with the days: a folk folded away hurt comes back a little better.
			long since = level.getGameTime() - data.lastPosTime();
			folk.setHealth(Math.min(folk.getMaxHealth(), health + Math.max(0, since / 600)));
		}
		folk.setBrain(new FolkBrain(folk, settlement, resident, town));
		this.embodied.put(resident.id, folk);
		settlement.setResidentEmbodied(resident.id, true);
	}

	/** Folds an entity back into its record: state written to {@code extra("npc")}, the sim told, the entity gone. */
	public void fold(ServerLevel level, Folk folk, boolean discard) {
		if (folk.isFolded()) {
			return;
		}
		folk.markFolded();
		UUID id = folk.residentId();
		FolkBrain brain = folk.brain();
		if (brain != null) {
			brain.onFold(level);
			NpcData data = brain.data;
			data.setLastPos(folk.isSleeping() ? brain.places(level).home : folk.blockPosition(), level.getGameTime());
			data.setHealth(folk.getHealth() < folk.getMaxHealth() ? folk.getHealth() : -1);
			brain.settlement.setResidentEmbodied(brain.resident.id, false);
			brain.settlement.markDirty();
		}
		if (id != null) {
			this.embodied.remove(id, folk);
			this.lastDistance.remove(id);
		}
		folk.setBrain(null);
		if (discard) {
			if (folk.isSleeping()) {
				folk.stopSleeping();
			}
			folk.discard();
		}
	}

	public void foldAll(MinecraftServer server) {
		for (Folk f : new ArrayList<>(this.embodied.values())) {
			if (f.level() instanceof ServerLevel level) {
				fold(level, f, true);
			}
		}
		this.queue.clear();
		this.queued.clear();
	}

	private void foldAbandoned(ServerLevel level, List<Settlement> live) {
		Set<Long> ids = new HashSet<>();
		for (Settlement s : live) {
			ids.add(s.id());
		}
		for (Folk f : new ArrayList<>(this.embodied.values())) {
			if (f.level() == level && !ids.contains(f.settlementId())) {
				fold(level, f, true);
			}
		}
	}

	// ---------------------------------------------------------------- what happens to entities

	/** The entity was removed by the game (its chunk unloaded, a command): it becomes a record again. */
	public void onRemoved(ServerLevel level, Folk folk) {
		fold(level, folk, false);
	}

	/** A resident died: they leave their settlement for good, and nearby players hear of it. */
	public void onDeath(ServerLevel level, Folk folk, DamageSource source) {
		if (folk.isFolded()) {
			return;
		}
		FolkBrain brain = folk.brain();
		folk.markFolded();
		UUID id = folk.residentId();
		if (id != null) {
			this.embodied.remove(id, folk);
			this.lastDistance.remove(id);
		}
		if (brain == null) {
			return;
		}
		brain.onFold(level);
		Settlement s = brain.settlement;
		Resident r = brain.resident;
		s.setResidentEmbodied(r.id, false);
		s.removeResident(r.id);
		s.markDirty();
		if (NpcModule.config().threats().deathMessages()) {
			Component job = Component.translatable(r.age == AgeStage.CHILD ? "hearth.npc.child" : "hearth.npc.job." + r.job.getSerializedName());
			Component msg = Component.translatable("hearth.npc.death", r.fullName(), job, s.name(), source.getLocalizedDeathMessage(folk))
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(folk) < 64 * 64) {
					p.sendSystemMessage(msg);
				}
			}
		}
		folk.setBrain(null);
	}

	/** The sim removed a resident (they died of age, moved away) while their entity walks about: it goes too. */
	public void onResidentRemoved(UUID resident) {
		Folk f = this.embodied.remove(resident);
		if (f != null && !f.isFolded()) {
			f.markFolded();
			f.setBrain(null);
			f.discard();
		}
	}

	/** The sim says a settlement stopped being embodied: everyone back to records. */
	public void onSettlementAbstract(ServerLevel level, long settlementId) {
		for (Folk f : new ArrayList<>(this.embodied.values())) {
			if (f.level() == level && f.settlementId() == settlementId) {
				fold(level, f, true);
			}
		}
	}

	/**
	 * An entity nobody manages: from a spawn egg or {@code /summon}, or left over from before a restart. If its
	 * resident still exists it is rebound; otherwise it is made a resident of the nearest settlement (spawn eggs are
	 * an admin's tool), or of a wanderers' camp when there is none.
	 */
	public void adoptStray(ServerLevel level, Folk folk) {
		UUID id = folk.residentId();
		if (id != null) {
			for (Settlement s : SettlementSources.all(level)) {
				Resident r = s.resident(id);
				if (r != null) {
					if (this.embodied.containsKey(id) && this.embodied.get(id) != folk) {
						folk.markFolded();
						folk.discard();
						return;
					}
					bind(level, folk, s, r, town(s));
					return;
				}
			}
		}
		Settlement home = StrayHomes.find(level, folk.blockPosition());
		if (home == null) {
			folk.markFolded();
			folk.discard();
			return;
		}
		Resident r = HearthServices.residents().create(home, AgeStage.ADULT, level.getRandom(), null, null);
		NpcData.of(r).setAnchor(folk.blockPosition());
		home.addResident(r);
		home.markDirty();
		bind(level, folk, home, r, town(home));
	}

	// ---------------------------------------------------------------- markers and housekeeping

	private void refreshMarkers(MinecraftServer server) {
		int every = NpcModule.config().talk().markerRefresh();
		QuestService quests = HearthServices.quests();
		if (this.embodied.isEmpty()) {
			return;
		}
		// A slice of the folk each tick: each is refreshed once every marker_refresh ticks.
		int phase = (int) (this.ticks % every);
		int i = 0;
		for (Folk f : this.embodied.values()) {
			if (i++ % every != phase) {
				continue;
			}
			FolkBrain brain = f.brain();
			if (quests == null || brain == null || !(f.level() instanceof ServerLevel level)) {
				f.setMarker(QuestService.Marker.NONE);
				continue;
			}
			ServerPlayer near = (ServerPlayer) level.getNearestPlayer(f, 32.0);
			f.setMarker(near == null || near.isSpectator() ? QuestService.Marker.NONE : quests.marker(near, brain.settlement, brain.resident));
		}
	}

	private void housekeeping(MinecraftServer server) {
		// Scaffolding left by a crash or by builders folded where their chunk was not loaded.
		Set<UUID> working = new HashSet<>(this.embodied.keySet());
		for (ServerLevel level : server.getAllLevels()) {
			for (Settlement s : SettlementSources.embodied(level)) {
				ScaffoldLedger ledger = s.data(ScaffoldLedger.KEY);
				if (!ledger.isEmpty() && ledger.clearOrphans(level, working) > 0) {
					s.markDirty();
				}
			}
		}
		// Forget towns and cached places nobody has asked about in a while.
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, CachedPlaces>> it = this.placeCache.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().at > 20 * 60 * 10) {
				it.remove();
			}
		}
		if (this.towns.size() > 256) {
			Set<Long> live = new HashSet<>();
			for (Folk f : this.embodied.values()) {
				live.add(f.settlementId());
			}
			this.towns.keySet().removeIf(k -> !live.contains(k));
		}
		Hearth.LOG.debug("hearthfolk: {} embodied, {} queued, {} towns", this.embodied.size(), this.queue.size(), this.towns.size());
	}

	private RoutinePlacement.Placement placement(ServerLevel level, Settlement s, Resident r, TownState town) {
		long now = level.getServer().getTickCount();
		CachedPlaces c = this.placeCache.get(r.id);
		if (c == null || now - c.at > (c.places.complete ? 20 * 60 * 5 : 200)) {
			c = new CachedPlaces(ResidentPlaces.resolve(level, s, r, town.places), now);
			this.placeCache.put(r.id, c);
		}
		return RoutinePlacement.where(level, s, r, c.places, town);
	}

	public void onServerStopped() {
		this.towns.clear();
		this.embodied.clear();
		this.lastDistance.clear();
		this.placeCache.clear();
		this.queue.clear();
		this.queued.clear();
		this.cursors.clear();
	}
}
