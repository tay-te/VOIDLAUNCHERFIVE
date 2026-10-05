package dev.voidmc.hearth.build.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Decay (docs/buildings/age.md): every archetype in every culture on six terrains keeps the invariants at weathered,
 * abandoned and ruined levels (the light rule only below {@link Decay#ABANDONED}, by the check's exemption); the pass is
 * deterministic, monotone (more decay never puts back what less decay took) and the identity at 0; an abandoned building
 * is nobody's bed or workplace.
 */
class DecayTest {
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final double[] LEVELS = {0.12, 0.35, 0.6, 0.85, 1.0};

	static Stream<Arguments> archetypes() {
		return AgeSupport.SERVICE.archetypes().stream().map(Arguments::of);
	}

	@ParameterizedTest(name = "{0} decayed")
	@MethodSource("archetypes")
	void decayKeepsTheInvariants(ArchetypeInfo info) {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (int t = 0; t < TERRAINS.length; t++) {
			SyntheticTerrain probe = SyntheticTerrain.named(TERRAINS[t], 42);
			for (Culture c : Culture.values()) {
				long seed = 6151L * (t + 1) + c.ordinal() * 7919L;
				double level = LEVELS[(t + c.ordinal()) % LEVELS.length];
				Direction facing = AgeSupport.FACINGS[(t + c.ordinal()) % 4];
				CompoundTag params = new CompoundTag();
				params.putDouble(Decay.PARAM, level);
				PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe, params);
				BlueprintImpl bp = AgeSupport.SERVICE.compiled(spec, probe);
				// what the building already gets wrong as built is its archetype's business, not decay's
				java.util.Set<String> before = new java.util.HashSet<>();
				for (String p : BlueprintChecks.all(AgeSupport.SERVICE.compiled(AgeSupport.with(spec, Decay.PARAM, 0.0), probe), probe)) {
					before.add(kind(p));
				}
				for (String p : BlueprintChecks.all(bp, probe)) {
					if (!before.contains(kind(p))) {
						problems.add(TERRAINS[t] + " " + c.getSerializedName() + " decay " + level + " seed " + seed + " " + facing.getSerializedName() + ": " + p);
					}
				}
				n++;
			}
		}
		assertTrue(problems.isEmpty(), info.id() + ", " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The kind of a check's problem (its words without the numbers and places). */
	static String kind(String problem) {
		String p = problem.replaceAll("BlockPos\\{[^}]*\\}", "").replaceAll("Block\\{[^}]*\\}(\\[[^\\]]*\\])?", "").replaceAll("-?\\d+", "#");
		int cut = p.indexOf(", e.g.");
		return cut > 0 ? p.substring(0, cut) : p.length() > 60 ? p.substring(0, 60) : p;
	}

	/** More decay only takes more away: what was gone at a lower level is never back as it was built. */
	@Test
	void decayIsMonotone() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 3);
		for (String path : new String[]{"house/cottage", "house/townhouse", "house/farmhouse", "house/longhouse", "commerce/inn", "civic/chapel",
			"house/round_house", "house/stilt_house"}) {
			ArchetypeInfo info = AgeSupport.SERVICE.archetype(Hearth.id(path));
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT, Culture.KARST, Culture.STEPPE, Culture.BAYOU}) {
				if (!info.cultures().isEmpty() && !info.cultures().contains(c)) {
					continue;
				}
				PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(c), 777L + c.ordinal(), Direction.SOUTH, probe, new CompoundTag());
				var built = AgeSupport.blocks(AgeSupport.SERVICE.compiled(base, probe));
				var gone = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
				for (double level : new double[]{0.3, 0.5, 0.7, 0.9, 1.0}) {
					var now = AgeSupport.blocks(AgeSupport.SERVICE.compiled(AgeSupport.with(base, Decay.PARAM, level), probe));
					for (long k : gone) {
						BlockState was = built.get(k);
						if (now.get(k) == was) {
							problems.add(path + " " + c.getSerializedName() + ": " + was + " at " + BlockPos.of(k) + " is back at decay " + level);
						}
					}
					for (var e : built.long2ObjectEntrySet()) {
						BlockState s = now.get(e.getLongKey());
						if (!e.getValue().isAir() && s != null && s.isAir()) {
							gone.add(e.getLongKey());
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(40, problems.size()))));
	}

	@Test
	void decayIsDeterministicAndZeroIsTheIdentity() {
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 9);
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(Culture.TEMPERATE), 4242L, Direction.EAST, probe, new CompoundTag());
			PlotSpec ruin = AgeSupport.with(base, Decay.PARAM, 0.7);
			BlueprintImpl a = Compiler.compile(AgeSupport.SERVICE.draw(ruin, probe));
			BlueprintImpl b = Compiler.compile(AgeSupport.SERVICE.draw(ruin, probe));
			assertTrue(BlueprintChecks.same(a, b), info.id() + " decays differently twice");
			BlueprintImpl kept = Compiler.compile(AgeSupport.SERVICE.draw(base, probe));
			BlueprintImpl zero = Compiler.compile(AgeSupport.SERVICE.draw(AgeSupport.with(base, Decay.PARAM, 0.0), probe));
			assertTrue(BlueprintChecks.same(kept, zero), info.id() + " changes at decay 0");
		}
	}

	/** From the abandonment level an empty house has no beds, workplaces or stores: only where it opens. */
	@Test
	void anAbandonedBuildingIsNobodys() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.MESA, Culture.TUNDRA}) {
				PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(c), 99L, Direction.SOUTH, probe, new CompoundTag());
				BlueprintImpl bp = AgeSupport.SERVICE.compiled(AgeSupport.with(base, Decay.PARAM, Decay.ABANDONED), probe);
				assertEquals(0, bp.beds(), info.id() + " in " + c + " still sleeps folk when abandoned");
				for (var p : bp.pois()) {
					assertTrue(p.type() == PoiType.DOOR || p.type() == PoiType.GATE || p.type() == PoiType.VIEWPOINT,
						info.id() + " in " + c + " keeps a " + p.type() + " when abandoned");
				}
				assertTrue(!bp.entrances().isEmpty(), info.id() + " in " + c + " loses its entrance");
				if (info.category() == ArchetypeCategory.HOUSING) {
					// and it is still lived in, lamps and all, just below the line
					BlueprintImpl worn = AgeSupport.SERVICE.compiled(AgeSupport.with(base, Decay.PARAM, 0.2), probe);
					assertTrue(worn.beds() >= 1, info.id() + " in " + c + " loses its beds while still lived in");
				}
			}
		}
	}

	@Test
	void theParamTakesEveryForm() {
		CompoundTag t = new CompoundTag();
		t.putFloat(Decay.PARAM, 0.5F);
		assertEquals(0.5, Decay.level(t), 1e-6);
		t.putInt(Decay.PARAM, 40);
		assertEquals(0.4, Decay.level(t), 1e-6);
		t.putInt(Decay.PARAM, 1);
		assertEquals(1.0, Decay.level(t), 1e-6);
		t.putString(Decay.PARAM, "0.75");
		assertEquals(0.75, Decay.level(t), 1e-6);
		t.putDouble(Decay.PARAM, 7);
		assertEquals(1.0, Decay.level(t), 1e-6);
		assertEquals(0, Decay.level(new CompoundTag()), 1e-9);
	}
}
