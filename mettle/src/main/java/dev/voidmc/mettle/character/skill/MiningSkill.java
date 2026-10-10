package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.api.Skill;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mining (TREES.md §3): XP from breaking natural stone and ore, and every Mining perk. Placed blocks give nothing.
 *
 * <p>Drop perks (Prospector, Gem Cutter, Strike the Seam, Diamond Hands, Forgemaster) work through the loot event, so
 * they respect Fortune and Silk Touch like vanilla drops do. Area perks (Tunneler, Motherlode, Mountain Breaker) break
 * the extra blocks through the player's own game mode, so tool wear, drops and protection all behave normally.
 */
public final class MiningSkill {
	public static final ResourceKey<Skill> MINING = Progress.skill("mining");

	public static final Identifier QUARRYMAN = Mettle.id("mining/quarryman");
	public static final Identifier PROSPECTOR = Mettle.id("mining/prospector");
	public static final Identifier SURE_FOOTING = Mettle.id("mining/sure_footing");
	public static final Identifier HARD_HAT = Mettle.id("mining/hard_hat");
	public static final Identifier LANTERN_EYES = Mettle.id("mining/lantern_eyes");
	public static final Identifier ORE_SENSE = Mettle.id("mining/ore_sense");
	public static final Identifier ROCK_HAULER = Mettle.id("mining/rock_hauler");
	public static final Identifier TUNNELER = Mettle.id("mining/tunneler");
	public static final Identifier STRIKE_THE_SEAM = Mettle.id("mining/strike_the_seam");
	public static final Identifier GEM_CUTTER = Mettle.id("mining/gem_cutter");
	public static final Identifier DEEP_DELVER = Mettle.id("mining/deep_delver");
	public static final Identifier MOTHERLODE = Mettle.id("mining/motherlode");
	public static final Identifier MOUNTAIN_BREAKER = Mettle.id("mining/mountain_breaker");
	public static final Identifier DIAMOND_HANDS = Mettle.id("mining/diamond_hands");
	public static final Identifier DOWN_THERE = Mettle.id("mining/down_there");
	public static final Identifier FORGEMASTER = Mettle.id("mining/forgemaster");
	public static final Identifier STONESENSE = Mettle.id("mining/stonesense");

	private static final Identifier HARD_HAT_ARMOR = Mettle.id("perk/mining/hard_hat_armor");
	private static final int VEIN_LIMIT = 24;

	/** Set while an area perk breaks its extra blocks, so they do not chain into more areas. */
	private static boolean breakingArea;
	private static final Map<UUID, Set<Long>> CURRENT_VEIN = new HashMap<>();
	private static final Map<UUID, Set<Long>> CHIMED = new HashMap<>();
	private static final Map<UUID, Integer> SNEAK_TICKS = new HashMap<>();

