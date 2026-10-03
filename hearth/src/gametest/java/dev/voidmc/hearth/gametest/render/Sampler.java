package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** A block sampler for checking the renderer and the shape resolver: every tricky block kind, in a known layout. */
public final class Sampler implements Archetype {
	private static final ArchetypeInfo INFO = new ArchetypeInfo(Identifier.fromNamespaceAndPath("hearth", "debug/sampler"), ArchetypeCategory.LANDSCAPE,
		Set.of(), 0, 12, 12, 10, 10, 1, 12, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		d.inStage(StageKind.FOUNDATION, () -> d.fill(0, -1, 0, 11, -1, 9, Blocks.SMOOTH_STONE.defaultBlockState()));
		// a stair ring: corners must come out as outer/inner shapes
		for (int x = 1; x <= 4; x++) {
			d.set(x, 0, 1, States.stairs(Blocks.OAK_STAIRS, Dirs.BACK, Half.BOTTOM));
			d.set(x, 0, 4, States.stairs(Blocks.OAK_STAIRS, Dirs.FRONT, Half.BOTTOM));
		}
		for (int z = 2; z <= 3; z++) {
			d.set(1, 0, z, States.stairs(Blocks.OAK_STAIRS, Dirs.RIGHT, Half.BOTTOM));
			d.set(4, 0, z, States.stairs(Blocks.OAK_STAIRS, Dirs.LEFT, Half.BOTTOM));
		}
		// fences, a gate, walls with a post under a block, panes against a wall
		for (int x = 6; x <= 10; x++) {
			d.set(x, 0, 1, Blocks.SPRUCE_FENCE);
		}
		d.set(8, 0, 1, States.gate(Blocks.SPRUCE_FENCE_GATE, Dirs.FRONT, false));
		for (int x = 6; x <= 10; x++) {
			d.set(x, 0, 3, Blocks.COBBLESTONE_WALL);
		}
		d.set(10, 1, 3, Blocks.STONE_BRICKS);
		d.set(6, 0, 5, Blocks.STONE_BRICKS);
		d.set(6, 1, 5, Blocks.STONE_BRICKS);
		for (int x = 7; x <= 10; x++) {
			d.set(x, 0, 5, Blocks.GLASS_PANE);
			d.set(x, 1, 5, Blocks.GLASS_PANE);
		}
		// a door in a wall, a bed, a lantern on a chain, slabs, logs on three axes, a trapdoor shutter
		d.fill(0, 0, 7, 4, 2, 7, Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
		d.door(2, 0, 7, Blocks.SPRUCE_DOOR, Dirs.FRONT, DoorHingeSide.LEFT);
		d.set(1, 1, 6, States.trapdoor(Blocks.SPRUCE_TRAPDOOR, Dirs.FRONT, Half.BOTTOM, true));
		d.bed(6, 0, 8, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED), Direction.EAST);
		d.set(9, 3, 8, Blocks.OAK_PLANKS);
		d.set(9, 2, 8, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		d.set(9, 1, 8, States.lantern(Blocks.LANTERN, true));
		d.set(11, 0, 9, States.slab(Blocks.BRICK_SLAB, SlabType.BOTTOM));
		d.set(11, 1, 9, States.slab(Blocks.BRICK_SLAB, SlabType.TOP));
		d.set(0, 0, 9, States.axis(Blocks.OAK_LOG, Direction.Axis.X));
		d.set(1, 0, 9, States.axis(Blocks.OAK_LOG, Direction.Axis.Z));
		d.set(2, 0, 9, States.axis(Blocks.OAK_LOG, Direction.Axis.Y));
		d.tallPlant(3, 0, 9, Blocks.ROSE_BUSH);
		d.set(4, 0, 9, Blocks.POPPY);
		d.set(5, 0, 9, States.leaves(Blocks.OAK_LEAVES));
		d.set(7, 0, 9, Blocks.CHEST);
		d.set(8, 0, 9, Blocks.BARREL);
		d.set(10, 0, 9, Blocks.WATER);
		d.entrance(2, 0, 6);
	}
}
