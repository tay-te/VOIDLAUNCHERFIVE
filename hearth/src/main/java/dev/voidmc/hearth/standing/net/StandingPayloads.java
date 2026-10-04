package dev.voidmc.hearth.standing.net;

import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The standing module's packets. The server decides everything; the client shows a player's standing where they are,
 * the reputation page, and a line on the conversation and trade screens.
 */
public final class StandingPayloads {
	private StandingPayloads() {
	}

	/**
	 * The player's standing in the town they are in (or {@code id == NONE}: in no town). Sent when they walk in
	 * ({@code arrived}: show the line under the arrival banner) and whenever their standing there changes.
	 *
	 * @param title      the title's serialized name
	 * @param renown     −1 infamous, 0 unknown or mixed, 1 renowned (around here)
	 */
	public record Here(long id, String town, int standing, String title, int fame, int renown, boolean arrived) implements CustomPacketPayload {
		public static final long NONE = Long.MIN_VALUE;
		public static final Here LEFT = new Here(NONE, "", 0, "stranger", 0, 0, false);
		public static final Type<Here> TYPE = new Type<>(Hearth.id("standing/here"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Here> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeLong(p.id);
			buf.writeUtf(p.town);
			buf.writeVarInt(p.standing);
			buf.writeUtf(p.title);
			buf.writeVarInt(p.fame);
			buf.writeByte(p.renown);
			buf.writeBoolean(p.arrived);
		}, buf -> new Here(buf.readLong(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readByte(), buf.readBoolean()));

		public boolean none() {
			return this.id == NONE;
		}

		@Override
		public Type<Here> type() {
			return TYPE;
		}
	}

	/**
	 * Who the player is talking to, for the line on the conversation screen: their opinion of the player, and the outcome
	 * of the last speech check with them (empty if none).
	 */
	public record Talk(int entity, String resident, int opinion, Component result) implements CustomPacketPayload {
		public static final Type<Talk> TYPE = new Type<>(Hearth.id("standing/talk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Talk> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeUtf(p.resident);
			buf.writeVarInt(p.opinion);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.result);
		}, buf -> new Talk(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf)));

		@Override
		public Type<Talk> type() {
			return TYPE;
		}
	}

	/** A town on the reputation page. {@code distance} −1 when in another dimension. */
	public record TownRow(long id, String name, String culture, String tier, int standing, String title, boolean owner, int distance, boolean here) {
	}

	/**
	 * The reputation page.
	 *
	 * @param notes    what folk notice about the player's looks (keys under {@code hearth.standing.look.})
	 * @param members  the company's members' names, founder first
	 */
	public record Page(List<TownRow> towns, int fame, int renown, float charisma, float menace, List<String> notes, String company,
		List<String> members, boolean companies) implements CustomPacketPayload {
		public static final Type<Page> TYPE = new Type<>(Hearth.id("standing/page"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.towns.size());
			for (TownRow t : p.towns) {
				buf.writeLong(t.id);
				buf.writeUtf(t.name);
				buf.writeUtf(t.culture);
				buf.writeUtf(t.tier);
				buf.writeVarInt(t.standing);
				buf.writeUtf(t.title);
				buf.writeBoolean(t.owner);
				buf.writeVarInt(t.distance);
				buf.writeBoolean(t.here);
			}
			buf.writeVarInt(p.fame);
			buf.writeByte(p.renown);
			buf.writeFloat(p.charisma);
			buf.writeFloat(p.menace);
			writeStrings(buf, p.notes);
			buf.writeUtf(p.company);
			writeStrings(buf, p.members);
			buf.writeBoolean(p.companies);
		}, buf -> {
			int n = Math.min(buf.readVarInt(), 1024);
			List<TownRow> towns = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				towns.add(new TownRow(buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
					buf.readVarInt(), buf.readBoolean()));
			}
			return new Page(towns, buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readFloat(), readStrings(buf), buf.readUtf(), readStrings(buf),
				buf.readBoolean());
		});

		@Override
		public Type<Page> type() {
			return TYPE;
		}
	}

	/** The client asks for the reputation page (the key, or the page's refresh). */
	public record RequestPage() implements CustomPacketPayload {
		public static final RequestPage INSTANCE = new RequestPage();
		public static final Type<RequestPage> TYPE = new Type<>(Hearth.id("standing/request_page"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestPage> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<RequestPage> type() {
			return TYPE;
		}
	}

	private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		for (String s : list) {
			buf.writeUtf(s);
		}
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 256);
		List<String> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(buf.readUtf());
		}
		return out;
	}
}
