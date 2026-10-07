package dev.voidmc.gloam.far;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CloudGridTest {
	private static WeatherMap.Picture uniform(float front) {
		float[] fronts = new float[WeatherMap.SIZE * WeatherMap.SIZE];
		Arrays.fill(fronts, front);
		double half = WeatherMap.SIZE * WeatherMap.TEXEL / 2.0;
		return new WeatherMap.Picture(-half, -half, fronts, 0, 1);
	}

	private static CloudGrid.Mesh build(float front) {
		return CloudGrid.build(new CloudGrid.Input(uniform(front), 1, 0.3F, 0, true, 0, 0, 0, 0, 0, 70, CloudGrid.Layers.of(192.33F, 639, -1), null, 1024, 16384, false));
	}

	private static int towerFaces(CloudGrid.Mesh mesh) {
		return mesh.groups().stream().filter(g -> g.y() > CloudGrid.DECK).mapToInt(CloudGrid.Group::count).sum();
	}

	private static int faces(CloudGrid.Mesh mesh) {
		return mesh.groups().stream().mapToInt(CloudGrid.Group::count).sum();
	}

	@Test
	void layersFollowTheWorldsHeight() {
		CloudGrid.Layers vanilla = CloudGrid.Layers.of(192.33F, 319, -1), tall = CloudGrid.Layers.of(192.33F, 639, -1);
		System.out.printf("vanilla %s, tall %s, now %s%n", vanilla, tall, CloudGrid.Layers.of(192.33F, 383, -1));
		assertTrue(vanilla.low() < 0 && vanilla.main() == 192);
		assertTrue(tall.low() > 192 && tall.main() > 350 && tall.high() >= 610);
	}

	@Test
	void clearSkiesHaveFewCloudsAndStormsTower() {
		CloudGrid.Mesh clear = build(0.2F), broken = build(0.55F), storm = build(0.97F);
		System.out.printf("clear %d faces, broken %d faces, storm %d faces (%d in towers)%n", faces(clear), faces(broken), faces(storm), towerFaces(storm));
		assertTrue(faces(clear) < faces(broken));
		assertTrue(towerFaces(storm) > 0);
		assertTrue(towerFaces(storm) > 2 * towerFaces(clear));
	}
}
