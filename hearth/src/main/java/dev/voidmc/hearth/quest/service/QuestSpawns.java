package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.RandomSource;
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
	/** A member of an ambush (bandits, wolves) or a robbery's target (a caravan's pack animals). */
	public static final String ROLE_AMBUSH = "hearth_quest_role:ambush";
	/** A culprit to bring in alive (a market thief, an outlaw). */
	public static final String ROLE_CULPRIT = "hearth_quest_role:culprit";
	/** A caravan animal behind the leader: losing it costs part of the reward, not the quest. */
	public static final String FOLLOWER = "hearth_quest_follower";
	/** Killing this creature is a crime (the objective names the offence). */
	public static final String CRIMINAL = "hearth_quest_crime";
	/** A culprit that has yielded and waits to be taken away. */
	public static final String YIELDED = "hearth_quest_yielded";
	private static final int SPREAD = 6;
	private static final String ID_PREFIX = "hearth_quest_id:";
	private static final String OWNER_PREFIX = "hearth_quest_owner:";

	private QuestSpawns() {
	}

	/**
	 * The pack animal of an escort, next to the player and on their lead — or, for a caravan, a train of them, each on the
	 * lead of the one before, their chests full of the town's goods. Null if the leader could not be made.
	 */
	public static @Nullable Entity spawnCompanion(ServerPlayer player, QuestInstance q, Objective o) {
		ServerLevel level = player.level();
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(o.target)).orElse(net.minecraft.world.entity.EntityTypes.MULE);
		int size = Math.max(1, Math.min(6, o.data.getIntOr(Objective.Keys.CARAVAN, 1)));
		String name = o.data.getStringOr(Objective.Keys.NAME, "Bramble");
		String cargo = o.data.getStringOr(Objective.Keys.CARGO, "");
		Entity leader = null;
		Entity previous = player;
		ListTag members = new ListTag();
		for (int i = 0; i < size; i++) {
			Entity e = type.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				break;
			}
			BlockPos at = player.blockPosition().relative(player.getDirection().getOpposite(), 2 + i * 2);
			e.snapTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, player.getYRot(), 0);
			e.setCustomName(Component.literal(i == 0 ? name : name + " " + roman(i + 1)).withStyle(ChatFormatting.GOLD));
			e.setCustomNameVisible(true);
			mark(e, q, player.getUUID(), ROLE_ESCORT);
			if (i > 0) {
				e.addTag(FOLLOWER);
			}
			if (e instanceof Mob mob) {
				mob.setPersistenceRequired();
			}
			if (e instanceof AbstractChestedHorse chested) {
				chested.setChest(true);
				if (!cargo.isEmpty()) {
					loadCargo(chested, q, cargo, level);
				}
			}
			if (!level.addFreshEntity(e)) {
				if (i == 0) {
					return null;
				}
				break;
			}
			if (e instanceof Leashable leashable) {
				leashable.setLeashedTo(previous, true);
			}
			members.add(StringTag.valueOf(e.getUUID().toString()));
			if (i == 0) {
				leader = e;
			}
			previous = e;
		}
		if (leader == null) {
			return null;
		}
		o.entity = leader.getUUID();
		o.data.put(Objective.Keys.MEMBERS, members);
		o.data.putBoolean(Objective.Keys.SPAWNED, true);
		return leader;
	}

	/**
	 * Raises an ambush round {@code at} (surface): the objective's creatures in turn, {@code count} of them, angry at the
	 * player if they are the kind that waits to be provoked (wolves). Pack animals carry cargo chests when the objective
	 * says so, and are marked as a crime to kill when it names one. Returns how many were raised.
	 */
	public static int spawnGroup(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos at, int count) {
		String[] kinds = o.data.getStringOr(Objective.Keys.ENTITIES, o.target).split(",");
		ListTag members = o.data.getListOrEmpty(Objective.Keys.MEMBERS).copy();
		String cargo = o.data.getStringOr(Objective.Keys.CARGO, "");
		boolean crime = !o.data.getStringOr(Objective.Keys.CRIME, "").isEmpty();
		double health = Math.max(1.0, o.data.getDoubleOr(Objective.Keys.HEALTH, 1.0));
		RandomSource random = level.getRandom();
		int raised = 0;
		for (int i = 0; i < count; i++) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(kinds[i % kinds.length].trim())).orElse(null);
			if (type == null) {
				continue;
			}
			Entity e = type.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				continue;
			}
			int x = at.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
			int z = at.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
			if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			e.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360F, 0);
			if (e instanceof Mob mob) {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.EVENT, null);
				mob.setPersistenceRequired();
			}
			boostHealth(e, health);
			mark(e, q, player.getUUID(), ROLE_AMBUSH);
			if (crime) {
				e.addTag(CRIMINAL);
			}
			if (e instanceof AbstractChestedHorse chested) {
				chested.setChest(true);
				if (!cargo.isEmpty()) {
					loadCargo(chested, q, cargo, level);
				}
			}
			if (e instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader) {
				trader.setDespawnDelay(0);
			}
			if (!level.addFreshEntity(e)) {
				continue;
			}
			if (e instanceof net.minecraft.world.entity.NeutralMob neutral && e instanceof Mob mob && !(e instanceof AbstractChestedHorse)) {
				neutral.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.of(player));
				neutral.startPersistentAngerTimer();
				mob.setTarget(player);
			}
			members.add(StringTag.valueOf(e.getUUID().toString()));
			raised++;
		}
		o.data.put(Objective.Keys.MEMBERS, members);
		o.data.putBoolean(Objective.Keys.SPRUNG, true);
		return raised;
	}

	/**
	 * The culprit of an apprehend objective, at its place (the surface, or the place's own height when it is known and the
	 * player is near it). It flees and fights as its kind does; beaten down it yields (see {@code QuestTracker.onDamage}).
	 */
	public static @Nullable Entity spawnCulprit(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o) {
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
		int x = o.pos.getX() + level.getRandom().nextInt(7) - 3;
		int z = o.pos.getZ() + level.getRandom().nextInt(7) - 3;
		int y = o.pos.getY() != Objective.SURFACE_Y && Math.abs(o.pos.getY() - player.getBlockY()) < 12 ? o.pos.getY()
			: level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		e.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360F, 0);
		if (e instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.EVENT, null);
			mob.setPersistenceRequired();
		}
		if (e instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader) {
			trader.setDespawnDelay(0);
		}
		boostHealth(e, Math.max(1.0, o.data.getDoubleOr(Objective.Keys.HEALTH, 1.0)));
		String name = o.data.getStringOr(Objective.Keys.NAME, "the culprit");
		e.setCustomName(Component.literal(name).withStyle(ChatFormatting.RED));
		e.setCustomNameVisible(true);
		mark(e, q, player.getUUID(), ROLE_CULPRIT);
		if (!level.addFreshEntity(e)) {
			return null;
		}
		o.entity = e.getUUID();
		o.data.putBoolean(Objective.Keys.SPAWNED, true);
		return e;
	}

	/** Fills a pack animal's chest with a load of the goods (what robbers would take), in the town's own items. */
	private static void loadCargo(AbstractChestedHorse chested, QuestInstance q, String goodsName, ServerLevel level) {
		dev.voidmc.hearth.api.Goods goods = null;
		for (dev.voidmc.hearth.api.Goods g : dev.voidmc.hearth.api.Goods.VALUES) {
			if (g.getSerializedName().equals(goodsName)) {
				goods = g;
			}
		}
		if (goods == null) {
			return;
		}
		dev.voidmc.hearth.api.Settlement s = dev.voidmc.hearth.quest.world.QuestWorlds.byId(level.getServer(), q.settlementId);
		dev.voidmc.hearth.api.Culture culture = s == null ? dev.voidmc.hearth.api.Culture.TEMPERATE : s.culture();
		dev.voidmc.hearth.quest.gen.GoodsItems.Choice ch = dev.voidmc.hearth.quest.gen.GoodsItems.pick(culture, goods, level.getRandom(),
			id -> BuiltInRegistries.ITEM.containsKey(id));
		if (ch == null) {
			return;
		}
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(ch.item());
		int slots = 3 + level.getRandom().nextInt(3);
		for (int i = 0; i < slots; i++) {
			net.minecraft.world.entity.SlotAccess slot = chested.getSlot(net.minecraft.world.entity.animal.equine.AbstractHorse.INVENTORY_SLOT_OFFSET + i);
			if (slot == null) {
				break;
			}
			int max = new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
			slot.set(new net.minecraft.world.item.ItemStack(item, Math.max(1, Math.min(max, 8 + level.getRandom().nextInt(Math.max(1, max - 7))))));
		}
	}

	private static void boostHealth(Entity e, double factor) {
		if (factor > 1.0 && e instanceof LivingEntity living) {
			AttributeInstance health = living.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(health.getBaseValue() * factor);
				living.setHealth(living.getMaxHealth());
			}
		}
	}

	private static String roman(int n) {
		return switch (n) {
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			default -> Integer.toString(n);
		};
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

	/**
	 * Takes a quest's creatures back when it ends: a beast, an ambush, a culprit and a loaded caravan vanish (they were the
	 * quest's), a lone pack animal is let off its lead.
	 */
	public static void release(MinecraftServer server, QuestInstance q) {
		for (Objective o : q.objectives) {
			List<UUID> ids = new ArrayList<>(members(o));
			if (o.entity != null && o.data.getBooleanOr(Objective.Keys.SPAWNED, false)) {
				ids.add(o.entity);
			}
			// a loaded caravan goes back to its master: its cargo is not left on the road for the taking
			boolean cargo = !o.data.getStringOr(Objective.Keys.CARGO, "").isEmpty();
			for (UUID id : ids) {
				for (ServerLevel level : server.getAllLevels()) {
					Entity e = level.getEntity(id);
					if (e == null) {
						continue;
					}
					if (e.entityTags().contains(ROLE_BEAST) || e.entityTags().contains(ROLE_AMBUSH) || e.entityTags().contains(ROLE_CULPRIT)
						|| (cargo && e.entityTags().contains(ROLE_ESCORT))) {
						e.discard();
					} else if (e instanceof Leashable leashable && leashable.isLeashed()) {
						leashable.dropLeash();
					}
					break;
				}
			}
		}
	}

	/** The creatures an objective raised (an ambush's band, a caravan's train). */
	public static List<UUID> members(Objective o) {
		List<UUID> out = new ArrayList<>();
		ListTag list = o.data.getListOrEmpty(Objective.Keys.MEMBERS);
		for (int i = 0; i < list.size(); i++) {
			try {
				list.getString(i).map(UUID::fromString).ifPresent(out::add);
			} catch (IllegalArgumentException ignored) {
				// a malformed id from an edited save: skip it
			}
		}
		return out;
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
