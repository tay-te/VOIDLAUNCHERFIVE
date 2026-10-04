package dev.voidmc.hearth.law.item;

import dev.voidmc.hearth.api.LawService.Owner;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Network codecs for the api's law records. */
public final class LawStreamCodecs {
	public static final StreamCodec<ByteBuf, Owner.Kind> OWNER_KIND = ByteBufCodecs.idMapper(i -> Owner.Kind.values()[i], Owner.Kind::ordinal);

	public static final StreamCodec<ByteBuf, Owner> OWNER = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, Owner::settlementId,
		ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), o -> java.util.Optional.ofNullable(o.resident()),
		ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), o -> java.util.Optional.ofNullable(o.player()),
		OWNER_KIND, Owner::kind,
		(s, r, p, k) -> new Owner(s, r.orElse(null), p.orElse(null), k));

	private LawStreamCodecs() {
	}
}
