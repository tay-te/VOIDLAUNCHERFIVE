package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Where the character lives: a persistent attachment ({@code mettle:core/character}) that survives death and is synced
 * to the owner's client only, which is all the client screens need.
 */
public final class CharacterStore {
	public static final AttachmentType<CharacterState> CHARACTER = AttachmentRegistry.create(Mettle.id("core/character"), b -> b
		.persistent(CharacterState.CODEC)
		.copyOnDeath()
		.syncWith(CharacterState.SYNC_CODEC, AttachmentSyncPredicate.targetOnly()));

	private CharacterStore() {
	}

	/** Forces the class (and so the attachment) to register during init. */
	public static void init() {
	}

	/** The player's character; the client reads its synced copy the same way. */
	public static CharacterState get(Player player) {
		CharacterState state = player.getAttached(CHARACTER);
		return state == null ? CharacterState.EMPTY : state;
	}

	public static void set(Player player, CharacterState state) {
		player.setAttached(CHARACTER, state);
	}

	/** The chosen background's definition, or null while undecided (or if a datapack removed it). */
	public static @Nullable Background background(RegistryAccess registries, CharacterState state) {
		return state.background()
			.flatMap(key -> registries.lookupOrThrow(MettleRegistries.BACKGROUND).getOptional(key))
			.orElse(null);
	}
}
