package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.math.MeterSet;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Status build-up tuning from data (the user's call on open question 4, 2026-10-21). */
class StatusTuningTest {
	private static JsonObject resource(String path) throws Exception {
		try (var in = StatusTuningTest.class.getResourceAsStream(path)) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void shippedDefaultsAreTheCodeDefaults() throws Exception {
		StatusTuning.Values d = StatusTuning.merge(StatusTuning.CODE_DEFAULTS, List.of(resource("/data/fray/fray/status_tuning/defaults.json")));
		StatusTuning.Values c = StatusTuning.CODE_DEFAULTS;
		assertEquals(c.baseThreshold(), d.baseThreshold());
		assertArrayEquals(c.rankThreshold(), d.rankThreshold());
		assertEquals(c.drainDelayTicks(), d.drainDelayTicks());
		assertEquals(c.drainPerTick(), d.drainPerTick(), 1e-9);
		for (DamageKind k : DamageKind.VALUES) {
			assertEquals(c.buildFactor(k), d.buildFactor(k), k.id());
		}
		assertEquals(100, d.baseThreshold());
		assertEquals(4, d.rank(Rank.BOSS));
		assertEquals(MeterSet.DRAIN_PER_TICK, d.drainPerTick(), 1e-9);
		assertEquals(1.5F, d.buildFactor(DamageKind.SLASH));
		assertEquals(3.0F, d.buildFactor(DamageKind.FIRE), "unlisted kinds take '*'");
	}

	@Test
	void laterFilesOverrideKeyByKey() {
		JsonObject a = json("{'rank_threshold':{'elite':2},'creatures':{'minecraft:blaze':{'burn':3,'chill':-0.5}}}");
		JsonObject b = json("{'build_factor':{'slash':2},'creatures':{'minecraft:blaze':{'burn':5}}}");
		StatusTuning.Values v = StatusTuning.merge(StatusTuning.CODE_DEFAULTS, List.of(a, b));
		assertEquals(2, v.rank(Rank.ELITE));
		assertEquals(4, v.rank(Rank.BOSS), "untouched keys keep the defaults");
		assertEquals(2, v.buildFactor(DamageKind.SLASH));
		Identifier blaze = Identifier.parse("minecraft:blaze");
		assertEquals(5, v.creatureResist(blaze, StatusType.BURN), "the later file wins");
		assertEquals(-0.5F, v.creatureResist(blaze, StatusType.CHILL), "and keeps the earlier file's other entries");
		assertEquals(0, v.creatureResist(Identifier.parse("minecraft:zombie"), StatusType.BURN));
		assertEquals(0, v.creatureResist(blaze, StatusType.STAGGER), "statuses without a meter have no resistance");
		assertEquals(600, MeterSet.threshold(v.baseThreshold(), v.creatureResist(blaze, StatusType.BURN)), 1e-3);
	}

	@Test
	void rejectsStatusesWithoutAMeter() {
		JsonObject bad = json("{'creatures':{'minecraft:pig':{'stagger':1}}}");
		assertThrows(IllegalArgumentException.class, () -> StatusTuning.merge(StatusTuning.CODE_DEFAULTS, List.of(bad)));
	}

	@Test
	void bestiaryCreatureTableParses() throws Exception {
		JsonObject o = json(Files.readString(Path.of("../src/main/resources/data/bestiary/fray/status_tuning/creatures.json")));
		StatusTuning.Values v = StatusTuning.merge(StatusTuning.CODE_DEFAULTS, List.of(o));
		assertEquals(9, v.creatureResist(Identifier.parse("minecraft:blaze"), StatusType.BURN));
		assertEquals(100, v.baseThreshold(), "Bestiary's file only sets creatures");
	}
}
