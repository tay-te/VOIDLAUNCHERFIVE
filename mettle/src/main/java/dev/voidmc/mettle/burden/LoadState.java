package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.LoadBand;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What a player carries, as the server last worked it out (BURDEN.md §2): load and capacity in kg, the band, and the
 * heaviest kind of item (total kg over how many stacks), which is what "Heaviest: cobblestone ×3 stacks · 48.0 kg" reads.
 * Synced to its owner as {@code mettle:burden/load}; derived, so not saved.
 */
public record LoadState(float load, float capacity, LoadBand band, boolean exempt, Identifier heaviest, float heaviestKg, int heaviestStacks) {
	public static final Identifier NONE = Identifier.withDefaultNamespace("air");
	public static final LoadState EMPTY = new LoadState(0f, 0f, LoadBand.LIGHT, false, NONE, 0f, 0);

	public static final StreamCodec<RegistryFriendlyByteBuf, LoadState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, LoadState::load,
		ByteBufCodecs.FLOAT, LoadState::capacity,
		ByteBufCodecs.idMapper(i -> LoadBand.values()[i], Enum::ordinal), LoadState::band,
		ByteBufCodecs.BOOL, LoadState::exempt,
		Identifier.STREAM_CODEC, LoadState::heaviest,
		ByteBufCodecs.FLOAT, LoadState::heaviestKg,
		ByteBufCodecs.VAR_INT, LoadState::heaviestStacks,
		LoadState::new);

	public static final AttachmentType<LoadState> ATTACHMENT = AttachmentRegistry.create(Mettle.id("burden/load"), b -> b
		.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** The synced state (the client's copy for the local player); null before the first sync. */
	public static @Nullable LoadState of(Player player) {
		return player.getAttached(ATTACHMENT);
	}

	public static LoadBand band(Player player) {
		LoadState s = player.getAttached(ATTACHMENT);
		return s == null ? LoadBand.LIGHT : s.band();
	}

	public boolean hasHeaviest() {
		return !this.heaviest.equals(NONE) && this.heaviestKg > 0f;
	}

	/** Worth syncing: a value moved by 0.1 kg or more, or the band, exemption or heaviest item changed. */
	public boolean differsEnough(@Nullable LoadState old) {
		return old == null || old.band != this.band || old.exempt != this.exempt || !old.heaviest.equals(this.heaviest)
			|| Math.abs(old.load - this.load) >= 0.1f || Math.abs(old.capacity - this.capacity) >= 0.1f
			|| Math.abs(old.heaviestKg - this.heaviestKg) >= 0.1f || old.heaviestStacks != this.heaviestStacks;
	}
}