	private MiningSkill() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel serverLevel) {
				afterBreak(serverLevel, sp, pos, state);
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player
				&& context.getOptional(LootContextParams.BLOCK_STATE) instanceof BlockState state
				&& context.getOptional(LootContextParams.ORIGIN) instanceof Vec3 origin) {
				modifyDrops(context.getLevel(), player, BlockPos.containing(origin), state, drops);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && isFallingRock(source.typeHolder().unwrapKey().orElse(null))) {
				return !(Perks.has(player, HARD_HAT) || (Perks.has(player, DEEP_DELVER) && player.getY() < 0));
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	// ---------------------------------------------------------------- XP

	public static boolean isStoneLike(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.COBBLESTONE)
			|| s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.END_STONE) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN);
	}

	public static boolean isOre(BlockState s) {
		return s.is(ConventionalBlockTags.ORES);
	}

	// XP per block (BALANCE.md §3)
	public static final double XP_STONE = 1;
	public static final double XP_DEEPSLATE = 1.5;
	public static final double XP_NETHERRACK = 0.5;
	public static final double XP_OBSIDIAN = 3;
	/** Coal, copper and nether quartz. */
	public static final double XP_COMMON_ORE = 3;
	public static final double XP_NETHER_GOLD = 4;
	/** Any other ore (modded). */
	public static final double XP_OTHER_ORE = 4;
	public static final double XP_REDSTONE = 5;
	/** Iron and lapis. */
	public static final double XP_IRON = 6;
	public static final double XP_GOLD = 8;
	public static final double XP_DIAMOND = 20;
	public static final double XP_EMERALD = 25;
	public static final double XP_DEBRIS = 30;
	public static final double XP_AMETHYST = 2;
	/** Deepslate ore variants pay this much more. */
	public static final double DEEPSLATE_ORE = 1.25;

	/** BALANCE.md §3: XP for mining a block naturally, or 0. */
	public static double baseXp(BlockState s) {
		double ore = 0;
		if (s.is(Blocks.ANCIENT_DEBRIS)) {
			ore = XP_DEBRIS;
		} else if (s.is(ConventionalBlockTags.EMERALD_ORES)) {
			ore = XP_EMERALD;
		} else if (s.is(ConventionalBlockTags.DIAMOND_ORES)) {
			ore = XP_DIAMOND;
		} else if (s.is(Blocks.NETHER_GOLD_ORE)) {
			ore = XP_NETHER_GOLD;
		} else if (s.is(ConventionalBlockTags.GOLD_ORES)) {
			ore = XP_GOLD;
		} else if (s.is(ConventionalBlockTags.IRON_ORES) || s.is(ConventionalBlockTags.LAPIS_ORES)) {
			ore = XP_IRON;
		} else if (s.is(ConventionalBlockTags.REDSTONE_ORES)) {
			ore = XP_REDSTONE;
		} else if (s.is(ConventionalBlockTags.COAL_ORES) || s.is(ConventionalBlockTags.COPPER_ORES) || s.is(ConventionalBlockTags.QUARTZ_ORES)) {
			ore = XP_COMMON_ORE;
		} else if (isOre(s)) {
			ore = XP_OTHER_ORE;
		} else if (s.is(Blocks.AMETHYST_CLUSTER)) {
			return XP_AMETHYST;
		}
		if (ore > 0) {
			return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("deepslate_") ? ore * DEEPSLATE_ORE : ore;
		}
		if (s.is(Blocks.NETHERRACK)) {
			return XP_NETHERRACK;
		}
		if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) {
			return XP_OBSIDIAN;
		}
		if (s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE)) {
			return XP_DEEPSLATE;
		}
		return isStoneLike(s) ? XP_STONE : 0;
	}

	private static void afterBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		// Only blocks that give Mining XP are Mining's to forget; other trees (logs for Heavy Weapons) keep their own.
		double xp = baseXp(state);
		boolean placed = xp > 0 && PlacedBlocks.takePlaced(level, pos);
		if (placed || player.isCreative()) {
			return;
		}
		Progress.award(player, MINING, xp);
		if (state.is(ConventionalBlockTags.DIAMOND_ORES)) {
			FeatCounters.add(player, "mine_diamond_ore", 1);
		}
		if (breakingArea || !player.isShiftKeyDown() || !player.getMainHandItem().is(ItemTags.PICKAXES)) {
			return;
		}
		breakingArea = true;
		try {
			if (isOre(state) && Perks.has(player, MOTHERLODE)) {
				for (BlockPos p : vein(level, pos, state.getBlock(), VEIN_LIMIT)) {
					if (!breakNext(player, p)) {
						break;
					}
				}
			} else if (isStoneLike(state) && Perks.has(player, MOUNTAIN_BREAKER)) {
				for (BlockPos p : plane(player, pos)) {
					BlockState s = level.getBlockState(p);
					if (isStoneLike(s) && !breakNext(player, p)) {
						break;
					}
				}
			} else if (Perks.has(player, TUNNELER)) {
				BlockPos second = Math.abs(player.getXRot()) > 50 ? pos.relative(player.getXRot() > 0 ? Direction.DOWN : Direction.UP) : pos.above();
				BlockState s = level.getBlockState(second);
				if (isStoneLike(s) || isOre(s)) {
					breakNext(player, second);
				}
			}
		} finally {
			breakingArea = false;
		}
	}

	/** Breaks one more block as the player would; false once the tool is about to break. */
	private static boolean breakNext(ServerPlayer player, BlockPos pos) {
		ItemStack tool = player.getMainHandItem();
		if (tool.isEmpty() || (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1)) {
			return false;
		}
		player.gameMode.destroyBlock(pos);
		return true;
	}

	/** The connected blocks of one kind around {@code start} (not counting it), nearest first, at most {@code limit}. */
	public static List<BlockPos> vein(ServerLevel level, BlockPos start, Block block, int limit) {
		List<BlockPos> found = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start.asLong());
		while (!queue.isEmpty() && found.size() < limit) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (seen.add(n.asLong()) && level.getBlockState(n).is(block)) {
					BlockPos copy = n.immutable();
					found.add(copy);
					queue.add(copy);
					if (found.size() >= limit) {
						break;
					}
				}
			}
		}
		return found;
	}

	/** The 3 × 3 around a block, facing the player: flat when looking steeply up or down, upright otherwise. */
	private static List<BlockPos> plane(ServerPlayer player, BlockPos centre) {
		List<BlockPos> out = new ArrayList<>();
		boolean flat = Math.abs(player.getXRot()) > 50;
		Direction facing = player.getDirection();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos p = flat ? centre.offset(a, 0, b)
					: facing.getAxis() == Direction.Axis.X ? centre.offset(0, b, a) : centre.offset(a, b, 0);
				out.add(p);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- drops

	private static void modifyDrops(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, List<ItemStack> drops) {
		if (!isOre(state) && !state.is(Blocks.AMETHYST_CLUSTER) || drops.isEmpty() || PlacedBlocks.isPlaced(level, pos)) {
			return;
		}
		var random = level.getRandom();
		// Greedy Pick (cursed): now and then an ore crumbles to the stone it sat in, and nothing else drops.
		if (isOre(state) && Perks.has(player, dev.voidmc.mettle.character.draft.Cursed.GREEDY_PICK)
			&& random.nextFloat() < dev.voidmc.mettle.character.draft.Cursed.GREEDY_PICK_CRUMBLE) {
			drops.clear();
			drops.add(new ItemStack(dev.voidmc.mettle.character.draft.Cursed.crumble(state)));
			dev.voidmc.mettle.character.signature.SignaturePerks.feedback(player, dev.voidmc.mettle.character.draft.Cursed.GREEDY_PICK,
				net.minecraft.network.chat.Component.translatable("mettle.perk.mining.greedy_pick.crumbled"));
			return;
		}
		List<ItemStack> extra = new ArrayList<>();
		ItemStack main = drops.getFirst();

		// Forgemaster (Mining 10 + Smithing 10): raw metal sometimes comes out already smelted.
		if (Perks.has(player, FORGEMASTER) && random.nextFloat() < 0.15f) {
			Item ingot = ingotFor(main);
			if (ingot != null) {
				drops.set(0, new ItemStack(ingot, main.getCount()));
				main = drops.getFirst();
			}
		}
		int prospector = Perks.rank(player, PROSPECTOR);
		if (prospector > 0 && isOre(state) && random.nextFloat() < 0.05f * prospector) {
			extra.add(main.copyWithCount(1));
		}
		if (Perks.has(player, GEM_CUTTER) && isGem(state) && random.nextFloat() < 0.10f) {
			extra.add(main.copyWithCount(1));
		}
		if (Perks.has(player, DIAMOND_HANDS) && state.is(ConventionalBlockTags.DIAMOND_ORES) && random.nextFloat() < 0.25f) {
			extra.add(main.copyWithCount(1));
		}
		// Strike the Seam: the first ore of each vein drops double.
		if (Perks.has(player, STRIKE_THE_SEAM) && isOre(state)) {
			Set<Long> vein = CURRENT_VEIN.get(player.getUUID());
			if (vein == null || !vein.contains(pos.asLong())) {
				Set<Long> next = new HashSet<>();
				next.add(pos.asLong());
				vein(level, pos, state.getBlock(), 64).forEach(p -> next.add(p.asLong()));
				CURRENT_VEIN.put(player.getUUID(), next);
				for (ItemStack d : drops) {
					extra.add(d.copy());
				}
			}
		}
		drops.addAll(extra);
	}

	private static boolean isGem(BlockState s) {
		return s.is(ConventionalBlockTags.DIAMOND_ORES) || s.is(ConventionalBlockTags.EMERALD_ORES) || s.is(ConventionalBlockTags.LAPIS_ORES)
			|| s.is(ConventionalBlockTags.QUARTZ_ORES) || s.is(Blocks.AMETHYST_CLUSTER);
	}

	private static Item ingotFor(ItemStack raw) {
		if (raw.is(Items.RAW_IRON)) {
			return Items.IRON_INGOT;
		}
		if (raw.is(Items.RAW_GOLD)) {
			return Items.GOLD_INGOT;
		}
		if (raw.is(Items.RAW_COPPER)) {
			return Items.COPPER_INGOT;
		}
		return null;
	}

	// ---------------------------------------------------------------- speed and safety (called from mixins)

	/** Player#getDestroySpeed's result, after Quarryman, Sure Footing and Deep Delver. */
	public static float destroySpeed(Player player, BlockState state, float speed) {
		int quarry = Perks.rank(player, QUARRYMAN);
		if (quarry > 0 && isStoneLike(state)) {
			speed *= 1f + 0.10f * quarry;
		}
		if (Perks.has(player, SURE_FOOTING)) {
			if (!player.onGround()) {
				speed *= 2f;
			}
			if (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
				speed *= 2.5f;
			}
		}
		if (player.getY() < 0 && Perks.has(player, DEEP_DELVER)) {
			speed *= 1.25f;
		}
		return speed;
	}

	/** Deep Delver: 30 % less damage taken below y 0. */
	public static float damageTaken(ServerPlayer player, float damage) {
		return player.getY() < 0 && Perks.has(player, DEEP_DELVER) ? damage * 0.7f : damage;
	}

	private static boolean isFallingRock(ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
		return type == DamageTypes.FALLING_BLOCK || type == DamageTypes.FALLING_ANVIL || type == DamageTypes.FALLING_STALACTITE
			|| type == DamageTypes.STALAGMITE;
	}

	// ---------------------------------------------------------------- every half second

	private static void tick(ServerPlayer player) {
		// Hard Hat: +2 armour below y 40.
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
		boolean wantArmor = player.getY() < 40 && Perks.has(player, HARD_HAT);
		if (armor != null && wantArmor != armor.hasModifier(HARD_HAT_ARMOR)) {
			if (wantArmor) {
				armor.addTransientModifier(new AttributeModifier(HARD_HAT_ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE));
			} else {
				armor.removeModifier(HARD_HAT_ARMOR);
			}
		}
		if (player.getY() < 0 && !player.isCreative() && !player.isSpectator()) {
			FeatCounters.add(player, "ticks_below_zero", 10);
		}

		boolean prospecting = player.isShiftKeyDown() && player.getMainHandItem().is(ItemTags.PICKAXES);
		if (!prospecting) {
			SNEAK_TICKS.remove(player.getUUID());
			return;
		}
		int sneak = SNEAK_TICKS.merge(player.getUUID(), 10, Integer::sum);
		if (Perks.has(player, ORE_SENSE)) {
			oreSense(player);
		}
		// Stonesense (teaching): after two seconds of stillness, a hum towards the nearest diamond, every three seconds.
		if (sneak >= 40 && (sneak - 40) % 60 == 0 && Perks.has(player, STONESENSE)) {
			stonesense(player);
		}
	}

	private static void oreSense(ServerPlayer player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		ServerLevel level = player.level();
		Set<Long> chimed = CHIMED.computeIfAbsent(player.getUUID(), u -> new HashSet<>());
		if (chimed.size() > 512) {
			chimed.clear();
		}
		for (BlockPos p : BlockPos.betweenClosed(block.getBlockPos().offset(-3, -3, -3), block.getBlockPos().offset(3, 3, 3))) {
			if (isOre(level.getBlockState(p)) && !PlacedBlocks.isPlaced(level, p) && chimed.add(p.asLong())) {
				sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, Vec3.atCenterOf(p), 0.6f, 1.4f);
				return;
			}
		}
	}

	private static void stonesense(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos origin = player.blockPosition();
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-16, -16, -16), origin.offset(16, 16, 16))) {
			if (level.getBlockState(p).is(ConventionalBlockTags.DIAMOND_ORES)) {
				double d = p.distSqr(origin);
				if (d < best) {
					best = d;
					nearest = p.immutable();
				}
			}
		}
		if (nearest != null) {
			Vec3 eye = player.getEyePosition();
			Vec3 toward = Vec3.atCenterOf(nearest).subtract(eye).normalize().scale(4);
			sound(player, SoundEvents.BEACON_AMBIENT, eye.add(toward), 0.8f, 0.6f);
		}
	}

	private static void sound(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
			at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void forget(UUID player) {
		CURRENT_VEIN.remove(player);
		CHIMED.remove(player);
		SNEAK_TICKS.remove(player);
	}
}
