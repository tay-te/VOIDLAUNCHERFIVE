package dev.voidmc.hearth.quest.commission;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * A player's rank in a lord's service (docs/LIVING_WORLD_2.md §8.1), lowest first: one who asked to serve, a retainer
 * (sworn to the lord with the pledge of service, one commission done), a sworn man (three, a Friend of the seat), and a
 * household knight (six, standing 70 or better, a tournament won or a raid repelled; dubbed at the throne).
 */
public enum Rank implements StringRepresentable {
	PETITIONER, RETAINER, SWORN, KNIGHT;

	public static final StringRepresentable.EnumCodec<Rank> CODEC = StringRepresentable.fromEnum(Rank::values);
	public static final Codec<Rank> LENIENT = Codec.STRING.xmap(Rank::byId, Rank::getSerializedName);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "hearth.retinue.rank." + getSerializedName();
	}

	public @Nullable Rank next() {
		return this == KNIGHT ? null : values()[ordinal() + 1];
	}

	public Rank previous() {
		return this == PETITIONER ? PETITIONER : values()[ordinal() - 1];
	}

	public boolean atLeast(Rank other) {
		return ordinal() >= other.ordinal();
	}

	public static Rank byId(String id) {
		for (Rank r : values()) {
			if (r.getSerializedName().equals(id)) {
				return r;
			}
		}
		return PETITIONER;
	}
}
