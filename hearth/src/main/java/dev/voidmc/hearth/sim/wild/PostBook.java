package dev.voidmc.hearth.sim.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;

/**
 * A level's posts as founded (world data {@code hearth:sim/wild}): which sites became which settlement, and which were
 * passed over (a town grew there first, the history failed), so each site is founded once.
 */
public final class PostBook {
	record Founded(long site, long settlement) {
		static final Codec<Founded> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("site").forGetter(Founded::site),
			Codec.LONG.fieldOf("settlement").forGetter(Founded::settlement)
		).apply(i, Founded::new));
	}

	public static final Codec<PostBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		Founded.CODEC.listOf().optionalFieldOf("founded", List.of()).forGetter(b -> {
			List<Founded> out = new ArrayList<>();
			b.founded.long2LongEntrySet().forEach(e -> out.add(new Founded(e.getLongKey(), e.getLongValue())));
			out.sort(java.util.Comparator.comparingLong(Founded::site));
			return out;
		}),
		Codec.LONG.listOf().optionalFieldOf("skipped", List.of()).forGetter(b -> {
			List<Long> out = new ArrayList<>(b.skipped);
			out.sort(null);
			return out;
		})
	).apply(i, (founded, skipped) -> {
		PostBook b = new PostBook();
		for (Founded f : founded) {
			b.founded.put(f.site(), f.settlement());
		}
		b.skipped.addAll(skipped);
		return b;
	}));

	public static final HearthDataKey<PostBook> KEY = HearthDataKey.register(Hearth.id("sim/wild"), HearthDataKey.Scope.WORLD, CODEC, PostBook::new);

	private final Long2LongOpenHashMap founded = new Long2LongOpenHashMap();
	private final LongOpenHashSet skipped = new LongOpenHashSet();

	public boolean known(long site) {
		return this.founded.containsKey(site) || this.skipped.contains(site);
	}

	/** The settlement founded on a site, or 0. */
	public long settlement(long site) {
		return this.founded.getOrDefault(site, 0L);
	}

	void found(long site, long settlement) {
		this.founded.put(site, settlement);
		this.skipped.remove(site);
	}

	void skip(long site) {
		if (!this.founded.containsKey(site)) {
			this.skipped.add(site);
		}
	}

	/** Lets a passed-over site be founded again ({@code /hearth wild found}). */
	void forget(long site) {
		this.skipped.remove(site);
	}

	public int foundedCount() {
		return this.founded.size();
	}

	public int skippedCount() {
		return this.skipped.size();
	}
}
