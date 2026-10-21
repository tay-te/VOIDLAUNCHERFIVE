package dev.voidmc.hearth.social.burgher;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * A town's sworn burghers and its council (settlement data {@code hearth:social/burghers}; docs/LIVING_WORLD_2.md §8.2):
 * who swore the burgher's oath and when, since when each player has held property here (the oath asks thirty days), and
 * the council's sitting — the public works on the table, the burghers' votes, the last choice. Mutable.
 */
public final class BurgherBook {
	public static final Codec<BurgherBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("sworn", Map.of()).forGetter(b -> b.sworn),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owners", Map.of()).forGetter(b -> b.owners),
		Codec.LONG.optionalFieldOf("sitting", Long.MIN_VALUE).forGetter(b -> b.sitting),
		Codec.LONG.optionalFieldOf("last_sitting", Long.MIN_VALUE).forGetter(b -> b.lastSitting),
		Codec.INT.listOf().optionalFieldOf("table", List.of()).forGetter(b -> b.table),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("votes", Map.of()).forGetter(b -> b.votes),
		Codec.INT.optionalFieldOf("chosen", -1).forGetter(b -> b.chosen)
	).apply(i, (sworn, owners, sitting, last, table, votes, chosen) -> {
		BurgherBook b = new BurgherBook();
		b.sworn.putAll(sworn);
		b.owners.putAll(owners);
		b.sitting = sitting;
		b.lastSitting = last;
		b.table.addAll(table);
		b.votes.putAll(votes);
		b.chosen = chosen;
		return b;
	}));

	/** Sworn burghers, by the day they swore. */
	public final Map<UUID, Long> sworn = new LinkedHashMap<>();
	/** Players holding property here, by the first day they were seen holding it. */
	public final Map<UUID, Long> owners = new LinkedHashMap<>();
	/** The day the council sits (votes are taken), or {@link Long#MIN_VALUE} when it is not sitting. */
	public long sitting = Long.MIN_VALUE;
	public long lastSitting = Long.MIN_VALUE;
	/** The public works on the council's table (plot ids), in the planner's order. */
	public final List<Integer> table = new ArrayList<>();
	public final Map<UUID, Integer> votes = new LinkedHashMap<>();
	/** The council's last choice (plot id), or -1. */
	public int chosen = -1;

	public boolean isSworn(UUID player) {
		return this.sworn.containsKey(player);
	}

	public boolean sittingOn(long day) {
		return this.sitting == day;
	}
}
