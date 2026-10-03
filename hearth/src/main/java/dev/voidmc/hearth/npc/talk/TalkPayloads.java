package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The conversation's packets. The server decides everything; the client shows a name, a line of speech and labelled
 * options, and sends back the id of the option picked.
 */
public final class TalkPayloads {
	private TalkPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(End.TYPE, End.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Markers.TYPE, Markers.CODEC);
	}

	/** An option as shown: its namespaced id and label. */
	public record Option(String id, Component label) {
	}

	/**
	 * Opens the conversation screen ({@code open}) or updates the one open for this folk.
	 *
	 * @param entity     the folk's entity id (the client draws their face from its skin)
	 * @param title      their trade, as a translation key
	 * @param settlement the settlement's name
	 */
	public record Show(int entity, boolean open, String name, String title, String settlement, Component speech, List<Option> options)
		implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(Hearth.id("npc/conversation"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeBoolean(p.open);
			buf.writeUtf(p.name);
			buf.writeUtf(p.title);
			buf.writeUtf(p.settlement);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.speech);
			buf.writeVarInt(p.options.size());
			for (Option o : p.options) {
				buf.writeUtf(o.id);
				ComponentSerialization.STREAM_CODEC.encode(buf, o.label);
			}
		}, buf -> {
			int entity = buf.readVarInt();
			boolean open = buf.readBoolean();
			String name = buf.readUtf();
			String title = buf.readUtf();
			String settlement = buf.readUtf();
			Component speech = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = Math.min(buf.readVarInt(), 64);
			List<Option> options = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				options.add(new Option(buf.readUtf(), ComponentSerialization.STREAM_CODEC.decode(buf)));
			}
			return new Show(entity, open, name, title, settlement, speech, options);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Quest markers over folk as this player should see them (each player's quests differ): only the folk whose marker
	 * changed for them. {@code markers[i]} is the {@code QuestService.Marker} ordinal for entity {@code entities[i]}.
	 */
	public record Markers(int[] entities, byte[] markers) implements CustomPacketPayload {
		public static final Type<Markers> TYPE = new Type<>(Hearth.id("npc/markers"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Markers> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entities.length);
			for (int i = 0; i < p.entities.length; i++) {
				buf.writeVarInt(p.entities[i]);
				buf.writeByte(p.markers[i]);
			}
		}, buf -> {
			int n = Math.min(buf.readVarInt(), 4096);
			int[] e = new int[n];
			byte[] m = new byte[n];
			for (int i = 0; i < n; i++) {
				e[i] = buf.readVarInt();
				m[i] = buf.readByte();
			}
			return new Markers(e, m);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player picked an option. */
	public record Choose(int entity, String option) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(Hearth.id("npc/choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeUtf(p.option, 256);
		}, buf -> new Choose(buf.readVarInt(), buf.readUtf(256)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player closed the conversation. */
	public record End(int entity) implements CustomPacketPayload {
		public static final Type<End> TYPE = new Type<>(Hearth.id("npc/end"));
		public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.entity),
			buf -> new End(buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
