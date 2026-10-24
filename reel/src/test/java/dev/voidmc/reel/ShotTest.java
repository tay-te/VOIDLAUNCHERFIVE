package dev.voidmc.reel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.ArcLength;
import dev.voidmc.reel.math.Monotone;
import dev.voidmc.reel.math.Ramps;
import dev.voidmc.reel.math.Spline;
import dev.voidmc.reel.math.Vec3;
import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotException;
import dev.voidmc.reel.shot.ShotParser;
import dev.voidmc.reel.shot.ShotReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotTest {
	private static final double EPS = 1e-6;

	@Test
	void splinePassesThroughEveryKey() {
		List<Vec3> points = List.of(new Vec3(0, 0, 0), new Vec3(10, 2, 0), new Vec3(12, 2, 30), new Vec3(-5, 8, 31));
		Spline spline = new Spline(points);
		for (int i = 0; i < points.size(); i++) {
			assertEquals(0, spline.at(i).distance(points.get(i)), 1e-9, "key " + i);
		}
	}

	@Test
	void evenTimingKeepsSpeedSteady() {
		// keys bunched at one end: timed by spline parameter this would crawl then race
		Shot shot = ShotParser.parse("""
			{ "duration": 10, "camera": { "ease": "linear", "face": "path",
			  "keys": [ {"pos": [0,64,0]}, {"pos": [1,64,0]}, {"pos": [2,64,0]}, {"pos": [100,64,0]} ] } }
			""", "steady");
		double first = shot.camera(1).pos().distance(shot.camera(0).pos());
		double middle = shot.camera(5).pos().distance(shot.camera(4).pos());
		assertEquals(first, middle, first * 0.03, "the camera covers the same distance each second");
	}

	@Test
	void keyedTimingPassesEachKeyAtItsTime() {
		Shot shot = ShotParser.parse("""
			{ "camera": { "keys": [
			  {"pos": [0,64,0], "t": 0, "yaw": 0, "pitch": 0},
			  {"pos": [10,64,0], "t": 2, "yaw": 90, "pitch": 10},
			  {"pos": [10,64,40], "t": 7, "yaw": 180, "pitch": 0} ] } }
			""", "keyed");
		assertEquals(7, shot.duration(), EPS);
		assertEquals(0, shot.camera(2).pos().distance(new Vec3(10, 64, 0)), 1e-6);
		assertEquals(90, shot.camera(2).yaw(), 1e-6);
		assertEquals(10, shot.camera(2).pitch(), 1e-6);
	}

	@Test
	void monotoneNeverOvershoots() {
		Monotone m = new Monotone(new double[] {0, 1, 2, 3}, new double[] {0, 10, 10, 0});
		for (double x = 0; x <= 3; x += 0.01) {
			double y = m.at(x);
			assertTrue(y >= -EPS && y <= 10 + EPS, "y=" + y + " at " + x);
		}
	}

	@Test
	void yawTurnsTheShortWay() {
		Shot shot = ShotParser.parse("""
			{ "duration": 4, "camera": { "ease": "linear", "keys": [
			  {"pos": [0,64,0], "yaw": 170, "pitch": 0}, {"pos": [0,64,0], "yaw": -170, "pitch": 0} ] } }
			""", "wrap");
		double mid = Angles.wrap(shot.camera(2).yaw());
		assertEquals(180, Math.abs(mid), 0.5, "through 180, not through 0");
	}

	@Test
	void lookPointIsCentred() {
		Shot shot = ShotParser.parse("""
			{ "duration": 3, "camera": { "look": [100, 64, 0], "keys": [ {"pos": [0,64,0]}, {"pos": [0,64,50]} ] } }
			""", "look");
		for (double t = 0; t <= 3; t += 0.5) {
			CameraState s = shot.camera(t);
			Vec3 dir = Angles.direction(s.yaw(), s.pitch());
			Vec3 toTarget = new Vec3(100, 64, 0).sub(s.pos());
			toTarget = toTarget.scale(1 / toTarget.length());
			assertEquals(0, dir.distance(toTarget), 1e-6, "at " + t);
		}
	}

	@Test
	void minecraftAngles() {
		Vec3 south = Angles.direction(0, 0);
		assertEquals(0, south.distance(new Vec3(0, 0, 1)), EPS);
		Vec3 west = Angles.direction(90, 0);
		assertEquals(0, west.distance(new Vec3(-1, 0, 0)), EPS);
		assertEquals(90, Angles.yawTo(Vec3.ZERO, new Vec3(-5, 0, 0)), EPS);
		assertEquals(90, Angles.pitchTo(Vec3.ZERO, new Vec3(0, -5, 0)), EPS, "looking straight down is pitch 90");
		assertEquals(37.85, Angles.lensToFov(35), 0.01);
	}

	@Test
	void orbitKeepsItsCentreInView() {
		Shot shot = ShotParser.parse("""
			{ "duration": 8, "camera": { "orbit": { "center": [0, 70, 0], "radius": 50, "from": 0, "sweep": 180 } } }
			""", "orbit");
		CameraState start = shot.camera(0);
		assertEquals(0, start.yaw(), 1e-6, "faces south at the start");
		assertEquals(-50, start.pos().z(), 1e-6, "so it stands north of the centre");
		CameraState end = shot.camera(8);
		assertEquals(180, Math.abs(Angles.wrap(end.yaw())), 1e-6);
		assertEquals(50, Math.hypot(end.pos().x(), end.pos().z()), 1e-6);
	}

	@Test
	void exposureIsCentredOnTheFrame() {
		Shot shot = ShotParser.parse("""
			{ "fps": 50, "duration": 2, "camera": { "keys": [ {"pos": [0,64,0], "yaw": 0} ] }, "render": { "samples": 4, "shutter": 180 } }
			""", "blur");
		double[] times = shot.exposure(10);
		double centre = 0;
		for (double t : times) {
			centre += t;
		}
		assertEquals(0.2, centre / times.length, 1e-9);
		assertEquals(0.01 * 3 / 4, times[3] - times[0], 1e-9, "spread over 180 degrees of a 1/50 s frame");
	}

	@Test
	void errorsNameTheField() {
		ShotException e = assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "keys": [ {"pos": [0,64,0], "yaw": 0}, {"pos": [1, 2]} ] } }
			""", "bad"));
		assertEquals("camera.keys[1].pos", e.field());
		ShotException typo = assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "keys": [ {"pos": [0,64,0], "yaw": 0, "ptich": 4} ] } }
			""", "typo"));
		assertEquals("camera.keys[0].ptich", typo.field());
		assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "keys": [ {"pos": [0,64,0]} ] } }
			""", "blind"), "a camera with nowhere to look");
	}

	@Test
	void reportFlagsFastPans() {
		Shot shot = ShotParser.parse("""
			{ "duration": 2, "camera": { "ease": "linear", "keys": [
			  {"pos": [0,64,0], "yaw": 0, "pitch": 0}, {"pos": [0,64,0], "yaw": 180, "pitch": 0} ] } }
			""", "whip");
		ShotReport report = ShotReport.of(shot);
		assertTrue(report.warnings().stream().anyMatch(w -> w.startsWith("pan of")), "90 deg/s judders without blur: " + report.text());
		Shot whip = ShotParser.parse("""
			{ "duration": 1, "camera": { "ease": "linear", "keys": [
			  {"pos": [0,64,0], "yaw": 0, "pitch": 0}, {"pos": [0,64,0], "yaw": 170, "pitch": 0} ] } }
			""", "whip");
		assertTrue(ShotReport.of(whip).warnings().stream().anyMatch(w -> w.startsWith("whip pan")), ShotReport.of(whip).text());
	}

	@Test
	void arcLengthInverts() {
		Spline spline = new Spline(List.of(new Vec3(0, 0, 0), new Vec3(30, 0, 0), new Vec3(30, 0, 30)));
		ArcLength arc = new ArcLength(spline);
		for (double u = 0; u <= 2; u += 0.125) {
			assertEquals(u, arc.parameterAt(arc.distanceAt(u)), 1e-3);
		}
	}

	@Test
	void examplesParse() throws Exception {
		for (String name : List.of("example", "orbit-example", "paced-example")) {
			Shot shot = ShotParser.read(java.nio.file.Path.of(System.getProperty("reel.shots")).resolve(name + ".json"));
			assertTrue(shot.frames() > 0);
		}
	}

	@Test
	void rampsCoverHalfTheirLength() {
		assertEquals(10 - 1.5, Ramps.run(10, 10, 1, 2), 1e-9, "a ramp covers what half its time at full speed would");
		assertEquals(10, Ramps.run(10, 10, 0, 0), 1e-9);
		assertEquals(0, Ramps.factor(0, 10, 1, 2), 1e-9, "from rest");
		assertEquals(1, Ramps.factor(0, 10, 0, 2), 1e-9, "already moving");
		double step = 1e-4;
		for (double t = step; t < 10; t += 0.25) {
			double slope = (Ramps.run(t + step, 10, 1, 2) - Ramps.run(t - step, 10, 1, 2)) / (2 * step);
			assertEquals(Ramps.factor(t, 10, 1, 2), slope, 1e-3, "run is the integral of factor, at " + t);
		}
	}

	@Test
	void pacedSpeedsSetTheDuration() {
		Shot still = ShotParser.parse("""
			{ "camera": { "face": "path", "motion": { "in": 0, "out": 0 },
			  "keys": [ {"pos": [0,64,0], "speed": 10}, {"pos": [30,64,0]}, {"pos": [60,64,0]} ] } }
			""", "derived");
		assertEquals(6, still.duration(), 1e-3, "60 blocks at 10 b/s");
		ShotReport report = ShotReport.of(still);
		assertEquals(10, report.entrySpeed(), 0.1, "cut in moving: " + report.text());
		assertEquals(10, report.exitSpeed(), 0.1, "cut out moving");

		Shot ramped = ShotParser.parse("""
			{ "camera": { "face": "path", "motion": { "in": 1, "out": 1 },
			  "keys": [ {"pos": [0,64,0], "speed": 10}, {"pos": [60,64,0]} ] } }
			""", "ramped");
		assertEquals(7, ramped.duration(), 1e-3, "each ramp adds half its length");
		assertTrue(ShotReport.of(ramped).entrySpeed() < 0.5, "starts from rest");
		assertEquals(0, ramped.camera(7).pos().distance(new Vec3(60, 64, 0)), 1e-6);
	}

	@Test
	void pacedSpeedsAreScaledToFitTheDuration() {
		// 5 b/s rising to 15 b/s over 120 blocks takes 12 ln 3 = 13.18 s; asked for 12 s, every speed runs 9.9% faster
		Shot shot = ShotParser.parse("""
			{ "duration": 12, "camera": { "face": "path", "motion": { "in": 0, "out": 0 },
			  "keys": [ {"pos": [0,64,0], "speed": 5}, {"pos": [120,64,0], "speed": 15} ] } }
			""", "fit");
		double scale = 12 * Math.log(3) / 12;
		ShotReport report = ShotReport.of(shot);
		assertEquals(5 * scale, report.entrySpeed(), 0.05, report.text());
		assertEquals(15 * scale, report.exitSpeed(), 0.1, report.text());
		assertEquals(0, shot.camera(12).pos().distance(new Vec3(120, 64, 0)), 1e-6);
		assertEquals(0, shot.camera(6).pos().y() - 64, 1e-9);
		assertTrue(shot.camera(6).pos().x() < 60, "slower at first, so short of halfway at half time");
	}

	@Test
	void pacedHoldsRestAtTheirKey() {
		Shot shot = ShotParser.parse("""
			{ "camera": { "face": "path", "motion": { "in": 0, "out": 0, "settle": 1 },
			  "keys": [ {"pos": [0,64,0], "speed": 10}, {"pos": [30,64,0], "hold": 2}, {"pos": [60,64,0]} ] } }
			""", "hold");
		// 0-30: 3 s plus half a 1 s settle; the hold; 30-60 the same
		assertEquals(9, shot.duration(), 1e-3);
		for (double t = 3.55; t <= 5.45; t += 0.1) {
			assertEquals(0, shot.camera(t).pos().distance(new Vec3(30, 64, 0)), 1e-3, "resting at " + t);
		}
		assertTrue(shot.camera(3.4).pos().x() < 30 && shot.camera(5.6).pos().x() > 30);
		assertEquals(0, shot.camera(9).pos().distance(new Vec3(60, 64, 0)), 1e-6);
	}

	@Test
	void pacedOrbitEntersMidTurn() {
		Shot shot = ShotParser.parse("""
			{ "duration": 10, "camera": { "motion": { "in": 0, "out": 2 },
			  "orbit": { "center": [0, 70, 0], "radius": 50, "from": 0, "sweep": 90 } } }
			""", "orbit-in");
		ShotReport report = ShotReport.of(shot);
		assertEquals(90 / 9.0, report.entryTurn(), 0.1, "steady turn from the first frame: " + report.text());
		assertTrue(report.exitTurn() < 0.2, "settles at the end");
		assertEquals(90, Angles.wrap(shot.camera(10).yaw()), 1e-6);
	}

	@Test
	void pacingErrorsNameTheField() {
		assertEquals("camera.ease", assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "ease": "linear", "face": "path", "motion": {},
			  "keys": [ {"pos": [0,64,0]}, {"pos": [9,64,0]} ] } }
			""", "both")).field());
		assertEquals("camera.keys[0].speed", assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "face": "path", "keys": [ {"pos": [0,64,0], "speed": 0}, {"pos": [9,64,0]} ] } }
			""", "stop")).field());
		assertEquals("camera", assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 1.5, "camera": { "face": "path", "motion": { "in": 1, "out": 1 },
			  "keys": [ {"pos": [0,64,0]}, {"pos": [9,64,0]} ] } }
			""", "short")).field(), "ramps longer than the shot");
		assertEquals("camera", assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "duration": 3, "camera": { "keys": [ {"pos": [0,64,0], "yaw": 0, "speed": 3}, {"pos": [0,64,0], "yaw": 90} ] } }
			""", "pan")).field(), "a camera that stays put has no speed");
		assertEquals("camera.keys", assertThrows(ShotException.class, () -> ShotParser.parse("""
			{ "camera": { "face": "path", "motion": {}, "keys": [ {"pos": [0,64,0], "t": 0}, {"pos": [9,64,0], "t": 2} ] } }
			""", "timed")).field());
	}

	@Test
	void evenShotsAreUnchanged() {
		// the old timing still eases the whole move: at rest at both ends
		Shot shot = ShotParser.parse("""
			{ "duration": 10, "camera": { "face": "path", "keys": [ {"pos": [0,64,0]}, {"pos": [100,64,0]} ] } }
			""", "even");
		ShotReport report = ShotReport.of(shot);
		assertTrue(report.entrySpeed() < 0.5 && report.exitSpeed() < 0.5, report.text());
		assertEquals(50, shot.camera(5).pos().x(), 1e-3);
	}
}
