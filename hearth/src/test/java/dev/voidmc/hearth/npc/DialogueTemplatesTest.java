package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import dev.voidmc.hearth.npc.talk.Rumours;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class DialogueTemplatesTest {
	private static DialogueTemplates.Context ctx(int minute, String weather, int rep, Trait trait, JobType job) {
		return new DialogueTemplates.Context(minute, weather, rep, trait == null ? EnumSet.noneOf(Trait.class) : EnumSet.of(trait), job,
			Culture.TEMPERATE, AgeStage.ADULT, 0, 0);
	}

	@Test
	void builtInLinesLoadAndCoverEveryCategory() {
		Map<String, List<DialogueTemplates.Line>> all = DialogueTemplates.loadBuiltin();
		for (String cat : new String[]{"greeting", "news.built", "news.stalled", "news.tier", "news.none", "rumour.settlement", "rumour.landmark",
			"rumour.none", "about", "about.job", "passing", "farewell"}) {
			assertTrue(all.containsKey(cat) && !all.get(cat).isEmpty(), "missing " + cat);
		}
		assertTrue(all.get("greeting").size() >= 60);
	}

	@Test
	void everyJobHasSomethingToSayAboutItsWork() {
		RandomSource r = RandomSource.create(1);
		for (JobType job : JobType.values()) {
			assertNotNull(DialogueTemplates.pick("about.job", ctx(600, "clear", 0, null, job), r), job.toString());
		}
	}

	@Test
	void specificLinesWin() {
		Map<String, List<DialogueTemplates.Line>> m = new HashMap<>();
		DialogueTemplates.parseInto(new StringReader("""
			{ "greeting": [ "plain", {"text": "grumpy rain", "traits": ["grumpy"], "weather": "rain"} ] }
			"""), m);
		DialogueTemplates.replace(m);
		RandomSource r = RandomSource.create(5);
		int specific = 0;
		for (int i = 0; i < 200; i++) {
			if ("grumpy rain".equals(DialogueTemplates.pick("greeting", ctx(600, "rain", 0, Trait.GRUMPY, JobType.NONE), r))) {
				specific++;
			}
			assertEquals("plain", DialogueTemplates.pick("greeting", ctx(600, "clear", 0, Trait.GRUMPY, JobType.NONE), r));
		}
		assertTrue(specific > 130, "the fitting specific line should be preferred: " + specific);
		DialogueTemplates.replace(DialogueTemplates.loadBuiltin());
	}

	@Test
	void conditionsFilter() {
		Map<String, List<DialogueTemplates.Line>> m = new HashMap<>();
		DialogueTemplates.parseInto(new StringReader("""
			{ "x": [ {"text": "night only", "time": "night"}, {"text": "friends", "rep": [50, 100]} ] }
			"""), m);
		DialogueTemplates.replace(m);
		RandomSource r = RandomSource.create(2);
		assertEquals("night only", DialogueTemplates.pick("x", ctx(23 * 60, "clear", 0, null, JobType.NONE), r));
		assertEquals("friends", DialogueTemplates.pick("x", ctx(12 * 60, "clear", 80, null, JobType.NONE), r));
		assertEquals(null, DialogueTemplates.pick("x", ctx(12 * 60, "clear", 0, null, JobType.NONE), r));
		DialogueTemplates.replace(DialogueTemplates.loadBuiltin());
	}

	@Test
	void placeholdersFill() {
		assertEquals("Hello, Steve, welcome to Ashby {unknown}.",
			DialogueTemplates.fill("Hello, {player}, welcome to {settlement} {unknown}.", Map.of("player", "Steve", "settlement", "Ashby")));
		assertFalse(DialogueTemplates.fill("{", Map.of()).isEmpty());
	}

	@Test
	void compassDirections() {
		BlockPos o = BlockPos.ZERO;
		assertEquals("north", Rumours.direction(o, new BlockPos(0, 0, -100)));
		assertEquals("south", Rumours.direction(o, new BlockPos(0, 0, 100)));
		assertEquals("east", Rumours.direction(o, new BlockPos(100, 0, 0)));
		assertEquals("west", Rumours.direction(o, new BlockPos(-100, 0, 0)));
		assertEquals("north-east", Rumours.direction(o, new BlockPos(70, 0, -70)));
		assertEquals("south-west", Rumours.direction(o, new BlockPos(-70, 0, 70)));
	}
}
