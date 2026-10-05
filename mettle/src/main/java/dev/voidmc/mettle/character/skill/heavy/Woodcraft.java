package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import dev.voidmc.mettle.character.skill.MiningSkill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The axe in the forest: Feller and Rhythm of the Axe (dig speed, both sides so breaking stays predicted), Timber
 * (felling a whole trunk), Old Growth's tally and the placed-log ledger, and Stonebreaker's mace-as-pickaxe.
 *
 * <p>Logs a player placed are remembered (as Mining remembers ore), so a log cabin never counts towards Old Growth
 * and Timber never takes a wall down: it only follows natural logs, and only when they carry natural leaves.
 */
public final class Woodcraft {
	public static final String NATURAL_LOG_COUNTER = "chop_natural_log";

	/** Set while Timber breaks its extra logs, so they neither fell more trees nor count as rhythm. */
	private static boolean felling;
	/**
	 * The last log each player chopped and the length of their rhythm. One map per side: in single player the client
	 * and the server share this class, and each side predicts the same speed from its own copy.
	 */
	private static final Map<UUID, Rhythm> SERVER_RHYTHM = new HashMap<>();
	private static final Map<UUID, Rhythm> CLIENT_RHYTHM = new HashMap<>();

	private record Rhythm(long lastTick, int streak) {
	}

	private Woodcraft() {
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	private static boolean isNaturalLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	private static boolean choppingWith(Player player, BlockState state) {
		return isLog(state) && player.getMainHandItem().is(ItemTags.AXES);
	}

	// ---------------------------------------------------------------- speed (Player#getDestroySpeed, both sides)

	public static float destroySpeed(Player player, BlockState state, float speed) {
		if (choppingWith(player, state)) {
			int feller = Perks.rank(player, HeavyWeaponsSkill.FELLER);
			if (feller > 0) {
				speed *= HeavyRules.feller(feller);
			}
			if (Perks.has(player, HeavyWeaponsSkill.RHYTHM_OF_THE_AXE)) {
				Rhythm r = rhythms(player.level()).get(player.getUUID());
				if (r != null) {
					speed *= 1f + HeavyRules.rhythm(r.streak(), player.level().getGameTime() - r.lastTick());
				}
			}
		} else if (stonebreaks(player, state)) {
			// The mace digs as an iron pickaxe would: its own speed is 1, an iron pickaxe's is 6.
			float own = player.getMainHandItem().getDestroySpeed(state);
			speed *= ToolMaterial.IRON.speed() / Math.max(own, 0.1f);
		}
		return speed;
	}

	/** Stonebreaker: a mace counts as an iron pickaxe on stone, ore and deepslate (speed and drops). */
	public static boolean stonebreaks(Player player, BlockState state) {
		ItemStack hand = player.getMainHandItem();
		return hand.is(Items.MACE) && (MiningSkill.isStoneLike(state) || MiningSkill.isOre(state)) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
			&& !state.is(ToolMaterial.IRON.incorrectBlocksForDrops()) && Perks.has(player, HeavyWeaponsSkill.STONEBREAKER);
	}

	private static Map<UUID, Rhythm> rhythms(Level level) {
		return level.isClientSide() ? CLIENT_RHYTHM : SERVER_RHYTHM;
	}

	/** Block#playerWillDestroy, both sides: a log is about to fall, so the rhythm moves on. */
	public static void willDestroy(Level level, Player player, BlockState state) {
		if (felling || !choppingWith(player, state) || !Perks.has(player, HeavyWeaponsSkill.RHYTHM_OF_THE_AXE)) {
			return;
		}
		Map<UUID, Rhythm> map = rhythms(level);
		long now = level.getGameTime();
		Rhythm old = map.get(player.getUUID());
		int streak = HeavyRules.nextStreak(old == null ? 0 : old.streak(), old == null ? Long.MAX_VALUE : now - old.lastTick());
		map.put(player.getUUID(), new Rhythm(now, streak));
		if (player instanceof ServerPlayer sp && (old == null || streak != old.streak())) {
			int bonus = Math.round(100 * HeavyRules.rhythm(streak, 0));
			HeavyWeaponsSkill.chip(sp, HeavyWeaponsSkill.RHYTHM_OF_THE_AXE, bonus);
		}
	}

	// ---------------------------------------------------------------- breaking (server)

	/** After a block breaks: the placed-log ledger, Old Growth's tally and Timber. */
	public static void afterBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!isLog(state)) {
			return;
		}
		boolean placed = PlacedBlocks.takePlaced(level, pos);
		if (placed || player.isCreative() || !player.getMainHandItem().is(ItemTags.AXES)) {
			return;
		}
		FeatCounters.add(player, NATURAL_LOG_COUNTER, 1);
		if (felling || !player.isShiftKeyDown() || isLog(level.getBlockState(pos.below())) || !Perks.has(player, HeavyWeaponsSkill.TIMBER)) {
			return;
		}
		int felled = fell(level, player, pos);
		if (felled > 0) {
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.TIMBER, felled + 1);
		}
	}

	/** Timber: brings down the trunk above a cut base log, lowest first; returns how many more logs fell. */
	private static int fell(ServerLevel level, ServerPlayer player, BlockPos base) {
		List<BlockPos> trunk = HeavyRules.trunk(base, p -> isLog(level.getBlockState(p)) && !PlacedBlocks.isPlaced(level, p), HeavyRules.TIMBER_LIMIT);
		if (trunk.isEmpty() || !HeavyRules.hasCrown(trunk, p -> isNaturalLeaves(level.getBlockState(p)))) {
			return 0;
		}
		int felled = 0;
		felling = true;
		try {
			for (BlockPos p : trunk) {
				ItemStack tool = player.getMainHandItem();
				// Each log wears the axe as a swing would; stop before the last point of durability.
				if (!tool.is(ItemTags.AXES) || tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
					break;
				}
				if (player.gameMode.destroyBlock(p)) {
					felled++;
				}
			}
		} finally {
			felling = false;
		}
		return felled;
	}

	/** Old Growth: chopping wood doesn't wear an axe. Called as the axe would take its wear. */
	public static boolean freeChop(Player player, ItemStack tool, BlockState state) {
		return player instanceof ServerPlayer && isLog(state) && tool.is(ItemTags.AXES) && Perks.has(player, HeavyWeaponsSkill.OLD_GROWTH);
	}

	public static void forget(UUID player) {
		SERVER_RHYTHM.remove(player);
	}
}
