package dev.voidmc.gloam.sound;

/**
 * What the land around the camera holds, as the soundscape hears it: read by {@link Land} every second and a half
 * from a few hundred blocks, eased between reads. The fractions are 0..1. The direction fields are where a sound's
 * source lies, as an offset from the camera in blocks (up to 8 long; shorter when it is all around).
 *
 * @param canopy   leaves overhead
 * @param trees    how wooded the land within ~30 blocks is
 * @param grass    how much of the ground around is grass, soil and moss
 * @param sand     how much is sand and gravel
 * @param snow     how much is under snow
 * @param still    how near and how much still water there is (a pond, a lake, a slow river)
 * @param running  how near running water is (flowing water, a narrow river above the sea)
 * @param shore    how much the camera stands at the edge of open water, where waves come in
 * @param height   the camera's height above sea level, in blocks
 * @param exposure how far the camera stands above the land around it (0 level with it, 1 on a summit)
 * @param roof     a roof close overhead (a building, an overhang), 0..1
 * @param buried   how deep under the ground the camera is (1 well inside a cave), where no outdoor sound reaches
 */
public record Surroundings(float canopy, float trees, float grass, float sand, float snow, float still, float running, float shore,
	float height, float exposure, float roof, float buried, float stillX, float stillZ, float runningX, float runningZ, float shoreX, float shoreZ) {
	/** Open grassland at sea level, nothing else: what the soundscape assumes before its first read. */
	public static final Surroundings OPEN = new Surroundings(0, 0, 1, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	/** These surroundings eased a fraction {@code t} of the way toward {@code to}. */
	public Surroundings toward(Surroundings to, float t) {
		return new Surroundings(lerp(this.canopy, to.canopy, t), lerp(this.trees, to.trees, t), lerp(this.grass, to.grass, t),
			lerp(this.sand, to.sand, t), lerp(this.snow, to.snow, t), lerp(this.still, to.still, t), lerp(this.running, to.running, t),
			lerp(this.shore, to.shore, t), lerp(this.height, to.height, t), lerp(this.exposure, to.exposure, t), lerp(this.roof, to.roof, t),
			lerp(this.buried, to.buried, t), lerp(this.stillX, to.stillX, t), lerp(this.stillZ, to.stillZ, t), lerp(this.runningX, to.runningX, t),
			lerp(this.runningZ, to.runningZ, t), lerp(this.shoreX, to.shoreX, t), lerp(this.shoreZ, to.shoreZ, t));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
