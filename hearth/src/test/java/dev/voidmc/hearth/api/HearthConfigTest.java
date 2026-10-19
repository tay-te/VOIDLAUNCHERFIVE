package dev.voidmc.hearth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonParser;
import dev.voidmc.hearth.sim.SimConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/** config/hearth/*.json: a good file is read as written; a bad value costs only itself, not the whole file. */
class HearthConfigTest {
	private static SimConfig sim(String json) {
		return HearthConfig.parse("sim", JsonParser.parseString(json), SimConfig.CODEC, SimConfig.DEFAULTS);
	}

	@Test
	void emptyFileIsTheDefaults() {
		assertEquals(SimConfig.DEFAULTS, sim("{}"));
	}

	@Test
	void goodValuesAreRead() {
		SimConfig c = sim("{\"embody\": {\"radius\": 200}, \"clock\": {\"budget_ms\": 4.0}}");
		assertEquals(200, c.embody().radius());
		assertEquals(4.0, c.clock().budgetMs());
		assertEquals(SimConfig.DEFAULTS.embody().hysteresis(), c.embody().hysteresis());
	}

	@Test
	void oneBadValueKeepsTheRest() {
		// budget_ms out of range, radius the wrong type: both fall back, everything else the file sets is kept
		SimConfig c = sim("{\"dimensions\": [\"minecraft:overworld\", \"minecraft:the_nether\"], \"embody\": {\"radius\": \"far\", \"hysteresis\": 40},"
			+ " \"clock\": {\"budget_ms\": -3, \"step_interval\": 600}, \"daughters\": {\"enabled\": false}}");
		assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), c.dimensions());
		assertEquals(SimConfig.DEFAULTS.embody().radius(), c.embody().radius());
		assertEquals(40, c.embody().hysteresis());
		assertEquals(SimConfig.DEFAULTS.clock().budgetMs(), c.clock().budgetMs());
		assertEquals(600, c.clock().stepInterval());
		assertEquals(false, c.daughters().enabled());
	}

	@Test
	void notAnObjectIsTheDefaults() {
		assertSame(SimConfig.DEFAULTS, sim("[1, 2, 3]"));
	}
}
