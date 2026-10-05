package dev.voidmc.hearth.build.refine;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.kit.light.Fixture;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Debugging (not a gate): where the light pass still falls back on vanilla's invisible light block, spec by spec, so
 * the archetype can light (or seal) the place by design. Specs are the light survey's (six terrains, every culture,
 * two seeds). {@code HEARTH_HIDDEN=cathedral ./gradlew test --rerun --tests '*HiddenLightsTest*'}; each line gives the
 * spec as preview params (for {@code -Pterrain="flat;seed=..;facing=..;frontage=..;depth=..;night=true"}), why each
 * hidden light was used, its local cell and what stands under it and over it.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_HIDDEN", matches = ".+")
class HiddenLightsTest {
	static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	@Test
	void hidden() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String only = System.getenv("HEARTH_HIDDEN");
		boolean verbose = System.getenv("HEARTH_HIDDEN_ALL") != null;
		StringBuilder sb = new StringBuilder("\nHIDDEN LIGHTS\n");
		TreeMap<String, Integer> totals = new TreeMap<>();
		for (ArchetypeInfo info : service.archetypes()) {
			if (!only.equals("1") && !info.id().getPath().contains(only)) {
				continue;
			}
			int specs = 0;
			int hidden = 0;
			int lines = 0;
			for (String terrain : TERRAINS) {
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Direction facing = FACINGS[(s + c.ordinal()) % 4];
						SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
						PlotSpec spec = spec(info, c, seed, facing, probe);
						Draft d = service.draw(spec, probe);
						specs++;
						StringBuilder here = new StringBuilder();
						for (Fixture f : AutoLighter.lightLogged(d)) {
							if (f.kind() != Fixture.Kind.HIDDEN) {
								continue;
							}
							hidden++;
							for (Fixture.Part p : f.parts()) {
								here.append(String.format(Locale.ROOT, " %s@%d,%d,%d[%s|%s]", f.why(), p.x(), p.y(), p.z(), name(d, p.x(), p.y() - 1, p.z()),
									over(d, p.x(), p.y(), p.z())));
							}
						}
						if (!here.isEmpty() && (verbose || lines < 12)) {
							lines++;
							sb.append(String.format(Locale.ROOT, "%-24s %-13s %-6s seed=%d;facing=%s;frontage=%d;depth=%d %s%n", info.id().getPath(),
								c.getSerializedName(), terrain, seed, facing.getSerializedName(), d.frontage(), d.depth(), here));
						}
					}
				}
			}
			totals.put(info.id().getPath(), hidden);
			sb.append(String.format(Locale.ROOT, "== %s: %d hidden in %d blueprints%n", info.id().getPath(), hidden, specs));
		}
		sb.append("TOTALS ").append(totals).append('\n');
		System.out.println(sb);
	}

	private static String name(Draft d, int x, int y, int z) {
		var c = d.get(x, y, z);
		return c == null ? "-" : BuiltInRegistries.BLOCK.getKey(c.getBlock()).getPath();
	}

	/** The first thing drawn over a cell, and how far up. */
	private static String over(Draft d, int x, int y, int z) {
		for (int k = 1; k <= 8; k++) {
			var c = d.get(x, y + k, z);
			if (c != null && !c.isAir()) {
				return BuiltInRegistries.BLOCK.getKey(c.getBlock()).getPath() + "+" + k;
			}
		}
		return "sky";
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, SyntheticTerrain probe) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case EAST -> ox + sizeX;
			case WEST -> ox - 1;
			default -> ox + sizeX / 2;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != dev.voidmc.hearth.api.TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2,
			new CompoundTag());
	}
}
