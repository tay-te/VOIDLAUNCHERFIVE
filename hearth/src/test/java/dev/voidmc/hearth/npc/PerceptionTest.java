package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.npc.service.Perception;
import org.junit.jupiter.api.Test;

/** Sight cones: the geometry under {@code FolkHandle.canSee}. */
class PerceptionTest {
	private static final double RANGE = 24;
	private static final double FOV = 140;
	private static final double NEAR = 1.5;

	@Test
	void seesWhatIsInFront() {
		// yaw 0 faces +z
		assertTrue(Perception.inSight(0, 0, 0, 0, 10, RANGE, FOV, NEAR));
		// 60 degrees off to the side is within a 140 degree cone
		assertTrue(Perception.inSight(0, 0, Math.sin(Math.toRadians(60)) * 10, 0, Math.cos(Math.toRadians(60)) * 10, RANGE, FOV, NEAR));
	}

	@Test
	void doesNotSeeBehindOrBeyondRange() {
		assertFalse(Perception.inSight(0, 0, 0, 0, -10, RANGE, FOV, NEAR));
		assertFalse(Perception.inSight(0, 0, Math.sin(Math.toRadians(80)) * 10, 0, Math.cos(Math.toRadians(80)) * 10, RANGE, FOV, NEAR));
		assertFalse(Perception.inSight(0, 0, 0, 0, 30, RANGE, FOV, NEAR));
	}

	@Test
	void yawTurnsTheCone() {
		// yaw 90 faces -x, yaw -90 (or 270) faces +x
		assertTrue(Perception.inSight(90, 0, -10, 0, 0, RANGE, FOV, NEAR));
		assertFalse(Perception.inSight(90, 0, 10, 0, 0, RANGE, FOV, NEAR));
		assertTrue(Perception.inSight(270, 0, 10, 0, 0, RANGE, FOV, NEAR));
	}

	@Test
	void notesWhatIsCloseWhateverTheDirection() {
		assertTrue(Perception.inSight(0, 0, 0, 0, -1, RANGE, FOV, NEAR));
		assertFalse(Perception.inSight(0, 0, 0, 0, -2, RANGE, FOV, NEAR));
	}

	@Test
	void pitchLooksUpAndDown() {
		// looking down 60 degrees: a point far overhead is out of view, one at their feet in it
		assertFalse(Perception.inSight(0, 60, 0, 10, 1, RANGE, 90, NEAR));
		assertTrue(Perception.inSight(0, 60, 0, -8, 4, RANGE, 90, NEAR));
	}
}
