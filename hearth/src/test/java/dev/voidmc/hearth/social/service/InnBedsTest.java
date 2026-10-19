package dev.voidmc.hearth.social.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.social.SocialLedger;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Every bed in an inn is a guest bed: any bed not let tonight can be rented. */
class InnBedsTest {
	private static Poi bed(int x) {
		return new Poi(PoiType.BED, new BlockPos(x, 64, 0), Direction.NORTH, null);
	}

	@Test
	void anyBedNotLetIsFree() {
		List<Poi> beds = List.of(bed(2), bed(1));
		SocialLedger ledger = new SocialLedger();
		UUID me = new UUID(1, 1);
		UUID other = new UUID(2, 2);
		assertEquals(new BlockPos(1, 64, 0), Inn.freeBed(beds, ledger, me, p -> true), "two beds, nobody staying: the first is free");
		ledger.beds.add(new SocialLedger.Rental(new BlockPos(1, 64, 0).asLong(), other, 100));
		assertEquals(new BlockPos(2, 64, 0), Inn.freeBed(beds, ledger, me, p -> true), "one let to another guest: the other");
		ledger.beds.add(new SocialLedger.Rental(new BlockPos(2, 64, 0).asLong(), me, 100));
		assertEquals(new BlockPos(2, 64, 0), Inn.freeBed(beds, ledger, me, p -> true), "my own bed is still mine");
		assertNull(Inn.freeBed(beds, ledger, new UUID(3, 3), p -> true), "both let: full");
		assertNull(Inn.freeBed(List.of(bed(5)), new SocialLedger(), me, p -> false), "a bed broken out is no bed");
	}
}
