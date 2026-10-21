package dev.voidmc.mettle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.StandingEvents;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mettle's key strings are Hearth's (the {@code src/hearthApi} copy of PlayerSkills is verbatim from Hearth ae4da055):
 * every {@code PlayerSkills.BONUS_*} has its twin in {@link HearthKeys} and nothing else is there; Hearth's clamp helper
 * keeps NaN, zero and a throwing partner at 0 as Mettle relies on; and Hearth's check-event difficulty matches.
 */
class HearthKeysTest {
	@Test
	void theKeysAreHearths() throws IllegalAccessException {
		List<String> hearth = new ArrayList<>();
		for (Field f : PlayerSkills.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getName().startsWith("BONUS_")) {
				hearth.add((String) f.get(null));
			}
		}
		assertEquals(11, hearth.size(), "Hearth's eleven BONUS_* constants");
		assertEquals(PlayerSkills.BONUS_HAGGLE_BAND, HearthKeys.HAGGLE_BAND);
		assertEquals(PlayerSkills.BONUS_HAGGLE_PATIENCE, HearthKeys.HAGGLE_PATIENCE);
		assertEquals(PlayerSkills.BONUS_HAGGLE_NEVER_SOUR, HearthKeys.HAGGLE_NEVER_SOUR);
		assertEquals(PlayerSkills.BONUS_SPEECH, HearthKeys.SPEECH);
		assertEquals(PlayerSkills.BONUS_SPEECH_RETRY, HearthKeys.SPEECH_RETRY);
		assertEquals(PlayerSkills.BONUS_STANDING_GAIN, HearthKeys.STANDING_GAIN);
		assertEquals(PlayerSkills.BONUS_STEALTH_SIGHT, HearthKeys.STEALTH_SIGHT);
		assertEquals(PlayerSkills.BONUS_STEALTH_UNSEEN_BEYOND, HearthKeys.STEALTH_UNSEEN_BEYOND);
		assertEquals(PlayerSkills.BONUS_STEALTH_GIVE_UP, HearthKeys.STEALTH_GIVE_UP);
		assertEquals(PlayerSkills.BONUS_PICKPOCKET_AWARENESS, HearthKeys.PICKPOCKET_AWARENESS);
		assertEquals(PlayerSkills.BONUS_GREET_HERO, HearthKeys.GREET_HERO);
		for (String k : hearth) {
			assertTrue(k.equals(PlayerSkills.BONUS_SPEECH) ? HearthKeys.SPEECH_KINDS.stream().allMatch(kind -> HearthKeys.ALL.contains(k + kind))
				: HearthKeys.ALL.contains(k), k + " is in HearthKeys.ALL");
		}
	}

	@Test
	void hearthsClampKeepsBrokenAnswersAtZero() {
		PlayerSkills nan = new Stub(Double.NaN);
		PlayerSkills big = new Stub(99);
		PlayerSkills boom = new Stub(0) {
			@Override
			public double bonus(net.minecraft.server.level.ServerPlayer player, String key) {
				throw new IllegalStateException("broken");
			}
		};
		assertEquals(0, PlayerSkills.bonus(nan, null, HearthKeys.HAGGLE_BAND, 0, 10));
		assertEquals(10, PlayerSkills.bonus(big, null, HearthKeys.HAGGLE_BAND, 0, 10));
		assertEquals(0, PlayerSkills.bonus(boom, null, HearthKeys.HAGGLE_BAND, 0, 10));
		assertEquals(0, PlayerSkills.NONE.bonus(null, HearthKeys.GREET_HERO));
		assertEquals(10, StandingEvents.difficulty(PlayerSkills.odds(0, 3, 10)), "the event's difficulty is the plain check's turned round");
	}

	private static class Stub implements PlayerSkills {
		private final double value;

		Stub(double value) {
			this.value = value;
		}

		@Override
		public double bonus(net.minecraft.server.level.ServerPlayer player, String key) {
			return this.value;
		}

		@Override
		public int level(net.minecraft.server.level.ServerPlayer player, String skill) {
			return 0;
		}

		@Override
		public int attribute(net.minecraft.server.level.ServerPlayer player, String attribute) {
			return 3;
		}

		@Override
		public boolean hasPerk(net.minecraft.server.level.ServerPlayer player, net.minecraft.resources.Identifier perk) {
			return false;
		}

		@Override
		public Check check(net.minecraft.server.level.ServerPlayer player, String skill, int difficulty) {
			return new Check(0.5, 0.5);
		}

		@Override
		public void award(net.minecraft.server.level.ServerPlayer player, String skill, float xp, String reason) {
		}
	}
}
