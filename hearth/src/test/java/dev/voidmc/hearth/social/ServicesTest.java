package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.dice.DicePersonality;
import dev.voidmc.hearth.social.service.Inn;
import dev.voidmc.hearth.social.service.WageTask;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicesTest {
	@Test
	void aBedIsKeptUntilTheNextMorning() {
		int nine = 9 * 60;
		// taken in the evening: the next morning at nine
		assertEquals(SocialTime.at(11, nine), Inn.checkout(SocialTime.at(10, 20 * 60), nine));
		// taken in the small hours (still the sim day that began at sunrise): that morning
		assertEquals(SocialTime.at(11, nine), Inn.checkout(SocialTime.at(10, 2 * 60), nine));
		// taken just before checkout: not an hour's sleep, so the morning after
		assertEquals(SocialTime.at(11, nine), Inn.checkout(SocialTime.at(10, 8 * 60), nine));
	}

	@Test
	void wagesPayForWhatWasBroughtIn() {
		assertEquals(10, WageTask.wage(WageTask.Kind.HARVEST, 10, 16, 1, 6));
		assertEquals(12, WageTask.wage(WageTask.Kind.HARVEST, 20, 12, 1, 6), "never more than was harvested");
		assertEquals(6, WageTask.wage(WageTask.Kind.HAUL, 32, 0, 1, 6));
	}

	@Test
	void tempersFollowTraits() {
		assertEquals(DicePersonality.CAREFUL, DicePersonality.of(List.of("cautious", "pious"), 1));
		assertEquals(DicePersonality.RECKLESS, DicePersonality.of(List.of("greedy"), 1));
		assertEquals(DicePersonality.BOLD, DicePersonality.of(List.of("brave"), 1));
		assertEquals(DicePersonality.SHREWD, DicePersonality.of(List.of("curious"), 1));
		assertEquals(DicePersonality.SOUR, DicePersonality.of(List.of("grumpy", "shy"), 1));
	}
}
