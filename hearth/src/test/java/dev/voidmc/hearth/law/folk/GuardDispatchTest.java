package dev.voidmc.hearth.law.folk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.voidmc.hearth.law.folk.GuardDispatch.Option;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who the watch sends after a reported player. */
class GuardDispatchTest {
	private static final double INF = Double.POSITIVE_INFINITY;

	private static UUID id(int n) {
		return new UUID(0, n);
	}

	@Test
	void theNearestAwakeGuardOnDutyGoes() {
		List<Option> os = List.of(new Option(id(1), true, true, true, 50), new Option(id(2), true, true, true, 20), new Option(id(3), false, false, true, INF));
		assertEquals(id(2), GuardDispatch.choose(os, 64).id());
	}

	@Test
	void sleepersAreLeftAbedAndOneOnDutyIsMadeToAppear() {
		// run 4 of the playthrough: the only guard about slept 40 blocks away
		List<Option> os = List.of(new Option(id(1), true, false, false, 40), new Option(id(2), false, false, true, INF),
			new Option(id(3), false, false, false, INF));
		assertEquals(id(2), GuardDispatch.choose(os, 64).id());
	}

	@Test
	void aFarGuardOnDutyIsPassedOverForOneThatCanAppearNear() {
		List<Option> os = List.of(new Option(id(1), true, true, true, 90), new Option(id(2), false, false, true, INF));
		assertEquals(id(2), GuardDispatch.choose(os, 64).id());
		// with nobody else on duty, the far one still comes
		assertEquals(id(1), GuardDispatch.choose(List.of(new Option(id(1), true, true, true, 90), new Option(id(2), false, false, false, INF)), 64).id());
	}

	@Test
	void anAwakeGuardOffDutyNearByComesWhenNobodyOnDutyCan() {
		List<Option> os = List.of(new Option(id(1), true, true, false, 30), new Option(id(2), true, true, false, 100), new Option(id(3), false, false, false, INF));
		assertEquals(id(1), GuardDispatch.choose(os, 64).id());
		// far off duty, or abed: nobody, and the bounty is posted instead
		assertNull(GuardDispatch.choose(List.of(new Option(id(2), true, true, false, 100), new Option(id(4), true, false, true, 10)), 64));
	}
}
