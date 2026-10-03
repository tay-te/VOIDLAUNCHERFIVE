package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The creatures quests put in the world: the named beast of a bounty, which appears at its lair when the player comes
 * near (never before: the lair's chunk may not exist yet), and the pack animal of an escort, leashed to the player
 * when the quest is taken. Both are ordinary vanilla mobs marked with entity tags naming their quest and owner, so the
 * death event can find their quest without any lookup table.
 */
public final class QuestSpawns {
	public static final String TAG = "hearth_quest";
	public static final String ROLE_BEAST = "hearth_quest_role:beast";
	public static final String ROLE_ESCORT = "hearth_quest_role:escort";
	private static final String ID_PREFIX = "hearth_quest_id:";
	private static final String OWNER_PREFIX = "hearth_quest_owner:";

	private QuestSpawns() {
	}

	/** The pack animal of an escort, next to the player and on their lead. Null if it could not be made. */
	public static @Nullable Entity spawnCompanion(ServerPlayer player, QuestInstance q, Objective o) {
		ServerLevel level = player.level();
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(o.target)).orElse(net.minecraft.world.entity.EntityTypes.MULE);
		Entity e = type.create(level, EntitySpawnReason.EVENT);
		if (e == null) {
			return null;
		}
		BlockPos at = player.blockPosition().relative(player.getDirection().getOpposite(), 2);
		e.snapTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, player.getYRot(), 0);
		String name = o.data.getStringOr(Objective.Keys.NAME, "Bramble");
		e.setCustomName(Component.literal(name).withStyle(ChatFormatting.GOLD));
		e.setCustomNameVisible(true);
		mark(e, q, player.getUUID(), ROLE_ESCORT);
		if (e instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		if (e instanceof AbstractChestedHorse chested) {
			chested.setChest(true);
		}
		if (!level.addFreshEntity(e)) {
			return null;
		}
		if (e instanceof Leashable leashable) {
			leashable.setLeashedTo(player, true);
		}
		o.entity = e.getUUID();
		o.data.putBoolean(Objective.Keys.SPAWNED, true);
		return e;
	}

	/**
	 * The named beast of a bounty, at the surface of its lair. Called when the player comes within the objective's
	 * radius of the lair, so the chunk is loaded. Null if it could not be placed.
	 */
	public static @Nullable Entity spawnBeast(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o) {
		if (o.pos == null || !level.hasChunkAt(new BlockPos(o.pos.getX(), level.getMinY(), o.pos.getZ()))) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(o.target)).orElse(null);
		if (type == null) {
			return null;
		}
		Entity e = type.create(level, EntitySpawnReason.EVENT);
		if (e == null) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.pos.getX(), o.pos.getZ());
		e.snapTo(o.pos.getX() + 0.5, y, o.pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
		if (e instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.EVENT, null);
			mob.setPersistenceRequired();
		}
		if (e instanceof LivingEntity living) {
			AttributeInstance health = living.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(health.getBaseValue() * Math.max(1.0, o.data.getDoubleOr(Objective.Keys.HEALTH, 1.0)));
				living.setHealth(living.getMaxHealth());
			}
		}
		String name = o.data.getStringOr(Objective.Keys.NAME, "the beast");
		e.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
		e.setCustomNameVisible(true);
		mark(e, q, player.getUUID(), ROLE_BEAST);
		if (!level.addFreshEntity(e)) {
			return null;
		}
		o.entity = e.getUUID();
		o.data.putBoolean(Objective.Keys.SPAWNED, true);
		return e;
	}

	/** Takes a quest's creatures back when it ends: a beast vanishes, a pack animal is let off its lead. */
	public static void release(MinecraftServer server, QuestInstance q) {
		for (Objective o : q.objectives) {
			if (o.entity == null || !o.data.getBooleanOr(Objective.Keys.SPAWNED, false)) {
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				Entity e = level.getEntity(o.entity);
				if (e == null) {
					continue;
				}
				if (e.entityTags().contains(ROLE_BEAST)) {
					e.discard();
				} else if (e instanceof Leashable leashable && leashable.isLeashed()) {
					leashable.dropLeash();
				}
				break;
			}
		}
	}

	private static void mark(Entity e, QuestInstance q, UUID owner, String role) {
		e.addTag(TAG);
		e.addTag(role);
		e.addTag(ID_PREFIX + q.id);
		e.addTag(OWNER_PREFIX + owner);
	}

	public static Optional<UUID> questOf(Entity e) {
		return tagged(e, ID_PREFIX);
	}

	public static Optional<UUID> ownerOf(Entity e) {
		return tagged(e, OWNER_PREFIX);
	}

	private static Optional<UUID> tagged(Entity e, String prefix) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(prefix)) {
				try {
					return Optional.of(UUID.fromString(tag.substring(prefix.length())));
				} catch (IllegalArgumentException ignored) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}
}
