package dev.voidmc.hearth.law.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** Codecs for the api's law records, which the api leaves to the law module to persist. */
public final class Codecs {
	public static final Codec<Crime> CRIME = RecordCodecBuilder.create(i -> i.group(
		Offence.CODEC.fieldOf("offence").forGetter(Crime::offence),
		UUIDUtil.CODEC.fieldOf("offender").forGetter(Crime::offender),
		Codec.LONG.fieldOf("settlement").forGetter(Crime::settlementId),
		BlockPos.CODEC.fieldOf("pos").forGetter(Crime::pos),
		UUIDUtil.CODEC.optionalFieldOf("victim").forGetter(c -> Optional.ofNullable(c.victim())),
		Codec.LONG.optionalFieldOf("value", 0L).forGetter(Crime::value),
		Codec.BOOL.optionalFieldOf("witnessed", false).forGetter(Crime::witnessed),
		Codec.LONG.optionalFieldOf("time", 0L).forGetter(Crime::gameTime)
	).apply(i, (o, off, s, p, v, val, w, t) -> new Crime(o, off, s, p, v.orElse(null), val, w, t)));

	public static final Codec<Owner.Kind> OWNER_KIND = Codec.STRING.xmap(Owner.Kind::valueOf, Owner.Kind::name);

	public static final Codec<Owner> OWNER = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("settlement").forGetter(Owner::settlementId),
		UUIDUtil.CODEC.optionalFieldOf("resident").forGetter(o -> Optional.ofNullable(o.resident())),
		UUIDUtil.CODEC.optionalFieldOf("player").forGetter(o -> Optional.ofNullable(o.player())),
		OWNER_KIND.fieldOf("kind").forGetter(Owner::kind)
	).apply(i, (s, r, p, k) -> new Owner(s, r.orElse(null), p.orElse(null), k)));

	private Codecs() {
	}

	/** A crime with one field changed: whether it was witnessed. */
	public static Crime witnessed(Crime c, boolean witnessed) {
		return new Crime(c.offence(), c.offender(), c.settlementId(), c.pos(), c.victim(), c.value(), witnessed, c.gameTime());
	}

	/** A crime with the offender's id, for tests and reports. */
	public static Crime crime(Offence offence, UUID offender, long settlement, BlockPos pos, UUID victim, long value, boolean witnessed, long time) {
		return new LawService.Crime(offence, offender, settlement, pos, victim, value, witnessed, time);
	}
}
