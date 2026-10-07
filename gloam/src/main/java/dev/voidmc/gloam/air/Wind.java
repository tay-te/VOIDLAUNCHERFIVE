package dev.voidmc.gloam.air;

/**
 * The wind at a moment, at ground level in the open: {@code x} and {@code z} are the direction it blows toward (a unit
 * vector), {@code speed} its steady strength in blocks per second, and {@code gust} how much a gust adds right now
 * (0 calm .. 1 the strongest gusts).
 */
public record Wind(float x, float z, float speed, float gust) {
	public static final Wind CALM = new Wind(1, 0, 0, 0);

	/** The speed with the gust in it, blocks per second. */
	public float now() {
		return this.speed * (1 + this.gust * 0.8F);
	}
}
