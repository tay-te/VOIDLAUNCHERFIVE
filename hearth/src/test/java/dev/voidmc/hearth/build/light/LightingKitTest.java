package dev.voidmc.hearth.build.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

/**
 * The Lighting kit and the pass on a plain room drawn by hand: a stone room 9 × 7 inside, 4 high, a door in its front.
 * Unlit it fails the rule; lit by {@link Lighting#room} it passes with visible fittings and leaves the pass nothing to do.
 */
class LightingKitTest {
	static {
		LightSupport.init();
	}

	private static final int W = 11;
	private static final int D = 9;

	private static Draft room(Culture c, boolean unlit) {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		int floor = flat.groundY(0, 0);
		CompoundTag params = new CompoundTag();
		if (unlit) {
			params.putString("lighting", "none");
		}
		PlotSpec spec = new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(c), 1L, new BlockPos(-W / 2, floor, -D / 2), W, D, Direction.SOUTH,
			12, 2, params);
		Draft d = new Draft(spec, LightSupport.SERVICE.styles().getOrDefault(spec.style()), new Site(spec, flat));
		try (var s = d.stage(StageKind.FOUNDATION)) {
			d.fill(0, -1, 0, W - 1, -1, D - 1, Blocks.COBBLESTONE.defaultBlockState());
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= 3; y++) {
				for (int x = 0; x < W; x++) {
					d.set(x, y, 0, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(x, y, D - 1, Blocks.STONE_BRICKS.defaultBlockState());
				}
				for (int z = 0; z < D; z++) {
					d.set(0, y, z, Blocks.STONE_BRICKS.defaultBlockState());
					d.set(W - 1, y, z, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.fill(0, 4, 0, W - 1, 4, D - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.fill(1, 0, 1, W - 2, 3, D - 2, Blocks.AIR.defaultBlockState());
			// a table and a chest
			d.set(5, 0, 5, Blocks.SPRUCE_FENCE.defaultBlockState());
			d.set(5, 1, 5, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			// the door in the front wall (local z = 0 is the street side)
			d.set(5, 0, 0, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
			d.set(5, 1, 0, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}
		d.entrance(5, 0, -1);
		return d;
	}

	private static long rooms(LightCheck.Result r) {
		return r.unmet().stream().filter(n -> n.kind() == LightRules.Kind.ROOM).count();
	}

	@Test
	void anUnlitRoomFailsTheRule() {
		Draft d = room(Culture.TEMPERATE, true);
		BlueprintImpl bp = Compiler.compile(d);
		LightCheck.Result r = LightCheck.evaluate(bp, SyntheticTerrain.named("flat", 1));
		assertTrue(rooms(r) > 30, "a sealed stone room without a light is dark: " + rooms(r) + " dark cells");
	}

	@Test
	void lightingRoomLightsItWithVisibleFittings() {
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT, Culture.KARST}) {
			Draft d = room(c, false);
			int put = Lighting.room(d, 1, 1, W - 2, D - 2, 0);
			assertTrue(put > 0, c + ": Lighting.room put nothing up");
			int[] lanterns = {0};
			int[] hidden = {0};
			d.forEach((x, y, z, cell) -> {
				if (cell.state().getBlock() instanceof LanternBlock || cell.state().getLightEmission() > 0 && !cell.state().is(Blocks.LIGHT)) {
					lanterns[0]++;
				}
				if (cell.state().is(Blocks.LIGHT)) {
					hidden[0]++;
				}
			});
			assertTrue(lanterns[0] > 0, c + ": no visible light");
			assertEquals(0, hidden[0], c + ": the room was lit with hidden light blocks");
			// the compiler's pass finds the room lit; only the lamp outside the door is left to it
			BlueprintImpl bp = Compiler.compile(d);
			LightCheck.Result r = LightCheck.evaluate(bp, SyntheticTerrain.named("flat", 1));
			assertEquals(0, r.unmet().size(), c + ": " + r.unmet());
		}
	}

	@Test
	void thePassLightsTheRoomAndItsDoorOnce() {
		Draft d = room(Culture.TEMPERATE, false);
		var log = AutoLighter.lightLogged(d);
		assertFalse(log.isEmpty());
		// lit once: the compiler does not run the pass again over a draft already lit
		assertTrue(AutoLighter.lightLogged(d).isEmpty());
		BlueprintImpl bp = Compiler.compile(d);
		LightCheck.Result r = LightCheck.evaluate(bp, SyntheticTerrain.named("flat", 1));
		assertEquals(0, r.unmet().size(), r.unmet().toString());
		assertTrue(log.stream().anyMatch(f -> f.kind() == dev.voidmc.hearth.build.kit.light.Fixture.Kind.SCONCE
			|| f.kind() == dev.voidmc.hearth.build.kit.light.Fixture.Kind.TORCH || f.kind() == dev.voidmc.hearth.build.kit.light.Fixture.Kind.YARD
			|| f.kind() == dev.voidmc.hearth.build.kit.light.Fixture.Kind.POST_TOP), "no lamp by the door: " + log.stream().map(f -> f.kind()).toList());
	}
}
