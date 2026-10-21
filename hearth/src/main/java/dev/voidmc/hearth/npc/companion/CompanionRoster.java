package dev.voidmc.hearth.npc.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Who a player has hired ({@code hearth:npc/companions}, persistent, copied on death): their companions' home towns and
 * ids, so they can be found and brought back at login. The resident's own {@code extra("companion")} is the truth about
 * the hire; this is the player's index of it.
 */
public record CompanionRoster(List<Entry> entries) {
	public static final CompanionRoster EMPTY = new CompanionRoster(List.of());

	/**
	 * @param settlementId their home town
	 * @param dimension    the home town's dimension
	 */
	public record Entry(long settlementId, ResourceKey<Level> dimension, UUID resident) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("settlement").forGetter(Entry::settlementId),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
			UUIDUtil.STRING_CODEC.fieldOf("resident").forGetter(Entry::resident)
		).apply(i, Entry::new));
	}

	public static final Codec<CompanionRoster> CODEC = Entry.CODEC.listOf().xmap(CompanionRoster::new, CompanionRoster::entries);


	public CompanionRoster {
		entries = List.copyOf(entries);
	}

	/** The attachment, registered on first use (init, at mod start): records and codecs stay usable in tests without it. */
	public static final class Attachment {
		public static final AttachmentType<CompanionRoster> TYPE = AttachmentRegistry.create(Hearth.id("npc/companions"), b -> b
			.persistent(CODEC)
			.initializer(() -> EMPTY)
			.copyOnDeath());

		private Attachment() {
		}
	}

	public static void init() {
		Hearth.LOG.debug("player attachment {}", Attachment.TYPE.identifier());
	}

	public static CompanionRoster of(Player player) {
		CompanionRoster r = player.getAttached(Attachment.TYPE);
		return r == null ? EMPTY : r;
	}

	public static void set(ServerPlayer player, CompanionRoster roster) {
		player.setAttached(Attachment.TYPE, roster);
	}

	public int size() {
		return this.entries.size();
	}

	public boolean has(UUID resident) {
		for (Entry e : this.entries) {
			if (e.resident.equals(resident)) {
				return true;
			}
		}
		return false;
	}

	public CompanionRoster with(Entry e) {
		List<Entry> l = new ArrayList<>(this.entries);
		l.removeIf(x -> x.resident.equals(e.resident));
		l.add(e);
		return new CompanionRoster(l);
	}

	public CompanionRoster without(UUID resident) {
		List<Entry> l = new ArrayList<>(this.entries);
		l.removeIf(x -> x.resident.equals(resident));
		return new CompanionRoster(l);
	}
}
