package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.look.SkinComposer;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Station and office: how folk are dressed, named and addressed for their place in the town (LIVING_WORLD.md §8.1). */
class StationAndOfficeTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void theSyncedLookCarriesEveryJobStationAndOffice() {
		for (JobType job : JobType.values()) {
			for (Station st : Station.values()) {
				for (Office of : Office.values()) {
					int packed = Folk.packLook(Culture.MESA, job, AgeStage.ELDER, true, st, of);
					FolkLook look = Folk.unpackLook(1234, packed);
					assertEquals(job, look.job());
					assertEquals(st, look.station());
					assertEquals(of, look.office());
					assertEquals(Culture.MESA, look.culture());
					assertEquals(AgeStage.ELDER, look.age());
					assertTrue(look.female());
				}
			}
		}
	}

	@Test
	void stationChangesTheClothesNotThePerson() {
		RandomSource r = RandomSource.create(9);
		int differing = 0;
		for (int i = 0; i < 40; i++) {
			int g = Genome.random(Culture.TEMPERATE, r);
			FolkLook peasant = new FolkLook(g, Culture.TEMPERATE, JobType.NONE, AgeStage.ADULT, i % 2 == 0, Station.PEASANT, Office.NONE);
			FolkLook poor = new FolkLook(g, Culture.TEMPERATE, JobType.NONE, AgeStage.ADULT, i % 2 == 0, Station.POOR, Office.NONE);
			FolkLook lord = new FolkLook(g, Culture.TEMPERATE, JobType.LORD, AgeStage.ADULT, i % 2 == 0, Station.LORD, Office.NONE);
			assertEquals(peasant.baseKey(), poor.baseKey());
			assertNotEquals(peasant.key(), poor.key());
			int[] a = SkinComposer.compose(peasant);
			int[] b = SkinComposer.compose(poor);
			int[] c = SkinComposer.compose(lord);
			// the face is the same person whatever their station
			for (int y = 8; y < 16; y++) {
				for (int x = 8; x < 16; x++) {
					assertEquals(a[y * 64 + x], b[y * 64 + x], "face pixel " + x + "," + y);
				}
			}
			if (!java.util.Arrays.equals(a, b) && !java.util.Arrays.equals(a, c)) {
				differing++;
			}
		}
		assertEquals(40, differing, "the poor and the lord should never dress like a peasant");
	}

	@Test
	void everyTradeAndStationComposes() {
		RandomSource r = RandomSource.create(3);
		for (JobType job : JobType.values()) {
			for (Station st : Station.values()) {
				for (Culture culture : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.WISTERIA, Culture.TUNDRA}) {
					int[] px = SkinComposer.compose(new FolkLook(Genome.random(culture, r), culture, job, AgeStage.ADULT, r.nextBoolean(), st,
						job == JobType.GUARD ? Office.CAPTAIN : Office.NONE));
					assertEquals(64 * 64, px.length);
					// the base layer of the head and body is always opaque
					assertEquals(0xFF, px[12 * 64 + 12] >>> 24, job + " " + st);
					assertEquals(0xFF, px[24 * 64 + 22] >>> 24, job + " " + st);
				}
			}
		}
	}

	@Test
	void theSeniorOfATradeHoldsItsOffice() {
		SimpleSettlement s = new SimpleSettlement(1, "Wardholt", Culture.TEMPERATE, BlockPos.ZERO, 0);
		Resident lone = guard(-300, 1);
		s.addResident(lone);
		assertNull(Office.holder(s, JobType.GUARD), "a lone guard is just the guard");
		Resident veteran = guard(-900, 2);
		Resident young = guard(-100, 3);
		s.addResident(veteran);
		s.addResident(young);
		assertEquals(veteran.id, Office.holder(s, JobType.GUARD));
		assertEquals(Office.CAPTAIN, Office.of(s, veteran));
		assertEquals(Office.NONE, Office.of(s, young));
		s.removeResident(veteran.id);
		assertEquals(lone.id, Office.holder(s, JobType.GUARD), "the next in line takes it up");
	}

	private static Resident guard(long born, long id) {
		return new Resident(new UUID(0, id), "G" + id, "Ward", false, AgeStage.ADULT, born, JobType.GUARD, Resident.NO_PLOT, Resident.NO_PLOT, 0,
			new CompoundTag());
	}

	@Test
	void linesCanBeForAStation() {
		Map<String, List<DialogueTemplates.Line>> m = new HashMap<>();
		DialogueTemplates.parseInto(new StringReader("""
			{ "x": [ "plain", {"text": "poor", "stations": ["poor"]}, {"text": "lordly", "stations": ["lord"]} ] }
			"""), m);
		DialogueTemplates.replace(m);
		try {
			RandomSource r = RandomSource.create(4);
			int poor = 0;
			for (int i = 0; i < 200; i++) {
				String line = DialogueTemplates.pick("x", new DialogueTemplates.Context(600, "clear", 0,
					EnumSet.noneOf(dev.voidmc.hearth.npc.person.Trait.class), JobType.HAULER, Culture.TEMPERATE, AgeStage.ADULT, 0, 0, Station.POOR), r);
				assertNotEquals("lordly", line, "a line for another station never fits");
				if ("poor".equals(line)) {
					poor++;
				}
			}
			assertTrue(poor > 60, "the station's own line comes up often: " + poor);
		} finally {
			DialogueTemplates.replace(DialogueTemplates.loadBuiltin());
		}
	}
}
