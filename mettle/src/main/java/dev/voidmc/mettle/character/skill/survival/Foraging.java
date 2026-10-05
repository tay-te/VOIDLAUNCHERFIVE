package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The Forager's harvest: XP for wild plants and berries picked (never ones a player planted), Gatherer's bonus item, and
 * Living off the Land's finds in grass, ferns, bushes and leaves (the loot table {@code mettle:survival/forage}).
 * Everything goes through the loot event, so it covers breaking and picking alike and respects shears and Fortune.
 */
public final class Foraging {
	public static final ResourceKey<LootTable> FORAGE = ResourceKey.create(Registries.LOOT_TABLE, Mettle.id("survival/forage"));

	private static final Map<UUID, Integer> FINDS = new HashMap<>();
	private static final Set<BlockPos> JUST_BROKEN = new HashSet<>();

	private Foraging() {
	}

	public static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!(context.getOptional(LootContextParams.BLOCK_STATE) instanceof BlockState state)
				|| !(context.getOptional(LootContextParams.ORIGIN) instanceof Vec3 origin)) {
				return;
			}
			// Broken blocks name the breaker as THIS_ENTITY; picked ones (berry bushes, cave vines) as INTERACTING_ENTITY.
			ServerPlayer player = context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer p ? p
				: context.getOptional(LootContextParams.INTERACTING_ENTITY) instanceof ServerPlayer p2 ? p2 : null;
			if (player != null) {
				modifyDrops(context.getLevel(), player, BlockPos.containing(origin), state, drops);
			}
		});
		// Fabric's AFTER fires when the block is removed, before its drops are rolled; so a planted plant is forgotten
		// here but kept in JUST_BROKEN until the end of the tick, for the loot event that follows.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			// Forget only what this tree remembered; other trees keep their own placed blocks.
			if (isWildPlant(state) || isCover(state)) {
				if (PlacedBlocks.takePlaced(level, pos) | (upperHalf(state) && PlacedBlocks.takePlaced(level, pos.below()))) {
					JUST_BROKEN.add(pos.immutable());
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> JUST_BROKEN.clear());
	}

	public static boolean isWildPlant(BlockState state) {
		return state.is(SurvivalSkill.WILD_PLANTS);
	}

	/** Grass, ferns, bushes and natural leaves (leaves a player placed are persistent and never count). */
	public static boolean isCover(BlockState state) {
		if (state.is(BlockTags.LEAVES)) {
			return !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
		}
		return state.is(SurvivalSkill.FORAGE_COVER);
	}

	/** BlockItem#place: plants and cover a player puts down are remembered, so picking them again teaches nothing. */
	public static void placed(Level level, BlockPos pos, BlockState state) {
		if (isWildPlant(state) || (isCover(state) && !state.is(BlockTags.LEAVES))) {
			PlacedBlocks.mark(level, pos);
		}
	}

	private static boolean upperHalf(BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
	}

	private static boolean planted(Level level, BlockPos pos, BlockState state) {
		return JUST_BROKEN.contains(pos) || PlacedBlocks.isPlaced(level, pos) || (upperHalf(state) && PlacedBlocks.isPlaced(level, pos.below()));
	}

	private static void modifyDrops(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, List<ItemStack> drops) {
		if (isWildPlant(state)) {
			// An empty roll is the other half of a tall flower, or a bush with nothing on it yet.
			if (drops.isEmpty() || planted(level, pos, state)) {
				return;
			}
			SurvivalSkill.award(player, "plant", SurvivalRules.XP_PLANT);
			int gatherer = Perks.rank(player, SurvivalSkill.GATHERER);
			if (gatherer > 0 && level.getRandom().nextDouble() < SurvivalRules.GATHERER_PER_RANK * gatherer) {
				drops.add(drops.getFirst().copyWithCount(1));
				SurvivalSkill.chipThrottled(player, SurvivalSkill.GATHERER, 100, "gatherer");
			}
		} else if (isCover(state) && !planted(level, pos, state) && Perks.has(player, SurvivalSkill.LIVING_OFF_THE_LAND)
			&& level.getRandom().nextDouble() < SurvivalRules.LIVING_OFF_THE_LAND_CHANCE) {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(FORAGE);
			List<ItemStack> found = table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
			if (!found.isEmpty()) {
				drops.addAll(found);
				FINDS.merge(player.getUUID(), 1, Integer::sum);
				SurvivalSkill.chipThrottled(player, SurvivalSkill.LIVING_OFF_THE_LAND, 100, "living_off_the_land", found.getFirst().getHoverName());
			}
		}
	}

	/** Test hook: how many Living off the Land finds this player has made since joining. */
	public static int finds(ServerPlayer player) {
		return FINDS.getOrDefault(player.getUUID(), 0);
	}

	public static void forget(UUID player) {
		FINDS.remove(player);
	}
}
