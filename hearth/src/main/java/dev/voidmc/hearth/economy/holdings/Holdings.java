package dev.voidmc.hearth.economy.holdings;

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
 * The businesses players own in a settlement ({@code settlement.data(EconomyModule.HOLDINGS)}): a workshop, a shop, a
 * farm or an extraction site bought with a deed. The workers stay and keep working; the owner draws what the business
 * earns above their wages, paid into the owner's bank account each day.
 */
public final class Holdings {
	public static final Codec<Holdings> CODEC = Holding.CODEC.listOf().xmap(list -> {
		Holdings h = new Holdings();
		for (Holding b : list) {
			h.byPlot.put(b.plot, b);
		}
		return h;
	}, h -> new ArrayList<>(h.byPlot.values()));

	/** By plot id. */
	public final Map<Integer, Holding> byPlot = new LinkedHashMap<>();

	public @Nullable Holding get(int plot) {
		return this.byPlot.get(plot);
	}

	public @Nullable UUID owner(int plot) {
		Holding h = this.byPlot.get(plot);
		return h == null ? null : h.owner;
	}

	/** One owned business. */
	public static final class Holding {
		public static final Codec<Holding> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(h -> h.plot),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(h -> h.owner),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(h -> h.since),
			Codec.LONG.optionalFieldOf("earned", 0L).forGetter(h -> h.earned),
			Codec.LONG.optionalFieldOf("last_income", 0L).forGetter(h -> h.lastIncome),
			Codec.LONG.optionalFieldOf("pending", 0L).forGetter(h -> h.pending),
			Codec.DOUBLE.optionalFieldOf("fraction", 0.0).forGetter(h -> h.fraction)
		).apply(i, (plot, owner, since, earned, last, pending, fraction) -> {
			Holding h = new Holding(plot, owner, since);
			h.earned = earned;
			h.lastIncome = last;
			h.pending = pending;
			h.fraction = fraction;
			return h;
		}));

		public final int plot;
		public UUID owner;
		public final long since;
		/** Everything it has paid its owner. */
		public long earned;
		/** What it paid yesterday. */
		public long lastIncome;
		/** Earned but not yet paid into the owner's account (paid when the server can reach the bank ledger). */
		public long pending;
		/** Fractions of a bit carried to the next day, so small businesses still pay. */
		public double fraction;
		/** Income accrued today (not saved: settled in the same daily step). */
		public transient double today;

		public Holding(int plot, UUID owner, long since) {
			this.plot = plot;
			this.owner = owner;
			this.since = since;
		}
	}
}
