package dev.voidmc.hearth.quest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PosterTracksTest {
	@Test
	void theQuarryIsPlacedToTheGridNotPinpointed() {
		assertEquals(new BlockPos(16, 64, 48), PosterTracks.snap(new BlockPos(3, 70, 40)));
		assertEquals(new BlockPos(16, 64, 48), PosterTracks.snap(new BlockPos(31, 64, 63)));
		assertEquals(new BlockPos(-16, -8, -16), PosterTracks.snap(new BlockPos(-1, -1, -32)));
	}
}
