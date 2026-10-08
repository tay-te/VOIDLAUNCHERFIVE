package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.StyleRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The building site's works (scaffolding, a fence, a pile of materials, a board roof: {@code blueprint/SiteWorks}) come
 * and go between stages and leave nothing behind: every archetype, in every culture, finishes exactly as it does without
 * them. And no chimney shows a flame: every lit campfire on a roof is closed over (art review 2 §8 items 8 and 18).
 */
class SiteWorksTest {
	private static final String[] TERRAINS = {"flat", "steep"};

	static {
		BuildTestSupport.init();
	}

	static Stream<ArchetypeInfo> archetypes() {
		return BuildTestSupport.SERVICE.archetypes().stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("archetypes")
	void worksLeaveTheFinishedBuildingAsDrawn(ArchetypeInfo info) {
		List<String> problems = new ArrayList<>();
		for (String terrain : TERRAINS) {
			for (Culture c : Culture.values()) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				long seed = 4111L + c.ordinal() * 7907L;
				Direction facing = Direction.from2DDataValue(c.ordinal() % 4);
				PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe);
				BlueprintImpl with;
				BlueprintImpl without;
				try {
					with = Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe), true);
					without = Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe), false);
				} catch (RuntimeException e) {
					continue; // a spec the archetype cannot draw is the invariants test's business
				}
				Long2ObjectOpenHashMap<BlockState> a = finished(with);
				a.long2ObjectEntrySet().removeIf(e -> e.getValue().is(Blocks.CAVE_AIR));
				Long2ObjectOpenHashMap<BlockState> b = finished(without);
				String where = c.getSerializedName() + " on " + terrain + ": ";
				for (var e : b.long2ObjectEntrySet()) {
					BlockState s = a.get(e.getLongKey());
					if (s != e.getValue()) {
						problems.add(where + BlockPos.of(e.getLongKey()) + " finishes as " + s + ", drawn " + e.getValue());
						break;
					}
				}
				for (var e : a.long2ObjectEntrySet()) {
					if (!b.containsKey(e.getLongKey())) {
						problems.add(where + "a work left standing at " + BlockPos.of(e.getLongKey()) + ": " + e.getValue());
						break;
					}
				}
				for (var e : a.long2ObjectEntrySet()) {
					BlockState s = e.getValue();
					if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
						BlockPos p = BlockPos.of(e.getLongKey());
						BlockState under = a.get(p.below().asLong());
						// on masonry well over the ground: a stack's head (a camp's fire pit on the ground is meant to burn)
						boolean onStack = under != null && under.isSolidRender() && p.getY() >= probe.groundY(p.getX(), p.getZ()) + 4;
						// open to the sky: a flame seen from the street and the air (a hearth indoors is roofed over)
						boolean open = true;
						for (int dy = 1; dy <= 64 && open; dy++) {
							BlockState over = a.get(p.above(dy).asLong());
							open = over == null || over.isAir();
						}
						if (onStack && open) {
							problems.add(where + "a bare flame on a stack at " + p + " (on " + under.getBlock() + ", stage " + stageOf(with, p) + ")");
							break;
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), info.id() + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	private static String stageOf(BlueprintImpl bp, BlockPos p) {
		String[] out = {"?"};
		for (int st = 0; st < bp.stageCount(); st++) {
			int k = st;
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					if (x == p.getX() && y == p.getY() && z == p.getZ()) {
						out[0] = bp.stageKind(k).getSerializedName();
					}
				});
			}
		}
		return out[0];
	}

	/** The last step at every position, through every stage. */
	private static Long2ObjectOpenHashMap<BlockState> finished(BlueprintImpl bp) {
		Long2ObjectOpenHashMap<BlockState> out = new Long2ObjectOpenHashMap<>();
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> out.put(BlockPos.asLong(x, y, z), state));
			}
		}
		return out;
	}
}
