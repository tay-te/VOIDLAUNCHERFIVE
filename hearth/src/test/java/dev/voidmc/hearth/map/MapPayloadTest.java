package dev.voidmc.hearth.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MapPayloadTest {
	@Test
	void roundTrips() {
		MapPayload p = new MapPayload(List.of(
			new MapPayload.Entry(7, "Brookhollow", "village", "temperate", 34, 120, 70, -300, "minecraft:overworld", 40, 25, "friend", true),
			new MapPayload.Entry(9, "Ashford", "town", "steppe", 80, -900, 64, 410, "minecraft:overworld", 72, -10, "", false)));
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		MapPayload.CODEC.encode(buf, p);
		assertEquals(p, MapPayload.CODEC.decode(buf));
	}

	@Test
	void sendsWhatIsWithinRangeLessItsSize() {
		assertTrue(MapModule.near(1000, 0, 0, 0, 0, 1024));
		assertFalse(MapModule.near(1100, 0, 0, 0, 0, 1024));
		assertTrue(MapModule.near(1100, 0, 80, 0, 0, 1024), "its edge is in range");
		assertTrue(MapModule.near(-700, 700, 0, 0, 0, 1024));
		assertFalse(MapModule.near(-800, 800, 0, 0, 0, 1024));
	}
}
