package dev.voidmc.gloam.air;

/**
 * What the land gives the air at one place: the biome's warmth and wetness (vanilla's temperature and downfall, as
 * the biome declares them), how far below the land around it the place lies, and how near open water it is. The
 * callers work these out from the world they have (loaded chunks near the camera, VOID LOD's far tiles further out).
 *
 * @param temperature the biome's base temperature: about -0.5 (frozen peaks) to 2 (desert)
 * @param downfall    the biome's downfall: 0 (dry) to 1 (jungle, swamp)
 * @param lowness     0 on a ridge or open plain, 1 at the bottom of a deep valley or hollow, where cold air pools
 * @param water       0 far from water, 1 over a lake, river or the sea
 */
public record Climate(float temperature, float downfall, float lowness, float water) {
	public static final Climate TEMPERATE = new Climate(0.7F, 0.6F, 0.3F, 0.0F);
}
