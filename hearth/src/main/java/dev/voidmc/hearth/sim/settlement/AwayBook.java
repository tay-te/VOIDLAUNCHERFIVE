package dev.voidmc.hearth.sim.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Residents away from their town (docs/LIVING_WORLD_2.md §0.2): on the road, with a companion, at a remote work site,
 * each until a tick on the sim clock. Saved with the settlement ({@code hearth:sim/away}); an entry past its tick no
 * longer counts and is dropped on the next write.
 */
public final class AwayBook {
	public record Away(long until, String why) {
		static final Codec<Away> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("until").forGetter(Away::until),
			Codec.STRING.optionalFieldOf("why", "").forGetter(Away::why)
		).apply(i, Away::new));
	}

	static final Codec<AwayBook> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Away.CODEC).xmap(AwayBook::new, b -> b.entries);

	public static final HearthDataKey<AwayBook> KEY = HearthDataKey.register(Hearth.id("sim/away"), HearthDataKey.Scope.SETTLEMENT, CODEC,
		AwayBook::new);

	private final Map<UUID, Away> entries;

	AwayBook() {
		this(Map.of());
	}

	private AwayBook(Map<UUID, Away> entries) {
		this.entries = new HashMap<>(entries);
	}

	void put(UUID id, long until, String why) {
		this.entries.put(id, new Away(until, why));
	}

	boolean remove(UUID id) {
		return this.entries.remove(id) != null;
	}

	boolean away(UUID id, long now) {
		Away a = this.entries.get(id);
		return a != null && a.until > now;
	}

	/** Drops the entries past their tick; returns whether any were. */
	boolean expire(long now) {
		return this.entries.values().removeIf(a -> a.until <= now);
	}

	public int size() {
		return this.entries.size();
	}
}
