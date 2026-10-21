package dev.voidmc.hearth.economy.household;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A player's households ({@code hearth:economy/household}, persistent, copied on death; docs/LIVING_WORLD_2.md §8.5): the
 * houses they keep servants in, so the Household page (F2, until designed: the hub state's {@code household} section and
 * the servants' conversation) can list them without searching every town. The town's {@link HouseBook} holds the
 * household itself.
 */
public record HouseholdIndex(List<Entry> houses) {
	public static final HouseholdIndex EMPTY = new HouseholdIndex(List.of());

	public record Entry(long settlementId, ResourceKey<Level> dimension, int plot) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("settlement").forGetter(Entry::settlementId),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
			Codec.INT.fieldOf("plot").forGetter(Entry::plot)
		).apply(i, Entry::new));
	}

	public static final Codec<HouseholdIndex> CODEC = Entry.CODEC.listOf().xmap(HouseholdIndex::new, HouseholdIndex::houses);


	public HouseholdIndex {
		houses = List.copyOf(houses);
	}

	/** The attachment, registered on first use (init, at mod start): records and codecs stay usable in tests without it. */
	public static final class Attachment {
		public static final AttachmentType<HouseholdIndex> TYPE = AttachmentRegistry.create(Hearth.id("economy/household"), b -> b
			.persistent(CODEC)
			.initializer(() -> EMPTY)
			.copyOnDeath());

		private Attachment() {
		}
	}

	public static void init() {
		Hearth.LOG.debug("player attachment {}", Attachment.TYPE.identifier());
	}

	public static HouseholdIndex of(Player player) {
		HouseholdIndex h = player.getAttached(Attachment.TYPE);
		return h == null ? EMPTY : h;
	}

	public static void set(ServerPlayer player, HouseholdIndex index) {
		player.setAttached(Attachment.TYPE, index);
	}

	public HouseholdIndex with(Entry e) {
		List<Entry> l = new ArrayList<>(this.houses);
		l.removeIf(x -> x.settlementId == e.settlementId && x.plot == e.plot);
		l.add(e);
		return new HouseholdIndex(l);
	}

	public HouseholdIndex without(long settlementId, int plot) {
		List<Entry> l = new ArrayList<>(this.houses);
		l.removeIf(x -> x.settlementId == settlementId && x.plot == plot);
		return new HouseholdIndex(l);
	}
}
