package dev.voidmc.hearth.economy.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a deed records: which settlement, which plot, and enough of the plot to describe it in the tooltip without asking
 * the server (the settlement may be far away, or gone).
 *
 * @param settlement     the settlement's id
 * @param settlementName its name when the deed was drawn up
 * @param plot           the plot id within the settlement
 * @param building       the plot's archetype path, e.g. {@code house/cottage} (for the tooltip)
 * @param built          a house (true) or an empty plot (false)
 * @param origin         the footprint's corner, at floor level
 * @param sizeX          footprint width
 * @param sizeZ          footprint depth
 */
public record DeedData(long settlement, String settlementName, int plot, String building, boolean built, BlockPos origin, int sizeX,
	int sizeZ) {

	public static final Codec<DeedData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("settlement").forGetter(DeedData::settlement),
		Codec.STRING.fieldOf("settlement_name").forGetter(DeedData::settlementName),
		Codec.INT.fieldOf("plot").forGetter(DeedData::plot),
		Codec.STRING.optionalFieldOf("building", "").forGetter(DeedData::building),
		Codec.BOOL.optionalFieldOf("built", true).forGetter(DeedData::built),
		BlockPos.CODEC.fieldOf("origin").forGetter(DeedData::origin),
		Codec.INT.fieldOf("size_x").forGetter(DeedData::sizeX),
		Codec.INT.fieldOf("size_z").forGetter(DeedData::sizeZ)
	).apply(i, DeedData::new));

	public static final StreamCodec<ByteBuf, DeedData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, DeedData::settlement,
		ByteBufCodecs.STRING_UTF8, DeedData::settlementName,
		ByteBufCodecs.VAR_INT, DeedData::plot,
		ByteBufCodecs.STRING_UTF8, DeedData::building,
		ByteBufCodecs.BOOL, DeedData::built,
		BlockPos.STREAM_CODEC, DeedData::origin,
		ByteBufCodecs.VAR_INT, DeedData::sizeX,
		ByteBufCodecs.VAR_INT, DeedData::sizeZ,
		DeedData::new);

	/** Whether {@code pos} lies within the plot's footprint (any height). */
	public boolean contains(BlockPos pos) {
		return pos.getX() >= this.origin.getX() && pos.getX() < this.origin.getX() + this.sizeX
			&& pos.getZ() >= this.origin.getZ() && pos.getZ() < this.origin.getZ() + this.sizeZ;
	}
}
