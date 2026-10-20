package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.archetype.extract.GreatPithead;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * The Great Mine's living pithead (docs/LIVING_WORLD_2.md §6.1–6.2): the headframe and deck over the shaft, the shaft's
 * mouth left open for Expanse's old ladder, and a gallery off each old level with its face.
 */
class GreatPitheadTest {
	static {
		BuildTestSupport.init();
	}

	private static BlueprintImpl compile(Culture culture, Direction facing, int[] levels) {
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 9);
		CompoundTag params = new CompoundTag();
		params.put("levels", new IntArrayTag(levels));
		PlotSpec spec = BuildTestSupport.spec(GreatPithead.INFO, StyleRegistry.styleId(culture), 4242, facing, probe, params);
		return BuildTestSupport.SERVICE.compiled(spec, probe);
	}

	@Test
	void eachOldLevelGetsAGalleryWithItsFaceDownTheShaft() {
		int[] levels = {-60, -90, -120};
		BlueprintImpl bp = compile(Culture.TEMPERATE, Direction.SOUTH, levels);
		int floor = bp.spec().floorY();
		Set<Integer> faces = new HashSet<>();
		for (Poi p : bp.pois()) {
			if (p.type() == PoiType.WORK_FACE) {
				assertEquals(JobType.MINER, p.job());
				faces.add(p.pos().getY() - floor);
			}
		}
		assertEquals(Set.of(-60, -90, -120), faces, "a face at every level");
		assertEquals(1 + levels.length, bp.stageCount() > 0 ? countCampaigns(bp) : 0, "the surface works and a part a gallery");
		// a gallery is cut (air steps) at its level
		boolean cut = false;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				if (bp.pos(s, i).getY() == floor - 90 && bp.state(s, i).isAir()) {
					cut = true;
				}
			}
		}
		assertTrue(cut, "the gallery at -90 is dug");
	}

	private static int countCampaigns(BlueprintImpl bp) {
		// steps below the deck by a level's depth belong to that level's part: count the levels with steps
		Set<Integer> depths = new HashSet<>();
		depths.add(0);
		int floor = bp.spec().floorY();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				int y = bp.pos(s, i).getY() - floor;
				if (y <= -50) {
					depths.add((y + 1000) / 30);
				}
			}
		}
		return depths.size();
	}

	@Test
	void theShaftsMouthStaysOpenAndTheHeadframeStandsOverIt() {
		for (Direction facing : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}) {
			BlueprintImpl bp = compile(Culture.ALPINE, facing, new int[0]);
			PlotSpec spec = bp.spec();
			BlockPos c = spec.origin().offset(spec.sizeX() / 2, 0, spec.sizeZ() / 2);
			int floor = spec.floorY();
			boolean chain = false;
			int posts = 0;
			for (int s = 0; s < bp.stageCount(); s++) {
				for (int i = 0; i < bp.stepCount(s); i++) {
					BlockPos p = bp.pos(s, i);
					BlockState st = bp.state(s, i);
					int dx = p.getX() - c.getX();
					int dz = p.getZ() - c.getZ();
					if (Math.max(Math.abs(dx), Math.abs(dz)) <= 2 && p.getY() < floor && p.getY() >= floor - 3) {
						// (the light pass may hang a torch on the mouth's wall: it closes nothing)
						assertTrue(st.isAir() || st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(), facing + ": nothing set in the shaft's mouth to close it, got " + st + " at " + p);
					}
					if (dx == 0 && dz == 0 && st.is(Blocks.IRON_CHAIN)) {
						chain = true;
					}
					if (Math.abs(dx) == 3 && Math.abs(dz) == 3 && p.getY() == floor + 10) {
						posts++;
					}
				}
			}
			assertTrue(chain, facing + ": the winding chain hangs over the shaft");
			assertEquals(4, posts, facing + ": the headframe's four posts");
		}
	}

	@Test
	void thePitheadKeepsTheBlueprintRulesInEveryCulture() {
		for (Culture c : Culture.values()) {
			for (String terrain : List.of("flat", "steep")) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 11);
				PlotSpec spec = BuildTestSupport.spec(GreatPithead.INFO, StyleRegistry.styleId(c), 77 + c.ordinal(), Direction.EAST, probe);
				BlueprintImpl bp = BuildTestSupport.SERVICE.compiled(spec, probe);
				List<String> problems = BlueprintChecks.all(bp, probe);
				assertTrue(problems.isEmpty(), c + " on " + terrain + ": " + problems);
				int beds = 0;
				for (Poi p : bp.pois()) {
					beds += p.type() == PoiType.BED ? 1 : 0;
				}
				assertFalse(beds == 0, c + ": the miners' huts have beds");
			}
		}
	}
}
