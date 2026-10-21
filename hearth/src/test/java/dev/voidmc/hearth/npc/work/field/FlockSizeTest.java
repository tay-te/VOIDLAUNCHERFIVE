package dev.voidmc.hearth.npc.work.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.Culture;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Stocked pastures (docs/LIVING_WORLD_2.md §6.2, §6.8): the flock's size from the books, its kinds, its losses. */
class FlockSizeTest {
	@BeforeAll
	static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test
	void aFlockIsItsHerdersTimesTheirHerdCappedByTheGroundAndThePens() {
		// two herders keep 16; a 16x16 pasture holds 21; no pens drawn: the ground alone caps it
		assertEquals(16, Flocks.size(2, 8, 16 * 16, 0, 0));
		// a small paddock: 8x8 = 64 square blocks, five beasts
		assertEquals(5, Flocks.size(2, 8, 8 * 8, 0, 0));
		// one pen keeps eight
		assertEquals(8, Flocks.size(3, 8, 40 * 40, 1, 0));
		// no herders, no flock; no herd per herder, no flock
		assertEquals(0, Flocks.size(0, 8, 400, 0, 0));
		assertEquals(0, Flocks.size(2, 0, 400, 0, 0));
	}

	@Test
	void lossesComeOffTheFlockAndAreMadeGoodADay() {
		assertEquals(13, Flocks.size(2, 8, 16 * 16, 0, 3));
		assertEquals(0, Flocks.size(1, 8, 16 * 16, 0, 50), "never below none");
		Flocks.Book book = new Flocks.Book();
		book.lost(4);
		book.lost(4);
		book.lost(4);
		book.lost(9);
		assertEquals(3, book.losses(4));
		assertFalse(book.heal(100, 1), "the first look only sets the day");
		assertFalse(book.heal(100, 1), "nothing more the same day");
		assertTrue(book.heal(102, 1), "two days later");
		assertEquals(1, book.losses(4));
		assertEquals(0, book.losses(9), "made good and forgotten");
		assertTrue(book.heal(110, 1));
		assertTrue(book.isEmpty());
	}

	@Test
	void theBooksRoundTrip() {
		Flocks.Book book = new Flocks.Book();
		book.lost(12);
		book.lost(12);
		book.heal(40, 1);
		var json = Flocks.Book.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		Flocks.Book back = Flocks.Book.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, back.losses(12));
		assertFalse(back.heal(40, 1), "the healed day kept");
	}

	@Test
	void everyCultureKeepsBeastsAndTheMainKindIsMostOfTheFlock() {
		for (Culture c : Culture.values()) {
			List<Flocks.Species> kinds = Flocks.species(c);
			assertFalse(kinds.isEmpty(), c + " keeps beasts");
			int main = 0;
			for (int i = 0; i < 24; i++) {
				if (Flocks.speciesOf(c, i) == kinds.get(0)) {
					main++;
				}
			}
			assertTrue(main >= 18, c + ": three in four the main kind, got " + main + " of 24");
		}
		assertEquals(Flocks.Species.SHEEP, Flocks.species(Culture.TEMPERATE).get(0), "Bohemia's flocks are sheep");
		assertEquals(Flocks.Species.COW, Flocks.species(Culture.ALPINE).get(0), "the alpine drove is cattle");
	}
}
