package dev.voidmc.hearth.build.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.style.StyleRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * Dressings (docs/buildings/age.md): for every archetype in a spread of cultures, each kind of day keeps the contract
 * the social module relies on: steps only where the blueprint leaves air, inside what the plot allows, each held by
 * something, doorways and entrances clear (a runner excepted), the same day the same dressing; winter only in the cold
 * cultures; the festival and market actually dress the houses.
 */
class DressingsTest {
	private static final Culture[] CULTURES = {Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT, Culture.KARST, Culture.TUNDRA,
		Culture.MEDITERRANEAN, Culture.BAYOU};

	@Test
	void everyDressingKeepsTheContract() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 4);
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			for (Culture c : CULTURES) {
				if (!info.suits(c)) {
					continue;
				}
				PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), 31L + c.ordinal(), AgeSupport.FACINGS[c.ordinal() % 4], probe,
					new CompoundTag());
				BlueprintImpl bp = AgeSupport.SERVICE.compiled(spec, probe);
				Long2ObjectOpenHashMap<BlockState> built = AgeSupport.blocks(bp);
				LongOpenHashSet doors = new LongOpenHashSet();
				for (var e : built.long2ObjectEntrySet()) {
					if (e.getValue().getBlock() instanceof DoorBlock) {
						doors.add(e.getLongKey());
					}
				}
				LongOpenHashSet entrances = new LongOpenHashSet();
				for (BlockPos p : bp.entrances()) {
					entrances.add(p.asLong());
					entrances.add(p.above().asLong());
				}
				LocalFrame f = new LocalFrame(spec);
				for (Dressings.Kind kind : Dressings.Kind.values()) {
					List<Dressings.Step> steps = Dressings.steps(spec, bp, kind, 3, probe);
					assertEquals(steps, Dressings.steps(spec, bp, kind, 3, probe), info.id() + " " + c + " " + kind + " differs on the same day");
					String where = info.id().getPath() + " " + c.getSerializedName() + " " + kind.getSerializedName() + ": ";
					if (!Dressings.applies(spec, kind) && !steps.isEmpty()) {
						problems.add(where + "dresses where the kind does not apply");
					}
					LongOpenHashSet placed = new LongOpenHashSet();
					for (Dressings.Step s : steps) {
						BlockPos p = s.pos();
						BlockState was = built.get(p.asLong());
						if (was != null && !was.isAir()) {
							problems.add(where + s.state() + " at " + p + " replaces the building's " + was);
						}
						int lx = f.localX(p.getX(), p.getZ());
						int lz = f.localZ(p.getX(), p.getZ());
						int ly = p.getY() - spec.floorY();
						boolean inside = lx >= 0 && lx < spec.frontage() && lz < spec.depth() && ly < spec.maxHeight() && (lz >= 0 || lz == -1 && ly >= 3);
						if (!inside) {
							problems.add(where + s.state() + " at local " + lx + "," + ly + "," + lz + " is outside the plot");
						}
						if (doors.contains(p.asLong()) || entrances.contains(p.asLong()) && !(s.state().getBlock() instanceof CarpetBlock)) {
							problems.add(where + s.state() + " at " + p + " blocks a door or an entrance");
						}
						boolean held = false;
						for (Direction d : Direction.values()) {
							BlockPos n = p.relative(d);
							BlockState b = built.get(n.asLong());
							held |= b != null && !b.isAir() || placed.contains(n.asLong()) || n.getY() < probe.groundY(n.getX(), n.getZ());
						}
						if (!held) {
							problems.add(where + s.state() + " at " + p + " hangs in the air");
						}
						placed.add(p.asLong());
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(60, problems.size()))));
	}

	@Test
	void housesDressForTheirDays() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 6);
		for (String path : new String[]{"house/cottage", "house/townhouse", "house/shophouse"}) {
			ArchetypeInfo info = AgeSupport.SERVICE.archetype(Hearth.id(path));
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.ALPINE}) {
				PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), 9L, Direction.SOUTH, probe, new CompoundTag());
				BlueprintImpl bp = AgeSupport.SERVICE.compiled(spec, probe);
				for (Dressings.Kind kind : Dressings.Kind.values()) {
					if (!Dressings.applies(spec, kind)) {
						continue;
					}
					assertTrue(Dressings.steps(spec, bp, kind, 0, probe).size() >= 2, path + " in " + c + " barely dresses for " + kind);
				}
			}
		}
		// winter is for the cold cultures only
		ArchetypeInfo cottage = AgeSupport.SERVICE.archetype(Hearth.id("house/cottage"));
		PlotSpec warm = AgeSupport.spec(cottage, StyleRegistry.styleId(Culture.DESERT), 9L, Direction.SOUTH, probe, new CompoundTag());
		assertTrue(Dressings.steps(warm, AgeSupport.SERVICE.compiled(warm, probe), Dressings.Kind.WINTER, 0, probe).isEmpty());
	}
}
