package dev.voidmc.hearth.repair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's repair queue (its {@code hearth:repair/queue} data): every damaged blueprint block, one entry per
 * position, plus the abstract labour clock and the scaffolding builders have up for repairs. Pure data and selection
 * logic; the world is touched elsewhere. Server thread only.
 */
public final class RepairQueue {
	public static final Codec<RepairQueue> CODEC = RecordCodecBuilder.create(i -> i.group(
		Damage.CODEC.listOf().optionalFieldOf("damage", List.of()).forGetter(q -> List.copyOf(q.entries.values())),
		Codec.LONG.optionalFieldOf("last_accrual", 0L).forGetter(q -> q.lastAccrual),
		Codec.DOUBLE.optionalFieldOf("carry", 0.0).forGetter(q -> q.carry),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG.listOf()).optionalFieldOf("scaffolds", Map.of()).forGetter(RepairQueue::scaffoldsForCodec)
	).apply(i, RepairQueue::new));

	private final Long2ObjectLinkedOpenHashMap<Damage> entries = new Long2ObjectLinkedOpenHashMap<>();
	/** Scaffolding up for repairs, by builder: taken down when they finish, are folded away, or the server restarts. */
	private final Map<UUID, LongArrayList> scaffolds = new LinkedHashMap<>();
	/** Sim tick (the overworld clock) up to which abstract repair labour has been accrued. */
	public long lastAccrual;
	/** Labour accrued and not yet spent, in blocks. */
	public double carry;
	private @Nullable Long2ObjectOpenHashMap<LongArrayList> byChunk;
	private @Nullable Int2IntOpenHashMap perPlot;

	public RepairQueue() {
	}

	private RepairQueue(List<Damage> damage, long lastAccrual, double carry, Map<UUID, List<Long>> scaffolds) {
		for (Damage d : damage) {
			this.entries.put(d.pos, d);
		}
		this.lastAccrual = lastAccrual;
		this.carry = carry;
		scaffolds.forEach((k, v) -> this.scaffolds.put(k, new LongArrayList(v)));
	}

	private Map<UUID, List<Long>> scaffoldsForCodec() {
		Map<UUID, List<Long>> out = new LinkedHashMap<>();
		this.scaffolds.forEach((k, v) -> out.put(k, List.copyOf(v)));
		return out;
	}

	public int size() {
		return this.entries.size();
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public @Nullable Damage get(long pos) {
		return this.entries.get(pos);
	}

	public Collection<Damage> all() {
		return Collections.unmodifiableCollection(this.entries.values());
	}

	/**
	 * Adds a damaged block unless the position is already queued or the queue is full ({@code max}). Returns whether it
	 * was added.
	 */
	public boolean add(Damage d, int max) {
		if (this.entries.containsKey(d.pos) || this.entries.size() >= max) {
			return false;
		}
		this.entries.put(d.pos, d);
		if (this.byChunk != null) {
			this.byChunk.computeIfAbsent(chunkKey(d.pos), k -> new LongArrayList()).add(d.pos);
		}
		if (this.perPlot != null) {
			this.perPlot.addTo(d.plot, 1);
		}
		return true;
	}

	public @Nullable Damage remove(long pos) {
		Damage d = this.entries.remove(pos);
		if (d != null && this.perPlot != null) {
			if (this.perPlot.addTo(d.plot, -1) <= 1) {
				this.perPlot.remove(d.plot);
			}
		}
		if (d != null && this.byChunk != null) {
			LongArrayList list = this.byChunk.get(chunkKey(pos));
			if (list != null) {
				list.rem(pos);
				if (list.isEmpty()) {
					this.byChunk.remove(chunkKey(pos));
				}
			}
		}
		return d;
	}

	/** How many entries a plot has. */
	public int countOf(int plot) {
		if (this.perPlot == null) {
			Int2IntOpenHashMap m = new Int2IntOpenHashMap();
			for (Damage d : this.entries.values()) {
				m.addTo(d.plot, 1);
			}
			this.perPlot = m;
		}
		return this.perPlot.get(plot);
	}

	/** Drops every entry of a plot (it was abandoned, rebuilt or demolished). Returns how many. */
	public int removePlot(int plot) {
		List<Long> gone = new ArrayList<>();
		for (Damage d : this.entries.values()) {
			if (d.plot == plot) {
				gone.add(d.pos);
			}
		}
		gone.forEach(this::remove);
		return gone.size();
	}

	/** The entries lying in a chunk, in the order they were damaged. */
	public List<Damage> inChunk(int chunkX, int chunkZ) {
		if (this.byChunk == null) {
			Long2ObjectOpenHashMap<LongArrayList> m = new Long2ObjectOpenHashMap<>();
			for (Damage d : this.entries.values()) {
				m.computeIfAbsent(chunkKey(d.pos), k -> new LongArrayList()).add(d.pos);
			}
			this.byChunk = m;
		}
		LongArrayList list = this.byChunk.get(ChunkKeys.key(chunkX, chunkZ));
		if (list == null) {
			return List.of();
		}
		List<Damage> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			Damage d = this.entries.get(list.getLong(i));
			if (d != null) {
				out.add(d);
			}
		}
		return out;
	}

	/** Chunks (packed) holding entries that abstract labour has mended and the world has not seen yet. */
	public List<Long> mendedChunks() {
		List<Long> out = new ArrayList<>();
		for (Damage d : this.entries.values()) {
			if (d.has(Damage.MENDED)) {
				long k = chunkKey(d.pos);
				if (!out.contains(k)) {
					out.add(k);
				}
			}
		}
		return out;
	}

	static long chunkKey(long pos) {
		return ChunkKeys.key(SectionPos.blockToSectionCoord(BlockPos.getX(pos)), SectionPos.blockToSectionCoord(BlockPos.getZ(pos)));
	}

	/**
	 * Whether an entry is ready for a builder: not mended already, not held for its owner's coin, not deferred, and not
	 * claimed by someone else (or their claim ran out).
	 */
	public static boolean open(Damage d, long now, int claimTicks) {
		return !d.has(Damage.MENDED) && !d.awaitingPayment() && !d.deferred(now) && !d.claimed(now, claimTicks);
	}

	/** How many entries a builder could take now. */
	public int openCount(long now, int claimTicks) {
		int n = 0;
		for (Damage d : this.entries.values()) {
			if (open(d, now, claimTicks)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Reserves for {@code builder} the open entry best for them: nearest to {@code near} (within {@code maxDistance}),
	 * earlier stages first (walls before the lamps on them), doorways first, among those {@code usable} accepts (goods in
	 * store, a blueprint to say what goes there). A builder holds one claim at a time: an earlier claim of theirs is
	 * released. Returns null if nothing is open.
	 */
	public @Nullable Damage claim(UUID builder, BlockPos near, double maxDistance, long now, int claimTicks, Predicate<Damage> usable) {
		release(builder);
		// a few candidates by score, then the first the caller can use (it checks goods, which is dearer)
		List<Damage> ranked = new ArrayList<>();
		List<Double> scores = new ArrayList<>();
		double max2 = maxDistance * maxDistance;
		for (Damage d : this.entries.values()) {
			if (!open(d, now, claimTicks)) {
				continue;
			}
			double d2 = distSqr(near, d.pos);
			if (d2 > max2) {
				continue;
			}
			double score = Math.sqrt(d2) + 6.0 * d.stage - (d.has(Damage.LITTER) ? 8.0 : 0.0);
			int at = 0;
			while (at < ranked.size() && scores.get(at) <= score) {
				at++;
			}
			if (at < 8) {
				ranked.add(at, d);
				scores.add(at, score);
				if (ranked.size() > 8) {
					ranked.removeLast();
					scores.removeLast();
				}
			}
		}
		for (Damage d : ranked) {
			if (usable.test(d)) {
				d.claimedBy = builder;
				d.claimedAt = now;
				return d;
			}
		}
		return null;
	}

	/** Gives back whatever {@code builder} holds. */
	public void release(UUID builder) {
		for (Damage d : this.entries.values()) {
			if (builder.equals(d.claimedBy)) {
				d.claimedBy = null;
			}
		}
	}

	/** The entry {@code builder} holds, if their claim is still good. */
	public @Nullable Damage claimOf(UUID builder, long now, int claimTicks) {
		for (Damage d : this.entries.values()) {
			if (builder.equals(d.claimedBy) && d.claimed(now, claimTicks)) {
				return d;
			}
		}
		return null;
	}

	private static double distSqr(BlockPos a, long b) {
		double dx = a.getX() - BlockPos.getX(b);
		double dy = a.getY() - BlockPos.getY(b);
		double dz = a.getZ() - BlockPos.getZ(b);
		return dx * dx + dy * dy + dz * dz;
	}

	// ---- scaffolding

	public void addScaffold(UUID builder, BlockPos pos) {
		this.scaffolds.computeIfAbsent(builder, k -> new LongArrayList()).add(pos.asLong());
	}

	public void removeScaffold(UUID builder, BlockPos pos) {
		LongArrayList list = this.scaffolds.get(builder);
		if (list != null) {
			list.rem(pos.asLong());
			if (list.isEmpty()) {
				this.scaffolds.remove(builder);
			}
		}
	}

	/** Every builder's scaffolding, newest last. */
	public Map<UUID, LongArrayList> scaffolds() {
		return this.scaffolds;
	}

	public int scaffoldCount() {
		int n = 0;
		for (LongArrayList l : this.scaffolds.values()) {
			n += l.size();
		}
		return n;
	}
}
