package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RestorerTest {
	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	@Test
	void doorsAndBedsGoBackWhole() {
		BlockPos p = new BlockPos(10, 64, 10);
		assertEquals(p.above(), Restorer.partner(p, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
		assertEquals(p.below(), Restorer.partner(p, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
		var bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		assertEquals(p.east(), Restorer.partner(p, bed.setValue(BedBlock.PART, BedPart.FOOT)), "the head lies the way the bed faces");
		assertEquals(p.west(), Restorer.partner(p, bed.setValue(BedBlock.PART, BedPart.HEAD)));
		assertEquals(p.above(), Restorer.partner(p, Blocks.TALL_GRASS.defaultBlockState()), "tall plants have two halves too");
		assertNull(Restorer.partner(p, Blocks.OAK_PLANKS.defaultBlockState()));
	}
}
