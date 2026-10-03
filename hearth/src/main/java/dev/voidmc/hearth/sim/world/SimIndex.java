package dev.voidmc.hearth.sim.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.sim.settlement.DataMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The small per-level index the game saves with the level's other data ({@code data/hearth/sim/index.dat}): the next
 * live settlement id, every settlement's id, name, centre and bounds (so lists and maps need not open settlement files),
 * the atlas regions already adopted, and the world-scoped {@link HearthDataKey} data. Settlements themselves are one file
 * each (see {@link SettlementStore}); the directory of those files is the truth, this index is a summary of it.
 */
public final class SimIndex extends SavedData {
	/** One settlement, summarised. */
	public record Entry(long id, String name, BlockPos center, BoundingBox bounds) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Entry::id),
			Codec.STRING.fieldOf("name").forGetter(Entry::name),
			BlockPos.CODEC.fieldOf("center").forGetter(Entry::center),
			BoundingBox.CODEC.fieldOf("bounds").forGetter(Entry::bounds)
		).apply(i, Entry::new));
	}

	public static final Codec<SimIndex> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(x -> x.nextId),
		Entry.CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(x -> List.copyOf(x.entries.values())),
		Codec.LONG_STREAM.optionalFieldOf("adopted", LongStream.empty()).forGetter(x -> LongStream.of(x.adopted.toLongArray())),
		CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(x -> x.data.save())
	).apply(i, (next, entries, adopted, data) -> {
		SimIndex x = new SimIndex();
		x.nextId = next;
		for (Entry e : entries) {
			x.entries.put(e.id, e);
		}
		adopted.forEach(x.adopted::add);
		x.data.load(data);
		return x;
	}));

	public static final SavedDataType<SimIndex> TYPE = new SavedDataType<>(Hearth.id("sim/index"), SimIndex::new, CODEC, null);

	private long nextId = 1;
	private final Map<Long, Entry> entries = new LinkedHashMap<>();
	private final LongSet adopted = new LongOpenHashSet();
	private final DataMap data = new DataMap(HearthDataKey.Scope.WORLD);

	public SimIndex() {
	}

	public long allocateId() {
		setDirty();
		return this.nextId++;
	}

	public void put(Entry e) {
		Entry old = this.entries.put(e.id, e);
		if (!e.equals(old)) {
			setDirty();
		}
	}

	public void remove(long id) {
		if (this.entries.remove(id) != null) {
			setDirty();
		}
	}

	public List<Entry> entries() {
		return new ArrayList<>(this.entries.values());
	}

	public boolean isAdopted(long regionKey) {
		return this.adopted.contains(regionKey);
	}

	public void markAdopted(long regionKey) {
		if (this.adopted.add(regionKey)) {
			setDirty();
		}
	}

	public int adoptedCount() {
		return this.adopted.size();
	}

	public DataMap data() {
		return this.data;
	}
}
