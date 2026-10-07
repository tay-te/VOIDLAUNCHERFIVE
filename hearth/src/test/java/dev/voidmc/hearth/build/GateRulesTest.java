package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.Gate;
import dev.voidmc.hearth.build.check.PlayChecks;
import dev.voidmc.hearth.build.check.PlayChecks.Rule;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.build.terrain.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The audit gate's play rules ({@link PlayChecks}), one small built case each that breaks the rule and one that keeps
 * it, and the {@link Gate}'s known-failure lists and switch.
 */
class GateRulesTest {
	static {
		BuildTestSupport.init();
	}

	private static final SyntheticTerrain FLAT = SyntheticTerrain.named("flat", 1);

	/** A 9 x 9 plot facing south on flat land, drawn by {@code body} in its local frame, with an entrance at the front. */
	private static List<PlayChecks.Finding> built(Consumer<Draft> body) {
		PlotSpec spec = new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(dev.voidmc.hearth.api.Culture.TEMPERATE), 1L,
			new BlockPos(0, FLAT.groundY(0, 0), 0), 9, 9, Direction.SOUTH, 16, 2, new CompoundTag());
		Draft d = new Draft(spec, BuildTestSupport.SERVICE.styles().getOrDefault(spec.style()), new Site(spec, FLAT));
		d.entrance(4, 0, 0);
		body.accept(d);
		BlueprintImpl bp = Compiler.compile(d);
		return PlayChecks.evaluate(bp, FLAT);
	}

	private static int count(List<PlayChecks.Finding> fs, Rule r) {
		return fs.stream().filter(f -> f.rule() == r).mapToInt(PlayChecks.Finding::count).sum();
	}

	/** A stone box (walls and a roof) over x0..x1, z0..z1, h high inside. */
	private static void box(Draft d, int x0, int z0, int x1, int z1, int h) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 0; y < h; y++) {
					if (wall) {
						d.set(x, y, z, Blocks.STONE_BRICKS);
					}
				}
				d.set(x, h, z, Blocks.STONE_BRICKS);
			}
		}
		// a lamp, so nothing here is about light
		d.set((x0 + x1) / 2, h - 1, (z0 + z1) / 2, States.lantern(Blocks.LANTERN, true));
	}

	@Test
	void theWalkDoesNotStandOnPots() {
		// a job site on a two-high platform, reached only by stepping onto a flower pot first
		List<PlayChecks.Finding> pot = built(d -> {
			d.set(5, 0, 4, Blocks.FLOWER_POT);
			for (int z = 3; z <= 5; z++) {
				d.set(6, 0, z, Blocks.OAK_PLANKS);
				d.set(6, 1, z, Blocks.OAK_PLANKS);
			}
			d.poi(PoiType.JOB_SITE, 6, 2, 4, Dirs.FRONT);
		});
		assertEquals(1, count(pot, Rule.WALK), pot.toString());
		// a full block for the step instead: reached
		List<PlayChecks.Finding> step = built(d -> {
			d.set(5, 0, 4, Blocks.OAK_PLANKS);
			for (int z = 3; z <= 5; z++) {
				d.set(6, 0, z, Blocks.OAK_PLANKS);
				d.set(6, 1, z, Blocks.OAK_PLANKS);
			}
			d.poi(PoiType.JOB_SITE, 6, 2, 4, Dirs.FRONT);
		});
		assertEquals(0, count(step, Rule.WALK), step.toString());
	}

	@Test
	void everyDoorHasTwoStandableSides() {
		Consumer<Draft> wall = d -> {
			for (int x = 0; x < 9; x++) {
				for (int y = 0; y < 3; y++) {
					d.set(x, y, 4, Blocks.STONE_BRICKS);
				}
			}
			d.set(4, 0, 4, States.door(Blocks.OAK_DOOR, Dirs.FRONT, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER));
			d.set(4, 1, 4, States.door(Blocks.OAK_DOOR, Dirs.FRONT, DoorHingeSide.LEFT, DoubleBlockHalf.UPPER));
		};
		assertEquals(0, count(built(wall), Rule.DOOR_SIDES));
		List<PlayChecks.Finding> blocked = built(wall.andThen(d -> d.set(4, 0, 5, Blocks.BARREL)));
		assertEquals(1, count(blocked, Rule.DOOR_SIDES), blocked.toString());
	}

	@Test
	void everyLadderTopHasAStepOff() {
		// a ladder up the face of a three-high block to its top: one steps off onto the block
		Consumer<Draft> tower = d -> {
			for (int x = 3; x <= 5; x++) {
				for (int z = 5; z <= 7; z++) {
					for (int y = 0; y < 3; y++) {
						d.set(x, y, z, Blocks.STONE_BRICKS);
					}
				}
			}
			for (int y = 0; y < 3; y++) {
				d.set(4, y, 4, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Dirs.FRONT));
			}
		};
		assertEquals(0, count(built(tower), Rule.LADDER_TOP));
		// a lid over the top and beside it: the climb ends under a ceiling with nowhere to go
		List<PlayChecks.Finding> lid = built(tower.andThen(d -> {
			for (int x = 3; x <= 5; x++) {
				for (int z = 3; z <= 7; z++) {
					d.set(x, 3, z, Blocks.STONE_BRICKS);
					d.set(x, 4, z, Blocks.STONE_BRICKS);
				}
			}
		}));
		assertEquals(1, count(lid, Rule.LADDER_TOP), lid.toString());
	}

	@Test
	void everyFurnishedRoomIsReached() {
		// a closed stone room with a table in it and no door: furnished, never reached
		List<PlayChecks.Finding> sealed = built(d -> {
			box(d, 2, 3, 7, 8, 3);
			d.set(4, 0, 5, Blocks.CRAFTING_TABLE);
		});
		assertTrue(count(sealed, Rule.FURNISHED_REACHED) >= 3, sealed.toString());
		// the same room with a doorway in its front wall
		List<PlayChecks.Finding> open = built(d -> {
			box(d, 2, 3, 7, 8, 3);
			d.set(4, 0, 5, Blocks.CRAFTING_TABLE);
			d.set(4, 0, 3, Blocks.AIR.defaultBlockState());
			d.set(4, 1, 3, Blocks.AIR.defaultBlockState());
		});
		assertEquals(0, count(open, Rule.FURNISHED_REACHED), open.toString());
	}

	@Test
	void noPieceStandsOnNothing() {
		// a tree: a trunk on the ground and its crown on the trunk
		Consumer<Draft> tree = d -> {
			for (int y = 0; y < 4; y++) {
				d.set(4, y, 5, Blocks.OAK_LOG);
			}
			for (int x = 3; x <= 5; x++) {
				for (int z = 4; z <= 6; z++) {
					d.set(x, 4, z, Blocks.OAK_LEAVES);
				}
			}
		};
		assertEquals(0, count(built(tree), Rule.FLOATING_PIECE));
		// a fence post stood on the crown: held up by leaves only
		List<PlayChecks.Finding> onLeaves = built(tree.andThen(d -> d.set(3, 5, 4, Blocks.OAK_FENCE)));
		assertEquals(1, count(onLeaves, Rule.FLOATING_PIECE), onLeaves.toString());
		// a deck of planks three blocks over the ground with nothing under it
		List<PlayChecks.Finding> deck = built(d -> {
			for (int x = 1; x <= 3; x++) {
				d.set(x, 3, 6, Blocks.OAK_SLAB);
			}
		});
		assertEquals(1, count(deck, Rule.FLOATING_PIECE), deck.toString());
	}

	@Test
	void noGapInARoof() {
		Consumer<Draft> room = d -> {
			box(d, 2, 3, 7, 8, 3);
			d.set(4, 0, 3, Blocks.AIR.defaultBlockState());
			d.set(4, 1, 3, Blocks.AIR.defaultBlockState());
		};
		assertEquals(0, count(built(room), Rule.ROOF_HOLE));
		// one roof block left out, over the room
		List<PlayChecks.Finding> gap = built(room.andThen(d -> d.set(5, 3, 6, Blocks.AIR.defaultBlockState())));
		assertEquals(1, count(gap, Rule.ROOF_HOLE), gap.toString());
		// the same gap over a fire is its smoke hole
		List<PlayChecks.Finding> smoke = built(room.andThen(d -> {
			d.set(5, 3, 6, Blocks.AIR.defaultBlockState());
			d.set(5, 0, 6, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}));
		assertEquals(0, count(smoke, Rule.ROOF_HOLE), smoke.toString());
	}

	@Test
	void knownFailuresAreReadPerArchetype(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("house.txt"), """
			# a comment
			house/courtyard_house walk door-sides   # H1, H2
			house/croft ladder-top
			""");
		Files.writeString(dir.resolve("notes.md"), "house/cottage walk\n");
		Map<String, Set<Rule>> known = Gate.load(dir);
		assertEquals(Set.of(Rule.WALK, Rule.DOOR_SIDES), known.get("house/courtyard_house"));
		assertEquals(Set.of(Rule.LADDER_TOP), known.get("house/croft"));
		assertFalse(known.containsKey("house/cottage"), "only .txt files are lists");
		Files.writeString(dir.resolve("bad.txt"), "house/hut stairs\n");
		assertThrows(IllegalArgumentException.class, () -> Gate.load(dir));
	}

	@Test
	void theSwitchDecidesWhetherARuleFails() {
		PlotSpec spec = new PlotSpec(Hearth.id("test/gate_probe"), StyleRegistry.styleId(dev.voidmc.hearth.api.Culture.TEMPERATE), 1L,
			new BlockPos(0, 64, 0), 9, 9, Direction.SOUTH, 16, 2, new CompoundTag());
		List<PlayChecks.Finding> fs = List.of(new PlayChecks.Finding(Rule.DOOR_SIDES, 2, "4/0/4 oak_door"));
		String before = System.getProperty("hearth.gate");
		try {
			System.setProperty("hearth.gate", "report");
			List<String> out = new ArrayList<>();
			Gate.apply(spec, fs, out);
			assertTrue(out.isEmpty(), "report mode adds no problem");
			System.setProperty("hearth.gate", "hard");
			Gate.apply(spec, fs, out);
			assertEquals(1, out.size(), "hard mode fails an archetype not listed as known");
			assertTrue(out.get(0).startsWith("gate door-sides: 2 "), out.get(0));
		} finally {
			if (before == null) {
				System.clearProperty("hearth.gate");
			} else {
				System.setProperty("hearth.gate", before);
			}
		}
	}
}
