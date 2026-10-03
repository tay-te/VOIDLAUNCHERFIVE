package dev.voidmc.hearth.sim.apply;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.sim.settlement.Cursor;
import java.util.Arrays;
import java.util.stream.LongStream;
import org.jspecify.annotations.Nullable;

/**
 * What has been written into one chunk, per plot: a cursor below which every step of that plot lying in this chunk is
 * in the world (placed by world generation, by the applier, or by an embodied builder). Kept on the chunk itself as a
 * persistent Fabric attachment ({@link ChunkLedgers}), so the blocks and the record of them are saved together and a crash can never leave
 * them disagreeing: whatever state the chunk comes back in, its ledger describes it.
 *
 * <p>Immutable; every change makes a new ledger. A chunk rarely holds more than a handful of plots, so it is a flat array
 * of {@code (settlement, plot, cursor)} triples searched linearly.
 */
public final class AppliedLedger {
	public static final AppliedLedger EMPTY = new AppliedLedger(new long[0]);
	public static final Codec<AppliedLedger> CODEC = Codec.LONG_STREAM.xmap(s -> new AppliedLedger(s.toArray()), l -> LongStream.of(l.data));

	private final long[] data;

	private AppliedLedger(long[] data) {
		this.data = data.length % 3 == 0 ? data : Arrays.copyOf(data, data.length - data.length % 3);
	}

	public boolean isEmpty() {
		return this.data.length == 0;
	}

	public int size() {
		return this.data.length / 3;
	}

	/** The plot's cursor in this chunk; {@link Cursor#ZERO} if nothing of it was ever written here. */
	public long get(long settlement, int plot) {
		for (int i = 0; i < this.data.length; i += 3) {
			if (this.data[i] == settlement && this.data[i + 1] == plot) {
				return this.data[i + 2];
			}
		}
		return Cursor.ZERO;
	}

	public boolean has(long settlement, int plot) {
		for (int i = 0; i < this.data.length; i += 3) {
			if (this.data[i] == settlement && this.data[i + 1] == plot) {
				return true;
			}
		}
		return false;
	}

	/** A ledger with the plot's cursor set (never moved backwards). */
	public AppliedLedger with(long settlement, int plot, long cursor) {
		for (int i = 0; i < this.data.length; i += 3) {
			if (this.data[i] == settlement && this.data[i + 1] == plot) {
				if (this.data[i + 2] >= cursor) {
					return this;
				}
				long[] d = this.data.clone();
				d[i + 2] = cursor;
				return new AppliedLedger(d);
			}
		}
		long[] d = Arrays.copyOf(this.data, this.data.length + 3);
		d[this.data.length] = settlement;
		d[this.data.length + 1] = plot;
		d[this.data.length + 2] = cursor;
		return new AppliedLedger(d);
	}

	/** Drops every entry of a settlement (it no longer exists). */
	public AppliedLedger without(long settlement) {
		long[] d = new long[this.data.length];
		int n = 0;
		for (int i = 0; i < this.data.length; i += 3) {
			if (this.data[i] != settlement) {
				d[n++] = this.data[i];
				d[n++] = this.data[i + 1];
				d[n++] = this.data[i + 2];
			}
		}
		return n == this.data.length ? this : new AppliedLedger(Arrays.copyOf(d, n));
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof AppliedLedger l && Arrays.equals(this.data, l.data);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.data);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder("Ledger[");
		for (int i = 0; i < this.data.length; i += 3) {
			b.append(i == 0 ? "" : ", ").append(this.data[i]).append('/').append(this.data[i + 1]).append('@').append(Cursor.toString(this.data[i + 2]));
		}
		return b.append(']').toString();
	}
}
