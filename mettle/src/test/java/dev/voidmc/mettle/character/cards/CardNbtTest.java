package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

/** The ledger's fast card writer gives exactly the tag the card codec gives, defaults left out the same way. */
class CardNbtTest {
	static {
		TestCards.DECK.toString();
	}

	@Test
	void sameTagAsTheCodec() {
		Random r = new Random(5);
		String[] perks = {"mining/quarryman", "mining/prospector", "mining/tunneler"};
		for (int i = 0; i < 300; i++) {
			CardData c = TestCards.plain(perks[i % 3], Rarity.values()[i % 3], r.nextBoolean() ? -1 : r.nextInt(6));
			CardData.History h = r.nextBoolean() ? c.history() : new CardData.History(r.nextInt(9), List.of());
			for (int k = 0; k < r.nextInt(7); k++) {
				h = h.passedTo(new CardData.Holder(new UUID(r.nextLong(), r.nextLong()), "P" + k, r.nextInt(400)));
			}
			CardData.Place place = switch (i % 5) {
				case 0 -> CardData.Place.BINDER;
				case 1 -> new CardData.Place(CardData.PlaceKind.TRADE, Optional.empty(), 0, r.nextInt(50));
				case 2 -> CardData.Place.RELEASED;
				default -> c.place();
			};
			List<CardData.TraitRoll> traits = r.nextBoolean() ? List.of() : List.of(new CardData.TraitRoll(TestCards.id("mining/deepborn"), r.nextDouble() * 20));
			CardData x = new CardData(c.id(), c.skill(), c.perk(), c.rarity(), r.nextInt(3) + 1, r.nextBoolean() ? c.keywords() : List.of(), traits,
				r.nextInt(3) == 0 ? 0 : c.needs(), new CardData.Drawn(new UUID(r.nextLong(), 3), "Ash", r.nextInt(20), r.nextInt(100), r.nextLong(),
				CardData.How.values()[i % CardData.How.values().length]), new UUID(1, r.nextInt(3)), h, r.nextInt(3), r.nextInt(3) == 0 ? 0 : r.nextLong(),
				place, r.nextBoolean() ? 0 : r.nextInt(99999));
			assertEquals(CardData.CODEC.encodeStart(NbtOps.INSTANCE, x).getOrThrow(), CardNbt.write(x), "card " + i);
		}
	}
}
