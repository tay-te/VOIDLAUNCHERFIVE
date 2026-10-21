package dev.voidmc.hearth.build.archetype.wild;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.archetype.house.RoundHouse;
import dev.voidmc.hearth.build.draft.Draft;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.DyeColor;

/**
 * {@code hearth:wild/yurt}: the nomads' felt tent, the portable round house of docs/LIVING_WORLD_2.md §7.1 (the
 * {@code portable} variant): the round house's ring and cone ({@link RoundHouse}: the door, the fire under the smoke hole,
 * beds to the wall), its wall in felt over the lattice (off-white, a band of brown at the head and a red pattern at
 * breast height, as the steppe's women stitch them), its cone in grey felt over the roof poles. Pitched and struck in a
 * day: what stands of it is felt and poles.
 *
 * <p>Laid out only by a post's planner ({@code plan/wild}): a weight of 0 keeps towns from choosing it for their houses,
 * and only a steppe city could have it at all.
 *
 * <p>Params: as {@link RoundHouse}.
 */
public final class Yurt implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("wild/yurt"), ArchetypeCategory.HOUSING, Set.of(), 2,
		9, 13, 9, 13, 10, 15, SettlementTier.CITY, Set.of(Culture.STEPPE), Set.of(), List.of(), 0, 0.0);

	private static final RoundHouse ROUND = new RoundHouse();

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		ROUND.draw(d);
		felt(d);
	}

	/** The wall's courses in felt, the cone's in grey felt. */
	static void felt(Draft d) {
		Long2ObjectMap<Draft.Cell> cells = d.cells();
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		List<long[]> walls = new ArrayList<>();
		for (Long2ObjectMap.Entry<Draft.Cell> e : cells.long2ObjectEntrySet()) {
			Draft.Cell c = e.getValue();
			long k = e.getLongKey();
			if (c.stage() == StageKind.WALLS && wall(c.state())) {
				int y = Draft.keyY(k);
				lowest = Math.min(lowest, y);
				highest = Math.max(highest, y);
				walls.add(new long[]{k});
			}
		}
		for (long[] w : walls) {
			long k = w[0];
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			Block felt;
			if (y == highest) {
				felt = Blocks.WOOL.pick(DyeColor.BROWN);
			} else if (y == lowest + 1 && highest - lowest >= 3 && Math.floorMod(x + z, 3) == 0) {
				felt = Blocks.WOOL.pick(DyeColor.RED);
			} else {
				felt = Blocks.WOOL.pick(DyeColor.WHITE);
			}
			d.restate(x, y, z, felt.defaultBlockState());
		}
		List<long[]> roof = new ArrayList<>();
		for (Long2ObjectMap.Entry<Draft.Cell> e : cells.long2ObjectEntrySet()) {
			if (e.getValue().stage() == StageKind.ROOF) {
				roof.add(new long[]{e.getLongKey()});
			}
		}
		for (long[] r : roof) {
			long k = r[0];
			Draft.Cell c = cells.get(k);
			BlockState s = c.state();
			BlockState n = null;
			if (s.getBlock() instanceof StairBlock) {
				n = Blocks.WOOL_STAIRS.pick(DyeColor.LIGHT_GRAY).withPropertiesOf(s);
			} else if (s.getBlock() instanceof SlabBlock) {
				n = Blocks.WOOL_SLAB.pick(DyeColor.LIGHT_GRAY).withPropertiesOf(s);
			} else if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !s.hasProperty(BlockStateProperties.AXIS)
				&& !s.is(Blocks.CAMPFIRE)) {
				n = Blocks.WOOL.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
			}
			if (n != null) {
				d.restate(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k), n);
			}
		}
	}

	/** A wall course of plaster, mud, render or masonry: a full block that is not a post, a frame, a window or a door. */
	private static boolean wall(BlockState s) {
		if (!s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || s.hasProperty(BlockStateProperties.AXIS)) {
			return false;
		}
		Block b = s.getBlock();
		return !(b instanceof net.minecraft.world.level.block.EntityBlock) && !s.is(net.minecraft.tags.BlockTags.LOGS)
			&& !s.is(net.minecraft.tags.BlockTags.PLANKS) && !s.is(Blocks.GLASS) && !s.isAir();
	}
}
