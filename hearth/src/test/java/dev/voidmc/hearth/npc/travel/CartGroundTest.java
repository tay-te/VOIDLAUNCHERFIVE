package dev.voidmc.hearth.npc.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.testing.ItemBoot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A cart stays on the ground under eaves and beside walls (lane A4's work tour found carts on roofs). */
class CartGroundTest {
	@BeforeAll
	static void boot() {
		ItemBoot.boot();
	}

	/** Ground at y 63 (top 64); whatever else is put in. */
	static final class Blocksy implements BlockGetter {
		final Map<BlockPos, BlockState> set = new HashMap<>();

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			BlockState s = this.set.get(pos);
			return s != null ? s : pos.getY() <= 63 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinY() {
			return -64;
		}
	}

	@Test
	void underEavesItKeepsToTheGround() {
		Blocksy w = new Blocksy();
		// eaves two and three blocks up over the cart's column, a roof above them
		w.set.put(new BlockPos(0, 65, 0), Blocks.OAK_PLANKS.defaultBlockState());
		w.set.put(new BlockPos(0, 66, 0), Blocks.OAK_STAIRS.defaultBlockState());
		assertEquals(64.0, CartEntity.groundY(w, 0.5, 0.5, 64.0), 1e-6);
		// even with the puller (and so the cart's guess) a block higher, on a stair
		assertEquals(64.0, CartEntity.groundY(w, 0.5, 0.5, 65.0), 1e-6);
	}

	@Test
	void itStepsUpOneAndDownSeveral() {
		Blocksy w = new Blocksy();
		w.set.put(new BlockPos(0, 64, 0), Blocks.DIRT.defaultBlockState());
		assertEquals(65.0, CartEntity.groundY(w, 0.5, 0.5, 64.0), 1e-6, "a step up");
		w.set.put(new BlockPos(1, 64, 0), Blocks.DIRT.defaultBlockState());
		w.set.put(new BlockPos(1, 65, 0), Blocks.DIRT.defaultBlockState());
		assertEquals(64.0, CartEntity.groundY(w, 2.5, 0.5, 67.0), 1e-6, "down off a bank");
		// a wall two high beside the way is not climbed
		w.set.put(new BlockPos(3, 64, 0), Blocks.STONE.defaultBlockState());
		w.set.put(new BlockPos(3, 65, 0), Blocks.STONE.defaultBlockState());
		w.set.put(new BlockPos(3, 66, 0), Blocks.STONE.defaultBlockState());
		assertEquals(64.0, CartEntity.groundY(w, 3.5, 0.5, 64.0), 1e-6, "no room over a step: stays");
	}
}
