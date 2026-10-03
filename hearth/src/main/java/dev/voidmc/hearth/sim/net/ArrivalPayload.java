package dev.voidmc.hearth.sim.net;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Settlement;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a player they walked into a settlement (or out of every one: an empty name), for the arrival banner and the F3
 * line. Sent only when the settlement a player stands in changes, so it costs nothing while they stay put.
 *
 * @param tier    the tier's serialized name (camp, hamlet, village, town, city)
 * @param culture the culture's serialized name, for the banner's flavour
 */
public record ArrivalPayload(long id, String name, String tier, int population, String culture, boolean embodied) implements CustomPacketPayload {
	public static final Type<ArrivalPayload> TYPE = new Type<>(Hearth.id("sim/arrival"));
	public static final StreamCodec<ByteBuf, ArrivalPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, ArrivalPayload::id,
		ByteBufCodecs.STRING_UTF8, ArrivalPayload::name,
		ByteBufCodecs.STRING_UTF8, ArrivalPayload::tier,
		ByteBufCodecs.VAR_INT, ArrivalPayload::population,
		ByteBufCodecs.STRING_UTF8, ArrivalPayload::culture,
		ByteBufCodecs.BOOL, ArrivalPayload::embodied,
		ArrivalPayload::new);
	public static final ArrivalPayload LEFT = new ArrivalPayload(Long.MIN_VALUE, "", "", 0, "", false);

	public static ArrivalPayload of(Settlement s) {
		return new ArrivalPayload(s.id(), s.name(), s.tier().getSerializedName(), s.population(), s.culture().getSerializedName(), s.embodied());
	}

	public boolean left() {
		return this.name.isEmpty();
	}

	@Override
	public Type<ArrivalPayload> type() {
		return TYPE;
	}
}
