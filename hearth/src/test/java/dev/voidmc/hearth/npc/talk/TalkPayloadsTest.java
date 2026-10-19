package dev.voidmc.hearth.npc.talk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/** The conversation's packets read back exactly what was written, however long the list of options. */
class TalkPayloadsTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static RegistryFriendlyByteBuf buf() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	@Test
	void aLongListOfOptionsIsCappedOnBothEnds() {
		List<TalkPayloads.Option> options = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			options.add(new TalkPayloads.Option("test:o" + i, Component.literal("Option " + i), i % 3 == 0, i % 2 == 0 ? Component.literal("hint") : null, null));
		}
		TalkPayloads.Show show = new TalkPayloads.Show(42, true, "Hester Thatcher", "hearth.npc.job.baker", "Ashby Green", Component.literal("Good day."), options);
		RegistryFriendlyByteBuf b = buf();
		TalkPayloads.Show.CODEC.encode(b, show);
		TalkPayloads.Show back = TalkPayloads.Show.CODEC.decode(b);
		// nothing left unread: a client that left bytes would drop the connection
		assertEquals(0, b.readableBytes());
		assertEquals(TalkPayloads.MAX_OPTIONS, back.options().size());
		assertEquals("test:o0", back.options().get(0).id());
		assertEquals("Hester Thatcher", back.name());
	}

	@Test
	void closeAndMarkersRoundTrip() {
		RegistryFriendlyByteBuf b = buf();
		TalkPayloads.Close.CODEC.encode(b, new TalkPayloads.Close(7));
		assertEquals(7, TalkPayloads.Close.CODEC.decode(b).entity());
		assertEquals(0, b.readableBytes());
		RegistryFriendlyByteBuf m = buf();
		TalkPayloads.Markers.CODEC.encode(m, new TalkPayloads.Markers(new int[]{1, 2, 3}, new byte[]{0, 1, 2}));
		TalkPayloads.Markers back = TalkPayloads.Markers.CODEC.decode(m);
		assertEquals(3, back.entities().length);
		assertEquals(2, back.markers()[2]);
		assertEquals(0, m.readableBytes());
	}
}
