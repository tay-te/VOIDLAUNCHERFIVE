package dev.voidmc.hearth.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Staged gatherings (docs/LIVING_WORLD_2.md §0.2, §3): {@link HearthServices#scenes()}; the social module's town events
 * (lane B1). B4's oath and dubbing and B2's weddings and funerals of named characters stage through it.
 */
public interface SceneService {
	/** Stages a scene (a {@code hearth_social/events/} data id); false if it cannot now (another major scene, no venue). */
	boolean stage(Settlement s, Identifier scene, List<UUID> principals, @Nullable ServerPlayer player, CompoundTag args);
}
