package dev.voidmc.hearth.build.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Accretion (docs/buildings/age.md): every housing archetype in every culture on six terrains keeps the invariants (the
 * light rule included) as it grows; growth is deterministic and only ever adds room (a house never loses beds or bulk
 * as its count goes up); the param takes every form; other categories ignore it.
 */
class AccretionTest {
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final int[] COUNTS = {1, 2, 4, 6};

	static Stream<Arguments> housing() {
		return AgeSupport.SERVICE.archetypes().stream().filter(a -> a.category() == ArchetypeCategory.HOUSING).map(Arguments::of);
	}

	@ParameterizedTest(name = "{0} grown")
	@MethodSource("housing")
	void growthKeepsTheInvariants(ArchetypeInfo info) {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (int t = 0; t < TERRAINS.length; t++) {
			SyntheticTerrain probe = SyntheticTerrain.named(TERRAINS[t], 42);
			for (Culture c : Culture.values()) {
				if (!info.suits(c)) {
					continue;
				}
				long seed = 4093L * (t + 1) + c.ordinal() * 6151L;
				int count = COUNTS[(t + c.ordinal()) % COUNTS.length];
				Direction facing = AgeSupport.FACINGS[(t + c.ordinal()) % 4];
				CompoundTag params = new CompoundTag();
				params.putInt(Accretion.PARAM, 0);
				PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe, params);
				// what the house gets wrong as first built is its archetype's business, not accretion's
				Set<String> before = new HashSet<>();
				for (String p : BlueprintChecks.all(AgeSupport.SERVICE.compiled(base, probe), probe)) {
					before.add(DecayTest.kind(p));
				}
				BlueprintImpl bp = AgeSupport.SERVICE.compiled(AgeSupport.with(base, Accretion.PARAM, count), probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					if (!before.contains(DecayTest.kind(p))) {
						problems.add(TERRAINS[t] + " " + c.getSerializedName() + " additions " + count + " seed " + seed + " " + facing.getSerializedName() + ": " + p);
					}
				}
				n++;
			}
		}
		assertTrue(problems.isEmpty(), info.id() + ", " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	/** More additions never take away: beds and bulk only grow with the count, and the same count draws the same house. */
	@Test
	void growthOnlyAdds() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 5);
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			if (info.category() != ArchetypeCategory.HOUSING) {
				continue;
			}
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT, Culture.MEDITERRANEAN, Culture.KARST}) {
				if (!info.suits(c)) {
					continue;
				}
				CompoundTag params = new CompoundTag();
				params.putInt(Accretion.PARAM, 0);
				PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(c), 515L + c.ordinal(), Direction.SOUTH, probe, params, info.maxFrontage(),
					info.maxDepth(), 2);
				int beds = -1;
				int bulk = -1;
				for (int k = 0; k <= 5; k++) {
					PlotSpec s = AgeSupport.with(base, Accretion.PARAM, k);
					BlueprintImpl a = Compiler.compile(AgeSupport.SERVICE.draw(s, probe));
					BlueprintImpl b = Compiler.compile(AgeSupport.SERVICE.draw(s, probe));
					if (!BlueprintChecks.same(a, b)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " grows differently twice at " + k);
					}
					int solid = 0;
					for (var st : AgeSupport.blocks(a).values()) {
						if (!st.isAir()) {
							solid++;
						}
					}
					if (a.beds() < beds) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " loses beds at " + k + ": " + beds + " -> " + a.beds());
					}
					// (an oriel or a dormer opens a few blocks of wall or roof into itself: never more than a handful)
					if (solid < bulk - 10) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " shrinks at " + k + ": " + bulk + " -> " + solid);
					}
					beds = a.beds();
					bulk = solid;
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Most houses with room to grow do grow: over a range of plots, the grown house differs from the first. */
	@Test
	void housesWithRoomGrow() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 8);
		int tried = 0;
		int grew = 0;
		for (String path : new String[]{"house/cottage", "house/farmhouse", "house/townhouse", "house/rowhouse", "house/shophouse", "house/longhouse"}) {
			ArchetypeInfo info = AgeSupport.SERVICE.archetype(Hearth.id(path));
			for (Culture c : Culture.values()) {
				if (!info.suits(c)) {
					continue;
				}
				CompoundTag params = new CompoundTag();
				params.putInt(Accretion.PARAM, 0);
				PlotSpec base = AgeSupport.spec(info, StyleRegistry.styleId(c), 77L + c.ordinal(), Direction.SOUTH, probe, params, info.maxFrontage(),
					info.maxDepth(), 2);
				BlueprintImpl first = AgeSupport.SERVICE.compiled(base, probe);
				BlueprintImpl grown = AgeSupport.SERVICE.compiled(AgeSupport.with(base, Accretion.PARAM, 3), probe);
				tried++;
				if (!BlueprintChecks.same(first, grown)) {
					grew++;
				}
			}
		}
		assertTrue(grew * 10 >= tried * 7, "only " + grew + " of " + tried + " houses with room grew");
	}

	@Test
	void theParamTakesEveryFormAndOnlyHousingReadsIt() {
		ArchetypeInfo cottage = AgeSupport.SERVICE.archetype(Hearth.id("house/cottage"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		CompoundTag p0 = new CompoundTag();
		p0.putInt(Accretion.PARAM, 0);
		PlotSpec base = AgeSupport.spec(cottage, StyleRegistry.styleId(Culture.TEMPERATE), 3L, Direction.SOUTH, probe, p0, 11, 11, 2);
		BlueprintImpl two = AgeSupport.SERVICE.compiled(AgeSupport.with(base, Accretion.PARAM, 2), probe);
		CompoundTag s = base.params();
		s.putString(Accretion.PARAM, "2");
		BlueprintImpl twoString = AgeSupport.SERVICE.compiled(respec(base, s), probe);
		assertTrue(BlueprintChecks.same(two, twoString), "a count as a string grows the same");
		CompoundTag l = base.params();
		ListTag list = new ListTag();
		list.add(StringTag.valueOf("lean_to"));
		list.add(StringTag.valueOf("dormer"));
		l.put(Accretion.PARAM, list);
		CompoundTag c = base.params();
		c.putString(Accretion.PARAM, "lean_to, dormer");
		assertTrue(BlueprintChecks.same(AgeSupport.SERVICE.compiled(respec(base, l), probe), AgeSupport.SERVICE.compiled(respec(base, c), probe)),
			"a list of kinds and a comma-separated string grow the same");
		// not housing: no yard kept, nothing added
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			if (info.category() == ArchetypeCategory.HOUSING) {
				continue;
			}
			PlotSpec plain = AgeSupport.spec(info, StyleRegistry.styleId(Culture.TEMPERATE), 11L, Direction.SOUTH, probe, new CompoundTag());
			assertNull(Accretion.reserve(info, AgeSupport.with(plain, Accretion.PARAM, 3)), info.id() + " keeps a yard");
			assertTrue(BlueprintChecks.same(Compiler.compile(AgeSupport.SERVICE.draw(plain, probe)),
				Compiler.compile(AgeSupport.SERVICE.draw(AgeSupport.with(plain, Accretion.PARAM, 3), probe))), info.id() + " reads additions");
		}
		// a house without the param is drawn on its whole plot, exactly as before
		PlotSpec none = AgeSupport.spec(cottage, StyleRegistry.styleId(Culture.TEMPERATE), 3L, Direction.SOUTH, probe, new CompoundTag(), 11, 11, 2);
		assertNull(Accretion.reserve(cottage, none));
		assertTrue(Accretion.reserve(cottage, base) != null, "an 11 by 11 cottage plot that will grow keeps a yard");
	}

	private static PlotSpec respec(PlotSpec s, CompoundTag params) {
		return new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(), params);
	}
}
