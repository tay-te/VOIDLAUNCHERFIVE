package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Fires as events (docs/LIVING_WORLD.md §2): a fire that takes hold in a settlement's buildings becomes an episode — the
 * flames it knows of, the blocks lost, the players who fought it, the folk in the bucket brigade, the water they carry
 * from — from the first flame until no flame has been seen for {@code quiet_ticks}. Then it is out: players who helped
 * earn standing ({@code repair:fire_brigade}), {@link RepairEvents#FIRE_OUT} fires, and the builders rebuild from the
 * repair queue the fire filled.
 *
 * <p>Fed by the fire block's own tick (a mixin) and by fire damage, so it costs nothing where nothing burns. Fires in
 * hearths (on netherrack and other everlasting fuel) and fires a blueprint puts there are not fires in town.
 */
public final class FireWatch {
	/** One fire in one settlement. */
	public static final class Episode {
		public final long settlementId;
		final ServerLevel level;
		public final BlockPos origin;
		final long started;
		long lastSeen;
		final LongOpenHashSet flames = new LongOpenHashSet();
		final Object2IntLinkedOpenHashMap<UUID> helpers = new Object2IntLinkedOpenHashMap<>();
		/** Folk carrying water now. */
		final Set<UUID> brigade = new HashSet<>();
		int burned;
		@Nullable List<BlockPos> water;
		long nextPrune;

		Episode(long settlementId, ServerLevel level, BlockPos origin, long now) {
			this.settlementId = settlementId;
			this.level = level;
			this.origin = origin.immutable();
			this.started = now;
			this.lastSeen = now;
		}

		public ServerLevel level() {
			return this.level;
		}

		public int burned() {
			return this.burned;
		}

		/** The flames it knows of (prune keeps it true to the world once a second). */
		public int flames() {
			return this.flames.size();
		}

		/** The known flame nearest to {@code from}, or null. */
		public @Nullable BlockPos nearestFlame(BlockPos from) {
			long best = 0;
			double bestD = Double.MAX_VALUE;
			LongIterator it = this.flames.iterator();
			while (it.hasNext()) {
				long p = it.nextLong();
				double dx = BlockPos.getX(p) - from.getX();
				double dy = BlockPos.getY(p) - from.getY();
				double dz = BlockPos.getZ(p) - from.getZ();
				double d = dx * dx + dy * dy + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = p;
				}
			}
			return bestD == Double.MAX_VALUE ? null : BlockPos.of(best);
		}

		/** Water to fill buckets at, nearest first to the fire: found once per fire (a bounded scan round its origin). */
		public List<BlockPos> water(int radius) {
			if (this.water == null) {
				this.water = findWater(this.level, this.origin, radius);
			}
			return this.water;
		}

		/** Puts out the flames within {@code radius} of {@code at}; returns how many. */
		public int douse(BlockPos at, int radius) {
			int n = 0;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -1; dy <= radius; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
						if (this.level.isLoaded(p) && this.level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
							this.level.removeBlock(p, false);
							this.flames.remove(p.asLong());
							n++;
						}
					}
				}
			}
			return n;
		}

		public boolean inBrigade(UUID folk) {
			return this.brigade.contains(folk);
		}

		public int brigadeSize() {
			return this.brigade.size();
		}

		public void join(UUID folk) {
			this.brigade.add(folk);
		}

		public void leave(UUID folk) {
			this.brigade.remove(folk);
		}
	}

	private static final int MAX_FLAMES = 2048;

	private final RepairRuntime runtime;
	private final Map<Long, Episode> episodes = new LinkedHashMap<>();
	long started;
	long ended;

	FireWatch(RepairRuntime runtime) {
		this.runtime = runtime;
	}

	public Collection<Episode> episodes() {
		return this.episodes.values();
	}

	public @Nullable Episode episode(long settlementId) {
		return this.episodes.get(settlementId);
	}

	/** A fire block ticked. Starts or feeds the settlement's episode when the flame is in its buildings. */
	public void fireTick(ServerLevel level, BlockPos pos) {
		if (!RepairModule.config().fire().enabled() || !RepairModule.config().tracking().enabled()) {
			return;
		}
		HearthWorld world = HearthWorld.of(level);
		if (world == null) {
			return;
		}
		Settlement s = world.settlementAt(pos);
		if (s == null) {
			return;
		}
		Episode e = this.episodes.get(s.id());
		long now = this.runtime.now();
		if (e != null) {
			if (e.flames.size() < MAX_FLAMES) {
				e.flames.add(pos.asLong());
			}
			e.lastSeen = now;
			return;
		}
		if (level.getBlockState(pos.below()).is(level.dimensionType().infiniburn())) {
			// a hearth fire on netherrack
			return;
		}
		PlotLocator.Source source = PlotLocator.services(world);
		PlotLocator.Hit here = this.runtime.locator.locate(s, source, pos, now);
		if (here != null && here.expected().getBlock() instanceof BaseFireBlock) {
			return;
		}
		if (here == null && this.runtime.locator.locate(s, source, pos.below(), now) == null) {
			// a grass fire outside the buildings is not yet the town's fire
			return;
		}
		start(world, s, level, pos, now);
	}

	/** Fire burned a building's block. */
	void burned(ServerLevel level, Settlement s, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		if (world == null || !RepairModule.config().fire().enabled()) {
			return;
		}
		Episode e = this.episodes.get(s.id());
		if (e == null) {
			e = start(world, s, level, pos, this.runtime.now());
		}
		e.burned++;
	}

	private Episode start(HearthWorld world, Settlement s, ServerLevel level, BlockPos pos, long now) {
		Episode e = new Episode(s.id(), level, pos, now);
		e.flames.add(pos.asLong());
		this.episodes.put(s.id(), e);
		this.started++;
		RepairModule.LOG.info("Fire in {} at {}", s.name(), pos.toShortString());
		try {
			RepairEvents.FIRE_STARTED.invoker().onFireStarted(world, s, pos.immutable());
		} catch (RuntimeException ex) {
			RepairModule.LOG.warn("a FIRE_STARTED listener failed", ex);
		}
		return e;
	}

	/** A player put out a flame (by hand) or poured water near one. */
	void playerFought(ServerLevel level, ServerPlayer player, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		Settlement s = world == null ? null : world.settlementAt(pos);
		Episode e = s == null ? null : this.episodes.get(s.id());
		if (e != null) {
			e.helpers.addTo(player.getUUID(), 1);
			e.flames.remove(pos.asLong());
		}
	}

	/** A player emptied a water bucket: helping, if a fire of the town burns within a few blocks. */
	public void waterPoured(ServerLevel level, ServerPlayer player, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		Settlement s = world == null ? null : world.settlementAt(pos);
		Episode e = s == null ? null : this.episodes.get(s.id());
		if (e == null) {
			return;
		}
		BlockPos near = e.nearestFlame(pos);
		if (near != null && near.distSqr(pos) <= 16) {
			e.helpers.addTo(player.getUUID(), 2);
		}
	}

	/** A player put back blocks while the town still had a fire to recover from (counted toward the fire deed). */
	void playerRebuilt(Settlement s, ServerPlayer player) {
		Episode e = this.episodes.get(s.id());
		if (e != null) {
			e.helpers.addTo(player.getUUID(), 1);
		}
	}

	void tick(long now) {
		if (this.episodes.isEmpty()) {
			return;
		}
		int quiet = RepairModule.config().fire().quietTicks();
		Iterator<Episode> it = this.episodes.values().iterator();
		List<Episode> out = new ArrayList<>();
		while (it.hasNext()) {
			Episode e = it.next();
			if (now >= e.nextPrune) {
				e.nextPrune = now + 20;
				prune(e);
			}
			if (e.flames.isEmpty() && now - e.lastSeen >= quiet) {
				it.remove();
				out.add(e);
			}
		}
		for (Episode e : out) {
			end(e);
		}
	}

	/** Forgets flames that are gone (burned out, doused, rained on). */
	private static void prune(Episode e) {
		LongArrayList gone = new LongArrayList();
		LongIterator it = e.flames.iterator();
		while (it.hasNext()) {
			long p = it.nextLong();
			BlockPos pos = BlockPos.of(p);
			if (!e.level.isLoaded(pos) || !(e.level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) {
				gone.add(p);
			}
		}
		for (int i = 0; i < gone.size(); i++) {
			e.flames.remove(gone.getLong(i));
		}
	}

	private void end(Episode e) {
		this.ended++;
		RepairRuntime.Located at = this.runtime.find(e.settlementId);
		if (at == null) {
			return;
		}
		RepairModule.LOG.info("The fire in {} is out: {} blocks lost, {} players helped", at.settlement().name(), e.burned, e.helpers.size());
		StandingService standing = HearthServices.standing();
		int max = RepairModule.config().fire().brigadeDeedMax();
		if (standing != null) {
			for (Object2IntLinkedOpenHashMap.Entry<UUID> h : e.helpers.object2IntEntrySet()) {
				int weight = Math.min(max, 2 + h.getIntValue() / 2);
				standing.deed(new StandingService.Deed(h.getKey(), e.settlementId, "repair:fire_brigade", weight, null, e.origin, true));
			}
		}
		try {
			RepairEvents.FIRE_OUT.invoker().onFireOut(at.world(), at.settlement(), e.burned, Set.copyOf(e.helpers.keySet()));
		} catch (RuntimeException ex) {
			RepairModule.LOG.warn("a FIRE_OUT listener failed", ex);
		}
	}

	/** Water source blocks round a point, nearest first (at most 32). */
	static List<BlockPos> findWater(ServerLevel level, BlockPos origin, int radius) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= radius && out.size() < 32; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = -6; dy <= 4; dy++) {
						p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						if (!level.isLoaded(p)) {
							continue;
						}
						FluidState f = level.getFluidState(p);
						if (f.is(FluidTags.WATER) && f.isSource()) {
							out.add(p.immutable());
							break;
						}
					}
				}
			}
		}
		return out;
	}

	public String stats() {
		return this.started + " fires, " + this.ended + " put out, " + this.episodes.size() + " burning";
	}
}
