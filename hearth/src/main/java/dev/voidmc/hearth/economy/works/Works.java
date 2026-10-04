package dev.voidmc.hearth.economy.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * Public works players pay for and supply contracts they take, in one settlement
 * ({@code settlement.data(EconomyModule.WORKS)}).
 */
public final class Works {
	public static final Codec<Works> CODEC = RecordCodecBuilder.create(i -> i.group(
		Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(w -> new ArrayList<>(w.projects.values())),
		Contract.CODEC.listOf().optionalFieldOf("contracts", List.of()).forGetter(w -> w.contracts),
		Codec.INT.optionalFieldOf("next_contract", 1).forGetter(w -> w.nextContract)
	).apply(i, (projects, contracts, next) -> {
		Works w = new Works();
		for (Project p : projects) {
			w.projects.put(p.plot, p);
		}
		w.contracts.addAll(contracts);
		w.nextContract = next;
		return w;
	}));

	/** Funded construction, by plot id. */
	public final Map<Integer, Project> projects = new LinkedHashMap<>();
	public final List<Contract> contracts = new ArrayList<>();
	public int nextContract = 1;

	public @Nullable Contract contract(int id) {
		for (Contract c : this.contracts) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}

	/**
	 * A building players have paid for: the planner and the sim put it first, and its fund buys from passing traders
	 * whatever the town's stores lack for it.
	 */
	public static final class Project {
		public static final Codec<Project> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(p -> p.plot),
			Codec.LONG.optionalFieldOf("fund", 0L).forGetter(p -> p.fund),
			Codec.LONG.optionalFieldOf("spent", 0L).forGetter(p -> p.spent),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(p -> p.since),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("by", Map.of()).forGetter(p -> p.contributions),
			Codec.INT.listOf().optionalFieldOf("missing", List.of()).forGetter(p -> {
				List<Integer> l = new ArrayList<>();
				for (int v : p.missing) {
					l.add(v);
				}
				return l;
			})
		).apply(i, (plot, fund, spent, since, by, missing) -> {
			Project p = new Project(plot, since);
			p.fund = fund;
			p.spent = spent;
			p.contributions.putAll(by);
			for (int g = 0; g < Math.min(p.missing.length, missing.size()); g++) {
				p.missing[g] = missing.get(g);
			}
			return p;
		}));

		public final int plot;
		/** Coin given and not yet spent. */
		public long fund;
		/** Coin spent on goods for it so far. */
		public long spent;
		public final long since;
		/** Who gave how much. */
		public final Map<UUID, Long> contributions = new HashMap<>();
		/** What its construction last stalled for, by goods ordinal: what the fund buys in. */
		public final int[] missing = new int[Goods.VALUES.length];

		public Project(int plot, long since) {
			this.plot = plot;
			this.since = since;
		}

		public long given() {
			long t = 0;
			for (long v : this.contributions.values()) {
				t += v;
			}
			return t;
		}
	}

	/**
	 * A supply contract: deliver {@code units} of a goods by the due day for an agreed sum, held in escrow from the
	 * treasury when it is taken and paid out as goods arrive.
	 */
	public static final class Contract {
		public static final Codec<Contract> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(c -> c.id),
			Goods.CODEC.fieldOf("goods").forGetter(c -> c.goods),
			Codec.INT.fieldOf("units").forGetter(c -> c.units),
			Codec.INT.optionalFieldOf("delivered", 0).forGetter(c -> c.delivered),
			Codec.LONG.fieldOf("total").forGetter(c -> c.total),
			Codec.LONG.optionalFieldOf("paid", 0L).forGetter(c -> c.paid),
			UUIDUtil.CODEC.optionalFieldOf("taker").forGetter(c -> Optional.ofNullable(c.taker)),
			Codec.LONG.optionalFieldOf("offered", 0L).forGetter(c -> c.offered),
			Codec.LONG.optionalFieldOf("due", Long.MAX_VALUE).forGetter(c -> c.due),
			Codec.LONG.optionalFieldOf("escrow", 0L).forGetter(c -> c.escrow)
		).apply(i, (id, goods, units, delivered, total, paid, taker, offered, due, escrow) -> {
			Contract c = new Contract(id, goods, units, total, offered);
			c.delivered = delivered;
			c.paid = paid;
			c.taker = taker.orElse(null);
			c.due = due;
			c.escrow = escrow;
			return c;
		}));

		public final int id;
		public final Goods goods;
		public final int units;
		public int delivered;
		/** What the whole delivery is worth, in bits. */
		public final long total;
		public long paid;
		public @Nullable UUID taker;
		public final long offered;
		public long due = Long.MAX_VALUE;
		/** Coin the treasury set aside for it. */
		public long escrow;

		public Contract(int id, Goods goods, int units, long total, long offered) {
			this.id = id;
			this.goods = goods;
			this.units = units;
			this.total = total;
			this.offered = offered;
		}

		public boolean taken() {
			return this.taker != null;
		}

		/** What the delivery so far has earned in all ({@code total × delivered / units}, rounded down). */
		public long earned() {
			return this.units <= 0 ? 0 : this.total * Math.min(this.delivered, this.units) / this.units;
		}
	}
}
