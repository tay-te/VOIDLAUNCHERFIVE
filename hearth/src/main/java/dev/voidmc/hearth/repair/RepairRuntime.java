package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The repair module's state for one running server: the position index, the damage tracker and its batches, vandalism
 * sprees, fires, abstract repair and the chunk applier. Created when the server starts and dropped when it stops, so
 * nothing outlives a world. Server thread only.
 */
public final class RepairRuntime {
	private static volatile @Nullable RepairRuntime current;

	public final MinecraftServer server;
	public final PlotLocator locator;
	public final DamageTracker tracker;
	public final Vandalism vandalism;
	public final FireWatch fires;
	public final AbstractRepairs abstracts;
	/** Settlements with anything in their repair queue (the only ones abstract repair looks at). */
	final LongOpenHashSet withDamage = new LongOpenHashSet();
	/** Per level: the tick each loaded chunk loaded at (chunks loaded before the server started are not listed). */
	private final Map<ResourceKey<Level>, Long2LongOpenHashMap> loadedAt = new HashMap<>();

	private RepairRuntime(MinecraftServer server) {
		this.server = server;
		RepairConfig c = RepairModule.config();
		this.locator = new PlotLocator(c.tracking().indexMaxEntries(), c.tracking().gridTtlTicks());
		this.tracker = new DamageTracker(this);
		this.vandalism = new Vandalism(this);
		this.fires = new FireWatch(this);
		this.abstracts = new AbstractRepairs(this);
	}

	public static @Nullable RepairRuntime current() {
		return current;
	}

	static RepairRuntime start(MinecraftServer server) {
		RepairRuntime r = new RepairRuntime(server);
		current = r;
		return r;
	}

	static void stop() {
		current = null;
	}

	/** Game ticks since the server started: the clock for claims, deferrals and sprees (not saved). */
	public long now() {
		return this.server.getTickCount();
	}

	/** The sim day in a level (the sim's calendar where it runs). */
	public static long day(ServerLevel level) {
		HearthWorld w = HearthWorld.of(level);
		return w != null ? w.day() : level.getOverworldClockTime() / 24000L;
	}

	/** A settlement and its world, found by id in whichever level holds it. */
	public record Located(HearthWorld world, Settlement settlement) {
		public ServerLevel level() {
			return this.world.level();
		}
	}

	public @Nullable Located find(long settlementId) {
		for (ServerLevel level : this.server.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w != null) {
				Settlement s = w.settlement(settlementId);
				if (s != null) {
					return new Located(w, s);
				}
			}
		}
		return null;
	}

	public @Nullable HearthWorld worldOf(Settlement settlement) {
		ServerLevel level = this.server.getLevel(settlement.dimension());
		return level == null ? null : HearthWorld.of(level);
	}

	/** The queue of a settlement, noting whether it holds anything (for abstract repair's round). */
	public RepairQueue queue(Settlement settlement) {
		RepairQueue q = settlement.data(RepairModule.QUEUE);
		if (!q.isEmpty()) {
			this.withDamage.add(settlement.id());
		}
		return q;
	}

	/** Called once at server start: finds every settlement that has damage waiting, and mended blocks to write. */
	void scanAll() {
		for (ServerLevel level : this.server.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w == null) {
				continue;
			}
			for (Settlement s : w.settlements()) {
				RepairQueue q = s.data(RepairModule.QUEUE);
				if (!q.isEmpty()) {
					this.withDamage.add(s.id());
					this.abstracts.noteQueue(level, s, q);
				}
				// scaffolding left by a crash or a builder folded away mid-repair
				if (q.scaffoldCount() > 0) {
					Scaffolds.clearAll(level, s, q);
				}
			}
		}
	}

	void chunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		this.loadedAt.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap()).put(ChunkKeys.key(chunkX, chunkZ), now());
		this.abstracts.chunkLoaded(level, chunkX, chunkZ);
	}

	void chunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		Long2LongOpenHashMap m = this.loadedAt.get(level.dimension());
		if (m != null) {
			m.remove(ChunkKeys.key(chunkX, chunkZ));
		}
	}

	/** The tick a chunk loaded at; a long time ago for chunks loaded before the server started. */
	public long chunkLoadedAt(ServerLevel level, int chunkX, int chunkZ) {
		Long2LongOpenHashMap m = this.loadedAt.get(level.dimension());
		return m == null ? Long.MIN_VALUE / 4 : m.getOrDefault(ChunkKeys.key(chunkX, chunkZ), Long.MIN_VALUE / 4);
	}

	void tick() {
		long now = now();
		this.tracker.flush();
		this.vandalism.tick(now);
		this.fires.tick(now);
		this.abstracts.tick(now);
	}
}
