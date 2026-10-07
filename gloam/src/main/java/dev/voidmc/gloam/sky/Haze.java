package dev.voidmc.gloam.sky;

import dev.voidmc.gloam.air.Noise;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * How hazy the air of a land is: the murk that hangs in a cloud forest all day, the damp over a bayou, the dust over
 * painted canyons. Biomes used to say this with short fog distances of their own, which cut VOID LOD's vistas off at a
 * few hundred blocks; Gloam gives it back as haze that thickens the air without walling it, thinner at midday, so
 * there is always a horizon.
 *
 * <p>The table is keyed by biome id and holds what the biome's own fog used to reach ({@link Entry#reach}, in blocks),
 * so adding a biome is one line with a number anyone can picture. {@link #haziness} turns that into 0 (clear air) .. 1
 * (as murky as it gets). Expanse's country haze hints ({@link #HINTS}) are meant to feed the same knob.
 */
public final class Haze {
	/** What fills the air: water (mist, damp, low cloud: pale grey) or dust (a warm, dry haze). */
	public enum Kind {
		MIST,
		DUST
	}

	/** A land's haze: how far its old fog reached, in blocks, and what the haze is made of. */
	public record Entry(float reach, Kind kind) {
	}

	/** The nearest the old biome fogs came (cloud forest), and vanilla's clear-air fog end: haziness 1 and 0. */
	private static final float MURKIEST = 64, CLEAR = 1024;

	/** Expanse's biomes and the fog end they used to set (Expanse removed those in favour of this). */
	public static final Map<Identifier, Entry> BIOMES = Map.ofEntries(
		mist("expanse:cloud_forest", 72),
		mist("expanse:tepui", 110),
		mist("expanse:kapok_rainforest", 115),
		mist("expanse:willow_bayou", 120),
		mist("expanse:ancient_oakwood", 150),
		mist("expanse:boreal_muskeg", 150),
		mist("expanse:heather_moor", 170),
		mist("expanse:jade_karst", 190),
		mist("expanse:redwood_giants", 230),
		mist("expanse:monsoon_forest", 230),
		Map.entry(Identifier.parse("expanse:salt_flats"), new Entry(230, Kind.DUST)),
		mist("expanse:fjordlands", 240),
		mist("expanse:volcanic_highlands", 260),
		Map.entry(Identifier.parse("expanse:painted_canyons"), new Entry(300, Kind.DUST))
	);

	/**
	 * Expanse's coming country haze hints (ExpanseCountry), as the same kind of entry; read the same way once the
	 * country lands, under the biome's own entry where a biome has one.
	 */
	public static final Map<String, Entry> HINTS = Map.of(
		"humid_lowland", new Entry(380, Kind.MIST),
		"misty_lakes", new Entry(260, Kind.MIST),
		"forest_damp", new Entry(320, Kind.MIST),
		"dusty", new Entry(320, Kind.DUST),
		"temperate", new Entry(CLEAR, Kind.MIST),
		"clear_alpine", new Entry(CLEAR, Kind.MIST)
	);

	private Haze() {
	}

	private static Map.Entry<Identifier, Entry> mist(String id, float reach) {
		return Map.entry(Identifier.parse(id), new Entry(reach, Kind.MIST));
	}

	/** How hazy the air is where the old fog reached {@code reach} blocks: 0 clear .. 1 the murkiest, on a log scale. */
	public static float haziness(float reach) {
		double h = 1 - Math.log(Math.max(reach, MURKIEST) / MURKIEST) / Math.log(CLEAR / MURKIEST);
		return (float) Math.clamp(h, 0.0, 1.0);
	}

	/**
	 * How much of a land's haze hangs at {@code hour} (0..24): all of it through the night and the morning, thinning in
	 * the warm middle of the day as the air dries and lifts. The murkiest lands (a cloud forest sits in its cloud) keep
	 * most of theirs all day.
	 */
	public static float daily(float haziness, float hour) {
		float midday = Noise.smoothstep(8.0F, 12.0F, hour) * (1 - Noise.smoothstep(15.0F, 19.0F, hour));
		float keeps = 0.5F + 0.4F * Noise.smoothstep(0.75F, 0.95F, haziness);
		return 1 - (1 - keeps) * midday;
	}

	/**
	 * The fog end for an air this hazy (0..1): back out along the same log scale, never nearer than {@code nearest}
	 * blocks, so even the murkiest air leaves the near land clear.
	 */
	public static float reach(float haziness, float nearest) {
		double end = MURKIEST * Math.pow(CLEAR / MURKIEST, 1 - Math.clamp(haziness, 0, 1));
		return (float) Math.max(nearest, end);
	}

	/**
	 * How thick a curtain round the camera the haze hangs, in VOID LOD's rain-curtain units (0..1; see
	 * {@code GloamSky}'s fog start): only the hazier lands have one, so their far land fades out over a few kilometres
	 * instead of standing clear.
	 */
	public static float curtain(float haziness) {
		return 0.55F * haziness * haziness;
	}
}
