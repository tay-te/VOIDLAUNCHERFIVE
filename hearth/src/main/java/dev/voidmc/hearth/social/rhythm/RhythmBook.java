package dev.voidmc.hearth.social.rhythm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import java.util.ArrayList;
import java.util.List;

/**
 * What the rhythm keeps of a settlement (docs/LIVING_WORLD_2.md §2.5, §9.3). The calendar is computed, never stored; this
 * holds only the guard that pays a rest day's offering once ({@code hearth:social/rhythm}) and the watch lights standing,
 * positions only ({@code hearth:social/lights}), so a crash leaves nothing behind: they are cleared on load and on the
 * next embodiment.
 */
public final class RhythmBook {
	public static final Codec<RhythmBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("offering_day", Long.MIN_VALUE).forGetter(b -> b.offeringDay)
	).apply(i, RhythmBook::new));

	public static final HearthDataKey<RhythmBook> KEY = HearthDataKey.register(Hearth.id("social/rhythm"), HearthDataKey.Scope.SETTLEMENT, CODEC,
		RhythmBook::new);

	/** The watch lights standing in a settlement: block positions (packed). */
	public static final class Lights {
		public static final Codec<Lights> CODEC = Codec.LONG.listOf().xmap(Lights::new, l -> List.copyOf(l.positions));
		public final List<Long> positions;

		public Lights() {
			this(List.of());
		}

		Lights(List<Long> positions) {
			this.positions = new ArrayList<>(positions);
		}
	}

	public static final HearthDataKey<Lights> LIGHTS = HearthDataKey.register(Hearth.id("social/lights"), HearthDataKey.Scope.SETTLEMENT, Lights.CODEC,
		Lights::new);

	/** The last rest day whose offering went to the treasury. */
	public long offeringDay;

	public RhythmBook() {
		this(Long.MIN_VALUE);
	}

	RhythmBook(long offeringDay) {
		this.offeringDay = offeringDay;
	}

	/** Forces the keys to register (class init) during mod init, before any world loads. */
	static void touch() {
	}
}
