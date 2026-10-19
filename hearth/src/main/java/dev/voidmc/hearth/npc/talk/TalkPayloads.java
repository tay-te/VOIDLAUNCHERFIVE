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
import org.jspecify.annotations.Nullable;

/**
 * The conversation's packets. The server decides everything; the client shows a name, a line of speech and labelled
 * options, and sends back the id of the option picked.
 */
public final class TalkPayloads {
	private TalkPayloads() {
	}

	/** The most options one conversation shows, and markers one packet carries (both ends agree). */
	public static final int MAX_OPTIONS = 64;
	public static final int MAX_MARKERS = 4096;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(End.TYPE, End.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Markers.TYPE, Markers.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Detail.TYPE, Detail.CODEC);
	}

	/**
	 * What an option is, for screens that show more than its label and hint (docs/OVERLAY.md {@code talk.options[]}).
	 *
	 * @param kind       {@code DialogueProvider.Info#kind}: talk, trade, quest, persuade, bribe, train, leave...
	 * @param odds       a check's chance 0–100, or -1
	 * @param cost       bits it costs, or -1
	 * @param skill      the skill it reads ({@code mettle:speech}), or empty
	 * @param skillLevel the player's level in that skill as the server's skills mod says (0 without one), or -1 without a skill
	 */
	public record OptionInfo(String id, String kind, int odds, long cost, String skill, int skillLevel) {
		static final StreamCodec<RegistryFriendlyByteBuf, OptionInfo> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeUtf(o.id, 256);
			buf.writeUtf(o.kind, 64);
			buf.writeVarInt(o.odds);
			buf.writeVarLong(o.cost);
			buf.writeUtf(o.skill, 256);
			buf.writeVarInt(o.skillLevel);
		}, buf -> new OptionInfo(buf.readUtf(256), buf.readUtf(64), buf.readVarInt(), buf.readVarLong(), buf.readUtf(256), buf.readVarInt()));
	}

	/**
	 * The resident card and option facts that go with a {@link Show} (sent right after it, for the same entity): what the
	 * richer conversation screens draw beside the speech. Added 2026-10-19 (docs/OVERLAY.md); the parchment screen reads
	 * none of it.
	 *
	 * @param job          their trade as a translation key (as {@link Show#title})
	 * @param place        where they work ("Tavern"), or empty
	 * @param traits       their personality traits' serialized names ({@code sociable}...)
	 * @param mood         their mood, −100…100
	 * @param opinion      what they think of the player, −100…100
	 * @param memoryKind   the player's strongest memory with them (a deed kind), or empty
	 * @param memoryWeight its weight (negative: a bad one)
	 * @param standing     the player's standing in the town, −100…100
	 * @param title        the player's title there ({@code known}...)
	 * @param nextTitle    the next title up, or empty
	 * @param nextAt       the standing it takes
	 * @param minute       the minute of the day (0–1439) where they talk
	 * @param speech       the player's Speech level (0 without a skills mod)
	 */
	public record Detail(int entity, String job, String place, List<String> traits, int mood, int opinion, String memoryKind, int memoryWeight,
		int standing, String title, String nextTitle, int nextAt, int minute, int speech, List<OptionInfo> options) implements CustomPacketPayload {
		public static final Type<Detail> TYPE = new Type<>(Hearth.id("npc/conversation_detail"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Detail> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeUtf(p.job, 256);
			buf.writeUtf(p.place, 256);
			int traits = Math.min(p.traits.size(), 16);
			buf.writeVarInt(traits);
			for (String t : p.traits.subList(0, traits)) {
				buf.writeUtf(t, 64);
			}
			buf.writeVarInt(p.mood);
			buf.writeVarInt(p.opinion);
			buf.writeUtf(p.memoryKind, 256);
			buf.writeVarInt(p.memoryWeight);
			buf.writeVarInt(p.standing);
			buf.writeUtf(p.title, 64);
			buf.writeUtf(p.nextTitle, 64);
			buf.writeVarInt(p.nextAt);
			buf.writeVarInt(p.minute);
			buf.writeVarInt(p.speech);
			int count = Math.min(p.options.size(), MAX_OPTIONS);
			buf.writeVarInt(count);
			for (OptionInfo o : p.options.subList(0, count)) {
				OptionInfo.CODEC.encode(buf, o);
			}
		}, buf -> {
			int entity = buf.readVarInt();
			String job = buf.readUtf(256);
			String place = buf.readUtf(256);
			int nt = Math.min(buf.readVarInt(), 16);
			List<String> traits = new ArrayList<>(nt);
			for (int i = 0; i < nt; i++) {
				traits.add(buf.readUtf(64));
			}
			int mood = buf.readVarInt();
			int opinion = buf.readVarInt();
			String memoryKind = buf.readUtf(256);
			int memoryWeight = buf.readVarInt();
			int standing = buf.readVarInt();
			String title = buf.readUtf(64);
			String nextTitle = buf.readUtf(64);
			int nextAt = buf.readVarInt();
			int minute = buf.readVarInt();
			int speech = buf.readVarInt();
			int n = Math.min(buf.readVarInt(), MAX_OPTIONS);
			List<OptionInfo> options = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				options.add(OptionInfo.CODEC.decode(buf));
			}
			return new Detail(entity, job, place, traits, mood, opinion, memoryKind, memoryWeight, standing, title, nextTitle, nextAt, minute, speech,
				options);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		/** The facts for an option id, or null. */
		public @Nullable OptionInfo info(String id) {
			for (OptionInfo o : this.options) {
				if (o.id.equals(id)) {
					return o;
				}
			}
			return null;
		}
	}

	private static void writeOptional(RegistryFriendlyByteBuf buf, @Nullable Component c) {
		buf.writeBoolean(c != null);
		if (c != null) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
	}

	private static @Nullable Component readOptional(RegistryFriendlyByteBuf buf) {
		return buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null;
	}

	/**
	 * An option as shown: its namespaced id and label; locked ones are drawn dim and cannot be picked; a hint is drawn at
	 * the right ("Persuade · 65 %") and a tooltip shown on hover ({@code DialogueProvider.Option}).
	 */
	public record Option(String id, Component label, boolean locked, @Nullable Component hint, @Nullable Component tooltip) {
		public Option(String id, Component label) {
			this(id, label, false, null, null);
		}
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
			// as many as the reader takes (a longer list would leave bytes unread and drop the connection)
			int count = Math.min(p.options.size(), MAX_OPTIONS);
			buf.writeVarInt(count);
			for (Option o : p.options.subList(0, count)) {
				buf.writeUtf(o.id);
				ComponentSerialization.STREAM_CODEC.encode(buf, o.label);
				buf.writeBoolean(o.locked);
				writeOptional(buf, o.hint);
				writeOptional(buf, o.tooltip);
			}
		}, buf -> {
			int entity = buf.readVarInt();
			boolean open = buf.readBoolean();
			String name = buf.readUtf();
			String title = buf.readUtf();
			String settlement = buf.readUtf();
			Component speech = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = Math.min(buf.readVarInt(), MAX_OPTIONS);
			List<Option> options = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				String id = buf.readUtf();
				Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
				boolean locked = buf.readBoolean();
				Component hint = readOptional(buf);
				Component tooltip = readOptional(buf);
				options.add(new Option(id, label, locked, hint, tooltip));
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
			int count = Math.min(p.entities.length, MAX_MARKERS);
			buf.writeVarInt(count);
			for (int i = 0; i < count; i++) {
				buf.writeVarInt(p.entities[i]);
				buf.writeByte(p.markers[i]);
			}
		}, buf -> {
			int n = Math.min(buf.readVarInt(), MAX_MARKERS);
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

	/** The server ended the conversation (goodbye, the folk walked off or is gone): the client closes its screen. */
	public record Close(int entity) implements CustomPacketPayload {
		public static final Type<Close> TYPE = new Type<>(Hearth.id("npc/close"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.entity),
			buf -> new Close(buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
