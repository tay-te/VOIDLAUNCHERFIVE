package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Noise;

/**
 * Chooses how loud each underground bed should be from the caverns around the camera ({@link Hollow}) and the air
 * above. Pure, like {@link Mix}, and unit-tested the same way ({@code CaveMixTest}). Every bed is a product of plain
 * 0..1 factors and of {@link Hollow#under()}, so at a cave mouth the caverns fade in as the land's beds (muffled by
 * {@link Surroundings#buried()}) fade out.
 *
 * <ul>
 *   <li>Drips: a few always, more where Expanse says the roof drips and after rain soaks down, few in the frozen and the
 *   burning realms; heard close and dry in a passage ({@link Bed#DRIP_NEAR}), far and echoing in a cavern
 *   ({@link Bed#DRIP_HALL}). Glowworm grottos are wet: more drips.</li>
 *   <li>Bats: the roosts' chatter, livelier at dusk when they wake and at dawn when they come home.</li>
 *   <li>Rivers: a cavern river's rush, deep and loud for a great river, from where it lies ({@link Bed#UNDER_RIVER}).</li>
 *   <li>The buried sea: slow swells lapping on stone in a vast hall; a realm's lake the same, smaller and quieter.</li>
 *   <li>Mines: the old timbers creaking and settling.</li>
 *   <li>Cavern wind: a slow hollow moan in the great caverns, stronger when the wind blows hard above (the caves
 *   breathe), and stilled where glowworms hang (they live only where the air is still).</li>
 * </ul>
 *
 * Vanilla's cave moods carry on beneath, as they do now.
 */
public final class CaveMix {
	/** How wet each realm's roof is, by {@link CaveKinds} realm id: the ice and the fire realms hardly drip. */
	private static final float[] REALM_WET = {
		1.0F, // none: the passages between realms
		0.8F, // wilds
		1.2F, // lush
		1.3F, // dripstone
		0.9F, // crystal
		0.1F, // ember
		1.2F, // mere
		0.2F, // frozen
		1.0F, // roots
		0.6F, // fossil
		1.3F, // tidal
		0.4F, // amber
		0.05F, // brimstone
		1.1F, // sea
	};

	private CaveMix() {
	}

	/**
	 * Fills the underground beds' entries of {@code out} (indexed by {@link Bed#ordinal()}) with each one's volume, 0..1
	 * times its {@link Bed#gain()}; the land's entries are {@link Mix}'s and are left alone.
	 */
	public static void volumes(AirSample air, Hollow cave, float[] out) {
		float under = cave.under();
		float open = cave.openness();
		float realmWet = cave.realm() >= 0 && cave.realm() < REALM_WET.length ? REALM_WET[cave.realm()] : 1;

		// ---- drips: a few anywhere under the land, many where the roof is wet; rain above soaks down over hours
		float soaked = 0.55F + 0.3F * air.wetness() + 0.15F * air.rain();
		float drips = (0.2F + 0.8F * Math.max(cave.drip(), cave.glowworms())) * soaked * realmWet * under;
		set(out, Bed.DRIP_NEAR, drips * (1 - open));
		set(out, Bed.DRIP_HALL, drips * open);

		// ---- bats: a murmur by day, waking at dusk, coming home before dawn
		float waking = Mix.window(air.hour(), 17.5F, 19.0F, 20.5F, 22.5F) + Mix.window(air.hour(), 3.8F, 4.8F, 5.6F, 6.6F) * 0.6F;
		set(out, Bed.BATS, cave.bats() * (0.45F + 0.55F * Math.min(1, waking)) * under);

		// ---- a cavern river: from a brook-like rush to the great rivers' roar; the vault carries it
		set(out, Bed.UNDER_RIVER, cave.river() * (0.4F + 0.6F * cave.great()) * (0.7F + 0.3F * open) * (0.85F + 0.15F * air.wetness()) * under);

		// ---- the buried sea, or a realm's lake: lapping on stone, the hall ringing with it
		set(out, Bed.BURIED_SEA, Math.max(cave.sea(), 0.45F * cave.lake()) * (0.6F + 0.4F * open) * under);

		// ---- the great mines: timbers creaking, louder in the tight galleries than in the open workings
		set(out, Bed.MINE_CREAK, cave.mine() * (0.7F + 0.3F * (1 - open)) * under);

		// ---- wind in the great caverns: the vault moans; the caves breathe harder in a gale above
		float breeze = Noise.smoothstep(0.5F, 11.0F, air.wind().now());
		float hall = Math.max(Noise.smoothstep(0.4F, 0.95F, open), 0.8F * Noise.smoothstep(16, 64, cave.vault()) * open);
		set(out, Bed.CAVERN_WIND, hall * (0.55F + 0.45F * breeze) * (1 - 0.7F * cave.glowworms()) * under);
	}

	/**
	 * The pitch of an underground bed: the great river lower and slower than a cavern brook, the sea lower than a lake,
	 * the drips brighter off crystal and ice, the cavern wind deeper the taller the vault. 1 for the land's beds.
	 */
	public static float pitch(Bed bed, Hollow cave, AirSample air) {
		return switch (bed) {
			case DRIP_NEAR, DRIP_HALL -> cave.realm() == CaveKinds.CRYSTAL ? 1.18F : cave.realm() == CaveKinds.FROZEN ? 1.1F : 1;
			case UNDER_RIVER -> 1.15F - 0.35F * cave.great();
			case BURIED_SEA -> {
				float sea = cave.sea(), lake = 0.45F * cave.lake();
				yield sea + lake > 0 ? 1.12F - 0.2F * sea / (sea + lake) : 1;
			}
			case CAVERN_WIND -> Math.clamp(1.04F - 0.16F * Noise.smoothstep(16, 80, cave.vault()) + 0.05F * air.wind().gust(), 0.85F, 1.1F);
			default -> 1;
		};
	}

	/** Where an underground bed's source lies from the camera (blocks), as {@code {x, z}}; all around is {@code {0, 0}}. */
	public static float[] offset(Bed bed, Hollow cave) {
		return switch (bed) {
			case UNDER_RIVER -> new float[]{cave.riverX(), cave.riverZ()};
			case BURIED_SEA -> new float[]{cave.seaX(), cave.seaZ()};
			case BATS -> new float[]{cave.batsX(), cave.batsZ()};
			default -> new float[]{0, 0};
		};
	}

	private static void set(float[] out, Bed bed, float strength) {
		out[bed.ordinal()] = Math.clamp(strength, 0, 1) * bed.gain();
	}
}
