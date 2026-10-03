package dev.voidmc.hearth.economy.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.economy.coin.Change;
import dev.voidmc.hearth.economy.coin.Coin;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jspecify.annotations.Nullable;

/**
 * What a coin pouch holds: a count of each coin (not item stacks, so a pouch of five hundred coins is three numbers).
 * Also the pouch's tooltip image: the client draws the coins it holds.
 */
public record PouchContents(int bits, int marks, int crowns) implements TooltipComponent {
	public static final PouchContents EMPTY = new PouchContents(0, 0, 0);

	public static final Codec<PouchContents> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("bits", 0).forGetter(PouchContents::bits),
		Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("marks", 0).forGetter(PouchContents::marks),
		Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("crowns", 0).forGetter(PouchContents::crowns)
	).apply(i, PouchContents::new));

	public static final StreamCodec<ByteBuf, PouchContents> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PouchContents::bits,
		ByteBufCodecs.VAR_INT, PouchContents::marks,
		ByteBufCodecs.VAR_INT, PouchContents::crowns,
		PouchContents::new);

	public static PouchContents of(long[] counts) {
		return new PouchContents((int) counts[Coin.BIT.ordinal()], (int) counts[Coin.MARK.ordinal()], (int) counts[Coin.CROWN.ordinal()]);
	}

	public int count(Coin coin) {
		return switch (coin) {
			case BIT -> this.bits;
			case MARK -> this.marks;
			case CROWN -> this.crowns;
		};
	}

	public PouchContents with(Coin coin, int count) {
		return switch (coin) {
			case BIT -> new PouchContents(count, this.marks, this.crowns);
			case MARK -> new PouchContents(this.bits, count, this.crowns);
			case CROWN -> new PouchContents(this.bits, this.marks, count);
		};
	}

	public long[] counts() {
		return new long[] {this.bits, this.marks, this.crowns};
	}

	/** Coins held. */
	public int coins() {
		return this.bits + this.marks + this.crowns;
	}

	/** Worth in bits. */
	public long value() {
		return Change.value(counts());
	}

	public boolean isEmpty() {
		return coins() == 0;
	}

	/** The largest coin it holds, or null if empty. */
	public @Nullable Coin largest() {
		for (Coin coin : Coin.DESCENDING) {
			if (count(coin) > 0) {
				return coin;
			}
		}
		return null;
	}
}
