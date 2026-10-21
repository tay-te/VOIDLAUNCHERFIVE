package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The lives' packets: the People list (docs/LIVING_WORLD_2.md §4.1, F4) for the player's journal. */
public final class LivesPayloads {
	private LivesPayloads() {
	}

	/**
	 * One named character the player has met.
	 *
	 * @param role     the role's id ({@code drunk_smith}); its name is {@code hearth.lives.role.<role>}
	 * @param thread   the thread's id; where the story stands is {@code hearth.lives.story.<thread>.<beat>} (with the given
	 *                 name as its argument)
	 * @param ended    the story has come to an end
	 * @param relation how the player stands with them (their opinion, −100…100)
	 * @param by       whether this player moved the story last
	 * @param detail   the People page's detail as JSON (docs/OVERLAY.md {@code people.rows[]}: id, look, beats, choices, where
	 *                 it may go, relations, find-him windows), "" when none; added 2026-10-21
	 */
	public record Row(String name, String given, String role, String town, String thread, String beat, boolean ended, int relation, boolean by,
		String detail) {
		public Row(String name, String given, String role, String town, String thread, String beat, boolean ended, int relation, boolean by) {
			this(name, given, role, town, thread, beat, ended, relation, by, "");
		}

		static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.of((buf, r) -> {
			buf.writeUtf(r.name);
			buf.writeUtf(r.given);
			buf.writeUtf(r.role);
			buf.writeUtf(r.town);
			buf.writeUtf(r.thread);
			buf.writeUtf(r.beat);
			buf.writeBoolean(r.ended);
			buf.writeVarInt(r.relation);
			buf.writeBoolean(r.by);
			buf.writeUtf(r.detail, 1 << 15);
		}, buf -> new Row(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
			buf.readBoolean(), buf.readUtf(1 << 15)));
	}

	/** The People list: the named characters the player has met, in every town of the world they are in. */
	public record People(List<Row> rows) implements CustomPacketPayload {
		public static final Type<People> TYPE = new Type<>(Hearth.id("lives/people"));
		public static final StreamCodec<RegistryFriendlyByteBuf, People> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.rows.size());
			for (Row r : p.rows) {
				Row.CODEC.encode(buf, r);
			}
		}, buf -> {
			int n = Math.min(256, buf.readVarInt());
			List<Row> rows = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				rows.add(Row.CODEC.decode(buf));
			}
			return new People(rows);
		});

		@Override
		public Type<People> type() {
			return TYPE;
		}
	}

	/**
	 * Follow a named character (C2S, the People page's "Waypoint", 2026-10-21): {@code resident} their id, "" stops.
	 * The same one again stops too.
	 */
	public record Track(String resident) implements CustomPacketPayload {
		public static final Type<Track> TYPE = new Type<>(Hearth.id("lives/track"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Track> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.resident, 64),
			buf -> new Track(buf.readUtf(64)));

		@Override
		public Type<Track> type() {
			return TYPE;
		}
	}

	/**
	 * Where the followed character is (S2C, once a second while it changes): {@code active} false when nothing is followed.
	 * {@code placed}: they stand in the world (to the nearest 8 blocks), else {@code pos} is where their day has them
	 * (their workplace or home).
	 */
	public record Followed(boolean active, String resident, String name, String town, boolean placed, net.minecraft.core.BlockPos pos)
		implements CustomPacketPayload {
		public static final Followed NONE = new Followed(false, "", "", "", false, net.minecraft.core.BlockPos.ZERO);
		public static final Type<Followed> TYPE = new Type<>(Hearth.id("lives/followed"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Followed> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeBoolean(p.active);
			buf.writeUtf(p.resident, 64);
			buf.writeUtf(p.name, 256);
			buf.writeUtf(p.town, 256);
			buf.writeBoolean(p.placed);
			buf.writeBlockPos(p.pos);
		}, buf -> new Followed(buf.readBoolean(), buf.readUtf(64), buf.readUtf(256), buf.readUtf(256), buf.readBoolean(), buf.readBlockPos()));

		@Override
		public Type<Followed> type() {
			return TYPE;
		}
	}
}
