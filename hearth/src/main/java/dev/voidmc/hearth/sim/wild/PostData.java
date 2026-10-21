package dev.voidmc.hearth.sim.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * What makes a settlement a post (docs/LIVING_WORLD_2.md §7.5), under {@code hearth:plan/wild}: its site (type, centre,
 * the road it serves), which ground a nomad camp stands on and when it last moved, and who has paid a toll gate today.
 * A settlement without this key is a town.
 */
public final class PostData {
	/** One player's toll, paid on a sim day: they pass freely for the rest of it (§7.5). */
	public record Paid(UUID player, long day) {
		static final Codec<Paid> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Paid::player),
			Codec.LONG.fieldOf("day").forGetter(Paid::day)
		).apply(i, Paid::new));
	}

	public static final Codec<PostData> CODEC = RecordCodecBuilder.create(i -> i.group(
		PostSite.CODEC.optionalFieldOf("site").forGetter(d -> java.util.Optional.ofNullable(d.site)),
		Codec.INT.optionalFieldOf("ground", 0).forGetter(d -> d.ground),
		Codec.LONG.optionalFieldOf("moved_day", Long.MIN_VALUE).forGetter(d -> d.movedDay),
		Paid.CODEC.listOf().optionalFieldOf("tolls", List.of()).forGetter(d -> {
			List<Paid> out = new ArrayList<>();
			d.tolls.forEach((p, day) -> out.add(new Paid(p, day)));
			return out;
		}),
		Codec.LONG.optionalFieldOf("tolls_taken", 0L).forGetter(d -> d.tollsTaken),
		Codec.LONG.optionalFieldOf("guests", 0L).forGetter(d -> d.guests)
	).apply(i, (site, ground, moved, tolls, taken, guests) -> {
		PostData d = new PostData();
		d.site = site.orElse(null);
		d.ground = ground;
		d.movedDay = moved;
		for (Paid p : tolls) {
			d.tolls.put(p.player(), p.day());
		}
		d.tollsTaken = taken;
		d.guests = guests;
		return d;
	}));

	public static final HearthDataKey<PostData> KEY = HearthDataKey.register(Hearth.id("plan/wild"), HearthDataKey.Scope.SETTLEMENT, CODEC,
		PostData::new);

	public @Nullable PostSite site;
	/** A nomad camp's ground now (0 or 1). */
	public int ground;
	/** The sim day a nomad camp last packed up and moved (or was founded). */
	public long movedDay = Long.MIN_VALUE;
	/** Players who paid the toll, by the sim day they paid. */
	public final Map<UUID, Long> tolls = new HashMap<>();
	/** Bits taken at the gate (players and caravans), for the lord's books and the stats. */
	public long tollsTaken;
	/** Travellers put up (abstract), for the stats. */
	public long guests;

	/** The post data of a settlement that is a post, or null for a town (never creates it). */
	public static @Nullable PostData of(Settlement s) {
		if (s instanceof dev.voidmc.hearth.sim.settlement.SimSettlement sim) {
			if (!sim.dataMap().has(KEY)) {
				return null;
			}
			PostData d = sim.data(KEY);
			return d.site == null ? null : d;
		}
		if (s.kind() != dev.voidmc.hearth.api.SettlementKind.POST) {
			return null;
		}
		PostData d = s.data(KEY);
		return d.site == null ? null : d;
	}

	public @Nullable PostType type() {
		return this.site == null ? null : this.site.type();
	}

	/** Whether the player has paid today (sim day {@code day}). */
	public boolean paid(UUID player, long day) {
		Long d = this.tolls.get(player);
		return d != null && d == day;
	}

	/** Forgets tolls of earlier days. */
	public void forgetOld(long day) {
		this.tolls.values().removeIf(d -> d != day);
	}
}
