package dev.voidmc.hearth.standing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.standing.net.StandingPayloads;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class StandingPayloadsTest {
	private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		codec.encode(buf, value);
		return codec.decode(buf);
	}

	@Test
	void packetsRoundTrip() {
		StandingTestKit.boot();
		StandingPayloads.Here here = new StandingPayloads.Here(42, "Ashbrook", -17, "distrusted", 33, -1, true);
		assertEquals(here, roundTrip(StandingPayloads.Here.CODEC, here));
		assertEquals(StandingPayloads.Here.LEFT, roundTrip(StandingPayloads.Here.CODEC, StandingPayloads.Here.LEFT));

		StandingPayloads.Page page = new StandingPayloads.Page(List.of(
			new StandingPayloads.TownRow(1, "Ashbrook", "temperate", "village", 45, "friend", true, 0, true),
			new StandingPayloads.TownRow(2, "Coldbrook", "northern", "hamlet", -3, "stranger", false, -1, false)),
			12, 1, 0.25F, 0.5F, List.of("clean", "armed"), "Wolves", List.of("Alex", "Bea"), true);
		assertEquals(page, roundTrip(StandingPayloads.Page.CODEC, page));

		StandingPayloads.Talk talk = new StandingPayloads.Talk(7, "Edda Miller", 18, Component.literal("[Flatter 64%] Success"));
		StandingPayloads.Talk back = roundTrip(StandingPayloads.Talk.CODEC, talk);
		assertEquals(talk.opinion(), back.opinion());
		assertEquals(talk.result().getString(), back.result().getString());
	}
}
