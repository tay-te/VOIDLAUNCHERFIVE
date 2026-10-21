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
	 */
	public record Row(String name, String given, String role, String town, String thread, String beat, boolean ended, int relation, boolean by) {
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
		}, buf -> new Row(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
			buf.readBoolean()));
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
}
