package dev.voidmc.mettle.character.cards.legend;

import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.Mythics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Stonesong, the Deep Lantern and the Long Watch: what a still, patient holder learns of what is around them. */
public final class LegendSenses {
	public static final String STONESONG = "stonesong";
	public static final String LONG_WATCH = "long_watch";
	static final int SONG_RANGE = 24;
	static final int LANTERN_RANGE = 12;
	static final int WATCH_RANGE = 16;
	static final int WATCH_TICKS = 200;

	private static final Predicate<BlockState> VEIN = s -> s.is(BlockItemTags.DIAMOND_ORES.block()) || s.is(BlockItemTags.EMERALD_ORES.block())
		|| s.is(Blocks.ANCIENT_DEBRIS);

	private static final class Memory {
		Vec3 at = Vec3.ZERO;
		int songTicks;
		int watchTicks;
		@Nullable BlockPos hum;
		List<Integer> watched = List.of();
	}

	private static final Map<UUID, Memory> MEMORY = new HashMap<>();

	private LegendSenses() {
	}

	private static boolean has(Player player, String id) {
		return Mythics.active(player, id);
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(LegendSenses::tick);
		ServerPlayerEvents.LEAVE.register(p -> MEMORY.remove(p.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean song = has(player, "stonesong");
			boolean watch = has(player, "long_watch");
			if (!song && !watch) {
				continue;
			}
			Memory mem = MEMORY.computeIfAbsent(player.getUUID(), u -> new Memory());
			boolean still = player.position().distanceToSqr(mem.at) < 1e-4 && player.onGround();
			mem.at = player.position();
			// The Stonesong: 2 s standing still, sneaking, pickaxe in hand.
			if (song && still && player.isShiftKeyDown() && player.getMainHandItem().is(ItemTags.PICKAXES)) {
				if (++mem.songTicks >= 40 && CardCooldowns.ready(player, STONESONG)) {
					mem.songTicks = 0;
					sing(player, mem);
				}
			} else {
				mem.songTicks = 0;
			}
			// The Long Watch: 3 s standing still, sneaking, shield raised.
			if (watch && still && player.isShiftKeyDown() && player.isBlocking()) {
				if (++mem.watchTicks >= 60 && CardCooldowns.ready(player, LONG_WATCH)) {
					mem.watchTicks = 0;
					keepWatch(player, mem);
				}
			} else {
				mem.watchTicks = 0;
			}
		}
	}

	// ---------------------------------------------------------------- the Stonesong

	private static void sing(ServerPlayer player, Memory mem) {
		CardCooldowns.start(player, STONESONG, 1200);
		ServerLevel level = (ServerLevel) player.level();
		BlockPos ore = nearestVein(level, player.blockPosition(), SONG_RANGE);
		mem.hum = ore;
		if (ore == null) {
			LegendEffects.chip(player, STONESONG, Component.translatable("mettle.legend.stonesong.silent"));
			return;
		}
		// The hum comes from the vein's direction, four blocks off, for this player alone: heard, never shown.
		Vec3 eye = player.getEyePosition();
		Vec3 dir = Vec3.atCenterOf(ore).subtract(eye).normalize();
		Vec3 from = eye.add(dir.scale(4));
		BlockState state = level.getBlockState(ore);
		float pitch = state.is(Blocks.ANCIENT_DEBRIS) ? 0.6f : state.is(BlockItemTags.EMERALD_ORES.block()) ? 1.4f : 1.0f;
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_RESONATE), SoundSource.BLOCKS,
			from.x, from.y, from.z, 1.6f, pitch, level.getRandom().nextLong()));
		LegendEffects.chip(player, STONESONG, Component.translatable("mettle.legend.stonesong", state.getBlock().getName(), compass(eye, ore), height(eye, ore)));
	}

	/** The nearest natural diamond, emerald or ancient-debris block within {@code range} (never one a player placed). */
	public static @Nullable BlockPos nearestVein(ServerLevel level, BlockPos center, int range) {
		BlockPos best = null;
		double bestD = (double) range * range + 1;
		int minY = Math.max(level.getMinY(), center.getY() - range);
		int maxY = Math.min(level.getMaxY(), center.getY() + range);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = SectionPos.blockToSectionCoord(center.getX() - range); cx <= SectionPos.blockToSectionCoord(center.getX() + range); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(center.getZ() - range); cz <= SectionPos.blockToSectionCoord(center.getZ() + range); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= sections.length) {
						continue;
					}
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(VEIN)) {
						continue;
					}
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (!VEIN.test(section.getBlockState(x, y, z))) {
									continue;
								}
								pos.set(SectionPos.sectionToBlockCoord(cx) + x, SectionPos.sectionToBlockCoord(sy) + y, SectionPos.sectionToBlockCoord(cz) + z);
								double d = pos.distSqr(center);
								if (d < bestD && !PlacedBlocks.isPlaced(level, pos)) {
									bestD = d;
									best = pos.immutable();
								}
							}
						}
					}
				}
			}
		}
		return best;
	}

	private static Component compass(Vec3 from, BlockPos to) {
		double dx = to.getX() + 0.5 - from.x;
		double dz = to.getZ() + 0.5 - from.z;
		if (dx * dx + dz * dz < 4) {
			return Component.translatable("mettle.legend.dir.here");
		}
		String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
		// Minecraft's yaw: 0 is south (+z), 90 west (−x).
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		int i = (int) Math.floorMod(Math.round(yaw / 45.0), 8);
		return Component.translatable("mettle.legend.dir." + names[i]);
	}

	private static Component height(Vec3 from, BlockPos to) {
		double dy = to.getY() + 0.5 - from.y;
		return dy < -3 ? Component.translatable("mettle.legend.dir.below") : dy > 3 ? Component.translatable("mettle.legend.dir.above") : Component.empty();
	}

	/** Where this player's last Stonesong hummed from (null: silence, or never), for tests. */
	public static @Nullable BlockPos lastHum(ServerPlayer player) {
		Memory mem = MEMORY.get(player.getUUID());
		return mem == null ? null : mem.hum;
	}

	// ---------------------------------------------------------------- the Long Watch

	private static void keepWatch(ServerPlayer player, Memory mem) {
		CardCooldowns.start(player, LONG_WATCH, 600);
		ServerLevel level = (ServerLevel) player.level();
		List<Integer> ids = new ArrayList<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE),
			m -> m.isAlive() && m instanceof Enemy && m.distanceToSqr(player) <= WATCH_RANGE * WATCH_RANGE)) {
			if (ids.size() < 256) {
				ids.add(mob.getId());
			}
		}
		mem.watched = List.copyOf(ids);
		LegendPayloads.Watch watch = new LegendPayloads.Watch(mem.watched, WATCH_TICKS);
		for (ServerPlayer p : level.players()) {
			if ((p == player || CardHooks.helps(player, p) && p.distanceToSqr(player) <= WATCH_RANGE * WATCH_RANGE) && ServerPlayNetworking.canSend(p, LegendPayloads.Watch.TYPE)) {
				ServerPlayNetworking.send(p, watch);
			}
		}
		LegendEffects.chip(player, LONG_WATCH, Component.translatable("mettle.legend.long_watch", ids.size()));
	}

	/** The entities this player's last Long Watch outlined (tests). */
	public static List<Integer> lastWatch(ServerPlayer player) {
		Memory mem = MEMORY.get(player.getUUID());
		return mem == null ? List.of() : mem.watched;
	}

	// ---------------------------------------------------------------- the Deep Lantern

	/**
	 * Whether a hostile may not come into the world at {@code pos}: a Deep Lantern holder below y 0 stands within 12 blocks.
	 * Asked by natural spawning, monster spawners, trial spawners and zombie reinforcements (mixin.legend).
	 */
	public static boolean lanternForbids(ServerLevel level, Entity entity, BlockPos pos) {
		if (!LegendEffects.hostile(entity) && !(entity instanceof Enemy)) {
			return false;
		}
		Vec3 at = Vec3.atBottomCenterOf(pos);
		for (ServerPlayer player : level.players()) {
			if (player.getY() < 0 && !player.isSpectator() && player.position().distanceToSqr(at) <= LANTERN_RANGE * LANTERN_RANGE && has(player, "deep_lantern")) {
				return true;
			}
		}
		return false;
	}
}
