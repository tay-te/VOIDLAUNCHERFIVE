package dev.voidmc.hearth.law.own;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.state.Codecs;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/**
 * Ownership in the world (docs/LIVING_WORLD.md §4): the owner of a block is the owner of the plot it lies in
 * ({@link OwnerRules}); an animal belongs to the owner of the pasture, barn or stable it was first found in, and keeps
 * that owner (an entity attachment, saved with it) when it is led away.
 */
public final class Ownership {
	/** The owner an animal was first found to have. */
	public static final AttachmentType<Owner> ANIMAL_OWNER = AttachmentRegistry.createPersistent(Hearth.id("law/owner"), Codecs.OWNER);

	private Ownership() {
	}

	/** Where a block is, as the law sees it: the settlement, its plot and the plot's owner (null fields where none). */
	public record Place(Settlement settlement, @Nullable PlotView plot, @Nullable Owner owner) {
	}

	/** Loads the class (registers the attachment). */
	public static void init() {
	}

	public static @Nullable Place place(ServerLevel level, BlockPos pos) {
		Settlement s = Law.settlementAt(level, pos);
		if (s == null) {
			return null;
		}
		PlotView plot = PlotIndex.of(s, level.getGameTime()).plotAt(s, pos);
		return new Place(s, plot, plot == null ? null : OwnerRules.owner(s, plot));
	}

	public static @Nullable Owner owner(ServerLevel level, BlockPos pos) {
		Place p = place(level, pos);
		return p == null ? null : p.owner;
	}

	/** The owner of an animal, or null for wild animals and anything that is not one. */
	public static @Nullable Owner owner(Entity entity) {
		if (!(entity instanceof Animal animal) || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		Owner known = animal.getAttached(ANIMAL_OWNER);
		if (known != null) {
			return known;
		}
		Place p = place(level, animal.blockPosition());
		if (p == null || p.plot == null || p.owner == null) {
			return null;
		}
		ArchetypeCategory category = OwnerRules.category(p.plot.spec().archetype());
		if (category == ArchetypeCategory.FARM || category == ArchetypeCategory.WORKPLACE || category == ArchetypeCategory.NOBLE
			|| category == ArchetypeCategory.HOUSING) {
			// tame or penned animals of the town; once found they stay theirs wherever they wander
			animal.setAttached(ANIMAL_OWNER, p.owner);
			return p.owner;
		}
		return null;
	}

	/** Whether {@code player} is (or acts for) the owner: their own deeded property. */
	public static boolean isOwner(Owner owner, UUID player) {
		return owner.kind() == Owner.Kind.PLAYER && player.equals(owner.player());
	}

	/** "Edda Miller", "Ashbrook", "Alex" — whoever the owner is, by name. */
	public static Component name(Owner owner, @Nullable MinecraftServer server) {
		Settlement s = settlement(owner.settlementId(), server);
		switch (owner.kind()) {
			case HOUSEHOLD, WORKPLACE -> {
				Resident r = s == null || owner.resident() == null ? null : s.resident(owner.resident());
				if (r != null) {
					return Component.literal(r.fullName());
				}
				return s == null ? Component.translatable("hearth.law.owner.someone") : Component.literal(s.name());
			}
			case PLAYER -> {
				if (server != null && owner.player() != null) {
					ServerPlayer p = server.getPlayerList().getPlayer(owner.player());
					if (p != null) {
						return p.getName();
					}
					var cached = server.services().nameToIdCache().get(owner.player());
					if (cached.isPresent()) {
						return Component.literal(cached.get().name());
					}
				}
				return Component.translatable("hearth.law.owner.burgher");
			}
			default -> {
				return s == null ? Component.translatable("hearth.law.owner.town") : Component.literal(s.name());
			}
		}
	}

	public static @Nullable Settlement settlement(long id, @Nullable MinecraftServer server) {
		if (server == null) {
			return null;
		}
		HearthWorld world = HearthWorld.of(server.overworld());
		return world == null ? null : world.settlement(id);
	}
}
