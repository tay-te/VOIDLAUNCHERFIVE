package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.QuestProgress;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Drives objectives from what happens in the world, with work bounded per tick: each online player is looked at once
 * every {@code trackerInterval} ticks (staggered so players spread over the interval), and only their own few active
 * quests are checked — positions against targets, deadlines, a beast's lair, an escort's arrival. Kills arrive as
 * events. Monster pressure on a town is sampled from the players standing in it at night, one box query per player per
 * ten seconds. Nothing scans the world.
 */
public final class QuestTracker {
	private static final int PRESSURE_INTERVAL = 200;
	private static final int PRESSURE_RADIUS = 48;
	/** Checks an escort may go unseen in a loaded chunk before it counts as lost (entities load after their chunk). */
	private static final int ESCORT_MISSES = 4;
	private static final String MISSES = "misses";
	private static final String LAST = "last";

	private QuestTracker() {
	}

	public static void tick(MinecraftServer server) {
		int interval = QuestManager.config().trackerInterval();
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int phase = Math.floorMod(player.getId() * 7, interval);
			if (tick % interval == phase) {
				PlayerQuests pq = QuestStore.get(player);
				if (!pq.active.isEmpty()) {
					check(player, pq);
				}
			}
			if (tick % PRESSURE_INTERVAL == Math.floorMod(player.getId() * 13, PRESSURE_INTERVAL)) {
				samplePressure(player);
			}
		}
	}

	/** Runs a player's objective check now (dev harness and commands; the tick does this on its own schedule). */
	public static void checkNow(ServerPlayer player) {
		PlayerQuests pq = QuestStore.get(player);
		if (!pq.active.isEmpty()) {
			check(player, pq);
		}
	}

	/** One player's periodic check. Works on a copy and attaches it only if something changed. */
	private static void check(ServerPlayer player, PlayerQuests current) {
		ServerLevel level = player.level();
		long now = QuestManager.gameTime(player);
		BlockPos pos = player.blockPosition();
		PlayerQuests pq = current.copy();
		boolean dirty = false;
		List<QuestInstance> failed = new ArrayList<>();
		List<Component> reasons = new ArrayList<>();
		List<QuestInstance> completed = new ArrayList<>();
		for (QuestInstance q : pq.active) {
			if (QuestProgress.expired(q, now)) {
				failed.add(q);
				reasons.add(Component.translatable("hearth.quest.reason.deadline"));
				continue;
			}
			if (q.status != QuestStatus.ACTIVE) {
				continue;
			}
			boolean changed = false;
			if (level.dimension() == Level.OVERWORLD) {
				for (Objective o : QuestProgress.onPosition(q, pos, biome -> inBiome(level, pos, biome))) {
					changed = true;
					arrived(player, q, o);
				}
			}
			for (Objective o : q.currentObjectives()) {
				switch (o.kind) {
					case SLAY -> changed |= slay(level, player, q, o, pos);
					case ESCORT -> {
						EscortState state = escort(level, player, q, o);
						if (state == EscortState.LOST) {
							failed.add(q);
							reasons.add(Component.translatable("hearth.quest.reason.lost", o.data.getStringOr(Objective.Keys.NAME, "?")));
						}
						changed |= state != EscortState.UNCHANGED;
					}
					case SCOUT -> changed |= scout(level, player, q, o, pos);
					default -> {
					}
				}
			}
			if (changed) {
				dirty = true;
				QuestStatus settled = QuestProgress.settle(q);
				if (settled == QuestStatus.COMPLETED) {
					completed.add(q);
				} else if (settled == QuestStatus.READY) {
					QuestManager.readyNotice(player, q);
				}
			}
		}
		for (QuestInstance q : completed) {
			QuestManager.complete(player, pq, q);
		}
		for (int i = 0; i < failed.size(); i++) {
			if (pq.get(failed.get(i).id) != null) {
				QuestManager.end(player, pq, failed.get(i), QuestStatus.FAILED, reasons.get(i));
			}
		}
		if (dirty || !failed.isEmpty() || !completed.isEmpty()) {
			player.setAttached(QuestStore.PLAYER, pq);
		}
	}

	private static boolean inBiome(ServerLevel level, BlockPos pos, String biome) {
		Identifier id = Identifier.tryParse(biome);
		return id != null && level.getBiome(pos).is(ResourceKey.create(Registries.BIOME, id));
	}

	/** An arrival objective was done: hand over what it grants (a keepsake, a rubbing) and say so. */
	private static void arrived(ServerPlayer player, QuestInstance q, Objective o) {
		String item = o.data.getStringOr(Objective.Keys.GRANT_ITEM, "");
		if (!item.isEmpty()) {
			Identifier id = Identifier.tryParse(item);
			if (id != null) {
				String name = o.data.getStringOr(Objective.Keys.GRANT_NAME, "");
				QuestItems.give(player, QuestItems.make(id, name, o.data.getStringOr(Objective.Keys.GRANT_LORE, ""), q.id, QuestItems.ROLE_GRANT));
				player.sendSystemMessage(Component.translatable("hearth.quest.found", Component.literal(name).withStyle(ChatFormatting.GOLD))
					.withStyle(ChatFormatting.GREEN));
			}
		}
		player.sendSystemMessage(Component.literal("✔ ").withStyle(ChatFormatting.GREEN).append(Component.literal(o.label).withStyle(ChatFormatting.GRAY)));
	}

	/** Raises the beast when the player nears its lair, and raises it again if it wandered off and despawned. */
	private static boolean slay(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (o.pos == null || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int r = Math.max(16, o.radius);
		if (QuestProgress.horizontalDistSq(o.pos, pos) > (double) r * r) {
			return false;
		}
		if (o.data.getBooleanOr(Objective.Keys.SPAWNED, false) && o.entity != null && level.getEntity(o.entity) != null) {
			return false;
		}
		if (QuestSpawns.spawnBeast(level, player, q, o) != null) {
			player.sendSystemMessage(Component.translatable("hearth.quest.beast", o.data.getStringOr(Objective.Keys.NAME, "?"))
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			return true;
		}
		return false;
	}

	private enum EscortState {
		UNCHANGED,
		MOVED,
		ARRIVED,
		LOST
	}

	/** Follows the pack animal: arrival at the destination completes the objective; vanishing in a loaded chunk loses it. */
	private static EscortState escort(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o) {
		if (o.entity == null) {
			return EscortState.UNCHANGED;
		}
		Entity e = level.getEntity(o.entity);
		if (e == null) {
			long last = o.data.getLongOr(LAST, Long.MIN_VALUE);
			if (last != Long.MIN_VALUE && level.hasChunkAt(BlockPos.of(last))) {
				int misses = o.data.getIntOr(MISSES, 0) + 1;
				o.data.putInt(MISSES, misses);
				return misses >= ESCORT_MISSES ? EscortState.LOST : EscortState.MOVED;
			}
			return EscortState.UNCHANGED;
		}
		o.data.putLong(LAST, e.blockPosition().asLong());
		o.data.putInt(MISSES, 0);
		Settlement dest = QuestWorlds.byId(level.getServer(), o.settlement);
		boolean there = dest != null && (QuestProgress.horizontalDistSq(dest.center(), e.blockPosition()) <= 40.0 * 40.0
			|| (QuestWorlds.at(level, e.blockPosition()) instanceof Settlement s && s.id() == dest.id()));
		if (!there) {
			return EscortState.MOVED;
		}
		o.complete();
		if (e instanceof net.minecraft.world.entity.Leashable leashable && leashable.isLeashed()) {
			leashable.dropLeash();
		}
		player.sendSystemMessage(Component.translatable("hearth.quest.escort.arrived", o.data.getStringOr(Objective.Keys.NAME, "?"), dest.name())
			.withStyle(ChatFormatting.GREEN));
		return EscortState.ARRIVED;
	}

	/**
	 * A scouting objective: the player stands in the distance band, clear of every settlement, on gentle ground with water
	 * near. The ground is read from the loaded chunks round the player: 25 heightmap samples, nothing more.
	 */
	private static boolean scout(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !QuestProgress.inScoutBand(o, pos)) {
			return false;
		}
		int clearance = o.data.getIntOr(Objective.Keys.CLEARANCE, 500);
		for (Settlement s : QuestWorlds.all(level)) {
			if (QuestProgress.horizontalDistSq(s.center(), pos) < (double) clearance * clearance) {
				return false;
			}
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		boolean water = false;
		for (int dx = -24; dx <= 24; dx += 12) {
			for (int dz = -24; dz <= 24; dz += 12) {
				int x = pos.getX() + dx;
				int z = pos.getZ() + dz;
				if (!level.hasChunkAt(new BlockPos(x, pos.getY(), z))) {
					return false;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos surface = new BlockPos(x, top - 1, z);
				if (level.getFluidState(surface).is(FluidTags.WATER)) {
					water = true;
					continue;
				}
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				min = Math.min(min, ground);
				max = Math.max(max, ground);
			}
		}
		if (!water || min == Integer.MAX_VALUE || max - min > 8 || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
			return false;
		}
		o.complete();
		o.data.putLong(Objective.Keys.FOUND, pos.asLong());
		player.sendSystemMessage(Component.translatable("hearth.quest.scout.found").withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Kills toward bounties; a dead escort fails its quest; a dead beast completes its slayer's. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (entity.entityTags().contains(QuestSpawns.TAG)) {
			Optional<UUID> quest = QuestSpawns.questOf(entity);
			Optional<UUID> owner = QuestSpawns.ownerOf(entity);
			ServerPlayer player = owner.map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
			if (player != null && quest.isPresent()) {
				if (entity.entityTags().contains(QuestSpawns.ROLE_ESCORT)) {
					QuestManager.fail(player, quest.get(), Component.translatable("hearth.quest.reason.killed", entity.getName()));
					return;
				}
				if (entity.entityTags().contains(QuestSpawns.ROLE_BEAST)) {
					beastSlain(player, quest.get());
				}
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		PlayerQuests current = QuestStore.get(killer);
		if (current.active.isEmpty()) {
			return;
		}
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		QuestProgress.Kill kill = new QuestProgress.Kill(type.toString(),
			tag -> {
				Identifier id = Identifier.tryParse(tag);
				return id != null && entity.is(TagKey.create(Registries.ENTITY_TYPE, id));
			},
			entity instanceof Enemy || entity instanceof Monster, entity.blockPosition(), level.isDarkOutside());
		PlayerQuests pq = current.copy();
		boolean dirty = false;
		List<QuestInstance> completed = new ArrayList<>();
		for (QuestInstance q : pq.active) {
			if (QuestProgress.onKill(q, kill)) {
				dirty = true;
				for (Objective o : q.objectives) {
					if (o.kind == ObjectiveKind.KILL && !o.done() && QuestProgress.matches(o.target, kill)) {
						killer.sendSystemMessage(Component.literal(o.label + " (" + o.progress + "/" + o.count + ")").withStyle(ChatFormatting.GRAY), true);
					}
				}
				QuestStatus settled = QuestProgress.settle(q);
				if (settled == QuestStatus.COMPLETED) {
					completed.add(q);
				} else if (settled == QuestStatus.READY) {
					QuestManager.readyNotice(killer, q);
				}
			}
		}
		for (QuestInstance q : completed) {
			QuestManager.complete(killer, pq, q);
		}
		if (dirty) {
			killer.setAttached(QuestStore.PLAYER, pq);
		}
	}

	private static void beastSlain(ServerPlayer owner, UUID questId) {
		PlayerQuests pq = QuestStore.get(owner).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return;
		}
		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.SLAY) {
				o.complete();
			}
		}
		QuestStatus settled = QuestProgress.settle(q);
		if (settled == QuestStatus.COMPLETED) {
			QuestManager.complete(owner, pq, q);
		} else if (settled == QuestStatus.READY) {
			QuestManager.readyNotice(owner, q);
		}
		owner.setAttached(QuestStore.PLAYER, pq);
	}

	/** Night monster pressure from the monsters round a player standing in a town. */
	private static void samplePressure(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!level.isDarkOutside() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		Settlement s = QuestWorlds.at(level, player.blockPosition());
		if (s == null) {
			return;
		}
		int monsters = level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(PRESSURE_RADIUS), m -> true).size();
		if (monsters == 0) {
			return;
		}
		SettlementQuests data = QuestStore.board(s);
		data.pressure = Math.min(1000, data.pressure + monsters);
		s.markDirty();
		if (data.pressure >= QuestManager.config().pressureThreshold()) {
			QuestManager.board(level, s, true);
		}
	}
}
