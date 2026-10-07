package dev.voidmc.hearth.map;

import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The settlements a player knows of, for a map on their client (VOID Atlas): the whole list each time, sent only when it
 * changed (a settlement sighted or discovered, its tier, population or the player's standing there moved). Only sent to
 * clients that listen for it (Hearth's client registers the receiver only when Atlas is installed).
 */
public record MapPayload(List<Entry> settlements) implements CustomPacketPayload {
	/** At most this many settlements are sent (the nearest). */
	public static final int MAX = 512;
	public static final Type<MapPayload> TYPE = new Type<>(Hearth.id("map/settlements"));
	public static final StreamCodec<FriendlyByteBuf, MapPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.settlements.size());
		for (Entry e : p.settlements) {
			e.write(buf);
		}
	}, buf -> {
		int n = Math.min(buf.readVarInt(), MAX);
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(Entry.read(buf));
		}
		return new MapPayload(list);
	});

	public MapPayload {
		settlements = List.copyOf(settlements);
	}

	@Override
	public Type<MapPayload> type() {
		return TYPE;
	}

	/**
	 * One settlement. A settlement only sighted carries its place and size, not its name or anything of the player's
	 * standing there (those come once they have been).
	 *
	 * @param tier      the tier's serialized name (camp, hamlet, village, town, city)
	 * @param culture   the culture's serialized name, or empty while only sighted
	 * @param dimension the dimension's id, like {@code minecraft:overworld}
	 * @param radius    roughly how far it reaches from its centre, in blocks
	 * @param standing  the player's standing there (-100 to 100)
	 * @param title     the player's title there, serialized ({@code friend}), or empty while only sighted
	 * @param owner     whether the player owns property there
	 */
	public record Entry(long id, boolean discovered, String name, String tier, String culture, int population, int x, int y, int z,
		String dimension, int radius, int standing, String title, boolean owner) {
		void write(FriendlyByteBuf buf) {
			buf.writeVarLong(this.id);
			buf.writeBoolean(this.discovered);
			buf.writeUtf(this.name, 128);
			buf.writeUtf(this.tier, 32);
			buf.writeUtf(this.culture, 64);
			buf.writeVarInt(this.population);
			buf.writeVarInt(this.x);
			buf.writeVarInt(this.y);
			buf.writeVarInt(this.z);
			buf.writeUtf(this.dimension, 128);
			buf.writeVarInt(this.radius);
			buf.writeVarInt(this.standing);
			buf.writeUtf(this.title, 32);
			buf.writeBoolean(this.owner);
		}

		static Entry read(FriendlyByteBuf buf) {
			return new Entry(buf.readVarLong(), buf.readBoolean(), buf.readUtf(128), buf.readUtf(32), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readBoolean());
		}
	}
}
