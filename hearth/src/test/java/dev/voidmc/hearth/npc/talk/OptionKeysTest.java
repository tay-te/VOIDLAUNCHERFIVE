package dev.voidmc.hearth.npc.talk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptionKeysTest {
	@Test
	void keysCountOneToNineThenGoodbyeIsZero() {
		List<String> ids = List.of("npc:news", "economy:trade", "quest:offer/1", "mettle:train/speech", NpcDialogue.BYE);
		assertArrayEquals(new int[] {1, 2, 3, 4, 0}, OptionKeys.assign(ids, id -> null));
	}

	@Test
	void aTrainerWithManyLessonsGetsUniqueKeysAndTheRestStayKeyless() {
		List<String> ids = new ArrayList<>(List.of("npc:news", "npc:rumour", "economy:trade", "quest:offer/7"));
		for (String skill : List.of("blades", "archery", "speech")) {
			ids.add("mettle:train/" + skill);
			ids.add("mettle:teach/" + skill);
			ids.add("mettle:rebuild/" + skill);
		}
		ids.add(NpcDialogue.BYE);
		int[] keys = OptionKeys.assign(ids, id -> id.startsWith("mettle:") ? "train" : "talk");
		assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, 0}, keys);
	}

	@Test
	void onlyOneGoodbyeEvenWhenTwoOptionsReadAsLeave() {
		// a provider's own "leave him be" (order 10 000 reads as leave) is numbered, not a second key 0
		List<String> ids = List.of("npc:news", "law:leave", NpcDialogue.BYE);
		Map<String, String> kinds = Map.of("law:leave", "leave", NpcDialogue.BYE, "leave");
		assertArrayEquals(new int[] {1, 2, 0}, OptionKeys.assign(ids, kinds::get));
	}

	@Test
	void withoutTheFolksGoodbyeTheFirstLeaveIsZero() {
		List<String> ids = List.of("law:pay", "law:run", "law:walk_away");
		assertArrayEquals(new int[] {1, 2, 0}, OptionKeys.assign(ids, id -> id.equals("law:walk_away") ? "leave" : null));
		assertArrayEquals(new int[] {1, 2, 3}, OptionKeys.assign(ids, id -> null));
	}
}
