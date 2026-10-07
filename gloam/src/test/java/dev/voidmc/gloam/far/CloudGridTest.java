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
		return CloudGrid.build(new CloudGrid.Input(uniform(front), 1, 0.3F, 0, true, 0, 0, 0, 70, 432, 1024, 16384, false));
	}

	private static int towerFaces(CloudGrid.Mesh mesh) {
		return mesh.groups().stream().filter(g -> g.y() > CloudGrid.DECK).mapToInt(CloudGrid.Group::count).sum();
	}

	private static int faces(CloudGrid.Mesh mesh) {
		return mesh.groups().stream().mapToInt(CloudGrid.Group::count).sum();
	}

	@Test
	void clearSkiesHaveFewCloudsAndStormsTower() {
		CloudGrid.Mesh clear = build(0.2F), broken = build(0.55F), storm = build(0.97F);
		System.out.printf("clear %d faces, broken %d faces, storm %d faces (%d in towers)%n", faces(clear), faces(broken), faces(storm), towerFaces(storm));
		assertTrue(faces(clear) < faces(broken));
		assertTrue(towerFaces(storm) > 0);
		assertTrue(towerFaces(clear) == 0);
	}
}
