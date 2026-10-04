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
			if (takeAway(level, q)) {
				dirty = true;
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
					case AMBUSH -> changed |= ambush(level, player, q, o, pos);
					case APPREHEND -> changed |= apprehend(level, player, q, o, pos);
					case RESCUE -> changed |= rescue(level, player, q, o, pos);
					case EXTINGUISH -> changed |= extinguish(level, player, q, o, pos);
					case PROSPECT -> changed |= prospect(level, player, q, o, pos);
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
		for (Objective other : q.objectives) {
			// highwaymen waiting further along a road the caravan never took never came: the road was safe
			if (other.kind == ObjectiveKind.AMBUSH && !other.done() && other.data.contains(Objective.Keys.ROUTE_TO)
				&& !other.data.getBooleanOr(Objective.Keys.SPRUNG, false)) {
				other.complete();
			}
		}
		boolean cargo = !o.data.getStringOr(Objective.Keys.CARGO, "").isEmpty();
		for (UUID id : QuestSpawns.members(o)) {
			Entity member = level.getEntity(id);
			if (cargo && member != null) {
				// the train is unloaded and stabled at the destination: its cargo is delivered, not left for the taking
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, member.getX(), member.getY() + 0.5, member.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
				member.discard();
			} else if (member instanceof net.minecraft.world.entity.Leashable leashable && leashable.isLeashed()) {
				leashable.dropLeash();
			}
		}
		if (!e.isRemoved() && e instanceof net.minecraft.world.entity.Leashable leashable && leashable.isLeashed()) {
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
		// and somewhere the sim will found a hamlet: clear of towns nobody has visited yet too (never blocks: a region
		// still being surveyed answers on a later check)
		dev.voidmc.hearth.api.HearthWorld world = dev.voidmc.hearth.api.HearthWorld.of(level);
		if (world != null && world.whyNotFound(pos) != null) {
			return false;
		}
		o.complete();
		o.data.putLong(Objective.Keys.FOUND, pos.asLong());
		player.sendSystemMessage(Component.translatable("hearth.quest.scout.found").withStyle(ChatFormatting.GREEN));
		return true;
	}

	/**
	 * Kills toward bounties; a dead escort fails its quest (a caravan's follower costs part of the reward); a dead beast
	 * completes its slayer's; an ambush's members count down; a culprit killed counts only if the quest allows it; an
	 * outlaw player killed completes a hunt. Killing a caravan's animals is reported to the law as robbery.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		ServerPlayer killerPlayer = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (entity.entityTags().contains(QuestSpawns.TAG)) {
			Optional<UUID> quest = QuestSpawns.questOf(entity);
			Optional<UUID> owner = QuestSpawns.ownerOf(entity);
			ServerPlayer player = owner.map(id -> owner(server, id, killerPlayer)).orElse(null);
			if (killerPlayer != null && quest.isPresent()) {
				reportKill(level, killerPlayer, entity, player, quest.get());
			}
			if (player != null && quest.isPresent()) {
				if (entity.entityTags().contains(QuestSpawns.ROLE_ESCORT)) {
					if (entity.entityTags().contains(QuestSpawns.FOLLOWER)) {
						followerLost(player, quest.get(), entity);
					} else {
						QuestManager.fail(player, quest.get(), Component.translatable("hearth.quest.reason.killed", entity.getName()));
					}
					return;
				}
				if (entity.entityTags().contains(QuestSpawns.ROLE_BEAST)) {
					beastSlain(player, quest.get());
				}
				if (entity.entityTags().contains(QuestSpawns.ROLE_AMBUSH)) {
					memberDown(player, quest.get(), entity.getUUID());
				}
				if (entity.entityTags().contains(QuestSpawns.ROLE_CULPRIT)) {
					culpritKilled(player, quest.get(), entity);
				}
			}
		}
		if (entity instanceof ServerPlayer victim && killerPlayer != null && killerPlayer != victim) {
			hunted(killerPlayer, victim);
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

	// ------------------------------------------------------------------ the living world (phase 3)

	/**
	 * An ambush: raised when the player comes within its radius (after dark if it waits for night) — or, on the road to an
	 * escort's destination, once the player is past its point along the way — and raised again where it was if the band
	 * vanished (the player went away and its chunks unloaded) before it was beaten.
	 */
	private static boolean ambush(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (o.pos == null || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int r = Math.max(24, o.radius);
		if (!o.data.getBooleanOr(Objective.Keys.SPRUNG, false)) {
			if (o.data.getBooleanOr(Objective.Keys.AT_NIGHT, false) && !level.isDarkOutside()) {
				return false;
			}
			BlockPos at = null;
			if (QuestProgress.horizontalDistSq(o.pos, pos) <= (double) r * r) {
				at = new BlockPos(o.pos.getX(), pos.getY(), o.pos.getZ());
			} else if (o.data.contains(Objective.Keys.ROUTE_TO)) {
				at = alongRoute(o, pos);
			}
			if (at == null) {
				return false;
			}
			if (QuestSpawns.spawnGroup(level, player, q, o, at, o.remaining()) == 0) {
				return false;
			}
			o.data.putLong(Objective.Keys.SPRUNG_AT, at.asLong());
			player.sendSystemMessage(Component.translatable("hearth.quest.ambush", o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"))
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			return true;
		}
		for (UUID id : QuestSpawns.members(o)) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				o.data.putInt(MISSES, 0);
				return false;
			}
		}
		BlockPos at = BlockPos.of(o.data.getLongOr(Objective.Keys.SPRUNG_AT, o.pos.asLong()));
		if (QuestProgress.horizontalDistSq(at, pos) > (double) r * r || !level.hasChunkAt(at)) {
			return false;
		}
		int misses = o.data.getIntOr(MISSES, 0) + 1;
		o.data.putInt(MISSES, misses);
		if (misses < ESCORT_MISSES) {
			return true;
		}
		o.data.putInt(MISSES, 0);
		QuestSpawns.spawnGroup(level, player, q, o, at, o.remaining());
		return true;
	}

	/**
	 * Where on the road an ambush springs: once the player has come {@code along} of the way from the town toward the
	 * destination (within 160 blocks of the straight road), a little ahead of them. Null while they are short of it.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos alongRoute(Objective o, BlockPos pos) {
		BlockPos from = BlockPos.of(o.data.getLongOr(Objective.Keys.ROUTE_FROM, o.pos == null ? 0L : o.pos.asLong()));
		BlockPos to = BlockPos.of(o.data.getLongOr(Objective.Keys.ROUTE_TO, 0L));
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double len2 = dx * dx + dz * dz;
		if (len2 < 1) {
			return null;
		}
		double px = pos.getX() - from.getX();
		double pz = pos.getZ() - from.getZ();
		double t = (px * dx + pz * dz) / len2;
		double off = Math.abs(px * dz - pz * dx) / Math.sqrt(len2);
		if (t < o.data.getDoubleOr(Objective.Keys.ALONG, 0.5) || t > 0.98 || off > 160) {
			return null;
		}
		double len = Math.sqrt(len2);
		return new BlockPos(pos.getX() + (int) Math.round(dx / len * 20), pos.getY(), pos.getZ() + (int) Math.round(dz / len * 20));
	}

	/** A culprit: raised at its place when the player comes near (after dark if it keeps to the night). */
	private static boolean apprehend(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (o.pos == null || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int r = Math.max(16, o.radius);
		if (QuestProgress.horizontalDistSq(o.pos, pos) > (double) r * r) {
			return false;
		}
		if (o.data.getBooleanOr(Objective.Keys.AT_NIGHT, false) && !level.isDarkOutside()) {
			return false;
		}
		if (o.data.getBooleanOr(Objective.Keys.SPAWNED, false) && o.entity != null && level.getEntity(o.entity) != null) {
			return false;
		}
		if (QuestSpawns.spawnCulprit(level, player, q, o) != null) {
			player.sendSystemMessage(Component.translatable("hearth.quest.culprit", o.data.getStringOr(Objective.Keys.NAME, "?"))
				.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
			return true;
		}
		return false;
	}

	/** A culprit who yielded is taken away by the watch at the next check (it was the quest's own creature). */
	private static boolean takeAway(ServerLevel level, QuestInstance q) {
		boolean changed = false;
		for (Objective o : q.objectives) {
			if (o.kind != ObjectiveKind.APPREHEND || !o.done() || o.entity == null) {
				continue;
			}
			Entity e = level.getEntity(o.entity);
			if (e != null && e.entityTags().contains(QuestSpawns.YIELDED)) {
				e.discard();
				changed = true;
			}
		}
		return changed;
	}

	/** Digging out the trapped: time spent at the place with a pickaxe or shovel in hand. */
	private static boolean rescue(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (o.pos == null || !QuestProgress.near(o.pos, pos, Math.max(4, o.radius))) {
			return false;
		}
		net.minecraft.world.item.ItemStack hand = player.getMainHandItem();
		if (!hand.is(net.minecraft.tags.ItemTags.PICKAXES) && !hand.is(net.minecraft.tags.ItemTags.SHOVELS)) {
			player.sendSystemMessage(Component.translatable("hearth.quest.rescue.tool").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		int held = o.data.getIntOr(Objective.Keys.HELD, 0) + QuestManager.config().trackerInterval();
		int need = Math.max(1, o.data.getIntOr(Objective.Keys.HOLD, 30)) * 20;
		o.data.putInt(Objective.Keys.HELD, held);
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRAVEL_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.9F);
		if (held >= need) {
			o.complete();
			player.sendSystemMessage(Component.translatable("hearth.quest.rescue.done", o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"))
				.withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.translatable("hearth.quest.rescue.digging", held * 100 / need).withStyle(ChatFormatting.GOLD), true);
		}
		return true;
	}

	/** The bucket brigade: the fires in the burning plot's box, counted while the player is there; done when none are left. */
	private static boolean extinguish(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (o.pos == null || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int r = Math.max(16, o.radius);
		if (QuestProgress.horizontalDistSq(o.pos, pos) > (double) r * r) {
			return false;
		}
		BlockPos min = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MIN, o.pos.offset(-12, -6, -12).asLong()));
		BlockPos max = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MAX, o.pos.offset(12, 16, 12).asLong()));
		int fires = Fires.count(level, min, max);
		if (fires < 0) {
			return false;
		}
		int seen = Math.max(o.data.getIntOr(Objective.Keys.FIRES, 0), fires);
		boolean changed = seen != o.data.getIntOr(Objective.Keys.FIRES, 0);
		o.data.putInt(Objective.Keys.FIRES, seen);
		if (fires == 0) {
			o.complete();
			player.sendSystemMessage(Component.translatable("hearth.quest.fire.out", o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"))
				.withStyle(ChatFormatting.GREEN));
			return true;
		}
		player.sendSystemMessage(Component.translatable("hearth.quest.fire.left", fires).withStyle(ChatFormatting.GOLD), true);
		return changed;
	}

	/**
	 * Prospecting: in the distance band from the town and outside every town, the blocks in a small cube round the player
	 * show enough of the resource (a rock face, ore in a cave wall, clay in a river bed).
	 */
	private static boolean prospect(ServerLevel level, ServerPlayer player, QuestInstance q, Objective o, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !QuestProgress.inScoutBand(o, pos) || QuestWorlds.at(level, pos) != null) {
			return false;
		}
		dev.voidmc.hearth.api.ResourceSite.Kind kind = null;
		for (dev.voidmc.hearth.api.ResourceSite.Kind k : dev.voidmc.hearth.api.ResourceSite.Kind.values()) {
			if (k.getSerializedName().equals(o.data.getStringOr(Objective.Keys.RESOURCE, o.target))) {
				kind = k;
			}
		}
		dev.voidmc.hearth.quest.gen.Prospecting.Rule rule = kind == null ? null : dev.voidmc.hearth.quest.gen.Prospecting.rule(kind);
		if (rule == null) {
			return false;
		}
		int r = rule.radius();
		if (!level.hasChunksAt(pos.getX() - r - 1, pos.getZ() - r - 1, pos.getX() + r + 1, pos.getZ() + r + 1)) {
			return false;
		}
		int matching = 0;
		int exposed = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -r; dy <= r / 2; dy++) {
					at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (!rule.matches().test(level.getBlockState(at))) {
						continue;
					}
					matching++;
					for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
						side.setWithOffset(at, d);
						if (level.getBlockState(side).isAir()) {
							exposed++;
							break;
						}
					}
				}
			}
		}
		if (!dev.voidmc.hearth.quest.gen.Prospecting.found(rule, matching, exposed)) {
			return false;
		}
		o.complete();
		o.data.putLong(Objective.Keys.FOUND, pos.asLong());
		player.sendSystemMessage(Component.translatable("hearth.quest.prospect.found", o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"))
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** One of an ambush's band is down (by anyone's hand): its objective counts it. */
	private static void memberDown(ServerPlayer owner, UUID questId, UUID member) {
		PlayerQuests pq = QuestStore.get(owner).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return;
		}
		boolean changed = false;
		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.AMBUSH && !o.done() && QuestSpawns.members(o).contains(member)) {
				changed |= o.advance(1) > 0;
				owner.sendSystemMessage(Component.literal(o.label + " (" + o.progress + "/" + o.count + ")").withStyle(ChatFormatting.GRAY), true);
				if (o.done()) {
					// what the band carried (a caravan's stolen cargo) is the victor's to bring back
					grant(owner, q, o);
				}
			}
		}
		if (changed) {
			settleAndAttach(owner, pq, q);
		}
	}

	/** The culprit died: with a dead-or-alive warrant it counts (at the smaller reward); otherwise the quest fails. */
	private static void culpritKilled(ServerPlayer owner, UUID questId, Entity culprit) {
		PlayerQuests pq = QuestStore.get(owner).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return;
		}
		for (Objective o : q.objectives) {
			if (o.kind != ObjectiveKind.APPREHEND || o.done()) {
				continue;
			}
			if (!o.data.getBooleanOr(Objective.Keys.LETHAL, false)) {
				QuestManager.fail(owner, questId, Component.translatable("hearth.quest.reason.culprit_dead", culprit.getName()));
				return;
			}
			o.complete();
			o.data.putBoolean(Objective.Keys.ALIVE, false);
			grant(owner, q, o);
			owner.sendSystemMessage(Component.translatable("hearth.quest.culprit.dead", culprit.getName()).withStyle(ChatFormatting.GOLD));
		}
		settleAndAttach(owner, pq, q);
	}

	/**
	 * A culprit about to fall yields instead (unless the blow would kill outright on a dead-or-alive warrant): it stops,
	 * drops its weapon hand, and the watch takes it away at the next check. Returns false to cancel the damage.
	 */
	public static boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!entity.entityTags().contains(QuestSpawns.ROLE_CULPRIT) || !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		if (entity.entityTags().contains(QuestSpawns.YIELDED)) {
			return false;
		}
		if (entity.getHealth() - amount > entity.getMaxHealth() * 0.25F) {
			return true;
		}
		Optional<UUID> quest = QuestSpawns.questOf(entity);
		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer p ? p : null;
		ServerPlayer owner = QuestSpawns.ownerOf(entity).map(id -> owner(level.getServer(), id, attacker)).orElse(null);
		if (owner == null || quest.isEmpty()) {
			return true;
		}
		PlayerQuests pq = QuestStore.get(owner).copy();
		QuestInstance q = pq.get(quest.get());
		if (q == null) {
			return true;
		}
		Objective target = null;
		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.APPREHEND && !o.done() && entity.getUUID().equals(o.entity)) {
				target = o;
			}
		}
		if (target == null) {
			return true;
		}
		if (target.data.getBooleanOr(Objective.Keys.LETHAL, false) && amount >= entity.getHealth()) {
			// a killing blow on a dead-or-alive warrant kills
			return true;
		}
		entity.addTag(QuestSpawns.YIELDED);
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			mob.setNoAi(true);
			mob.setTarget(null);
		}
		entity.setHealth(Math.max(1F, entity.getHealth()));
		target.complete();
		target.data.putBoolean(Objective.Keys.ALIVE, true);
		grant(owner, q, target);
		Component name = entity.getName();
		owner.sendSystemMessage(Component.translatable("hearth.quest.culprit.yields", name).withStyle(ChatFormatting.GREEN));
		if (source.getEntity() instanceof ServerPlayer hitter && hitter != owner) {
			hitter.sendSystemMessage(Component.translatable("hearth.quest.culprit.yields", name).withStyle(ChatFormatting.GREEN));
		}
		settleAndAttach(owner, pq, q);
		return false;
	}

	/** A caravan animal behind the leader is lost: its share of the reward goes back to the treasury. */
	private static void followerLost(ServerPlayer owner, UUID questId, Entity animal) {
		PlayerQuests pq = QuestStore.get(owner).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return;
		}
		int size = 1;
		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.ESCORT) {
				size = Math.max(size, o.data.getIntOr(Objective.Keys.CARAVAN, 1));
			}
		}
		long share = q.escrow / Math.max(1, size);
		Settlement s = QuestWorlds.byId(owner.level().getServer(), q.settlementId);
		if (s != null && share > 0) {
			s.deposit(share);
			s.markDirty();
		}
		q.escrow -= share;
		q.reward = dev.voidmc.hearth.quest.gen.RewardText.withText(q.reward.withCoin(q.escrow), GameFactsImpl.INSTANCE);
		owner.sendSystemMessage(Component.translatable("hearth.quest.caravan.lost_one", animal.getName()).withStyle(ChatFormatting.RED));
		owner.setAttached(QuestStore.PLAYER, pq);
	}

	/** A player killed a wanted player: every hunt of theirs for that player is done. */
	private static void hunted(ServerPlayer hunter, ServerPlayer quarry) {
		PlayerQuests current = QuestStore.get(hunter);
		if (current.active.isEmpty()) {
			return;
		}
		PlayerQuests pq = current.copy();
		boolean changed = false;
		List<QuestInstance> done = new ArrayList<>();
		for (QuestInstance q : pq.active) {
			for (Objective o : q.currentObjectives()) {
				if (o.kind == ObjectiveKind.HUNT && quarry.getUUID().equals(o.entity)) {
					o.complete();
					changed = true;
					hunter.sendSystemMessage(Component.translatable("hearth.quest.hunt.done", quarry.getName()).withStyle(ChatFormatting.GOLD));
				}
			}
			if (changed) {
				QuestStatus settled = QuestProgress.settle(q);
				if (settled == QuestStatus.COMPLETED) {
					done.add(q);
				} else if (settled == QuestStatus.READY) {
					QuestManager.readyNotice(hunter, q);
				}
			}
		}
		for (QuestInstance q : done) {
			QuestManager.complete(hunter, pq, q);
		}
		if (changed) {
			hunter.setAttached(QuestStore.PLAYER, pq);
		}
	}

	/**
	 * Killing a quest's creature that is somebody's property — a caravan's pack animal, a robbery's target — is robbery,
	 * reported to the law. A drover still standing near is a witness.
	 */
	private static void reportKill(ServerLevel level, ServerPlayer killer, LivingEntity victim, @org.jspecify.annotations.Nullable ServerPlayer owner,
		UUID questId) {
		boolean escort = victim.entityTags().contains(QuestSpawns.ROLE_ESCORT);
		if (!escort && !victim.entityTags().contains(QuestSpawns.CRIMINAL)) {
			return;
		}
		if (!QuestManager.config().living().reportCrimes()) {
			return;
		}
		QuestInstance q = owner == null ? null : QuestStore.get(owner).get(questId);
		if (q == null) {
			return;
		}
		Objective o = null;
		for (Objective x : q.objectives) {
			if ((x.kind == ObjectiveKind.ESCORT || x.kind == ObjectiveKind.AMBUSH) && !x.data.getStringOr(Objective.Keys.CRIME, "").isEmpty()
				&& (victim.getUUID().equals(x.entity) || QuestSpawns.members(x).contains(victim.getUUID()))) {
				o = x;
			}
		}
		if (o == null) {
			return;
		}
		dev.voidmc.hearth.api.LawService.Offence offence = LawBridge.offence(o.data.getStringOr(Objective.Keys.CRIME, ""));
		Settlement s = QuestWorlds.byId(level.getServer(), escort ? q.settlementId : o.settlement);
		if (offence == null || s == null) {
			return;
		}
		boolean drover = false;
		for (UUID id : QuestSpawns.members(o)) {
			Entity e = level.getEntity(id);
			if (e instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader && e.isAlive() && e.distanceTo(killer) < 32) {
				drover = true;
			}
		}
		int size = Math.max(1, Math.max(o.count, o.data.getIntOr(Objective.Keys.CARAVAN, 1)));
		LawBridge.report(offence, killer, s, victim.blockPosition(), null, o.data.getLongOr(Objective.Keys.VALUE, 0L) / size, drover);
	}

	/**
	 * The player a quest creature belongs to: online, or the one acting on it now (a fake player — a server-side automaton
	 * or the self-test — is not in the player list).
	 */
	private static @org.jspecify.annotations.Nullable ServerPlayer owner(MinecraftServer server, UUID id,
		@org.jspecify.annotations.Nullable ServerPlayer acting) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online;
		}
		return acting != null && acting.getUUID().equals(id) ? acting : null;
	}

	/** Gives the item an objective grants (stolen goods recovered from a culprit). */
	private static void grant(ServerPlayer player, QuestInstance q, Objective o) {
		String item = o.data.getStringOr(Objective.Keys.GRANT_ITEM, "");
		Identifier id = item.isEmpty() ? null : Identifier.tryParse(item);
		if (id != null) {
			String name = o.data.getStringOr(Objective.Keys.GRANT_NAME, "");
			QuestItems.give(player, QuestItems.make(id, name, o.data.getStringOr(Objective.Keys.GRANT_LORE, ""), q.id, QuestItems.ROLE_GRANT));
			player.sendSystemMessage(Component.translatable("hearth.quest.found", Component.literal(name).withStyle(ChatFormatting.GOLD))
				.withStyle(ChatFormatting.GREEN));
		}
	}

	private static void settleAndAttach(ServerPlayer owner, PlayerQuests pq, QuestInstance q) {
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
