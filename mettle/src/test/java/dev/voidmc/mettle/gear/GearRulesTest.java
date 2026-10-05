package dev.voidmc.mettle.gear;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.GearSlot;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import org.junit.jupiter.api.Test;

class GearRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static GearRules.MoveContext ctx(GearSlot gear, boolean free, boolean vanilla, boolean quiver, int q, boolean back, int b) {
		return new GearRules.MoveContext(gear, free, vanilla, quiver, q, back, b, false);
	}

	@Test
	void layoutIsFixed() {
		assertEquals(46, GearRules.wornIndex(GearSlot.BACK));
		assertEquals(48, GearRules.wornIndex(GearSlot.QUIVER));
		assertEquals(GearRules.BACK_POCKETS + 27, GearRules.BELT_POCKETS);
		assertEquals(GearRules.BELT_POCKETS + 6, GearRules.QUIVER_POCKETS);
		assertEquals(GearRules.QUIVER_POCKETS + 4, GearRules.END);
		assertEquals(GearSlot.BACK, GearRules.pocketGroup(49));
		assertEquals(GearSlot.BACK, GearRules.pocketGroup(75));
		assertEquals(GearSlot.BELT, GearRules.pocketGroup(76));
		assertEquals(GearSlot.QUIVER, GearRules.pocketGroup(85));
		assertNull(GearRules.pocketGroup(48));
		assertNull(GearRules.pocketGroup(86));
	}

	@Test
	void gearGoesToItsSlot() {
		GearRules.Route route = GearRules.quickMove(20, ctx(GearSlot.BELT, true, false, false, 0, false, 18));
		assertArrayEquals(new int[] {47, 48}, route.ranges().getFirst());
		// Slot taken: a backpack cannot go in a backpack, so vanilla handles it.
		assertNull(GearRules.quickMove(20, ctx(GearSlot.BACK, false, false, false, 0, false, 18)));
	}

	@Test
	void arrowsToQuiverThenPack() {
		GearRules.Route route = GearRules.quickMove(40, ctx(null, false, false, true, 4, true, 9));
		assertEquals(2, route.ranges().size());
		assertArrayEquals(new int[] {82, 86}, route.ranges().get(0));
		assertArrayEquals(new int[] {49, 58}, route.ranges().get(1));
		assertTrue(route.thenVanilla());
	}

	@Test
	void packFirstForEverythingElse() {
		GearRules.Route route = GearRules.quickMove(12, ctx(null, false, false, false, 2, true, 18));
		assertEquals(1, route.ranges().size());
		assertArrayEquals(new int[] {49, 67}, route.ranges().getFirst());
		// No pack: vanilla's main ↔ hotbar move.
		assertNull(GearRules.quickMove(12, ctx(null, false, false, false, 0, false, 0)));
	}

	@Test
	void armourStillEquipsFirst() {
		assertNull(GearRules.quickMove(30, ctx(null, false, true, false, 0, true, 27)));
	}

	@Test
	void pocketsAndWornGoBackToTheInventory() {
		for (int index : new int[] {46, 48, 49, 80, 85}) {
			GearRules.Route route = GearRules.quickMove(index, ctx(null, false, false, true, 4, true, 27));
			assertArrayEquals(new int[] {9, 45}, route.ranges().getFirst());
			assertFalse(route.thenVanilla());
		}
	}

	@Test
	void craftingAndArmourSlotsStayVanilla() {
		for (int index : new int[] {0, 1, 4, 5, 8}) {
			assertNull(GearRules.quickMove(index, ctx(null, false, false, true, 4, true, 27)));
		}
	}

	@Test
	void creativeNeverFillsHiddenPockets() {
		GearRules.MoveContext creative = new GearRules.MoveContext(null, false, false, true, 4, true, 27, true);
		assertNull(GearRules.quickMove(20, creative));
		GearRules.MoveContext creativeGear = new GearRules.MoveContext(GearSlot.QUIVER, true, false, false, 0, false, 0, true);
		assertArrayEquals(new int[] {48, 49}, GearRules.quickMove(20, creativeGear).ranges().getFirst());
	}

	@Test
	void whatPocketsTake() {
		assertFalse(GearRules.fits(GearSlot.BACK, true, false, false, 0.8f), "no gear inside gear");
		assertTrue(GearRules.fits(GearSlot.BACK, false, false, false, 4f), "a pack takes anything");
		assertTrue(GearRules.fits(GearSlot.BELT, false, false, false, 0.35f), "a potion fits a belt");
		assertFalse(GearRules.fits(GearSlot.BELT, false, false, false, 2f), "a pickaxe does not");
		assertTrue(GearRules.fits(GearSlot.QUIVER, false, true, true, 0.02f));
		assertFalse(GearRules.fits(GearSlot.QUIVER, false, true, false, 0.02f), "quivers take only arrows");
	}

	@Test
	void capacityAddsUp() {
		GearSpec frame = new GearSpec(GearSlot.BACK, 25f, 27, Optional.empty());
		GearSpec toolBelt = new GearSpec(GearSlot.BELT, 5f, 6, Optional.empty());
		GearSpec quiver = new GearSpec(GearSlot.QUIVER, 0f, 4, Optional.of(ItemTags.ARROWS));
		assertEquals(30f, GearRules.capacity(Arrays.asList(frame, toolBelt, quiver)));
		assertEquals(5f, GearRules.capacity(Arrays.asList(null, toolBelt, null)));
		// BALANCE.md §5.1: a fresh Smith (Strength 6) with the kit belt carries 40 + 6 × 3 + 3 = 61 kg.
		assertEquals(61f, 40f + 6 * 3f + GearRules.capacity(Arrays.asList(null, new GearSpec(GearSlot.BELT, 3f, 4, Optional.empty()), null)));
	}

	@Test
	void pocketCountIsCappedBySlot() {
		assertEquals(6, new GearSpec(GearSlot.BELT, 0f, 20, Optional.empty()).pocketCount());
		assertEquals(4, new GearSpec(GearSlot.QUIVER, 0f, 9, Optional.empty()).pocketCount());
		assertEquals(27, new GearSpec(GearSlot.BACK, 0f, 27, Optional.empty()).pocketCount());
	}

	@Test
	void specCodecReadsData() {
		GearSpec spec = GearSpec.CODEC.parse(JsonOps.INSTANCE,
			JsonParser.parseString("{\"slot\":\"quiver\",\"pockets\":3,\"accepts\":\"#minecraft:arrows\"}")).getOrThrow();
		assertEquals(GearSlot.QUIVER, spec.slot());
		assertEquals(0f, spec.capacityKg());
		assertEquals(3, spec.pockets());
		assertEquals(ItemTags.ARROWS, spec.accepts().orElseThrow());
		assertTrue(GearSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"slot\":\"head\"}")).isError());
	}
}
