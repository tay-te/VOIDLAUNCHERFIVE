package dev.voidmc.hearth.economy.household;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * A town's player households (settlement data {@code hearth:economy/houses}; docs/LIVING_WORLD_2.md §8.2): for each house a
 * player owns that keeps servants, who serves there, the larder, and when the owner was last home. Kept on the town so
 * wages and the larder go on while the owner is offline; the player's own index of their houses is
 * {@link HouseholdIndex}. Mutable; the town is marked dirty by whoever changes it.
 */
public final class HouseBook {
	public static final Codec<HouseBook> CODEC = House.CODEC.listOf().xmap(list -> {
		HouseBook b = new HouseBook();
		for (House h : list) {
			b.houses.put(h.plot, h);
		}
		return b;
	}, b -> List.copyOf(b.houses.values()));

	public final Map<Integer, House> houses = new LinkedHashMap<>();

	public @Nullable House house(int plot) {
		return this.houses.get(plot);
	}

	public House open(int plot, UUID owner, long day) {
		return this.houses.computeIfAbsent(plot, p -> new House(p, owner, day));
	}

	public boolean isEmpty() {
		return this.houses.isEmpty();
	}

	/** One house. */
	public static final class House {
		public static final Codec<House> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(h -> h.plot),
			UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(h -> h.owner),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("servants", List.of()).forGetter(h -> h.servants),
			Codec.INT.optionalFieldOf("pending", 0).forGetter(h -> h.pending),
			Codec.INT.optionalFieldOf("stocked", 0).forGetter(h -> h.stocked),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(h -> h.since),
			Codec.LONG.optionalFieldOf("visit", 0L).forGetter(h -> h.lastVisitDay),
			Codec.LONG.optionalFieldOf("spent", 0L).forGetter(h -> h.spent),
			Codec.BOOL.optionalFieldOf("larder_short", false).forGetter(h -> h.larderShort)
		).apply(i, (plot, owner, servants, pending, stocked, since, visit, spent, shortOf) -> {
			House h = new House(plot, owner, since);
			h.servants.addAll(servants);
			h.pending = pending;
			h.stocked = stocked;
			h.lastVisitDay = visit;
			h.spent = spent;
			h.larderShort = shortOf;
			return h;
		}));

		public final int plot;
		public UUID owner;
		public final List<UUID> servants = new ArrayList<>(3);
		/** Food bought for the larder and not yet put in its chest (its chunk was not loaded). */
		public int pending;
		/** Food believed in the larder chest after the last fill (read again whenever the chest is loaded). */
		public int stocked;
		public final long since;
		/** The sim day the owner was last greeted at home: what the servants' report covers. */
		public long lastVisitDay;
		/** Coin the household has cost the owner's bank account since they were last home (wages and larder). */
		public long spent;
		/** The bank could not pay for the larder at the last fill. */
		public boolean larderShort;

		public House(int plot, UUID owner, long since) {
			this.plot = plot;
			this.owner = owner;
			this.since = since;
			this.lastVisitDay = since;
		}
	}
}
