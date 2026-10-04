package dev.voidmc.hearth.plan.growth;

/**
 * How a building wants to stand toward the land and the town (the {@code SITED} placement): near something (the sawmill
 * by the logging camp, the smelter by the mine, the rectory by the chapel), by the water, downwind (the tannery), at the
 * edge of town, high up, on a main street. Weights are per block (anchor, water, edge, height) or per core radius
 * (downwind); {@code maxDistance} is how far from the anchor a site may be and still count.
 */
public record Siting(int x, int z, double anchor, double water, double downwind, double edge, double height, double main, double maxDistance) {
	/** Near a point, nothing else. */
	public static Siting near(int x, int z, double maxDistance) {
		return new Siting(x, z, 1.5, 0, 0, 0, 0, 0, maxDistance);
	}

	public Siting withWater(double w) {
		return new Siting(this.x, this.z, this.anchor, w, this.downwind, this.edge, this.height, this.main, this.maxDistance);
	}

	public Siting withDownwind(double w) {
		return new Siting(this.x, this.z, this.anchor, this.water, w, this.edge, this.height, this.main, this.maxDistance);
	}

	public Siting withEdge(double w) {
		return new Siting(this.x, this.z, this.anchor, this.water, this.downwind, w, this.height, this.main, this.maxDistance);
	}

	public Siting withHeight(double w) {
		return new Siting(this.x, this.z, this.anchor, this.water, this.downwind, this.edge, w, this.main, this.maxDistance);
	}

	public Siting withMain(double w) {
		return new Siting(this.x, this.z, this.anchor, this.water, this.downwind, this.edge, this.height, w, this.maxDistance);
	}
}
